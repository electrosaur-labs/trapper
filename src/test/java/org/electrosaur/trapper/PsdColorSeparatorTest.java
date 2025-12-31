package org.electrosaur.trapper;

import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class PsdColorSeparatorTest {

    /**
     * Helper method to access the private parseTrapSize method via reflection
     */
    private double parseTrapSize(String spec) throws Exception {
        Method method = PsdColorSeparator.class.getDeclaredMethod("parseTrapSize", String.class);
        method.setAccessible(true);
        try {
            return (Double) method.invoke(null, spec);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap the actual exception thrown by the method
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) cause;
            }
            throw e;
        }
    }

    @Test
    public void testParseTrapSize_Decimal() throws Exception {
        // Test standard decimal formats
        assertEquals(0.03125, parseTrapSize("0.03125"), 0.0000001);
        assertEquals(0.015625, parseTrapSize("0.015625"), 0.0000001);
        assertEquals(0.0625, parseTrapSize("0.0625"), 0.0000001);
        assertEquals(0.0, parseTrapSize("0"), 0.0000001);
        assertEquals(0.0, parseTrapSize("0.0"), 0.0000001);
        assertEquals(1.0, parseTrapSize("1"), 0.0000001);
        assertEquals(1.5, parseTrapSize("1.5"), 0.0000001);
    }

    @Test
    public void testParseTrapSize_Fraction() throws Exception {
        // Test common fractional inch formats
        assertEquals(1.0 / 32.0, parseTrapSize("1/32"), 0.0000001);
        assertEquals(1.0 / 64.0, parseTrapSize("1/64"), 0.0000001);
        assertEquals(1.0 / 16.0, parseTrapSize("1/16"), 0.0000001);
        assertEquals(1.0 / 8.0, parseTrapSize("1/8"), 0.0000001);
        assertEquals(3.0 / 64.0, parseTrapSize("3/64"), 0.0000001);
        assertEquals(1.0 / 128.0, parseTrapSize("1/128"), 0.0000001);
        assertEquals(0.0, parseTrapSize("0/32"), 0.0000001);
        assertEquals(2.0, parseTrapSize("2/1"), 0.0000001);
    }

    @Test
    public void testParseTrapSize_FractionWithSpaces() throws Exception {
        // Test fractions with extra whitespace
        assertEquals(1.0 / 32.0, parseTrapSize(" 1/32 "), 0.0000001);
        assertEquals(1.0 / 64.0, parseTrapSize("1 / 64"), 0.0000001);
        assertEquals(1.0 / 16.0, parseTrapSize(" 1 / 16 "), 0.0000001);
    }

    @Test
    public void testParseTrapSize_DecimalEquivalence() throws Exception {
        // Verify that fractions and their decimal equivalents parse to the same value
        assertEquals(parseTrapSize("1/32"), parseTrapSize("0.03125"), 0.0000001);
        assertEquals(parseTrapSize("1/64"), parseTrapSize("0.015625"), 0.0000001);
        assertEquals(parseTrapSize("1/16"), parseTrapSize("0.0625"), 0.0000001);
        assertEquals(parseTrapSize("1/8"), parseTrapSize("0.125"), 0.0000001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTrapSize_InvalidFraction_TooManyParts() throws Exception {
        parseTrapSize("1/2/3");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTrapSize_InvalidFraction_DivisionByZero() throws Exception {
        parseTrapSize("1/0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTrapSize_InvalidFraction_NonNumeric() throws Exception {
        parseTrapSize("a/b");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTrapSize_InvalidDecimal() throws Exception {
        parseTrapSize("abc");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTrapSize_Empty() throws Exception {
        parseTrapSize("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTrapSize_InvalidFraction_MissingNumerator() throws Exception {
        parseTrapSize("/32");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTrapSize_InvalidFraction_MissingDenominator() throws Exception {
        parseTrapSize("1/");
    }

    @Test
    public void testParseTrapSize_NegativeValues() throws Exception {
        // Parser should accept negative values (validation happens elsewhere)
        assertEquals(-0.03125, parseTrapSize("-0.03125"), 0.0000001);
        assertEquals(-1.0 / 32.0, parseTrapSize("-1/32"), 0.0000001);
    }

    @Test
    public void testParseTrapSize_LargeFractions() throws Exception {
        // Test with larger denominators
        assertEquals(1.0 / 256.0, parseTrapSize("1/256"), 0.0000001);
        assertEquals(1.0 / 512.0, parseTrapSize("1/512"), 0.0000001);
    }

    @Test
    public void testParseTrapSize_NonUnitFractions() throws Exception {
        // Test fractions with numerators other than 1
        assertEquals(3.0 / 32.0, parseTrapSize("3/32"), 0.0000001);
        assertEquals(5.0 / 64.0, parseTrapSize("5/64"), 0.0000001);
        assertEquals(7.0 / 128.0, parseTrapSize("7/128"), 0.0000001);
    }
}
