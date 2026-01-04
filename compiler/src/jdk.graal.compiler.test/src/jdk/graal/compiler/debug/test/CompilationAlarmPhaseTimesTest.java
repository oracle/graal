/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug.test;

import java.util.Optional;

import org.junit.Test;

import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;

public class CompilationAlarmPhaseTimesTest extends GraalCompilerTest {

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites s = super.createSuites(opts);
        s = s.copy();
        s.getLowTier().appendPhase(new BasePhase<>() {

            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, LowTierContext context) {
                try {
                    for (int i = 0; i < SLEEP_SECONDS * 1000; i++) {
                        // sleep often to check progress often
                        Thread.sleep(1);
                        CompilationAlarm.checkProgress(graph);
                    }
                } catch (InterruptedException e) {
                    throw GraalError.shouldNotReachHere(e);
                }
            }

        });
        return s;
    }

    /**
     * Seconds this test tries to sleep a compiler phase to trigger a timeout.
     */
    public static final int SLEEP_SECONDS = 10;

    @Test
    public void testTimeOutRetryToString() {
        final double secondsToWait = 1D;
        OptionValues opt = new OptionValues(getInitialOptions(), CompilationAlarm.Options.CompilationExpirationPeriod, secondsToWait);
        try {
            test(opt, "foo", 10000);
        } catch (Throwable t) {
            if (!t.getMessage().contains("Compilation exceeded")) {
                throw new AssertionError("Unexpected exception: " + t, t);
            }
            StructuredGraph g = lastCompiledGraph;
            assert g != null;
            String message = t.getMessage();
            final String phaseName = "CompilationAlarmPhaseTimesTest$1";
            int index = message.indexOf(phaseName);
            // skip the "->"
            index += phaseName.length() + 2;
            String duration = "";
            char c;
            while (Character.isDigit((c = message.charAt(index)))) {
                duration += c;
                index++;
            }
            assert Integer.parseInt(duration) > 0 : String.format("Must at least wait some positive amount of time but waited %s error was %s", duration, message);
        }
    }

    public static int foo(int limit) {
        int result = 0;
        for (int i = 0; i < limit; i++) {
            result += i;
        }
        return result;
    }

    public static int convolutedWork(int limit) {
        int result = 0;
        for (int i = 0; i < limit; i++) {
            result += convolutedWork(1);
        }
        return result;
    }

    public static int bar(int limit) {
        int result = 0;
        for (int i = 0; i < limit; i++) {
            result += convolutedWork(i);
        }
        return result;
    }

    @Test
    public void testTimeOutRetryToStringWithInlining() {
        final double secondsToWait = 1D;
        OptionValues opt = new OptionValues(getInitialOptions(), CompilationAlarm.Options.CompilationExpirationPeriod, secondsToWait);
        try {
            test(opt, "bar", 10000);
        } catch (Throwable t) {
            if (!t.getMessage().contains("Compilation exceeded")) {
                throw new AssertionError("Unexpected exception: " + t, t);
            }
            StructuredGraph g = lastCompiledGraph;
            assert g != null;
            String message = t.getMessage();
            final String phaseName = "CompilationAlarmPhaseTimesTest$1";
            int index = message.indexOf(phaseName);
            // skip the "->"
            index += phaseName.length() + 2;
            String duration = "";
            char c;
            while (Character.isDigit((c = message.charAt(index)))) {
                duration += c;
                index++;
            }
            assert Integer.parseInt(duration) > 0 : String.format("Must at least wait some positive amount of time but waited %s error was %s", duration, message);
        }
    }

    public static boolean PRINT = false;

    /**
     * Test that the different combinations of graphs and phase names together with {@code null}
     * values does not cause any errors.
     */
    @Test
    public void testExplicitly() {
        final StructuredGraph g1 = parseEager("foo", StructuredGraph.AllowAssumptions.YES);
        final StructuredGraph g2 = parseEager("bar", StructuredGraph.AllowAssumptions.YES);
        final StructuredGraph g3 = parseEager("convolutedWork", StructuredGraph.AllowAssumptions.YES);

        Runnable empty = () -> {
        };
        Runnable singleSubPhase = () -> {
            CanonicalizerPhase.create().apply(g2, getDefaultHighTierContext());
        };
        Runnable listSubPhase = () -> {
            CanonicalizerPhase.create().apply(g2, getDefaultHighTierContext());
            CanonicalizerPhase.create().apply(g2, getDefaultHighTierContext());
        };

        StringBuilder singlePhaseNoSubTree = runAndTrack(() -> new PhaseWithSubPhases(empty).apply(g1));
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", singlePhaseNoSubTree);
        }

        StringBuilder singlePhaseSinlgeSub = runAndTrack(() -> new PhaseWithSubPhases(singleSubPhase).apply(g1));
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", singlePhaseSinlgeSub);
        }

        StringBuilder singlePhaseListSub = runAndTrack(() -> new PhaseWithSubPhases(listSubPhase).apply(g1));
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", singlePhaseListSub);
        }

        StringBuilder doublePhaseNoSubTree = runAndTrack(() -> {
            new PhaseWithSubPhases(empty).apply(g1);
            new PhaseWithSubPhases(empty).apply(g1);
        });
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", doublePhaseNoSubTree);
        }

        StringBuilder doublePhaseSinlgeSub = runAndTrack(() -> {
            new PhaseWithSubPhases(singleSubPhase).apply(g1);
            new PhaseWithSubPhases(singleSubPhase).apply(g1);
        });
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", doublePhaseSinlgeSub);
        }

        StringBuilder doublePhaseListSub = runAndTrack(() -> {
            new PhaseWithSubPhases(listSubPhase).apply(g1);
            new PhaseWithSubPhases(listSubPhase).apply(g1);
        });
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", doublePhaseListSub);
        }

        Runnable twoChildrenSinglePhase = () -> {
            new PhaseWithSubPhases(() -> {
                new PhaseWithSubPhases(() -> {
                    CanonicalizerPhase.create().apply(g3, getDefaultHighTierContext());
                }).apply(g2);
            }).apply(g1);
        };
        StringBuilder twoChildren = runAndTrack(twoChildrenSinglePhase);
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", twoChildren);
        }

        // enter the root with null
        StringBuilder nullGraphs = runAndTrack(() -> {
            CompilationAlarm c = CompilationAlarm.current();
            c.enterPhase("abcd", null);
            c.exitPhase("abcd", null);
        });
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", nullGraphs);
        }

        // enter child with null
        StringBuilder nullGraphs2 = runAndTrack(() -> {
            new PhaseWithSubPhases(() -> {
                CompilationAlarm c = CompilationAlarm.current();
                c.enterPhase("abcd", null);
                c.exitPhase("abcd", null);
            }).apply(g1);
        });
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", nullGraphs2);
        }

        StringBuilder nullGraphs3 = runAndTrack(() -> {
            CompilationAlarm c = CompilationAlarm.current();
            c.enterPhase("abcd", null);
            c.enterPhase("abcd2", g1);
            c.exitPhase("abcd2", g2);
            c.exitPhase("abcd", null);
        });
        if (PRINT) {
            TTY.printf("Tree is%n%s%n", nullGraphs3);
        }

    }

    private static StringBuilder runAndTrack(Runnable r) {
        // set the timeout very high, we just want to verify toString methods
        OptionValues opt = new OptionValues(getInitialOptions(), CompilationAlarm.Options.CompilationExpirationPeriod, Double.MAX_VALUE);
        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(opt)) {
            r.run();
            return alarm.elapsedPhaseTreeAsString();
        }
    }

    private static final class PhaseWithSubPhases extends Phase {

        final Runnable subPhases;

        private PhaseWithSubPhases(Runnable subPhases) {
            this.subPhases = subPhases;
        }

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph) {
            subPhases.run();
        }
    }
}
