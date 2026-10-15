package com.xtri6.grimstorage.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;

/** A running crafting request: a list of steps worked through bit by bit, each network tick. */
public final class CraftingJob {
    public static final class StepState {
        /** Null after a world load until the recipe is found on the network again (see {@link #ref}). */
        @javax.annotation.Nullable
        Pattern pattern;
        /** The saved description of the recipe, kept until it is found again. */
        @javax.annotation.Nullable
        net.minecraft.nbt.CompoundTag ref;
        final long total;
        long remaining;
        /** Processing only: primary outputs sent to a machine and not yet returned. */
        long awaiting;

        StepState(Pattern pattern, long times) {
            this.pattern = pattern;
            this.total = times;
            this.remaining = times;
        }

        StepState(net.minecraft.nbt.CompoundTag ref, long total, long remaining, long awaiting) {
            this.ref = ref;
            this.total = total;
            this.remaining = remaining;
            this.awaiting = awaiting;
        }

        /** The recipe this step runs, or null while it hasn't been found again after a world load. */
        @javax.annotation.Nullable
        public Pattern pattern() {
            return pattern;
        }

        /** Crafts (or machine runs) this step needs in all. */
        public long total() {
            return total;
        }

        /** Crafts not started yet. */
        public long remaining() {
            return remaining;
        }

        /** Machine runs sent off and not back yet. */
        public long inMachines() {
            long per = pattern == null ? 1 : pattern.primaryAmount();
            return (awaiting + per - 1) / per;
        }

        /** Crafts fully finished. */
        public long finished() {
            return Math.max(0, total - remaining - inMachines());
        }

        boolean done() {
            return remaining <= 0 && awaiting <= 0;
        }
    }

    private final UUID id;
    private final ItemKey target;
    private final long amount;
    private final String source;
    final List<StepState> steps = new ArrayList<>();
    Component status = Component.translatable("gui.grimstorage.job.starting");
    private boolean cancelled;
    /** Ticks in a row it has been waiting on something (missing items, a full machine). */
    private int waitingTicks;
    private boolean warned;
    /** Higher goes first. Moving a job up or down in the terminal changes it. */
    private int priority;
    private boolean paused;

    public int priority() {
        return priority;
    }

