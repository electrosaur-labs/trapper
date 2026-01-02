# Design Decisions

This document captures key architectural and design decisions made during the development of Trapper, along with the rationale behind them.

## File Format: PSD vs Multi-Layer TIFF

**Decision:** Use native PSD format for output instead of multi-layer TIFF.

**Date:** 2025-12

**Rationale:**
- **No mature open-source Java libraries** exist for writing multi-layer TIFF files with Photoshop tag 37724 (ImageSourceData)
- **TwelveMonkeys ImageIO** explicitly states multi-layer TIFF writing is "hard" in code comments
- **PSD format is well-documented** - Adobe's specification is clear and stable
- **Industry standard** - PSD is universally recognized for pre-press workflows
- **Implementation control** - Custom PSD writer gives full control over output format
- **Proven reading support** - TwelveMonkeys ImageIO reliably reads PSD files

**Alternatives considered:**
- Multi-layer TIFF with tag 37724: No reliable Java library available
- iCafe library: Partial support, would require significant development
- Aspose.PSD: Commercial library with licensing costs
- CMYK TIFF separations: Would require external tool to recombine layers

**See:** README.md "Why PSD Format (Not Multi-Layer TIFF)?" section

---

## Metadata Handling: Preservation vs Attribution

**Decision:** Preserve original XMP metadata completely intact and add only processing history.

**Date:** 2026-01-02

**Rationale:**
- **Images belong to their creators** - Trapper processes files but doesn't claim ownership
- **Software licensing ≠ Image copyright** - Trapper is GPL-3.0, but processed images retain original licensing/copyright
- **Standards-compliant approach** - Use `xmpMM:History` namespace to record processing steps (standard XMP practice)
- **Non-destructive** - Original creator, copyright, and rights information preserved exactly as-is
- **Provenance tracking** - Processing history shows file was trapped by Trapper v2.0

**What we DO:**
- Preserve all original XMP metadata as proper XML (not escaped text)
- Add `xmpMM:History` entry with:
  - `stEvt:action`: "trapped"
  - `stEvt:softwareAgent`: "Trapper v2.0 (https://github.com/electrosaur-labs/trapper)"
  - `stEvt:changed`: "/" (entire file)

**What we DON'T do:**
- Don't set `dc:creator` fields
- Don't set `dc:rights` or `xmpRights:*` fields
- Don't declare output as Public Domain (`xmpRights:Marked="False"`)
- Don't escape/dump original metadata as text into description field

**Analogy:** Just like Photoshop doesn't claim copyright on images you edit with it, Trapper doesn't claim copyright on images it processes.

**Implementation:** `PsdColorSeparator.createXMPMetadata()` method (lines 963-1033)

---

## Trapping Strategy: Universal Light-to-Dark Principle

**Decision:** Both offset lithography and screen printing modes use identical trap calculation (lighter layers expand under darker layers).

**Date:** 2025-12

**Rationale:**
- **Physics is universal** - Light colors need to spread under dark colors to prevent gaps, regardless of printing method
- **Industry confusion** - Terms "choking" and "spreading" are sometimes reversed in screen printing, but the underlying principle is the same
- **Single implementation** - Reduces code complexity and potential for bugs
- **Mode differences are cosmetic** - Only default trap sizes and terminology differ between modes

**Trap calculation:**
1. Sort colors by lightness (white → black)
2. White base layer gets 0 trap (optimization)
3. Lightest non-white layer gets maximum trap size
4. Darkest layer gets minimum trap size (usually 0 - defines edges)
5. Middle layers use linear interpolation

