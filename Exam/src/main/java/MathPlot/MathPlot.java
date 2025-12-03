package MathPlot;

import java.util.ArrayList;
import java.util.List;
import MathPlot.Parsers.AOS;
import MathPlot.Parsers.RPN;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

public class MathPlot {
    private class Plotter implements PlotterInterface {
        private interface PlotterItem {
            void plot();
        }

        private abstract class PlotterBase implements PlotterItem {
            final protected GraphicsContext gc;
            final protected Color color;
            final protected double lineWidth;

            public PlotterBase(Color color, double lineWidth) {
                this.gc = Plotter.this.canvas.getGraphicsContext2D();
                this.color = color;
                this.lineWidth = lineWidth;
            }
        }

        final List<PlotterItem> items;
        final private Canvas canvas;
        private Point min, max;
        private Point lastMouse;

        private class Circle extends PlotterBase {
            final private Point c;
            final private double r;

            public Circle(Point c, double r, Color color, double lineWidth) {
                super(color, lineWidth);

                this.c = c;
                this.r = r;
            }

            @Override
            public void plot() {
                this.gc.setStroke(this.color);
                this.gc.setLineWidth(this.lineWidth);
                this.gc.strokeOval(-this.r + this.c.x(), -this.r + this.c.y(), 2 * this.r + this.c.x(),
                        2 * this.r + this.c.y());
            }
        }

        private class Curve extends PlotterBase {
            final private Point.Iterator ptIt;

            public Curve(Point.Iterator ptIt, Color color, double lineWidth) {
                super(color, lineWidth);

                this.ptIt = ptIt;
            }

            @Override
            public void plot() {
                this.ptIt.reset();

                if (!this.ptIt.hasNext()) {
                        return;
                }

                this.gc.setLineWidth(this.lineWidth);
                this.gc.setStroke(this.color);

                this.gc.beginPath();

                Point origin = this.ptIt.nextPoint();
                this.gc.moveTo(origin.x(), origin.y());

                while (this.ptIt.hasNext()) {
                    final Point np = this.ptIt.nextPoint();

                    if (!this.ptIt.hasBreak()) {
                        this.gc.lineTo(np.x(), np.y());
                    }

                    this.gc.moveTo(np.x(), np.y());
                }

                this.gc.stroke();
            }
        }

        private class Line extends PlotterBase {
            final private Point from;
            final private Point to;

            public Line(Point from, Point to, Color color, double lineWidth) {
                super(color, lineWidth);

                this.from = from;
                this.to = to;
            }

            @Override
            public void plot() {
                this.gc.setStroke(this.color);
                this.gc.setLineWidth(this.lineWidth);
                this.gc.strokeLine(this.from.x(), this.from.y(), this.to.x(), this.to.y());
            }
        }