    void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean paused() {
        return paused;
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            status = Component.translatable("gui.grimstorage.job.paused");
        }
        waitingTicks = 0;
    }

    public boolean cancelled() {
        return cancelled;
    }

    /** True once, when it has been stuck for a minute, so the player can be told. */
    boolean shouldWarnStuck() {
        if (!warned && waitingTicks >= 1200) {
            warned = true;
            return true;
        }
        return false;
    }

    CraftingJob(ItemKey target, long amount, String source, CraftingPlanner.Plan plan) {
        this.id = UUID.randomUUID();
        this.target = target;
        this.amount = amount;
        this.source = source;
        for (CraftingPlanner.Step step : plan.steps()) {
            steps.add(new StepState(step.pattern(), step.times()));
        }
    }

    public UUID id() {
        return id;
    }

    public ItemKey target() {
        return target;
    }

    public long amount() {
        return amount;
    }

    public String source() {
        return source;
    }

    public Component status() {
        return status;
    }

    public List<StepState> steps() {
        return java.util.Collections.unmodifiableList(steps);
    }

    /** 0..1 progress: finished crafts over all crafts, machine runs on their way counting half. */
    public float progress() {
        double done = 0, all = 0;
        for (StepState s : steps) {
            all += s.total;
            done += s.finished() + s.inMachines() * 0.5;
        }
        return all <= 0 ? 1f : (float) Math.min(1, done / all);
    }

    public boolean finished() {
        if (cancelled) {
            return true;
        }
        for (StepState s : steps) {
            if (!s.done()) {
                return false;
            }
        }
        return true;
    }

    void cancel() {
        cancelled = true;
    }

    /** Processing outputs (an item, or a fluid / chemical) that came back from a machine; returns how many this job claimed. */
    long claim(Object key, long amount) {
        long claimed = 0;
        for (StepState s : steps) {
            if (s.awaiting > 0 && s.pattern != null && s.pattern.kind() == Pattern.Kind.PROCESSING && s.pattern.primaryKey().equals(key)) {
                long take = Math.min(s.awaiting, amount - claimed);
                s.awaiting -= take;
                claimed += take;
                if (claimed >= amount) {
                    break;
                }
            }
        }
        return claimed;
    }

    void tick(StorageNetwork network) {
        Component waiting = null;
        for (StepState step : steps) {
            if (step.remaining <= 0) {
                continue;
            }
            Pattern p = step.pattern;
            if (p == null) {
                // after a world load: its recipe card or machine isn't back on the network (yet)
                if (waiting == null) {
                    waiting = Component.translatable("gui.grimstorage.job.recipe_missing");
                }
                break;
            }
            if (p.kind() == Pattern.Kind.CRAFTING) {
                while (step.remaining > 0 && network.spendCraft()) {
                    Component problem = network.tryCraft(p);
                    if (problem != null) {
                        network.refundCraft();
                        if (waiting == null) {
                            waiting = problem;
                        }
                        break;
                    }
                    step.remaining--;
                }
                if (step.remaining > 0 && waiting == null && !network.hasAssemblers()) {
                    waiting = Component.translatable("gui.grimstorage.job.no_assembler");
                }
            } else {
                int sends = 0;
                while (step.remaining > 0 && sends < 4) {
                    StorageNetwork.Sent sent = network.trySendProcessing(p, step.remaining);
                    if (sent.runs() <= 0) {
                        if (waiting == null) {
                            waiting = sent.problem();
                        }
                        break;
                    }
                    step.remaining -= sent.runs();
                    step.awaiting += p.primaryAmount() * sent.runs();
                    sends++;
                }
            }
        }
        if (waiting != null) {
            status = waiting;
            waitingTicks++;
        } else {
            waitingTicks = 0;
            boolean machines = steps.stream().anyMatch(s -> s.remaining <= 0 && s.awaiting > 0);
            if (steps.stream().anyMatch(s -> s.pattern == null && !s.done())) {
                status = Component.translatable("gui.grimstorage.job.recipe_missing");
                return;
            }
            status = Component.translatable(machines ? "gui.grimstorage.job.processing" : "gui.grimstorage.job.crafting");
        }
    }

    // ------------------------------------------------------------------ saving

    net.minecraft.nbt.CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putUUID("id", id);
        tag.put("target", target.stack().save(registries));
        tag.putLong("amount", amount);
        tag.putString("source", source);
        tag.putInt("priority", priority);
        tag.putBoolean("paused", paused);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (StepState st : steps) {
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            t.put("recipe", st.pattern != null ? PatternRef.save(st.pattern, registries) : st.ref);
            t.putLong("total", st.total);
            t.putLong("remaining", st.remaining);
            t.putLong("awaiting", st.awaiting);
            list.add(t);
        }
        tag.put("steps", list);
        return tag;
    }

    private CraftingJob(UUID id, ItemKey target, long amount, String source) {
        this.id = id;
        this.target = target;
        this.amount = amount;
        this.source = source;
    }

    @javax.annotation.Nullable
    static CraftingJob load(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ItemKey target = ItemKey.of(net.minecraft.world.item.ItemStack.parse(registries, tag.getCompound("target"))
                .orElse(net.minecraft.world.item.ItemStack.EMPTY));
        if (target == null || !tag.hasUUID("id")) {
            return null;
        }
        CraftingJob job = new CraftingJob(tag.getUUID("id"), target, tag.getLong("amount"), tag.getString("source"));
        job.priority = tag.getInt("priority");
        job.paused = tag.getBoolean("paused");
        for (net.minecraft.nbt.Tag t : tag.getList("steps", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag c = (net.minecraft.nbt.CompoundTag) t;
            job.steps.add(new StepState(c.getCompound("recipe"), c.getLong("total"), c.getLong("remaining"), c.getLong("awaiting")));
        }
        job.status = Component.translatable(job.paused ? "gui.grimstorage.job.paused" : "gui.grimstorage.job.resuming");
        return job;
    }

    /** True while some step's recipe hasn't been found again since the world loaded. */
    boolean unresolved() {
        for (StepState s : steps) {
            if (s.pattern == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds each step's recipe on the network again after a world load. A machine step that was running gets its
     * machine supplied with fluids and chemicals again.
     */
    void resolve(StorageNetwork network) {
        for (StepState s : steps) {
            if (s.pattern != null || s.ref == null) {
                continue;
            }
            Pattern p = PatternRef.find(network, s.ref);
            if (p == null) {
                continue;
            }
            s.pattern = p;
            s.ref = null;
            if (s.awaiting > 0 && !p.bulkInputs().isEmpty() && !p.bulkOnlyInputs()) {
                for (MachineLink m : p.machines()) {
                    if (!m.linkRemoved()) {
                        m.onSent(p);
                    }
                }
            }
        }
    }
}