**Mode-specific defaults:**
- **Offset lithography**: 0 to 1/32" (0.03125") at 300 DPI
- **Screen printing**: 0 to 4-6 points (0.056-0.083") at 300-600 DPI

**Implementation:** `AbstractTrappingStrategy.calculateTrapSize()` method

---

## Compression: PackBits RLE

**Decision:** Use PackBits Run-Length Encoding (RLE) for layer image data.

**Date:** 2025-12

**Rationale:**
- **Dramatic file size reduction** - 96.6% compression (827 MB → 28 MB) on real-world images
- **Simple and reliable** - Well-established algorithm with clear specification
- **Photoshop native** - PSD format natively supports PackBits compression
- **Fast encode/decode** - No performance penalty
- **Lossless** - Perfect preservation of image data

**Alternative considered:**
- ZIP compression: More complex, not standard for PSD layer data
- No compression: Unacceptable file sizes (800+ MB)

---

## Performance: Parallel Layer Processing

**Decision:** Process each trap layer in parallel using thread pool.

**Date:** 2025-12

**Rationale:**
- **Significant speedup** - 3.2x faster on 16-core CPU (8m 12s → 2m 34s)
- **Layers are independent** - Each layer's trapping can be computed separately
- **Modern hardware** - Most machines have multi-core CPUs
- **Acceptable tradeoff** - Non-deterministic byte-level output is acceptable as long as pixel values are correct

**Known limitation:**
- Output files differ slightly between runs at byte level due to thread scheduling
- Verification tests confirm pixel-level correctness (0 differences when flattened)
- Golden reference file tests may fail due to non-determinism

**Implementation:** `PsdColorSeparator.processFile()` uses `ExecutorService` with `Runtime.availableProcessors()` threads

---

## White Layer Optimization

**Decision:** Skip trap computation for white base layers.

**Date:** 2025-12

**Rationale:**
- **White doesn't trap** - White layer is typically the substrate (paper/fabric) and doesn't need trapping
- **Performance gain** - Avoids unnecessary morphological dilation on largest layer
- **Correct semantics** - White layer should extend to edges exactly as-is
- **Common case** - Many print designs have white base layer

**Implementation:** `PsdColorSeparator.applyTrapping()` checks if color is white (RGB 255,255,255) and skips dilation

---

## DPI Awareness

**Decision:** Calculate trap sizes based on actual image DPI, not assumed resolution.

**Date:** 2025-12

**Rationale:**
- **Physical measurements** - Trap sizes are specified in physical units (inches/points)
- **Variable resolution** - Images may be 150 DPI, 300 DPI, 600 DPI, etc.
- **Accurate trapping** - 1/32" must be same physical size regardless of DPI
- **User expectations** - Users think in physical measurements, not pixels

**Implementation:**
- Extract DPI from PSD metadata (default 72 if not specified)
- Convert trap size from inches to pixels: `pixels = inches × DPI`
- GUI displays both physical and pixel measurements

---

## Testing Strategy

**Decision:** Use small (100×100 pixel) test PSD files with known colors for automated testing.

**Date:** 2025-12

**Rationale:**
- **Fast execution** - Tests run in milliseconds, not minutes
- **Version control friendly** - Test files are 19-30 KB, not hundreds of MB
- **Reproducible** - Generated programmatically with fixed seed
- **Sufficient coverage** - Small files test all code paths

**Test files:**
- `test-simple.psd`: 100×100 four-quadrant image (W/R/G/B colors)
- `test-complex.psd`: 80×80 overlapping shapes (C/M/Y/K colors)

**Test coverage:**
- 14 unit tests for trap size parsing
- 11 integration tests for end-to-end trapping
- 2 metadata preservation tests

---

## GUI vs Command-Line

**Decision:** Provide both GUI (primary) and command-line (advanced) interfaces.

**Date:** 2025-12

**Rationale:**
- **Target audience** - Primary users are non-technical (printers, designers)
- **Ease of use** - GUI provides file browsers, validation, progress feedback
- **Flexibility** - CLI enables automation, batch processing, scripting
- **Distribution** - Single executable JAR works for both modes

**GUI features:**
- File selection with native dialogs
- Mode-specific defaults (offset vs screen printing)
- Unit selection (points/inches/fractions)
- Progress bar and processing log
- Error messages with suggested fixes

**CLI usage:**
- `java -jar trapper.jar` (no args) → launches GUI
- `java -jar trapper.jar input.psd 0 1/32 offset` → CLI mode

---

## Verification System

**Decision:** Built-in verification that flattens trapped output and compares to original.

**Date:** 2025-12

**Rationale:**
- **Correctness guarantee** - Trapping must not change final appearance
- **Regression testing** - Catches bugs during development
- **User confidence** - Demonstrates output is pixel-perfect
- **Debugging aid** - Helps identify issues with trap algorithm

**Implementation:**
- Flatten original input image
- Flatten trapped output image
- Compare pixel-by-pixel
- Report number of differences (should be 0)

**Known limitation:** Byte-level comparison of files may fail due to parallel processing non-determinism, but pixel-level comparison always succeeds.

---

## Error Handling Philosophy

**Decision:** Fail fast with clear error messages rather than attempting recovery.

**Date:** 2025-12

**Rationale:**
- **Data integrity** - Print production errors are expensive; better to stop than produce incorrect output
- **Clear diagnosis** - Specific error messages help users fix issues
- **No silent failures** - Every error is reported

**Examples:**
- Invalid trap size format: Show expected formats (fractions, decimals, points)
- File read errors: Report specific file and permission issues
- DPI missing: Report and use 72 DPI default with warning
- Too many colors: Report limit and suggest color reduction

---

## Future Considerations

**Items NOT yet decided but worth documenting:**

### CMYK Color Mode Support
- Currently only RGB mode supported
- CMYK would require different color separation logic
- May need ICC color profile support
- Deferred until user demand is clear

### Batch Processing
- GUI currently processes one file at a time
- Could add batch mode for processing multiple files
- Would need job queue and aggregate progress reporting
- Deferred until requested by users

### Configurable Dilation Algorithm
- Currently uses 4-connected neighbors (N/S/E/W)
- Could support 8-connected (include diagonals)
- Would create smoother traps but larger trap sizes
- Deferred pending user feedback on current approach

### Preview Generation
- Could generate PNG preview of separated layers
- Would help users verify output before sending to printer
- Adds complexity for questionable benefit (users can open PSD)
- Deferred

---

## Lessons Learned

### Ask Clarifying Questions for Legal/Ownership Decisions

**Context:** Initial metadata implementation incorrectly set copyright fields and declared output as Public Domain.

**Learning:** When implementing features involving ownership, attribution, licensing, or legal metadata:
1. State assumptions explicitly and ask for confirmation
2. Present options when uncertain
3. Distinguish between software licensing and content licensing
4. Don't assume "standard practice" without validating

**Application:** Before implementing metadata/licensing features, ask: "Should the output claim any copyright or creator information, or just record that it was processed?"

---

## Document Change History

- 2026-01-02: Initial creation - captured key decisions from project history
