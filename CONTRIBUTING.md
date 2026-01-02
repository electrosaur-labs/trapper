# Contributing to Trapper

Thank you for your interest in contributing to Trapper! This document provides guidelines and instructions for contributing to the project.

## Code of Conduct

This project adheres to a code of conduct that all contributors are expected to follow. Please be respectful and constructive in all interactions.

## Ways to Contribute

- **Report bugs**: Found a problem? Let us know!
- **Suggest features**: Have an idea? Open an issue to discuss it.
- **Improve documentation**: Found a typo or unclear explanation? Submit a PR.
- **Write code**: Fix bugs or implement new features.
- **Test**: Try the software and report your experience.

## Getting Started

### Prerequisites

- Java 21 or higher
- Gradle 8.5 or higher
- Git

### Setting Up Development Environment

1. **Fork the repository** on GitHub

2. **Clone your fork**:
   ```bash
   git clone https://github.com/YOUR-USERNAME/trapper.git
   cd trapper
   ```

3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/electrosaur-labs/trapper.git
   ```

4. **Build the project**:
   ```bash
   ./gradlew build
   ```

5. **Run tests**:
   ```bash
   ./gradlew test
   ```

### Running the Application

**GUI Mode**:
```bash
./gradlew runGUI
```

**Command-line Mode**:
```bash
./gradlew runColorSeparator --args="input.psd"
```

## Making Changes

### Before You Start

1. **Check existing issues** to see if your bug/feature is already being discussed
2. **Open an issue** to discuss significant changes before starting work
3. **Keep changes focused**: One feature/fix per pull request

### Development Workflow

1. **Create a branch** for your changes:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/bug-description
   ```

2. **Make your changes**:
   - Write clear, commented code
   - Follow existing code style
   - Add tests for new functionality
   - Update documentation as needed

3. **Test your changes**:
   ```bash
   # Run all tests
   ./gradlew test

   # Run specific test class
   ./gradlew test --tests PsdColorSeparatorTest

   # Test with real images
   ./gradlew runColorSeparator --args="test.psd"
   ```

4. **Commit your changes**:
   ```bash
   git add .
   git commit -m "Brief description of changes"
   ```

   Write clear commit messages:
   - Use present tense ("Add feature" not "Added feature")
   - Keep first line under 72 characters
   - Add detailed explanation in body if needed

5. **Keep your branch updated**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

6. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Open a Pull Request** on GitHub

## Coding Standards

### Java Style

- **Indentation**: 4 spaces (no tabs)
- **Line length**: Max 120 characters
- **Naming**:
  - Classes: `PascalCase`
  - Methods/variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
- **Comments**: Use Javadoc for public methods/classes
- **Imports**: No wildcard imports (`import java.util.*`)

### Example

```java
/**
 * Calculates the trap size for a given layer based on its lightness.
 *
 * @param layerIndex The index of the layer (0 = lightest)
 * @param totalLayers The total number of layers
 * @param dpi Image resolution in dots per inch
 * @return The trap size in pixels
 */
public int calculateTrapSize(int layerIndex, int totalLayers, int dpi) {
    double normalizedPosition = (double) layerIndex / (totalLayers - 1);
    double trapInches = minTrap + (maxTrap - minTrap) * normalizedPosition;
    return (int) Math.round(trapInches * dpi);
}
```

## Testing Guidelines

### Writing Tests

- **Unit tests**: Test individual methods in isolation
- **Integration tests**: Test complete workflows with real PSD files
- **Test naming**: `testMethodName_Scenario_ExpectedResult`

### Test Coverage

- All new public methods should have tests
- Bug fixes should include a test that reproduces the bug
- Aim for high coverage, but prioritize meaningful tests

### Example Test

```java
@Test
public void testParseTrapSize_Fraction_ReturnsCorrectValue() throws Exception {
    double result = parseTrapSize("1/32");
    assertEquals(0.03125, result, 0.0000001);
}

@Test(expected = IllegalArgumentException.class)
public void testParseTrapSize_InvalidFormat_ThrowsException() throws Exception {
    parseTrapSize("invalid");
}
```

## Documentation

### When to Update Documentation

- Adding new features
- Changing existing behavior
- Fixing bugs that affect usage
- Improving clarity

### What to Document

- **README.md**: User-facing features, usage examples
- **Javadoc comments**: Public API methods and classes
- **Code comments**: Complex algorithms, non-obvious decisions
- **CHANGELOG.md**: Notable changes (we'll add this soon)

## Pull Request Process

### Before Submitting

- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] New tests added for new features
- [ ] Documentation updated
- [ ] Code follows project style
- [ ] Commit messages are clear

### PR Description Template

```markdown
## Description
Brief description of what this PR does.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Refactoring

## Testing
Describe how you tested your changes.

## Related Issues
Fixes #123
```

### Review Process

1. Maintainers will review your PR
2. Address any requested changes
3. Once approved, a maintainer will merge

## Project Structure

```
trapper/
├── src/
│   ├── main/java/org/electrosaur/trapper/
│   │   ├── PsdColorSeparator.java       # Main trapping engine
│   │   ├── TrapperGUI.java              # GUI interface
│   │   ├── TrappingStrategy.java        # Strategy pattern interface
│   │   ├── OffsetTrappingStrategy.java  # Offset lithography mode
│   │   └── ScreenPrintingTrappingStrategy.java  # Screen printing mode
│   └── test/java/org/electrosaur/trapper/
│       ├── PsdColorSeparatorTest.java   # Unit tests
│       └── TrappingIntegrationTest.java # Integration tests
├── build.gradle                          # Build configuration
├── README.md                             # User documentation
└── CONTRIBUTING.md                       # This file
```

## Good First Issues

Look for issues labeled `good first issue` - these are perfect for new contributors and include:
- Documentation improvements
- Simple bug fixes
- Adding test cases
- Code cleanup

## Getting Help

- **Questions**: Open a GitHub Discussion
- **Bugs**: Open a GitHub Issue with details
- **Feature ideas**: Open an issue to discuss before implementing

## Recognition

All contributors will be acknowledged in the project. Significant contributions may be highlighted in release notes.

## License

By contributing to Trapper, you agree that your contributions will be licensed under the GPL-3.0 license.

---

Thank you for contributing to Trapper! 🎨
