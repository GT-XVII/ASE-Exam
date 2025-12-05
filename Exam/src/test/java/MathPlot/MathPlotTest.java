package MathPlot;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;

import static org.junit.jupiter.api.Assertions.*;

public class MathPlotTest {

    @BeforeAll
    public static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX platform already initialized, safe to ignore
        }
    }

    // ---------- Parsing + printing (AOS) ----------

    @Test
    public void testAosParseAndPrint_SimplePolynomial() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("x^2", MathPlot.ExpressionFormat.AOS);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.AOS);

        // Assert
        assertEquals(2, printed.size(), "Function + derivative should be printed");
        assertEquals("(x ^ 2)", printed.get(0));
        assertEquals("(2 * x)", printed.get(1));
    }

    @Test
    public void testAosParseAndPrint_Constant() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("5", MathPlot.ExpressionFormat.AOS);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.AOS);

        // Assert
        assertEquals(2, printed.size());
        assertEquals("5", printed.get(0));
        assertEquals("0", printed.get(1));
    }

    @Test
    public void testAosParseAndPrint_SinX() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("sin(x)", MathPlot.ExpressionFormat.AOS);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.AOS);

        // Assert
        assertEquals(2, printed.size());
        assertEquals("sin(x)", printed.get(0));   // f(x)
        assertEquals("cos(x)", printed.get(1));   // f'(x)
    }

    // ---------- Parsing + printing (RPN) ----------

    @Test
    public void testRpnParseAndPrint_XSquared() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("x 2 ^", MathPlot.ExpressionFormat.RPN);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.RPN);

        // Assert
        assertEquals(2, printed.size());
        assertEquals("x 2 ^", printed.get(0));    // f
        assertEquals("2 x *", printed.get(1));    // f'
    }

    @Test
    public void testRpnParseAndPrint_SinX() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("x sin", MathPlot.ExpressionFormat.RPN);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.RPN);

        // Assert
        assertEquals(2, printed.size());
        assertEquals("x sin", printed.get(0));
        assertEquals("x cos", printed.get(1));    // derivative of sin(x)
    }

    // ---------- Derivative rules / simplification ----------

    @Test
    public void testDerivative_ProductRule() {
        // f(x) = x * sin(x)
        MathPlot mp = new MathPlot();

        mp.setExpression("x*sin(x)", MathPlot.ExpressionFormat.AOS);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.AOS);

        // Just check that derivative is non-constant and contains both x and sin/cos
        String derivative = printed.get(1);
        assertTrue(derivative.contains("x"), "Derivative should depend on x");
        assertTrue(derivative.contains("sin") || derivative.contains("cos"),
                "Derivative of x*sin(x) should contain trig functions");
    }

    @Test
    public void testDerivative_PowerGeneralXToX() {
        // f(x) = x^x
        MathPlot mp = new MathPlot();

        mp.setExpression("x^x", MathPlot.ExpressionFormat.AOS);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.AOS);

        String derivative = printed.get(1);
        // (x^x)' = x^x * (log(x) + 1)
        assertTrue(derivative.contains("log(x)"), "Derivative of x^x should contain log(x)");
        assertTrue(derivative.contains("(x ^ x)"), "Derivative should contain x^x factor");
    }

    // ---------- Area under curve ----------

    @Test
    public void testArea_XSquared_SymmetricInterval() {
        // Arrange
        MathPlot mp = new MathPlot();
        mp.setExpression("x^2", MathPlot.ExpressionFormat.AOS);

        // Act
        double area = mp.area(MathPlot.AreaType.Trapezoidal);

        // Assert  ∫_{-5}^{5} x^2 dx = 2 * 5^3 / 3 ≈ 83.333...
        assertTrue(area > 80.0 && area < 86.0,
                "Area for x^2 on default interval should be around 83.33, but was " + area);
    }

    @Test
    public void testArea_X_SymmetricIntervalIsZero() {
        // Arrange
        MathPlot mp = new MathPlot();
        mp.setExpression("x", MathPlot.ExpressionFormat.AOS);

        // Act
        double area = mp.area(MathPlot.AreaType.Trapezoidal);

        // Assert  ∫_{-a}^{a} x dx = 0
        assertEquals(0.0, area, 1e-1, "Area of x over symmetric interval should be ~0");
    }

    @Test
    public void testArea_DefaultMatchesRectangular() {
        // Arrange
        MathPlot mp = new MathPlot();
        mp.setExpression("x^2", MathPlot.ExpressionFormat.AOS);

        // Act
        double areaRect = mp.area(MathPlot.AreaType.Rectangular);
        double areaDefault = mp.area(); // no-arg, used by App.java

        // Assert
        assertEquals(areaRect, areaDefault, 1e-3,
                "Default area() should use the rectangular rule");
    }

    @Test
    public void testArea_RectangularAndTrapezoidalDifferForCurvedFunction() {
        // Arrange
        MathPlot mp = new MathPlot();
        mp.setExpression("exp(x)", MathPlot.ExpressionFormat.AOS);

        // Act
        double rect = mp.area(MathPlot.AreaType.Rectangular);
        double trap = mp.area(MathPlot.AreaType.Trapezoidal);

        // Assert
        assertNotEquals(rect, trap,
                "Rectangular and trapezoidal area should differ for a non-linear function like exp(x)");
    }

    // ---------- Error handling ----------

    @Test
    public void testInvalidExpression_ClearsFunction() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("x +", MathPlot.ExpressionFormat.RPN); // invalid RPN
        List<String> printed = mp.print(MathPlot.ExpressionFormat.AOS);
        double area = mp.area(MathPlot.AreaType.Trapezoidal);

        // Assert
        assertTrue(printed.isEmpty(), "Invalid expression should result in no printable output");
        assertTrue(Double.isNaN(area), "Area for invalid expression should be NaN");
    }

    // ---------- Edge cases ----------

    @Test
    public void testAosParseAndPrint_IgnoresExtraSpacesAndCase() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("  X   ^   2  ", MathPlot.ExpressionFormat.AOS);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.AOS);

        // Assert
        assertEquals(2, printed.size());
        assertEquals("(x ^ 2)", printed.get(0));
    }

    @Test
    public void testRpnParse_IgnoresExtraSpaces() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("   x    2    ^   ", MathPlot.ExpressionFormat.RPN);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.RPN);

        // Assert
        assertEquals(2, printed.size());
        assertEquals("x 2 ^", printed.get(0));
    }

    @Test
    public void testSimplification_RemovesPlusZeroAndTimesOne() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("(x+0)*1", MathPlot.ExpressionFormat.AOS);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.AOS);

        // Assert
        String functionPrinted = printed.get(0);
        assertFalse(functionPrinted.contains("+ 0"), "Expression should not contain '+ 0' after simplification");
        assertFalse(functionPrinted.contains("* 1"), "Expression should not contain '* 1' after simplification");
    }

    @Test
    public void testDiscontinuousFunction_OneOverX_AreaIsNaN() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("1/x", MathPlot.ExpressionFormat.AOS);
        double area = mp.area(MathPlot.AreaType.Trapezoidal);

        // Assert
        assertTrue(Double.isNaN(area) || Double.isInfinite(area),
                "Area of 1/x over interval containing 0 should be NaN or infinite");
    }

    @Test
    public void testInvalidFunctionName_ClearsFunction() {
        // Arrange
        MathPlot mp = new MathPlot();

        // Act
        mp.setExpression("foo(x)", MathPlot.ExpressionFormat.AOS);
        List<String> printed = mp.print(MathPlot.ExpressionFormat.AOS);
        double area = mp.area(MathPlot.AreaType.Trapezoidal);

        // Assert
        assertTrue(printed.isEmpty());
        assertTrue(Double.isNaN(area));
    }

    // ---------- Plotting tests ----------

    @Test
    public void testPlot_Cartesian_SinX_DoesNotThrow() {
        // Arrange
        MathPlot mp = new MathPlot();
        mp.setExpression("sin(x)", MathPlot.ExpressionFormat.AOS);
        Canvas canvas = new Canvas(400, 300);

        // Act & Assert
        assertDoesNotThrow(() -> mp.plot(canvas, MathPlot.PlotType.Cartesian),
                "Plotting sin(x) in Cartesian coordinates should not throw");
    }

    @Test
    public void testPlot_Cartesian_Rpn_XSquared_DoesNotThrow() {
        // Arrange
        MathPlot mp = new MathPlot();
        mp.setExpression("x 2 ^", MathPlot.ExpressionFormat.RPN);
        Canvas canvas = new Canvas(400, 300);

        // Act & Assert
        assertDoesNotThrow(() -> mp.plot(canvas, MathPlot.PlotType.Cartesian),
                "Plotting x^2 given in RPN should not throw");
    }

    @Test
    public void testPlot_Cartesian_DerivativePresent_DoesNotThrow() {
        // Arrange
        MathPlot mp = new MathPlot();
        mp.setExpression("x^3 + 2*x - 5", MathPlot.ExpressionFormat.AOS);
        Canvas canvas = new Canvas(400, 300);

        // Act & Assert
        assertDoesNotThrow(() -> mp.plot(canvas, MathPlot.PlotType.Cartesian),
                "Plotting polynomial and its derivative should not throw");
    }

    @Test
    public void testPlot_Polar_SinTheta_DoesNotThrow() {
        // Arrange
        MathPlot mp = new MathPlot();
        mp.setExpression("sin(x)", MathPlot.ExpressionFormat.AOS);
        Canvas canvas = new Canvas(400, 400);

        // Act & Assert
        assertDoesNotThrow(() -> mp.plot(canvas, MathPlot.PlotType.Polar),
                "Plotting sin(x) as polar radius should not throw");
    }

    @Test
    public void testPlot_Polar_WithDerivative_DoesNotThrow() {
        // Arrange
        MathPlot mp = new MathPlot();
        mp.setExpression("1+sin(x)", MathPlot.ExpressionFormat.AOS);
        Canvas canvas = new Canvas(400, 400);

        // Act & Assert
        assertDoesNotThrow(() -> mp.plot(canvas, MathPlot.PlotType.Polar),
                "Plotting 1+sin(x) and its derivative in polar coordinates should not throw");
    }
}