        public Plotter(Canvas canvas, Point min, Point max) {
            this.min = min;
            this.max = max;
            this.items = new ArrayList<>();
            this.canvas = canvas;

            this.canvas.widthProperty().addListener(_ -> render());
            this.canvas.heightProperty().addListener(_ -> render());

            this.canvas.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    this.lastMouse = new Point(e.getX(), e.getY());
                }
            });

            this.canvas.setOnMouseDragged(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    double dx = e.getX() - this.lastMouse.x();
                    double dy = e.getY() - this.lastMouse.y();

                    double width = this.canvas.getWidth();
                    double height = this.canvas.getHeight();

                    double dxUnits = (dx / width) * (this.max.x() - this.min.x());
                    double dyUnits = (dy / height) * (this.max.y() - this.min.y());

                    this.min = new Point(this.min.x() - dxUnits, this.min.y() + dyUnits);
                    this.max = new Point(this.max.x() - dxUnits, this.max.y() + dyUnits);
                    this.lastMouse = new Point(e.getX(), e.getY());

                    render();
                }
            });

            this.canvas.addEventHandler(ScrollEvent.SCROLL, e -> {
                double zoomFactor = (e.getDeltaY() > 0) ? 0.9 : 1.1;

                double mouseX = e.getX();
                double mouseY = e.getY();

                double width = this.canvas.getWidth();
                double height = this.canvas.getHeight();

                double mouseXUnit = this.min.x() + (mouseX / width) * (this.max.x() - this.min.x());
                double mouseYUnit = this.max.y() - (mouseY / height) * (this.max.y() - this.min.y());

                double newWidth = (this.max.x() - this.min.x()) * zoomFactor;
                double newHeight = (this.max.y() - this.min.y()) * zoomFactor;

                double xMin = mouseXUnit - (mouseX - 0) / width * newWidth;
                double xMax = this.min.x() + newWidth;

                double yMax = mouseYUnit + (mouseY - 0) / height * newHeight;
                double yMin = this.max.y() - newHeight;

                this.min = new Point(xMin, yMin);
                this.max = new Point(xMax, yMax);

                render();
            });
        }

        @Override
        public void addCircle(Point c, double r, Color color, double lineWidth) {
            this.items.add(new Circle(c, r, color, lineWidth));
        }

        @Override
        public void addLine(Point from, Point to, Color color, double lineWidth) {
            this.items.add(new Line(from, to, color, lineWidth));
        }

        @Override
        public void addCurve(Point.Iterator ptIt, Color color, double lineWidth) {
            this.items.add(new Curve(ptIt, color, lineWidth));
        }

        @Override
        public Canvas getCanvas() {
            return this.canvas;
        }

        public void render() {
            double width = this.canvas.getWidth();
            double height = this.canvas.getHeight();

            final GraphicsContext gc = this.canvas.getGraphicsContext2D();

            gc.setTransform(new Affine());
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, width, height);

            Affine transform = new Affine();
            transform.appendTranslation(0, height);
            transform.appendScale(1, -1);
            transform.appendScale(width / (this.max.x() - this.min.x()),
                    height / (this.max.y() - this.min.y()));
            transform.appendTranslation(-this.min.x(), -this.min.y());

            gc.setTransform(transform);

            for (final PlotterItem item : this.items) {
                item.plot();
            }
        }
    }

    // === Expression tree and helpers (you can change here) ===

    // Expression interface
    private interface Expr {
        double eval(double x);
        Expr diff();
        Expr simplify();
        String toAOS();
        String toRPN();
    }

    // Variable x
    private static class Var implements Expr {
        @Override
        public double eval(double x) {
            return x;
        }

        @Override
        public Expr diff() {
            return new Const(1.0);
        }

        @Override
        public Expr simplify() {
            return this;
        }

        @Override
        public String toAOS() {
            return "x";
        }

        @Override
        public String toRPN() {
            return "x";
        }
    }

    // Constant numeric literal
    private static class Const implements Expr {
        final double value;

        Const(double value) {
            this.value = value;
        }

        @Override
        public double eval(double x) {
            return value;
        }

        @Override
        public Expr diff() {
            return new Const(0.0);
        }

        @Override
        public Expr simplify() {
            return this;
        }

        @Override
        public String toAOS() {
            return formatNumber(value);
        }

        @Override
        public String toRPN() {
            return formatNumber(value);
        }
    }

    // Binary operator: +, -, *, /, ^
    private static class BinOp implements Expr {
        final char op;
        final Expr left;
        final Expr right;

        BinOp(char op, Expr left, Expr right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        public double eval(double x) {
            double a = left.eval(x);
            double b = right.eval(x);
            switch (op) {
                case '+':
                    return a + b;
                case '-':
                    return a - b;
                case '*':
                    return a * b;
                case '/':
                    return a / b;
                case '^':
                    return Math.pow(a, b);
                default:
                    return Double.NaN;
            }
        }

        @Override
        public Expr diff() {
            Expr u = left;
            Expr v = right;
            Expr du = u.diff();
            Expr dv = v.diff();

            switch (op) {
                case '+':
                    return new BinOp('+', du, dv);
                case '-':
                    return new BinOp('-', du, dv);
                case '*':
                    // (uv)' = u'v + uv'
                    return new BinOp('+',
                            new BinOp('*', du, v),
                            new BinOp('*', u, dv));
                case '/':
                    // (u/v)' = (u'v - uv') / v^2
                    return new BinOp('/',
                            new BinOp('-',
                                    new BinOp('*', du, v),
                                    new BinOp('*', u, dv)),
                            new BinOp('^', v, new Const(2.0)));
                case '^':
                    // handle u^v (various cases)
                    return handlePowerDerivative(u, v, du, dv);
                default:
                    return new Const(0.0);
            }
        }

        @Override
        public Expr simplify() {
            Expr ls = left.simplify();
            Expr rs = right.simplify();

            // Constant folding
            if (ls instanceof Const && rs instanceof Const) {
                double val = new BinOp(op, ls, rs).eval(0.0);
                return new Const(val);
            }

            // Algebraic simplifications
            if (op == '+') {
                if (ls instanceof Const && ((Const) ls).value == 0.0) return rs;
                if (rs instanceof Const && ((Const) rs).value == 0.0) return ls;
            }

            if (op == '-') {
                if (rs instanceof Const && ((Const) rs).value == 0.0) return ls;
            }

            if (op == '*') {
                if (ls instanceof Const) {
                    double v = ((Const) ls).value;
                    if (v == 0.0) return new Const(0.0);
                    if (v == 1.0) return rs;
                }
                if (rs instanceof Const) {
                    double v = ((Const) rs).value;
                    if (v == 0.0) return new Const(0.0);
                    if (v == 1.0) return ls;
                }
            }

            if (op == '/') {
                if (rs instanceof Const) {
                    double v = ((Const) rs).value;
                    if (v == 1.0) return ls;
                }
            }

            if (op == '^') {
                if (rs instanceof Const) {
                    double v = ((Const) rs).value;
                    if (v == 1.0) return ls;
                    if (v == 0.0) return new Const(1.0);
                }
            }

            return new BinOp(op, ls, rs);
        }

        @Override
        public String toAOS() {
            return "(" + left.toAOS() + " " + op + " " + right.toAOS() + ")";
        }

        @Override
        public String toRPN() {
            return left.toRPN() + " " + right.toRPN() + " " + op;
        }
    }

    // Unary functions: sin, cos, exp, log
    private static class Func implements Expr {
        final String name;
        final Expr arg;

        Func(String name, Expr arg) {
            this.name = name.toLowerCase();
            this.arg = arg;
        }

        @Override
        public double eval(double x) {
            double v = arg.eval(x);
            switch (name) {
                case "sin":
                    return Math.sin(v);
                case "cos":
                    return Math.cos(v);
                case "exp":
                    return Math.exp(v);
                case "log":
                    return Math.log(v);
                default:
                    return Double.NaN;
            }
        }

        @Override
        public Expr diff() {
            Expr da = arg.diff();
            switch (name) {
                case "sin":
                    // (sin u)' = cos u * u'
                    return new BinOp('*', new Func("cos", arg), da);
                case "cos":
                    // (cos u)' = -sin u * u'
                    return new BinOp('*',
                            new Const(-1.0),
                            new BinOp('*', new Func("sin", arg), da));
                case "exp":
                    // (exp u)' = exp u * u'
                    return new BinOp('*', new Func("exp", arg), da);
                case "log":
                    // (log u)' = u' / u
                    return new BinOp('*',
                            new BinOp('/', new Const(1.0), arg),
                            da);
                default:
                    return new Const(0.0);
            }
        }

        @Override
        public Expr simplify() {
            Expr sArg = arg.simplify();
            if (sArg instanceof Const) {
                double val = new Func(name, sArg).eval(0.0);
                return new Const(val);
            }
            return new Func(name, sArg);
        }

        @Override
        public String toAOS() {
            return name + "(" + arg.toAOS() + ")";
        }

        @Override
        public String toRPN() {
            return arg.toRPN() + " " + name;
        }
    }

    // Fields holding current expression and derivative
    private Expr functionExpr;
    private Expr derivativeExpr;
    private ExpressionFormat currentFormat = ExpressionFormat.AOS;

    // Default integration parameters (can be adjusted via setters if needed)
    private double integStart = -5.0;
    private double integEnd = 5.0;
    private double integStep = 0.01;
    private boolean useTrapezoidal = true;

    // Helper for power derivative
    private static Expr handlePowerDerivative(Expr u, Expr v, Expr du, Expr dv) {
        // u^n, n constant
        if (v instanceof Const) {
            double n = ((Const) v).value;
            // (u^n)' = n * u^(n-1) * u'
            return new BinOp('*',
                    new BinOp('*', new Const(n),
                            new BinOp('^', u, new Const(n - 1.0))),
                    du);
        }

        // Generic case: (u^v)' = u^v * (v' ln u + v u'/u)
        return new BinOp('*',
                new BinOp('^', u, v),
                new BinOp('+',
                        new BinOp('*', dv, new Func("log", u)),
                        new BinOp('*', v, new BinOp('/', du, u))));
    }

    private static String formatNumber(double v) {
        long rounded = Math.round(v);
        if (Math.abs(v - rounded) < 1e-10) {
            return Long.toString(rounded);
        }
        return Double.toString(v);
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Parse AOS expression string to Expr using provided parser
    private Expr parseAOS(String input) throws Exception {
        AOS parser = new AOS();
        AOS.Parts parts = parser.parse(input.trim());

        String main = parts.main;
        String left = parts.left;
        String right = parts.right;

        if ("x".equalsIgnoreCase(main)) {
            return new Var();
        }

        // function call: f(...)
        if (right == null && left != null && main.matches("[a-zA-Z]+")) {
            String lower = main.toLowerCase();
            // Only allow known functions; everything else is invalid
            if (!lower.equals("sin") && !lower.equals("cos")
                    && !lower.equals("exp") && !lower.equals("log")) {
                throw new Exception("Unknown function: " + main);
            }
            return new Func(lower, parseAOS(left));
        }

        // binary operator
        if (main.length() == 1 && "+-*/^".indexOf(main.charAt(0)) != -1) {
            Expr l = parseAOS(left);
            Expr r = parseAOS(right);
            return new BinOp(main.charAt(0), l, r);
        }

        // numeric constant
        try {
            double val = Double.parseDouble(main);
            return new Const(val);
        } catch (NumberFormatException e) {
            throw new Exception("Unknown token in AOS: " + main);
        }
    }

    // Parse RPN expression string to Expr using provided parser
    private Expr parseRPN(String input) throws Exception {
        RPN parser = new RPN(input);
        java.util.Stack<String> tokenStack = parser.parse();
        java.util.List<String> tokens = new java.util.ArrayList<>(tokenStack);

        java.util.Stack<Expr> stack = new java.util.Stack<>();

        for (String token : tokens) {
            token = token.toLowerCase();
            if ("x".equals(token)) {
                stack.push(new Var());
            } else if (isNumber(token)) {
                stack.push(new Const(Double.parseDouble(token)));
            } else if ("+-*/^".contains(token)) {
                if (stack.size() < 2) {
                    throw new Exception("Invalid RPN expression");
                }
                Expr right = stack.pop();
                Expr left = stack.pop();
                stack.push(new BinOp(token.charAt(0), left, right));
            } else if (token.equals("sin") || token.equals("cos")
                    || token.equals("exp") || token.equals("log")) {
                if (stack.isEmpty()) {
                    throw new Exception("Invalid RPN expression");
                }
                Expr arg = stack.pop();
                stack.push(new Func(token, arg));
            } else {
                throw new Exception("Illegal token in RPN: " + token);
            }
        }

        if (stack.size() != 1) {
            throw new Exception("Invalid RPN expression");
        }

        return stack.pop();
    }

    // Iterator for Cartesian plotting
    private static class ExprCurveIterator implements Point.Iterator {
        private final Expr expr;
        private final double start;
        private final double end;
        private final double step;
        private double current;

        ExprCurveIterator(Expr expr, double start, double end, double step) {
            this.expr = expr;
            this.start = start;
            this.end = end;
            this.step = step;
            reset();
        }

        @Override
        public boolean hasNext() {
            return current <= end + 1e-9;
        }

        @Override
        public boolean hasBreak() {
            return false;
        }

        @Override
        public void reset() {
            this.current = start;
        }

        @Override
        public Point nextPoint() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            double x = current;
            double y = expr.eval(x);
            current += step;
            return new Point(x, y);
        }
    }

    // Iterator for Polar plotting: expr(theta) = r
    private static class PolarExprCurveIterator implements Point.Iterator {
        private final Expr expr;
        private final double start;
        private final double end;
        private final double step;
        private double current;

        PolarExprCurveIterator(Expr expr, double start, double end, double step) {
            this.expr = expr;
            this.start = start;
            this.end = end;
            this.step = step;
            reset();
        }

        @Override
        public boolean hasNext() {
            return current <= end + 1e-9;
        }

        @Override
        public boolean hasBreak() {
            return false;
        }

        @Override
        public void reset() {
            this.current = start;
        }

        @Override
        public Point nextPoint() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            double theta = current;
            double r = expr.eval(theta);
            double x = r * Math.cos(theta);
            double y = r * Math.sin(theta);
            current += step;
            return new Point(x, y);
        }
    }

    // Numeric integration helpers
    private double areaRectangular(double a, double b, double h) {
        double sum = 0.0;
        for (double x = a; x < b; x += h) {
            sum += functionExpr.eval(x) * h;
        }
        return sum;
    }

    private double areaTrapezoidal(double a, double b, double h) {
        double sum = 0.0;
        int n = (int) ((b - a) / h);
        if (n <= 0) return 0.0;

        double x0 = a;
        double f0 = functionExpr.eval(x0);

        for (int i = 0; i < n; i++) {
            double x1 = a + (i + 1) * h;
            double f1 = functionExpr.eval(x1);
            sum += (f0 + f1) * 0.5 * h;
            x0 = x1;
            f0 = f1;
        }

        return sum;
    }

    public enum PlotType {
        Cartesian, Polar
    }

    public enum ExpressionFormat {
        AOS, RPN
    }

    public MathPlot() {
        // YOU CAN CHANGE HERE
        this.functionExpr = null;
        this.derivativeExpr = null;
    }

    public void setExpression(String expr, ExpressionFormat format) {
        // YOU CAN CHANGE HERE
        this.currentFormat = format;
        try {
            if (format == ExpressionFormat.AOS) {
                this.functionExpr = parseAOS(expr);
            } else {
                this.functionExpr = parseRPN(expr);
            }
            if (this.functionExpr != null) {
                this.functionExpr = this.functionExpr.simplify();
                this.derivativeExpr = this.functionExpr.diff().simplify();
            } else {
                this.derivativeExpr = null;
            }
        } catch (Exception e) {
            // On parse error, clear expressions
            this.functionExpr = null;
            this.derivativeExpr = null;
        }
    }

    public void plot(Canvas canvas, PlotType type) {
        final Plotter pf = new Plotter(canvas, new Point(-10, -10), new Point(10, 10));

        // YOU CAN CHANGE HERE
        // Draw axes
        pf.addLine(new Point(-10, 0), new Point(10, 0), Color.LIGHTGRAY, 0.5);
        pf.addLine(new Point(0, -10), new Point(0, 10), Color.LIGHTGRAY, 0.5);

        if (functionExpr != null) {
            double step = 0.05;

            if (type == PlotType.Cartesian) {
                // f(x)
                pf.addCurve(new ExprCurveIterator(functionExpr, -10.0, 10.0, step), Color.BLUE, 1.5);

                // f'(x)
                if (derivativeExpr != null) {
                    pf.addCurve(new ExprCurveIterator(derivativeExpr, -10.0, 10.0, step), Color.RED, 1.5);
                }
            } else { // Polar
                double thetaStart = 0.0;
                double thetaEnd = 2.0 * Math.PI;

                // f(theta)
                pf.addCurve(new PolarExprCurveIterator(functionExpr, thetaStart, thetaEnd, step), Color.BLUE, 1.5);

                // f'(theta)
                if (derivativeExpr != null) {
                    pf.addCurve(new PolarExprCurveIterator(derivativeExpr, thetaStart, thetaEnd, step), Color.RED, 1.5);
                }
            }
        }

        pf.render();
    }

    public double area() {
        // YOU CAN CHANGE HERE
        if (functionExpr == null) {
            return Double.NaN;
        }

        if (useTrapezoidal) {
            return areaTrapezoidal(integStart, integEnd, integStep);
        } else {
            return areaRectangular(integStart, integEnd, integStep);
        }
    }

    public List<String> print(ExpressionFormat format) {
        final List<String> res = new ArrayList<>();

        // YOU CAN CHANGE HERE
        if (functionExpr == null) {
            return res;
        }

        if (format == ExpressionFormat.AOS) {
            res.add(functionExpr.toAOS());
            if (derivativeExpr != null) {
                res.add(derivativeExpr.toAOS());
            }
        } else {
            res.add(functionExpr.toRPN());
            if (derivativeExpr != null) {
                res.add(derivativeExpr.toRPN());
            }
        }

        return res;
    }
}
