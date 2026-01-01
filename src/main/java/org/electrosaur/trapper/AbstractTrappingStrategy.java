package org.electrosaur.trapper;

/**
 * Abstract base class for trapping strategies.
 * Provides common implementation for trap expansion calculation.
 *
 * UNIVERSAL TRAPPING PRINCIPLE (applies to both offset and screen printing):
 * - Lighter colors always get MORE expansion (they go underneath)
 * - Darker colors always get LESS expansion (they trap on top)
 *
 * This achieves proper trapping in both printing methods:
 * - Offset Lithography: "Light spreads into dark" - light ink expands under dark edges
 * - Screen Printing: "Dark traps over light" - light ink expands, dark traps over it
 *
 * The terminology differs, but the mechanical implementation is identical.
 *
 * Subclasses can override methods to customize behavior for specific printing processes
 * (e.g., underbase handling for screen printing).
 */
public abstract class AbstractTrappingStrategy implements TrappingStrategy {

    /**
     * Calculate trap expansion for a layer using linear interpolation.
     *
     * Default implementation:
     * - Lightest layer (index 0): maxTrap
     * - Darkest layer (highest index): minTrap
     * - Intermediate layers: linear interpolation
     *
     * Subclasses can override to implement different trap calculations.
     */
    @Override
    public int calculateExpansion(int layerIndex, int totalLayers, int dpi,
                                 double minTrap, double maxTrap) {
        // Single layer case: use minimum trap
        if (totalLayers == 1) {
            return (int) Math.round(minTrap * dpi);
        }

        // Linear interpolation from lightest to darkest
        // Lightest layer (index 0): maxTrap
        // Darkest layer (index totalLayers-1): minTrap
        double ratio = (double) layerIndex / (totalLayers - 1);
        double trapInches = maxTrap - (ratio * (maxTrap - minTrap));

        // Convert to pixels
        int trapPixels = (int) Math.round(trapInches * dpi);

        return trapPixels;
    }

    /**
     * Returns the name of this trapping strategy.
     * Must be implemented by subclasses.
     */
    @Override
    public abstract String getName();

    /**
     * Returns a description of this trapping strategy.
     * Must be implemented by subclasses.
     */
    @Override
    public abstract String getDescription();

    /**
     * Returns a description of the trap direction.
     * Must be implemented by subclasses.
     */
    @Override
    public abstract String getTrapDirection();
}
