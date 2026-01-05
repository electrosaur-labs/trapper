package org.electrosaur.trapper;

/**
 * Trapping strategy for offset lithography printing.
 *
 * In offset lithography, "light spreads into dark" means:
 * - Lighter inks are expanded (more trap)
 * - Darker inks define edges (less trap)
 * - This is visually less noticeable since lighter inks are more transparent
 * - Preserves sharp edges defined by darker ink
 *
 * Typical trap sizes: 0.003" to 1/32" (0.03125") at 300 DPI
 *
 * Implementation: Light layers get maximum expansion, dark layers get minimum.
 * This is the original implementation from v1.x of Trapper.
 *
 * Uses the default trap calculation from AbstractTrappingStrategy (light expands under dark).
 */
public class OffsetTrappingStrategy extends AbstractTrappingStrategy {

    // Inherits calculateExpansion() from AbstractTrappingStrategy
    // Can override in the future if offset-specific logic is needed

    @Override
    public String getName() {
        return "Offset Lithography";
    }

    @Override
    public String getDescription() {
        return "Lighter colors spread into darker colors. Suitable for high-precision " +
               "commercial printing (0.003\" to 1/32\" traps at 300 DPI).";
    }

    @Override
    public String getTrapDirection() {
        return "Light spreads into dark";
    }
}
