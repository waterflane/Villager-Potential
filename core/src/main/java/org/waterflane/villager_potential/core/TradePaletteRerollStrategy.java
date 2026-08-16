package org.waterflane.villager_potential.core;

/** Controls how remembered logical trades affect palette generation. */
public enum TradePaletteRerollStrategy {
    PERSISTENT,
    VANILLA,
    WEIGHTED_MEMORY,
    EXHAUST,
    CYCLIC
}
