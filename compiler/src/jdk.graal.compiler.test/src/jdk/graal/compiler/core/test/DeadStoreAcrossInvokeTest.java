/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.Optional;

import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;

/** Tests dead-store elimination across invokes with precise memory effects. */
public class DeadStoreAcrossInvokeTest extends GraalCompilerTest {

    private static final String UNRELATED_CALLEE_NAME = "unrelatedWork";
    private static final String OBSERVING_CALLEE_NAME = "observingWork";
    private static final String THROWING_CALLEE_NAME = "potentiallyThrowingWork";
    private static final String ELIMINATED_SNIPPET_NAME = "eliminatedSnippet";

    /** Value modified before and after the call. */
    static final class Counter {
        int value;
    }

    /** Value modified only by the call. */
    static final class Logger {
        int count;
    }

    /** Call modeled as non-throwing and accessing no relevant memory. */
    @BytecodeParserNeverInline
    static void unrelatedWork(Logger logger) {
        if (logger != null) {
            logger.count++;
        }
    }

    /** Call modeled as non-throwing but observing the overwritten location. */
    @BytecodeParserNeverInline
    static void observingWork(Counter counter, Logger logger) {
        if (logger != null) {
            logger.count += counter.value;
        }
    }

    /** Call modeled as potentially throwing before the overwriting store. */
    @BytecodeParserNeverInline
    static void potentiallyThrowingWork(Logger logger) {
        logger.count++;
    }

    /** Contains a store overwritten after a call with disjoint effects. */
    public static int eliminatedSnippet(Counter counter, Logger logger) {
        counter.value = 1;
        unrelatedWork(logger);
        counter.value = 2;
        return counter.value + logger.count;
    }

    /** Contains a store observed by the intervening call. */
    public static int observedSnippet(Counter counter, Logger logger) {
        counter.value = 1;
        observingWork(counter, logger);
        counter.value = 2;
        return counter.value + logger.count;
    }

    /** Contains a store observable when the intervening call throws. */
    public static int exceptionalSnippet(Counter counter, Logger logger) {
        counter.value = 1;
        potentiallyThrowingWork(logger);
        counter.value = 2;
        return counter.value + logger.count;
    }

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options).copy();
        suites.getHighTier().prependPhase(new RefineInvokePhase());
        return suites;
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        super.checkMidTierGraph(graph);
        int expectedWrites = graph.method().getName().equals(ELIMINATED_SNIPPET_NAME) ? 1 : 2;
        Assert.assertEquals("Unexpected number of writes after dead-store elimination.", expectedWrites, graph.getNodes().filter(WriteNode.class).count());
    }

    /** Refines test invokes to model the relevant interprocedural summaries. */
    private static final class RefineInvokePhase extends BasePhase<HighTierContext> {

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, HighTierContext context) {
            for (InvokeNode invoke : graph.getNodes().filter(InvokeNode.class)) {
                String targetName = invoke.getTargetMethod().getName();
                switch (targetName) {
                    case UNRELATED_CALLEE_NAME -> {
                        invoke.setKilledLocationIdentity(MemoryKill.NO_LOCATION);
                        invoke.setLocationIdentity(MemoryKill.NO_LOCATION);
                        invoke.setGuaranteedNotToThrow();
                        invoke.setInlineControl(Invoke.InlineControl.Never);
                    }
                    case OBSERVING_CALLEE_NAME -> {
                        invoke.setKilledLocationIdentity(MemoryKill.NO_LOCATION);
                        invoke.setLocationIdentity(LocationIdentity.any());
                        invoke.setGuaranteedNotToThrow();
                        invoke.setInlineControl(Invoke.InlineControl.Never);
                    }
                    case THROWING_CALLEE_NAME -> {
                        invoke.setKilledLocationIdentity(MemoryKill.NO_LOCATION);
                        invoke.setLocationIdentity(MemoryKill.NO_LOCATION);
                        invoke.setInlineControl(Invoke.InlineControl.Never);
                    }
                    default -> {
                    }
                }
            }
        }
    }

    /** Verifies elimination across a non-throwing call with disjoint effects. */
    @Test
    public void testDeadStoreAcrossInvoke() {
        test(ELIMINATED_SNIPPET_NAME, new Counter(), new Logger());
    }

    /** Verifies that an intervening read keeps the first store observable. */
    @Test
    public void testStoreObservedByInvoke() {
        test("observedSnippet", new Counter(), new Logger());
    }

    /** Verifies that a potentially throwing invoke keeps the first store observable. */
    @Test
    public void testStoreObservableOnException() {
        test("exceptionalSnippet", new Counter(), new Logger());
    }
}
