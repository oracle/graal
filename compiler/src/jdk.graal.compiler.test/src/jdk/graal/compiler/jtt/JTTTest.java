/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.jtt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Formatter;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;

import org.junit.Assert;
import org.junit.AssumptionViolatedException;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.phases.fuzzing.AbstractCompilationPlan;
import jdk.graal.compiler.core.phases.fuzzing.FullFuzzedCompilationPlan;
import jdk.graal.compiler.core.phases.fuzzing.FullFuzzedTierPlan;
import jdk.graal.compiler.core.phases.fuzzing.FuzzedSuites;
import jdk.graal.compiler.core.phases.fuzzing.MinimalFuzzedCompilationPlan;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestPhase;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Base class for the JTT tests.
 * <p>
 * These tests are executed twice: once with arguments passed to the execution and once with the
 * arguments bound to the test's parameters during compilation. The latter is a good test of
 * canonicalization.
 */
public class JTTTest extends GraalCompilerTest {

    /**
     * Contains the fuzzed {@link Suites} to use for the compilation if
     * {@link #COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY} is {@code true}.
     */
    private Suites fuzzedSuites;

    public static final class DummyTestClass {
    }

    /**
     * The arguments which, if non-null, will replace the Locals in the test method's graph.
     */
    Object[] argsToBind;

    @SuppressWarnings("this-escape")
    public JTTTest() {
        Assert.assertNotNull(getCodeCache());
    }

    @Override
    protected Object[] getArgumentToBind() {
        return argsToBind;
    }

    /**
     * If non-null, then this is a test for a method returning a {@code double} value that must be
     * within {@code ulpDelta}s of the expected value.
     */
    protected Double ulpDelta;

    @Override
    protected void assertDeepEquals(Object expected, Object actual) {
        if (ulpDelta != null) {
            double expectedDouble = (double) expected;
            double actualDouble = (Double) actual;
            double ulp = Math.ulp(expectedDouble);
            double delta = ulpDelta * ulp;
            try {
                Assert.assertEquals(expectedDouble, actualDouble, delta);
            } catch (AssertionError e) {
                double diff = Math.abs(expectedDouble - actualDouble);
                double diffUlps = diff / ulp;
                throw new AssertionError(e.getMessage() + " // " + diffUlps + " ulps");
            }
        } else {
            super.assertDeepEquals(expected, actual);
        }
    }

    protected OptionValues getOptions() {
        OptionValues options = getInitialOptions();
        if (Boolean.getBoolean(COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY)) {
            options = FuzzedSuites.fuzzingOptions(options);
        }
        return options;
    }

    protected void runTest(String name, Object... args) {
        runTest(getOptions(), name, args);
    }

    protected void runTest(OptionValues options, String name, Object... args) {
        runTest(options, CollectionsUtil.setOf(), name, args);
    }

    protected void runTest(Set<DeoptimizationReason> shouldNotDeopt, String name, Object... args) {
        runTest(getOptions(), shouldNotDeopt, name, args);
    }

    protected void runTest(OptionValues options, Set<DeoptimizationReason> shouldNotDeopt, String name, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        Object receiver = method.isStatic() ? null : this;

        Result expect = executeExpected(method, receiver, args);

        testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
        if (args.length > 0) {
            this.argsToBind = args;
            testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
            this.argsToBind = null;
        }
    }

