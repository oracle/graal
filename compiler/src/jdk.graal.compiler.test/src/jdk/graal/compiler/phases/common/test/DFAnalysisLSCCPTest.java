/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.common.test;

import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;

import java.util.Random;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.nodes.test.ShortCircuitOrNodeTest;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.dfanalysis.DFAnalysis;
import jdk.graal.compiler.phases.dfanalysis.analyses.LSCCPPhase;

public class DFAnalysisLSCCPTest extends DFAnalysisBaseTest {

    @Override
    protected OptionValues modifyOptions(OptionValues current) {
        OptionValues standard = new OptionValues(current,
                        GraalOptions.ConditionalConstantPropagation, true,
                        GraalOptions.FullStampAnalysis, false,
                        GraalOptions.PentagonalAnalysis, false,
                        DFAnalysis.Options.DFA_EvalAll, true);
        return standard.getMap().containsKey(LSCCPPhase.Options.LSCCP_AllowInferences)
                        // if a concrete value is set for inferences, we use that
                        ? standard
                        // else we default to enabling inferences for this test class
                        : new OptionValues(standard, LSCCPPhase.Options.LSCCP_AllowInferences, true);
    }

    /**
     * Creates the conditional constant {@code 5}.
     */
    public static int conditionalConstant(int z) {
        return conditionalConstant(z, 5);
    }

    /**
     * This is not a tested snippet in itself. Rather, it is intended to be used to represent a
     * constant that needs conditional constant propagation to be found in other test snippets.
     */
    public static int conditionalConstant(int z, int constant) {
        int conditionalConstant = constant;
        int a = z;
        do {
            if (conditionalConstant != constant) {
                conditionalConstant = 2;
                GraalDirectives.controlFlowAnchor();
            }
        } while (a-- > 0);
        if (conditionalConstant > 100) {
            conditionalConstant = constant + 3;
        }
        return conditionalConstant;
    }

