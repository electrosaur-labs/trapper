package org.electrosaur.trapper;

/**
 * Trapping strategy for offset lithography printing.
 *
 * In offset lithography, lighter colors spread into darker colors because:
 * - Lighter inks are more transparent
 * - Expanding light into dark is less visually noticeable
 * - Dark inks define the "edge" - preserves sharp edges
 * - Typical trap sizes: 0.003" to 1/32" (0.03125")
 *
 * This is the original implementation from v1.x of Trapper.
 */
public class OffsetTrappingStrategy implements TrappingStrategy {

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

    @Override
    public String getName() {
        return "Offset Lithography";
    }

    @Override
    public String getDescription() {
        return "Lighter colors spread into darker colors. Suitable for high-precision " +
               "commercial printing (0.003\" to 1/32\" traps at 3000 DPI).";
    }

    @Override
    public String getTrapDirection() {
        return "Light spreads into dark";
    }
}