    /**
     * System property to indicate the compilation plan should be created by fuzzing.
     */
    protected static final String COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY = "test.graal.compilationplan.fuzzing";
    /**
     * System property to provide {@link MinimalFuzzedCompilationPlan#getRandomSeed()}.
     */
    private static final String SEED_SYSTEM_PROPERTY = "test.graal.compilationplan.fuzzing.seed";
    /**
     * System property to indicate a {@link MinimalFuzzedCompilationPlan} should be used instead of
     * a {@link FullFuzzedCompilationPlan}.
     */
    private static final String MINIMAL_PLAN_SYSTEM_PROPERTY = "test.graal.compilationplan.fuzzing.minimal";
    /**
     * System property to provide {@link FullFuzzedTierPlan#getPhaseSkipOdds()} for all tiers.
     */
    private static final String PHASE_SKIP_ODDS_SYSTEM_PROPERTY = "test.graal.skip.phase.insertion.odds";
    /**
     * System property to provide {@link FullFuzzedTierPlan#getPhaseSkipOdds()} for high tier.
     */
    private static final String HIGH_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY = "test.graal.skip.phase.insertion.odds.high.tier";
    /**
     * System property to provide {@link FullFuzzedTierPlan#getPhaseSkipOdds()} for mid tier.
     */
    private static final String MID_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY = "test.graal.skip.phase.insertion.odds.mid.tier";
    /**
     * System property to provide {@link FullFuzzedTierPlan#getPhaseSkipOdds()} for low tier.
     */
    private static final String LOW_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY = "test.graal.skip.phase.insertion.odds.low.tier";

    /**
     * Uses a fuzzed compilation plan for the test if the
     * {@link #COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY} is set to {@code true}. Otherwise, resumes
     * to the usual compilation using {@link GraalCompilerTest#testAgainstExpected}.
     */
    @Override
    protected final void testAgainstExpected(OptionValues options, ResolvedJavaMethod method, Result expect, Set<DeoptimizationReason> shouldNotDeopt, Object receiver, Object... args) {
        if (Boolean.getBoolean(COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY)) {
            testAgainstExpectedWithFuzzedCompilationPlan(options, method, expect, shouldNotDeopt, receiver, args);
        } else {
            super.testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
        }
    }

    /**
     * Uses a fuzzed compilation plan for the compilation instead of the predefined plan used in
     * {@link GraalCompilerTest#testAgainstExpected}.
     *
     * This fuzzed compilation plan is generated using the given random seed provided by the
     * corresponding system property (see {@link #SEED_SYSTEM_PROPERTY}). Otherwise, it uses a
     * random {@link Long} as seed.
     *
     * If the {@link #MINIMAL_PLAN_SYSTEM_PROPERTY} is set to {@code true}, a
     * {@link MinimalFuzzedCompilationPlan} will be used for compilation. Otherwise, a
     * {@link FullFuzzedCompilationPlan} is used.
     *
     * The probability of inserting an optional phase inside the plan (see
     * {@link FullFuzzedTierPlan#getPhaseSkipOdds()}) is determined using the system properties
     * {@link #PHASE_SKIP_ODDS_SYSTEM_PROPERTY}, {@link #HIGH_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY},
     * {@link #MID_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY} and
     * {@link #LOW_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY}.
     */
    private void testAgainstExpectedWithFuzzedCompilationPlan(OptionValues options, ResolvedJavaMethod method, Result expect, Set<DeoptimizationReason> shouldNotDeopt, Object receiver,
                    Object... args) {
        Suites originalSuites = super.createSuites(options);
        // Removes {@link TestPhase}s added by {@link GraalCompilerTest} since they are anonymous
        // phases that cannot be serialized.
        removeTestPhases(originalSuites);
        StructuredGraph graph = parseForCompile(method, getCompilationId(method), options);
        long randomSeed = Long.getLong(SEED_SYSTEM_PROPERTY, getRandomInstance().nextLong());
        RuntimeProvider runtime = Graal.getRequiredCapability(RuntimeProvider.class);

        MinimalFuzzedCompilationPlan minimalFuzzedCompilationPlan = MinimalFuzzedCompilationPlan.createMinimalFuzzedCompilationPlan(
                        originalSuites,
                        graph.getGraphState(),
                        GraphState.MandatoryStages.getFromName(runtime.getCompilerConfigurationName()),
                        randomSeed);
        if (Boolean.getBoolean(MINIMAL_PLAN_SYSTEM_PROPERTY)) {
            testFuzzedCompilationPlan(minimalFuzzedCompilationPlan, options, method, expect, shouldNotDeopt, receiver, args);
            return;
        }
        Integer phaseInsertionProbabilityHighTier = Integer.getInteger(HIGH_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY, Integer.getInteger(PHASE_SKIP_ODDS_SYSTEM_PROPERTY));
        if (phaseInsertionProbabilityHighTier == null) {
            // Creates a fuzzed compilation plan with the default odds of skipping the insertion of
            // a phase (see {@link FuzzedPhasePlan#createFullFuzzedPhasePlan(MinimalFuzzedPhasePlan,
            // GraphState)})
            FullFuzzedCompilationPlan fullFuzzedCompilationPlan = FullFuzzedCompilationPlan.createFullFuzzedCompilationPlan(minimalFuzzedCompilationPlan, graph.getGraphState());
            testFuzzedCompilationPlan(fullFuzzedCompilationPlan, options, method, expect, shouldNotDeopt, receiver, args);
            return;
        }
        int phaseInsertionProbabilityMidTier = Integer.getInteger(MID_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY, phaseInsertionProbabilityHighTier);
        int phaseInsertionProbabilityLowTier = Integer.getInteger(LOW_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY, phaseInsertionProbabilityHighTier);
        FullFuzzedCompilationPlan fullFuzzedCompilationPlan = FullFuzzedCompilationPlan.createFullFuzzedCompilationPlan(minimalFuzzedCompilationPlan, graph.getGraphState(),
                        phaseInsertionProbabilityHighTier,
                        phaseInsertionProbabilityMidTier,
                        phaseInsertionProbabilityLowTier);
        testFuzzedCompilationPlan(fullFuzzedCompilationPlan, options, method, expect, shouldNotDeopt, receiver, args);
    }

