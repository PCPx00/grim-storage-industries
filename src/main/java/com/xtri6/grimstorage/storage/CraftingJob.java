package com.xtri6.grimstorage.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;

/** A running crafting request: a list of steps worked through bit by bit, each network tick. */
public final class CraftingJob {
    public static final class StepState {
        final Pattern pattern;
        final long total;
        long remaining;
        /** Processing only: primary outputs sent to a machine and not yet returned. */
        long awaiting;

        StepState(Pattern pattern, long times) {
            this.pattern = pattern;
            this.total = times;
            this.remaining = times;
        }

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
            long per = Math.max(1, pattern.outputCount(pattern.primary()));
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

    private final UUID id = UUID.randomUUID();
    private final ItemKey target;
    private final long amount;
    private final String source;
    final List<StepState> steps = new ArrayList<>();
    Component status = Component.translatable("gui.grimstorage.job.starting");
    private boolean cancelled;

    CraftingJob(ItemKey target, long amount, String source, CraftingPlanner.Plan plan) {
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

    /** Processing outputs that came back from a machine; returns how many this job claimed. */
    long claim(ItemKey key, long amount) {
        long claimed = 0;
        for (StepState s : steps) {
            if (s.awaiting > 0 && s.pattern.kind() == Pattern.Kind.PROCESSING && s.pattern.primary().equals(key)) {
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
                    Component problem = network.trySendProcessing(p);
                    if (problem != null) {
                        if (waiting == null) {
                            waiting = problem;
                        }
                        break;
                    }
                    step.remaining--;
                    step.awaiting += p.outputCount(p.primary());
                    sends++;
                }
            }
        }
        if (waiting != null) {
            status = waiting;
        } else {
            boolean machines = steps.stream().anyMatch(s -> s.remaining <= 0 && s.awaiting > 0);
            status = Component.translatable(machines ? "gui.grimstorage.job.processing" : "gui.grimstorage.job.crafting");
        }
    }
}
