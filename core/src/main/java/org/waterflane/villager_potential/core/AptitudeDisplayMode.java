package org.waterflane.villager_potential.core;

/** Optional player-facing visibility for one profession's aptitude. */
public enum AptitudeDisplayMode {
    /** Preserves vanilla interaction with no Villager Potential text. */
    DISABLED(false, false),
    /** Shows only a localized qualitative tier. */
    QUALITATIVE(true, false),
    /** Explicit server opt-in for the tier and exact stored aptitude. */
    EXACT(true, true);

    private final boolean visible;
    private final boolean exactValueVisible;

    AptitudeDisplayMode(boolean visible, boolean exactValueVisible) {
        this.visible = visible;
        this.exactValueVisible = exactValueVisible;
    }

    public boolean visible() {
        return visible;
    }

    public boolean exactValueVisible() {
        return exactValueVisible;
    }
}