    /**
     * Executes the compilation with the given fuzzed compilation plan and asserts the result of the
     * compilation corresponds to the expected result. For this, {@link #fuzzedSuites} is set with
     * {@link MinimalFuzzedCompilationPlan#getSuites()}. If the execution of the fuzzed compilation
     * plan throws or the result is unexpected, the problem is handled by calling
     * {@link #handleFailingFuzzedCompilationPlan}.
     */
    private void testFuzzedCompilationPlan(MinimalFuzzedCompilationPlan plan, OptionValues options, ResolvedJavaMethod method, Result expect, Set<DeoptimizationReason> shouldNotDeopt,
                    Object receiver, Object... args) {
        OptionValues fuzzingOptions = FuzzedSuites.fuzzingOptions(options);
        fuzzedSuites = plan.getSuites();
        Result actual = null;
        try {
            actual = executeActualCheckDeopt(fuzzingOptions, method, shouldNotDeopt, receiver, args);
        } catch (AssumptionViolatedException e) {
            throw e;
        } catch (Throwable t) {
            handleFailingFuzzedCompilationPlan(t, "Error during the execution of the fuzzed compilation plan.", plan);
        }
        try {
            assertEquals(expect, actual);
        } catch (Throwable t) {
            handleFailingFuzzedCompilationPlan(t, "The assertion on the result of the compilation with the fuzzed compilation plan throws an error.", plan);
        }
    }

