package org.electrosaur.trapper;

/**
 * Strategy interface for calculating trap sizes in color trapping.
 *
 * Different printing technologies require different trapping approaches:
 * - Offset lithography: Lighter colors spread into darker colors
 * - Screen printing: Darker colors trap over lighter colors
 *
 * Implementations of this interface encapsulate the trap calculation logic
 * specific to each printing method.
 */
public interface TrappingStrategy {

    /**
     * Calculates the expansion (trap size) in pixels for a given layer.
     *
     * @param layerIndex The index of the layer (0 = lightest, n-1 = darkest)
     * @param totalLayers Total number of color layers
     * @param dpi Resolution in dots per inch
     * @param minTrap Minimum trap size in inches (typically for darkest layer)
     * @param maxTrap Maximum trap size in inches (typically for lightest layer)
     * @return Trap size in pixels for this layer
     */
    int calculateExpansion(int layerIndex, int totalLayers, int dpi,
                          double minTrap, double maxTrap);

    /**
     * Returns the name of this trapping strategy.
     *
     * @return Strategy name (e.g., "Offset Lithography", "Screen Printing")
     */
    String getName();

    /**
     * Returns a description of how this strategy works.
     *
     * @return Human-readable description
     */
    String getDescription();

    /**
     * Returns the trap direction for this strategy.
     *
     * @return Trap direction description (e.g., "Light spreads into dark")
     */
    String getTrapDirection();
}