    @Test
    public void simpleCC() {
        test("simpleCCSnippet", 5);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [1]")
    public static int simpleCCSnippet(int a) {
        int ctr = a;
        int x = 1;
        do {
            if (x != 1) {
                x = 2;
                GraalDirectives.controlFlowAnchor();
            }
            GraalDirectives.sideEffect(ctr);
        } while (ctr-- > 0);
        return x;
    }

    @Test
    public void shortCircuitCC() {
        test("shortCircuitCCSnippet", 5);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [4]")
    public static int shortCircuitCCSnippet(int a) {
        int ctr = a;
        int x = 1;
        int y = 3;
        do {
            if (ShortCircuitOrNodeTest.shortCircuitOr(x != 1, y != 3)) {
                GraalDirectives.controlFlowAnchor();
                x = 10;
                y = 30;
            }
        } while (ctr-- > 0);
        return x + y;
    }

    @Test
    public void codependentConstants() {
        test("codependentConstantsSnippet", 8);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [1]")
    public static int codependentConstantsSnippet(int a) {
        int x = 1;
        int y = 2;
        int z = a;
        while (z-- > 0) {
            if (y != 2) {
                GraalDirectives.controlFlowAnchor();
                x = 2;
            }
            x = 2 - x;
            if (x != 1) {
                GraalDirectives.controlFlowAnchor();
                y = 3;
            }
        }
        return x;
    }

    @Test
    public void symbolicFacts() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.FullUnroll, false);
        test(opt, "symbolicFactsSnippet", 8, 5);
    }

    @DFATestSnippet(loops = 1, anchors = 2)
    public static int symbolicFactsSnippet(int a, int b) {
        int x = 1;
        int y = b;
        int z = a;
        while (z-- > 0) {
            if (y != b) {
                x = 2;
                GraalDirectives.controlFlowAnchor();
            }
            x = 2 - x;
            if (x != 1) {
                y = 2;
                GraalDirectives.controlFlowAnchor();
            }
        }
        return x;
    }

    @Test
    public void simpleUnknownPhi() {
        test("simpleUnknownPhiSnippet", 1, 6);
    }

    @DFATestSnippet
    public static int simpleUnknownPhiSnippet(int init, int a) {
        int b;
        if (injectBranchProbability(0.5, (init & 1) == 1)) {
            b = a;
        } else {
            b = 5;
        }
        return b + 3;
    }

    @Test
    public void propagateThroughSwitch1() {
        test("propagateThroughSwitch1Snippet", 5);
    }

    @DFATestSnippet(anchors = 1, returns = "i32 [45724614]")
    public static int propagateThroughSwitch1Snippet(int z) {
        // switch with array like indices
        int result = switch (conditionalConstant(z)) {
            case 0 -> 1235;
            case 1 -> 2356;
            case 2 -> 12341435;
            case 3 -> 114235;
            case 4 -> 4567;
            case 5 -> 45724572;
            case 6 -> 72572;
            case 7 -> 245724572;
            case 8 -> 2452445;
            case 9 -> 45724245;
            default -> 972;
        };
        GraalDirectives.controlFlowAnchor();
        return result + 42;
    }

    @Test
    public void propagateThroughSwitch2() {
        test("propagateThroughSwitch2Snippet", 5);
    }

    @DFATestSnippet(anchors = 1, returns = "i32 [1014]")
    public static int propagateThroughSwitch2Snippet(int z) {
        // switch with shifted array like indices (1 key per value)
        int result = switch (conditionalConstant(z)) {
            case 10 -> 1235;
            case 11 -> 2356;
            case 12 -> 12341435;
            case 13 -> 114235;
            case 14 -> 4567;
            case 15 -> 45724572;
            case 16 -> 72572;
            case 17 -> 245724572;
            case 18 -> 2452445;
            case 19 -> 45724245;
            default -> 972;
        };
        GraalDirectives.controlFlowAnchor();
        return result + 42;
    }

    @Test
    public void propagateThroughSwitch3() {
        test("propagateThroughSwitch3Snippet", 5);
    }

    @DFATestSnippet(anchors = 1, returns = "i32 [1014]")
    public static int propagateThroughSwitch3Snippet(int z) {
        // switch with arbitrary indices (n keys per value)
        int result = switch (conditionalConstant(z)) {
            case 1, 2, 3, 4 -> 12341234;
            case 8 -> 1234;
            case 0 -> 456;
            case 1_000_000 -> 24662;
            default -> 972;
        };
        GraalDirectives.controlFlowAnchor();
        return result + 42;
    }

    @Test
    public void loopStack() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.FullUnroll, false);
        test(opt, "loopStackSnippet", 5);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [5]")
    public static int loopStackSnippet(int z) {
        int conditionalConstant = conditionalConstant(z);
        for (int i = 0; i < conditionalConstant; i++) {
            GraalDirectives.sideEffect(i);
        }
        for (int i = 0; i < conditionalConstant; i++) {
            GraalDirectives.sideEffect(i);
        }
        for (int i = 0; i < conditionalConstant; i++) {
            GraalDirectives.sideEffect(i);
        }
        return conditionalConstant;
    }