    /**
     * Handles the error caused by the fuzzed compilation plan. It saves a serialized version of the
     * fuzzed compilation plan to a file and throws the original error with some information on the
     * fuzzed compilation plan and how to reproduce the error.
     */
    private void handleFailingFuzzedCompilationPlan(Throwable cause, String reason, MinimalFuzzedCompilationPlan plan) {
        String dumpPath = getDebugContext().getDumpPath("", false);
        saveCompilationPlan(plan, dumpPath);

        String testName = "";
        Optional<StackTraceElement> stackTraceContainingTestName = Arrays.asList(new GraalError("").getStackTrace()).stream().filter(
                        stackTraceElement -> !stackTraceElement.getClassName().contains("JTTTest")).findFirst();
        if (stackTraceContainingTestName.isPresent()) {
            testName = stackTraceContainingTestName.get().getClassName();
        }

        Formatter failureInfoBuf = new Formatter();
        failureInfoBuf.format("%s%n%n", reason);
        failureInfoBuf.format("%s%n%n", plan.toString());
        failureInfoBuf.format("Command to retry:%n");
        RuntimeProvider runtime = Graal.getRequiredCapability(RuntimeProvider.class);
        if (plan instanceof FullFuzzedCompilationPlan) {
            failureInfoBuf.format("mx phaseplan-fuzz-jtt-tests -D%s=%s -D%s=%s -D%s=%s -D%s=%s -Djdk.graal.CompilerConfiguration=%s %s%n",
                            SEED_SYSTEM_PROPERTY, plan.getRandomSeed(),
                            HIGH_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY, ((FullFuzzedTierPlan<HighTierContext>) plan.getHighTier()).getPhaseSkipOdds(),
                            MID_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY, ((FullFuzzedTierPlan<MidTierContext>) plan.getMidTier()).getPhaseSkipOdds(),
                            LOW_TIER_PHASE_SKIP_ODDS_SYSTEM_PROPERTY, ((FullFuzzedTierPlan<LowTierContext>) plan.getLowTier()).getPhaseSkipOdds(),
                            runtime.getCompilerConfigurationName(),
                            testName);
        } else {
            failureInfoBuf.format("mx phaseplan-fuzz-jtt-tests -D%s=%s -D%s=true -Djdk.graal.CompilerConfiguration=%s %s%n",
                            SEED_SYSTEM_PROPERTY, plan.getRandomSeed(),
                            MINIMAL_PLAN_SYSTEM_PROPERTY,
                            runtime.getCompilerConfigurationName(),
                            testName);
        }
        String failureInfo = failureInfoBuf.toString();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream ps = new PrintStream(baos)) {
                ps.printf("%s%n", failureInfo);
                ps.printf("The stack trace:%n");
                cause.printStackTrace(ps);
            }
            Files.write(Paths.get(dumpPath + "_failure.log"), baos.toByteArray());
        } catch (IOException e) {
            GraalError.shouldNotReachHere(e, "Error saving log of failed fuzzed compilation plan to " + dumpPath + "_failure.log");
        }
        GraalError.shouldNotReachHere(cause, failureInfo);
    }

    /**
     * Saves the {@link AbstractCompilationPlan} to a file at the given path.
     */
    private static void saveCompilationPlan(AbstractCompilationPlan plan, String dumpPath) {
        removeTestPhases(plan.getSuites());
        plan.saveCompilationPlan(dumpPath);
    }

    /**
     * Removes {@link TestPhase}s from the high, mid and low tier of the given {@link Suites}.
     */
    private static void removeTestPhases(Suites suites) {
        removeTestPhases(suites.getHighTier());
        removeTestPhases(suites.getMidTier());
        removeTestPhases(suites.getLowTier());
    }

    /**
     * Removes {@link TestPhase}s from the given {@link PhaseSuite}.
     */
    private static <C> void removeTestPhases(PhaseSuite<C> tier) {
        ListIterator<BasePhase<? super C>> testPhase = tier.findPhase(TestPhase.class);
        while (testPhase != null) {
            testPhase.remove();
            testPhase = tier.findPhase(TestPhase.class);
        }
    }

    /**
     * @return {@link #fuzzedSuites} with a graph verification ({@link StructuredGraph#verify}) at
     *         the end of each tier if {@link #COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY} is
     *         {@code true}. Otherwise, returns {@link GraalCompilerTest#createSuites}.
     */
    @Override
    protected Suites createSuites(OptionValues opts) {
        if (!Boolean.getBoolean(COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY) || fuzzedSuites == null) {
            return super.createSuites(opts);
        }
        fuzzedSuites.getHighTier().appendPhase(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                checkHighTierGraph(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            public CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        fuzzedSuites.getMidTier().appendPhase(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                checkMidTierGraph(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            public CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        fuzzedSuites.getLowTier().appendPhase(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                checkLowTierGraph(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            public CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        return fuzzedSuites;
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        assert graph.verify() : "The verification of the graph after high tier returned a negative result";
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        assert graph.verify() : "The verification of the graph after mid tier returned a negative result";
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        assert graph.verify() : "The verification of the graph after low tier returned a negative result";
    }
}
