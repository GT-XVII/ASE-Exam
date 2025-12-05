package MathPlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import MathPlot.Parsers.AOS;
import MathPlot.Parsers.RPN;

class ExpressionEngine {
    private interface Expr {
        double eval(double x);
        Expr diff();
        Expr simplify();
        String toAOS();
        String toRPN();
    }

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
                case '+': return a + b;
                case '-': return a - b;
                case '*': return a * b;
                case '/': return a / b;
                case '^': return Math.pow(a, b);
                default:  return Double.NaN;
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
                    return handlePowerDerivative(u, v, du, dv);
                default:
                    return new Const(0.0);
            }
        }

        @Override
        public Expr simplify() {
            Expr ls = left.simplify();
            Expr rs = right.simplify();

            if (ls instanceof Const && rs instanceof Const) {
                double val = new BinOp(op, ls, rs).eval(0.0);
                return new Const(val);
            }

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
                case "sin": return Math.sin(v);
                case "cos": return Math.cos(v);
                case "exp": return Math.exp(v);
                case "log": return Math.log(v);
                default:    return Double.NaN;
            }
        }

        @Override
        public Expr diff() {
            Expr da = arg.diff();
            switch (name) {
                case "sin":
                    return new BinOp('*', new Func("cos", arg), da);
                case "cos":
                    return new BinOp('*',
                            new Const(-1.0),
                            new BinOp('*', new Func("sin", arg), da));
                case "exp":
                    return new BinOp('*', new Func("exp", arg), da);
                case "log":
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

    // === Stored state for the last set expression ===

    private static Expr functionExpr = null;
    private static Expr derivativeExpr = null;

    // Integration settings (fixed interval [-5,5] and step as in your version)
    private static final double INTEG_START = -5.0;
    private static final double INTEG_END   = 5.0;
    private static final double INTEG_STEP  = 0.01;

    // === Public API used from MathPlot ===

    static void reset() {
        functionExpr = null;
        derivativeExpr = null;
    }

    static void setExpression(String expr, MathPlot.ExpressionFormat format) {
        try {
            Expr f;
            if (format == MathPlot.ExpressionFormat.AOS) {
                f = parseAOS(expr);
            } else {
                f = parseRPN(expr);
            }
            if (f != null) {
                functionExpr = f.simplify();
                derivativeExpr = functionExpr.diff().simplify();
            } else {
                functionExpr = null;
                derivativeExpr = null;
            }
        } catch (Exception e) {
            functionExpr = null;
            derivativeExpr = null;
        }
    }

    static boolean hasFunction() {
        return functionExpr != null;
    }

    static boolean hasDerivative() {
        return derivativeExpr != null;
    }

    static Point.Iterator buildCartesianIterator(boolean derivative,
                                                 double start, double end, double step) {
        if (!hasFunction()) {
            return new EmptyIterator();
        }
        Expr e = derivative ? derivativeExpr : functionExpr;
        return new ExprCurveIterator(e, start, end, step);
    }

    static Point.Iterator buildPolarIterator(boolean derivative,
                                             double start, double end, double step) {
        if (!hasFunction()) {
            return new EmptyIterator();
        }
        Expr e = derivative ? derivativeExpr : functionExpr;
        return new PolarExprCurveIterator(e, start, end, step);
    }

    static double areaRectangular() {
        if (!hasFunction()) return Double.NaN;
        return areaRectangular(INTEG_START, INTEG_END, INTEG_STEP);
    }

    static double areaTrapezoidal() {
        if (!hasFunction()) return Double.NaN;
        return areaTrapezoidal(INTEG_START, INTEG_END, INTEG_STEP);
    }

    static List<String> print(MathPlot.ExpressionFormat format) {
        List<String> res = new ArrayList<>();
        if (!hasFunction()) {
            return res;
        }

        if (format == MathPlot.ExpressionFormat.AOS) {
            res.add(functionExpr.toAOS());
            if (hasDerivative()) {
                res.add(derivativeExpr.toAOS());
            }
        } else {
            res.add(functionExpr.toRPN());
            if (hasDerivative()) {
                res.add(derivativeExpr.toRPN());
            }
        }

        return res;
    }

    // === Iterators ===

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

    private static class EmptyIterator implements Point.Iterator {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public boolean hasBreak() {
            return false;
        }

        @Override
        public void reset() {
        }

        @Override
        public Point nextPoint() {
            throw new java.util.NoSuchElementException();
        }
    }

    // === Parsing and numeric integration ===

    private static Expr parseAOS(String input) throws Exception {
        AOS parser = new AOS();
        AOS.Parts parts = parser.parse(input.trim());

        String main = parts.main;
        String left = parts.left;
        String right = parts.right;

        if ("x".equalsIgnoreCase(main)) {
            return new Var();
        }

        if (right == null && left != null && main.matches("[a-zA-Z]+")) {
            String lower = main.toLowerCase();
            if (!lower.equals("sin") && !lower.equals("cos")
                    && !lower.equals("exp") && !lower.equals("log")) {
                throw new Exception("Unknown function: " + main);
            }
            return new Func(lower, parseAOS(left));
        }

        if (main.length() == 1 && "+-*/^".indexOf(main.charAt(0)) != -1) {
            Expr l = parseAOS(left);
            Expr r = parseAOS(right);
            return new BinOp(main.charAt(0), l, r);
        }

        try {
            double val = Double.parseDouble(main);
            return new Const(val);
        } catch (NumberFormatException e) {
            throw new Exception("Unknown token in AOS: " + main);
        }
    }

    private static Expr parseRPN(String input) throws Exception {
        RPN parser = new RPN(input);
        Stack<String> tokenStack = parser.parse();
        List<String> tokens = new ArrayList<>(tokenStack);

        Stack<Expr> stack = new Stack<>();

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

    private static Expr handlePowerDerivative(Expr u, Expr v, Expr du, Expr dv) {
        if (v instanceof Const) {
            double n = ((Const) v).value;
            return new BinOp('*',
                    new BinOp('*', new Const(n),
                            new BinOp('^', u, new Const(n - 1.0))),
                    du);
        }

        return new BinOp('*',
                new BinOp('^', u, v),
                new BinOp('+',
                        new BinOp('*', dv, new Func("log", u)),
                        new BinOp('*', v, new BinOp('/', du, u))));
    }

    private static double areaRectangular(double a, double b, double h) {
        double sum = 0.0;
        for (double x = a; x < b; x += h) {
            sum += functionExpr.eval(x) * h;
        }
        return sum;
    }

    private static double areaTrapezoidal(double a, double b, double h) {
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
}