    @Test
    public void deadCodeInLastIndexOf() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.PartialUnroll, false);
        test(opt, "deadCodeInLastIndexOfSnippet", "conditional constant propagation");
    }

    // This test is taken from EnterpriseRangeCheckEliminationIVTest#testConditionalExit.
    private static String substringConditional(String simpleName, char[] target) {
        char[] value = simpleName.toCharArray();
        int lastDotIndex = lastIndexOf(value, 0, value.length,
                        target, 0, target.length, value.length);
        if (lastDotIndex < 0) {
            return null;
        }
        GraalDirectives.deoptimize();
        return simpleName.substring(0, lastDotIndex);
    }

    private static final long SomeLongConstant = 1000;

    private static int lastIndexOf(char[] source, int sourceOffset, int sourceCount,
                    char[] target, int targetOffset, int targetCount,
                    int fIdx) {
        /*
         * Check arguments; return immediately where possible. For consistency, don't check for null
         * str.
         */
        int fromIndex = fIdx;
        int rightIndex = sourceCount - targetCount;
        if (fromIndex < 0) {
            return -1;
        }
        if (fromIndex > rightIndex) {
            fromIndex = rightIndex;
        }
        /* Empty string always matches. */
        if (targetCount == 0) {
            return fromIndex;
        }

        int strLastIndex = targetOffset + targetCount - 1;
        char strLastChar = target[strLastIndex];
        int min = sourceOffset + targetCount - 1;
        int i = min + fromIndex;
        startSearchForLastChar: while (true) {
            while (i >= min && source[i] != strLastChar) {
                GraalDirectives.sideEffect(1);
                i--;
            }
            if (i < min) {
                return -1;
            }
            int j = i - 1;
            int start = j - (targetCount - 1);
            int k = strLastIndex - 1;
            long ivLong = 0;

            while (GraalDirectives.injectIterationCount(1000, j > start)) {
                if (ivLong > SomeLongConstant) {
                    GraalDirectives.deoptimizeAndInvalidate();
                }
                if (source[j--] != target[k--]) {
                    i--;
                    continue startSearchForLastChar;
                }
                ivLong++;
            }
            return start - sourceOffset + 1;
        }
    }

    // Due to target being of length 1, multiple loops can be optimized away due to unreachability.
    @DFATestSnippet(loops = 1)
    public static String deadCodeInLastIndexOfSnippet(String simpleName) {
        return substringConditional(simpleName, ".".toCharArray());
    }

    @Test
    public void noCCPinLastIndexOf() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        test(opt, "noCCPinLastIndexOfSnippet", "conditional constant propagation");
    }

    // Due to target being of length > 1 multiple lops remain.
    @DFATestSnippet(loops = 2)
    public static String noCCPinLastIndexOfSnippet(String simpleName) {
        return substringConditional(simpleName, ".n".toCharArray());
    }

    @Test
    public void shortCircuitOr() {
        test("shortCircuitOrSnippet", 5);
    }

    @DFATestSnippet(anchors = 1, conditionals = 0, returns = "i32 [538]")
    public static int shortCircuitOrSnippet(int a) {
        int constant = conditionalConstant(a);
        int conditional = constant != 3 && constant < 10 ? 538 : 126;
        GraalDirectives.controlFlowAnchor();
        return conditional;
    }

    @Test
    public void piDivision() {
        test("piDivisionSnippet", 5);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [2]")
    public static int piDivisionSnippet(int a) {
        int constant = conditionalConstant(a);
        return 10 / constant;
    }

    @Test
    public void doubleWhetstoneBenchmarkLoop() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.PartialUnroll, false);
        test(opt, "doubleWhetstoneBenchmarkLoopSnippet", 5);
    }

    @DFATestSnippet(loops = 1, deopts = 0)
    public static double[] doubleWhetstoneBenchmarkLoopSnippet(int a) {
        double[] e1 = new double[4];
        int iterations = 210 * a;

        int j = 1;
        int k = 2;
        int l = 3;

        for (int i = 1; i <= iterations; i++) {
            j = j * (k - j) * (l - k);
            k = l * k - (l - j) * k;
            l = (l - k) * (k + j);
            // the loop is not removed due to these array writes but the constants are found
            e1[l - 1] = j + k + l;
            e1[k - 1] = j * k * l;
        }

        if (j + k + l != 6 && j * k * l != 6) {
            // this will be removed because j, k and l are found to be constant
            GraalDirectives.deoptimizeAndInvalidate();
        }

        return e1;
    }

    @Test
    public void exactArithmeticLoop() {
        test("exactArithmeticLoopSnippet", 5);
    }

    @DFATestSnippet(returns = "i64 [-25769803782]")
    public static long exactArithmeticLoopSnippet(int a) {
        int j = 1;
        int k = 2;
        int l = 3;

        for (int i = 1; i < a; i++) {
            j = Math.multiplyExact(Math.multiplyExact(j, Math.subtractExact(k, j)), Math.subtractExact(l, k));
            k = Math.subtractExact(Math.multiplyExact(l, k), Math.multiplyExact(Math.subtractExact(l, j), k));
            l = Math.multiplyExact(Math.subtractExact(l, k), Math.addExact(k, j));
        }

        return Math.negateExact((((long) (j + k + l)) << 32) | (j * k * l));
    }

    @Test
    public void exactArithmeticOverflow() {
        test("exactArithmeticOverflowSnippet", 5);
    }

    @DFATestSnippet(anchors = 0)
    public static int exactArithmeticOverflowSnippet(int a) {
        int constant = conditionalConstant(a);
        try {
            int result = Math.addExact(Integer.MAX_VALUE - 1, constant);
            // everything past this point is unreachable and eliminated
            GraalDirectives.controlFlowAnchor();
            return result;
        } catch (ArithmeticException e) {
            // this branch can either take the form of a return or a deopt
            return -1;
        }
    }

    @Test
    public void deeplyNestedLoops() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        test(opt, "deeplyNestedLoopsSnippet", 5);
    }

    @DFATestSnippet
    public static int deeplyNestedLoopsSnippet(int a) {
        for (int i = 0; i < a; i++) {
            if (i != 0) {
                for (int j = 0; j < a; j++) {
                    if (j == 0) {
                        for (int k = 0; k < a; k++) {
                            if (k != 0) {
                                for (int l = 0; l < a; l++) {
                                    if (l == 0) {
                                        GraalDirectives.sideEffect(a);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return a;
    }

    @Test
    public void ccpLoopWithExit() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        test(opt, "ccpLoopWithExitSnippet");
    }

    @DFATestSnippet(loops = 0, returns = "i32 [1]")
    public static int ccpLoopWithExitSnippet() {
        int x = 1;
        do {
            if (x == 1) {
                break;
            }
            x = 2;
        } while (true);
        return x;
    }

    @Test
    public void resetReachability() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false);
        test(opt, "resetReachabilitySnippet", 5);
    }

    @DFATestSnippet
    public static int resetReachabilitySnippet(int a) {
        for (int i = 0; i < a; i++) {
            if (i != 0) {
                // unreachable in the first iteration, correcting inference
                GraalDirectives.sideEffect(a);
            }
        }
        return a;
    }

    @Test
    public void nestedConditionalConstants() {
        test("nestedConditionalConstantsSnippet", 5);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [8]")
    public static int nestedConditionalConstantsSnippet(int a) {
        return conditionalConstant(a, conditionalConstant(a, 8));
    }

    @Test
    public void nestedIfInference() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopUnswitch, false, GraalOptions.PartialUnroll, false);
        test(opt, "nestedIfInferenceSnippet", 8, 5);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [1]")
    public static int nestedIfInferenceSnippet(int a, int b) {
        int constant = conditionalConstant(a);
        int x = 1;
        int z = a;
        do {
            if (b == 5) {
                if (b != constant) {
                    GraalDirectives.controlFlowAnchor();
                    x = 2;
                }
            }
            x = 2 - x;
        } while (z-- > 0);
        return x;
    }

    @Test
    public void resetNonZeroIfBranch() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopUnswitch, false);
        test(opt, "resetNonZeroIfBranchSnippet", 8, 5);
    }

    @DFATestSnippet(anchors = 0, conditionals = 0, returns = "i32 [5]")
    public static int resetNonZeroIfBranchSnippet(int a, int b) {
        // generate non-zero value
        int nonzero = switch (b) {
            case 0 -> -1;
            case 1, 2 -> -2;
            case 3, 4 -> -3;
            case 5, 6 -> -4;
            default -> -5;
        };

        // simulate discovery with CCP (constant = 5)
        int constant = conditionalConstant(a);

        // using inferences, this calculation should result in x = 5
        int x = 5;
        if (nonzero == -constant) {
            // infer: nonzero = -5;
            x = 1 | -(1 | nonzero);
            /*
             * This expression serves several purposes. First, since all these nodes (bitwise OR,
             * negate) float and are therefore not scheduled with priority, the non-zero value
             * produced earlier propagates through these nodes up to the PHI below the given IF.
             * Then nonzero = -5 is inferred causing the bitwise OR (and by extension the negation
             * and the second bitwise OR) to be reset and reevaluated resulting in x = 5.
             */
        }

        // simulate further discoveries with CCP (result = 5)
        int result = conditionalConstant(a, x);

        // should be reduced to return 5;
        return result;
    }

    @Test
    public void inferInLoop() {
        final int min = Integer.MAX_VALUE - 11;
        final int max = Integer.MAX_VALUE;
        OptionValues opt = new OptionValues(getInitialOptions(),
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopUnswitch, false,
                        GraalOptions.PartialUnroll, false);
        test(opt, "inferInLoopSnippet", min, max);
    }

    @DFATestSnippet(loops = 1, returns = "i32 [77]")
    public static int inferInLoopSnippet(int min, int max) {
        int counter = 0;
        int i;
        for (i = min; i <= max; i++) {
            if (counter == 77) {
                return counter;
            }
            counter++;
        }
        return 77;
    }

    @Test
    public void temporarilyUnreachablePhi() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        test(opt, "temporarilyUnreachablePhiSnippet", 8);
    }

    @DFATestSnippet
    public static int temporarilyUnreachablePhiSnippet(int a) {
        if (a < 1) {
            return -3;
        }
        // a := non-zero
        int res = 5;
        for (int i = 1; i < a; i++) {
            if (i != 1) {
                // loop PHI is unreachable on first evaluation
                // inferred fact for j is also unreachable until loop PHI becomes reachable
                for (int j = 1; j < i - 4; j += 4) {
                    res = j;
                }
            }
        }
        return res / 3;
    }

    @Test
    public void insertInferenceIntoLoopPhi() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false);
        test(opt, "insertInferenceIntoLoopPhiSnippet", 8);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [3]")
    public static int insertInferenceIntoLoopPhiSnippet(int a) {
        int res = 3;
        for (int i = 1; i < a; i++) {
            if (res != 1) {
                res = 3;
            } else {
                GraalDirectives.controlFlowAnchor();
                res += 2;
            }
        }
        return res;
    }

    @Test
    public void objectNullConstant() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        test(opt, "objectNullConstantSnippet", 8, new Object());
    }

    @DFATestSnippet(anchors = 0, returns = "a java.lang.Object NULL")
    public static Object objectNullConstantSnippet(int a, Object b) {
        if (b == null) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        Object target = null;
        for (int i = 0; i < a; i++) {
            if (target != null) {
                target = b;
                GraalDirectives.controlFlowAnchor();
            }
        }
        return target;
    }

    @Test
    public void rubyLoopPlainIntConst() {
        OptionValues opt = new OptionValues(getInitialOptions(),
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.OptReadElimination, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.ConditionalElimination, false,
                        LoopPeelingPhase.Options.IterativePeelingLimit, 1);
        test(opt, "rubyLoopPlainIntConstSnippet", 8, 1);
    }

    @DFATestSnippet(anchors = 0, deopts = 1, returns = "i32 [1]")
    public static int rubyLoopPlainIntConstSnippet(int limit, int init) {
        int myConst = init;
        // manual loop peeling
        if (myConst == 0) {
            myConst = 1;
        } else {
            // do sth else
            if (myConst != 1) {
                // this deopt stays
                GraalDirectives.deoptimizeAndInvalidate();
            }
        }
        int i = 0;
        while (i++ < limit) {
            if (myConst == 0) {
                myConst = 1;
                GraalDirectives.controlFlowAnchor();
            } else {
                // do sth else
                if (myConst != 1) {
                    GraalDirectives.deoptimizeAndInvalidate();
                }
            }
        }
        return myConst;
    }

    private static RubyTestClassInt rtc0;

    private static final class RubyTestClassInt {
        int field;
    }

    @Test
    public void rubyLoopIntConst() {
        rtc0 = new RubyTestClassInt();
        rtc0.field = 1;
        OptionValues opt = new OptionValues(getInitialOptions(),
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.OptReadElimination, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.ConditionalElimination, false,
                        LoopPeelingPhase.Options.IterativePeelingLimit, 1);
        test(opt, "rubyLoopIntConstSnippet", 100);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [1]")
    public static int rubyLoopIntConstSnippet(int limit) {
        RubyTestClassInt fiberLocals = rtc0;
        // manual loop peeling
        if (fiberLocals.field == 0) {
            fiberLocals.field = 1;
        } else {
            // do sth else
            if (fiberLocals.field != 1) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
        }
        int i = 0;
        while (i++ < limit) {
            fiberLocals = rtc0;
            if (fiberLocals.field == 0) {
                fiberLocals.field = 1;
                GraalDirectives.controlFlowAnchor();
            } else {
                // do sth else
                if (fiberLocals.field != 1) {
                    GraalDirectives.deoptimizeAndInvalidate();
                }
            }
        }
        return fiberLocals.field;
    }

    static RubyTestClassObject rtc1;
    static RubyTestClassObject rtc2;
    static final Object rtcEmpty = new Object();
    private static final Object rtcS1 = new Object();

    static final class RubyTestClassObject {
        Object field;
    }

    @Test
    public void rubyLoopNullConst() {
        rtc1 = new RubyTestClassObject();
        rtc1.field = null;
        OptionValues opt = new OptionValues(getInitialOptions(),
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.OptReadElimination, false,
                        GraalOptions.FullUnroll, false,
                        GraalOptions.ConditionalElimination, false,
                        LoopPeelingPhase.Options.IterativePeelingLimit, 1);
        test(opt, "rubyLoopNullConstSnippet", 100);
    }

    @DFATestSnippet(loops = 1, anchors = 3, deopts = 2)
    public static Object rubyLoopNullConstSnippet(int limit) {
        return rubyLoopNullConstPattern(limit);
    }

    public static Object rubyLoopNullConstPattern(int limit) {
        RubyTestClassObject fiberLocals = rtc1;
        if (fiberLocals.field == null) {
            GraalDirectives.controlFlowAnchor();
            fiberLocals.field = rtcS1;
        } else {
            GraalDirectives.controlFlowAnchor();
            if (fiberLocals.field != rtcS1) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
        }
        GraalDirectives.controlFlowAnchor();
        // rtc1.field == rtcS1 (but the stamp just says unspecified non-null object)
        int i = 0;
        while (i++ < limit) {
            fiberLocals = rtc1;
            if (fiberLocals.field == null) {
                // This condition is optimized away
                fiberLocals.field = 1;
                GraalDirectives.controlFlowAnchor();
            } else {
                if (fiberLocals.field != rtcS1) {
                    GraalDirectives.deoptimizeAndInvalidate();
                }
            }
        }
        return fiberLocals.field;
    }

    @Test
    public void rubyLoopObjectConst() {
        rtc2 = new RubyTestClassObject();
        rtc2.field = rtcEmpty;
        OptionValues opt = new OptionValues(getInitialOptions(),
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.OptReadElimination, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.ConditionalElimination, false,
                        LoopPeelingPhase.Options.IterativePeelingLimit, 1);
        test(opt, "rubyLoopObjectConstSnippet", 100);
    }

    @DFATestSnippet(loops = 1, anchors = 1, deopts = 2)
    public static Object rubyLoopObjectConstSnippet(int limit) {
        return rubyLoopObjectConstPattern(limit);
    }

    public static Object rubyLoopObjectConstPattern(int limit) {
        RubyTestClassObject fiberLocals = rtc2;
        if (fiberLocals.field == rtcEmpty) {
            fiberLocals.field = rtcS1;
        } else {
            // do sth else
            if (fiberLocals.field != rtcS1) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
        }
        // rtc2.field == rtcEmpty but ObjectStamp restricts us to just a non-null object
        int i = 0;
        while (i++ < limit) {
            // Ruby: Thread.current[:fiber_local] = VALUE, copy of the above but in the loop
            fiberLocals = rtc2;
            if (fiberLocals.field == rtcEmpty) {
                /*
                 * This cannot be proven without full Pentagons because C(rtcEmpty) can not be
                 * represented in ObjectStamp. Pentagons though can proe this falls
                 */
                fiberLocals.field = 1;
                GraalDirectives.controlFlowAnchor();
            } else {
                // do sth else
                if (fiberLocals.field != rtcS1) {
                    GraalDirectives.deoptimizeAndInvalidate();
                }
            }
        }
        return fiberLocals.field;
    }

    @Test
    public void dontInferUnused() {
        test("dontInferUnusedSnippet");
    }

    private static final class FuzzClass0 {
        private static final Random random = new Random(5255462684139976199L);

        public static short randomShort() {
            return (short) random.nextInt();
        }

        @SuppressWarnings("unused")
        FuzzClass0() {
            boolean var4;
            var4 = 24376 % randomShort() == randomShort();
            // the result of the modulo operation is only used in a frame state, we must not insert
            // an inference there and create a false fact which results in a division by 0
        }
    }

    @DFATestSnippet
    public static void dontInferUnusedSnippet() {
        GraalDirectives.blackhole(new FuzzClass0());
    }

    @Test
    public void unreachableTransitiveInferenceLoopPhi() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false);
        test(opt, "unreachableTransitiveInferenceLoopPhiSnippet", -5);
    }

    @DFATestSnippet
    public static void unreachableTransitiveInferenceLoopPhiSnippet(int a) {
        for (int i = 3; i > a;) {
            // i != 0
            i = switch (i) {
                case 3 -> 2;
                case 2 -> 1;
                case 1 -> -1;
                default -> Integer.MIN_VALUE;
            };
            int j = i;
            do {
                j--;
            } while (j < -1);
            GraalDirectives.sideEffect(j);
        }
    }

    @Test
    public void secondFullInnerLoopEval() {
        OptionValues opt = new OptionValues(getInitialOptions(),
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopPeeling, false,
                        DFAnalysis.Options.DFA_AllowInferences, false);
        test(opt, "secondFullInnerLoopEvalSnippet", -5);
    }

    @DFATestSnippet
    public static void secondFullInnerLoopEvalSnippet(int a) {
        for (int i = 3; i > a;) {
            // i != 0
            i = switch (i) {
                case 3 -> 2;
                case 2 -> 1;
                case 1 -> -1;
                default -> Integer.MIN_VALUE;
            };
            int j = i;
            while (j < -1) {
                GraalDirectives.neverStripMine();
                // initially full body unreachable, then j != 0
                if (j < -2) {
                    GraalDirectives.controlFlowAnchor();
                    j = Integer.MAX_VALUE - 3;
                } else {
                    GraalDirectives.controlFlowAnchor();
                    j = Integer.MAX_VALUE;
                }
                GraalDirectives.controlFlowAnchor();
            }
            GraalDirectives.sideEffect(j);
        }
    }

    @Test
    public void delayedIsNullInference() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false);
        test(opt, "delayedIsNullInferenceSnippet", 5);
    }

    private static final Object oc1 = new Object();
    private static final Object oc2 = new Object();
    private static final Object oc3 = new Object();

    @DFATestSnippet
    public static void delayedIsNullInferenceSnippet(int a) {
        int const0 = conditionalConstant(a, 0);
        Object o = oc1;
        for (int i = 10; i > const0; i--) {
            if (i != 10) {
                if (i == 0) {
                    GraalDirectives.controlFlowAnchor();
                    o = null;
                }
                GraalDirectives.controlFlowAnchor();
                if (o == null) {
                    GraalDirectives.controlFlowAnchor();
                    o = oc2;
                } else {
                    GraalDirectives.controlFlowAnchor();
                }
            } else {
                GraalDirectives.controlFlowAnchor();
                o = oc3;
            }
            GraalDirectives.controlFlowAnchor();
        }
        GraalDirectives.blackhole(o);
    }

    @Test
    public void doubleInference() {
        test("doubleInferenceSnippet", 8);
    }

    public static volatile Object memWrite;

    @DFATestSnippet
    public static int doubleInferenceSnippet(int a) {
        Object x = null;
        Object y = null;
        memWrite = new Object();
        for (int i = 0; i < a; i++) {
            if (y == null) {
                GraalDirectives.controlFlowAnchor();
                y = new Object();
                continue;
            }
            // here we infer y!=null (this happens AFTER the inference for the 'if' below)
            if (x == y) {
                /*
                 * Here we infer y=null in the first iteration of the loop. The fact that this
                 * inferred fact is actually impossible (conflicting inferences back to back) does
                 * not matter in this instance because at this stage in the analysis, this branch is
                 * considered unreachable.
                 *
                 * In the second iteration of the loop this should be corrected to y=UNRESTRICTED
                 * but inserting y!=null above may have severed the connection between the condition
                 * x==y and the inference y=null (We trust that any inference has one of the inputs
                 * of the condition as its input which may not be the case anymore after inferring
                 * y!=null after this inference was made).
                 *
                 * Therefore, in the error case we do not find and correct the inference y=null
                 * leading to a test failure.
                 */
                GraalDirectives.controlFlowAnchor();
                memWrite = y;
            }
            GraalDirectives.controlFlowAnchor();
            x = new Object();
            y = x;
        }
        // if memWrite is null here, we did not correct the faulty inference y=null into the true
        // branch of if(x==y)
        return memWrite == null ? 2 : 3;
    }

    @Test
    public void insertInferenceSwitch() {
        test("insertInferenceSwitchSnippet", 5, 3);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [10]")
    public static int insertInferenceSwitchSnippet(int a, int b) {
        int constantZero = conditionalConstant(a, 0);
        switch (b) {
            case 1, 2, 3 -> {
                // b is not-zero
                if (b == constantZero) {
                    // this branch is unreachable
                    GraalDirectives.controlFlowAnchor();
                    return 3;
                } else {
                    return 10;
                }
            }
            default -> {
                return 10;
            }
        }
    }

    @Test
    public void resetConditional() {
        test("resetConditionalSnippet", 5);
    }

    @DFATestSnippet(anchors = 1, conditionals = 0, returns = "i32 [2]")
    public static int resetConditionalSnippet(int a) {
        int constantOne = conditionalConstant(a, 1);
        GraalDirectives.controlFlowAnchor();
        /*
         * This represents a ConditionalNode of which the condition is evaluated after its value
         * inputs (due to constantOne being evaluated after the constant inputs). This causes the
         * ConditionalNode to first be evaluated to non-zero (condition = ???, trueValue = 2,
         * falseValue = 10 => result is sure to be non-zero). Then information about the condition
         * becomes available, causing the ConditionalNode to result in a constant 2, triggering a
         * reset using DFAMap#resetNodeAndUsages.
         */
        return constantOne == 1 ? 2 : 10;
    }

    @Test
    public void t3() {
        test("t3Snippet", new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
    }

    public static void t3Snippet(int[] a) {
        for (int i = 0, j = 1; j < a.length; j = (i = j) + 1) {
            GraalDirectives.blackhole(a[i] + a[j]);
        }
    }
}
