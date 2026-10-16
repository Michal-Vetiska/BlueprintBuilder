package com.blueprintbuilder.logic;

/**
 * Build order. Earlier stages are placed before later ones when their materials and supports are ready.
 */
public enum ConstructionStage {
    FOUNDATION,
    FLOOR,
    SUPPORTS,
    WALLS,
    ROOF,
    OPENINGS,
    DECOR
}
