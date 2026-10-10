package com.xtri6.grimstorage.client.ui;

import com.xtri6.grimstorage.ClientConfig;

/** The two colour schemes every Grim Storage screen is drawn with. Colours are ARGB. */
public record Theme(
        boolean dark,
        int panelTop, int panelBottom, int border, int borderLight,
        int slot, int slotShadow, int slotLight,
        int text, int textDim, int textFaint,
        int accent, int accentDim, int danger, int ok, int warn, int purple,
        int button, int buttonHover, int field, int hover) {

    public static final Theme DARK = new Theme(true,
            0x161D26, 0x0D1218, 0xFF2C3846, 0xFF3E5064,
            0xC00A0E13, 0xFF040608, 0xFF263240,
            0xFFE4F2FA, 0xFF8EA3B4, 0xFF5C6E7E,
            0xFF6FF2FF, 0xFF2A7F99, 0xFFFF6464, 0xFF5AE08A, 0xFFF2C94C, 0xFFB08CFF,
            0xFF1C2630, 0xFF263442, 0xFF070A0D, 0x386FF2FF);

    public static final Theme LIGHT = new Theme(false,
            0xEEF2F6, 0xD5DCE3, 0xFF8692A0, 0xFFFFFFFF,
            0xD0B3BDC7, 0xFF6F7B88, 0xFFF4F7FA,
            0xFF17212B, 0xFF4A5866, 0xFF7C8996,
            0xFF1588AD, 0xFF86CDE0, 0xFFC03030, 0xFF2E9E58, 0xFFB08000, 0xFF6A3FC0,
            0xFFCBD3DB, 0xFFDCE4EB, 0xFFF8FAFC, 0x401588AD);

    public static Theme get() {
        return ClientConfig.dark() ? DARK : LIGHT;
    }

    /** Panel colours carry the user's opacity setting; everything else stays solid for readability. */
    public int panelAlpha() {
        return Math.round(ClientConfig.opacity() * 2.55f) << 24;
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
