package org.electrosaur.trapper;

/**
 * Trapping strategy for screen printing.
 *
 * In screen printing, darker colors trap over lighter colors because:
 * - Darker inks are opaque/thicker
 * - Darker ink can successfully cover lighter ink edges
 * - Prevents lighter color from "peeking out" around edges
 * - Typical trap sizes: 2-6 points (0.028" to 0.083")
 *
 * This is the OPPOSITE of offset lithography trapping.
 */
public class ScreenPrintingTrappingStrategy implements TrappingStrategy {

    @Override
    public int calculateExpansion(int layerIndex, int totalLayers, int dpi,
                                 double minTrap, double maxTrap) {
        // Single layer case: use minimum trap
        if (totalLayers == 1) {
            return (int) Math.round(minTrap * dpi);
        }

        // REVERSED LINEAR INTERPOLATION from offset lithography
        // Darkest layer (highest index): maxTrap
        // Lightest layer (index 0): minTrap
        //
        // This is achieved by reversing the layer index in the ratio calculation
        double ratio = (double) (totalLayers - 1 - layerIndex) / (totalLayers - 1);
        double trapInches = maxTrap - (ratio * (maxTrap - minTrap));

        // Convert to pixels
        int trapPixels = (int) Math.round(trapInches * dpi);

        return trapPixels;
    }

    @Override
    public String getName() {
        return "Screen Printing";
    }

    @Override
    public String getDescription() {
        return "Darker colors trap over lighter colors. Suitable for garment printing, " +
               "posters, and textiles (2-6 points typical at 300-600 DPI).";
    }

    @Override
    public String getTrapDirection() {
        return "Dark traps over light";
    }
}
