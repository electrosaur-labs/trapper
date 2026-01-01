package org.electrosaur.trapper;

/**
 * Trapping strategy for screen printing.
 *
 * In screen printing, "dark traps over light" means:
 * - Lighter inks are expanded (more trap)
 * - Darker inks trap on top (less trap)
 * - Darker inks are opaque/thicker and successfully cover light ink edges
 * - Prevents lighter colors from "peeking out" around edges
 *
 * Typical trap sizes: 2-6 points (0.028" to 0.083") at 300-600 DPI
 *
 * Implementation: Light layers get maximum expansion, dark layers get minimum.
 * This is IDENTICAL to offset lithography trap calculation - the difference is only
 * in terminology and typical trap sizes, not the expansion logic.
 *
 * Uses the default trap calculation from AbstractTrappingStrategy (light expands under dark).
 * Can override in the future if screen-specific logic is needed (e.g., underbase handling).
 */
public class ScreenPrintingTrappingStrategy extends AbstractTrappingStrategy {

    // Inherits calculateExpansion() from AbstractTrappingStrategy
    // Can override in the future if screen-specific logic is needed (e.g., underbase handling)

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
