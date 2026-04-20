package jdk.graal.compiler.phases.common.test;

import static jdk.graal.compiler.phases.common.test.DFAnalysisLSCCPTest.rtc2;
import static jdk.graal.compiler.phases.common.test.DFAnalysisLSCCPTest.rtcEmpty;
import static jdk.graal.compiler.phases.common.test.DFAnalysisLSCCPTest.rubyLoopObjectConstPattern;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.dfanalysis.DFAnalysis;
import jdk.graal.compiler.phases.dfanalysis.analyses.PentagonalAnalysisPhase;

public class DFAnalysisPentagonTest extends DFAnalysisBaseTest {

    @Override
    protected OptionValues modifyOptions(OptionValues current) {
        return new OptionValues(current,
                        GraalOptions.ConditionalConstantPropagation, false,
                        GraalOptions.FullStampAnalysis, false,
                        GraalOptions.PentagonalAnalysis, true,
                        DFAnalysis.Options.DFA_EvalAll, true,
                        PentagonalAnalysisPhase.Options.PA_TrackTypes, true,
                        PentagonalAnalysisPhase.Options.PA_UseNonNullObjConstants, true);
    }

    @Test
    public void binSearch() {
        test("binSearchSnippet", new long[]{1, 3, 4, 5, 7, 8, 10, 15, 34, 54}, 5l);
    }

    @DFATestSnippet(deopts = 1)
    public static int binSearchSnippet(long[] array, long value) {
        /*
         * Only the deopt for array == null should remain. Pentagons can prove the bounds check for
         * the array access.
         */
        int num = 0;
        int num2 = array.length - 1;
        while (num <= num2) {
            GraalDirectives.controlFlowAnchor();
            int index = (num + num2) >>> 1;
            // with pentagons, we can prove this access to be safe
            long num4 = array[index];
            if (value == num4) {
                return index;
            }
            if (num4 < value) {
                num = index + 1;
            } else {
                num2 = index - 1;
            }
        }
        return num;
    }

    @Test
    public void simplePentagon() {
        test("simplePentagonSnippet", 5, 3);
    }

    @DFATestSnippet(deopts = 0)
    public static void simplePentagonSnippet(int a, int b) {
        int x;
        int y;
        if (a < b) {
            GraalDirectives.controlFlowAnchor();
            x = a;
            y = b;
        } else {
            GraalDirectives.controlFlowAnchor();
            x = 3;
            y = 5;
        }
        GraalDirectives.controlFlowAnchor();
        if (x < y) {
            return;
        } else {
            GraalDirectives.deoptimize();
        }
    }

    @Test
    public void loopPentagon() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.LoopPredication, false,
                        GraalOptions.SpeculativeGuardMovement, false);
        test(options, "loopPentagonSnippet", new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
    }

    @DFATestSnippet(deopts = 1)
    public static void loopPentagonSnippet(int[] a) {
        // the only remaining deopt should be the null check for a
        for (int i = 1; i < a.length; i++) {
            GraalDirectives.controlFlowAnchor();
            GraalDirectives.blackhole(a[i] + a[i - 1]);
        }
    }

    @Test
    public void rubyLoopObjectConst() {
        rtc2 = new DFAnalysisLSCCPTest.RubyTestClassObject();
        rtc2.field = rtcEmpty;
        OptionValues opt = new OptionValues(getInitialOptions(),
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.OptReadElimination, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.ConditionalElimination, false,
                        LoopPeelingPhase.Options.IterativePeelingLimit, 1);
        test(opt, "rubyLoopObjectConstSnippet", 100);
    }

    @DFATestSnippet(loops = 1, anchors = 0, deopts = 1)
    public static Object rubyLoopObjectConstSnippet(int limit) {
        return rubyLoopObjectConstPattern(limit);
    }

}
