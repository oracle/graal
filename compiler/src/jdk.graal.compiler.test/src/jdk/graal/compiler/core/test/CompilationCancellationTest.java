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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.CancellationBailoutException;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphDecoder;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;

public class CompilationCancellationTest extends GraalCompilerTest {

    @Test
    public void cancelledWithAlarmEnabled() {
        testProgressCancellation(300);
    }

    @Test
    public void cancelledWithAlarmDisabled() {
        testProgressCancellation(0);
    }

    @Test
    @SuppressWarnings("try")
    public void cancelledWithAlarmTemporarilyDisabled() {
        try (CompilationAlarm ignored = CompilationAlarm.disable()) {
            testProgressCancellation(0);
        }
    }

    @SuppressWarnings("try")
    private void testProgressCancellation(double expirationPeriod) {
        OptionValues options = new OptionValues(getInitialOptions(), CompilationAlarm.Options.CompilationExpirationPeriod, expirationPeriod,
                        CompilationAlarm.Options.CompilationNoProgressPeriod, 0d);
        try (CompilationAlarm ignored = CompilationAlarm.trackCompilationPeriod(options)) {
            for (boolean useEventCounterOverload : new boolean[]{false, true}) {
                AtomicBoolean cancelled = new AtomicBoolean();
                AtomicInteger polls = new AtomicInteger();
                Graph graph = new Graph(null, options, getDebugContext(), false, () -> {
                    polls.incrementAndGet();
                    return cancelled.get();
                });
                Runnable progress = useEventCounterOverload ? () -> CompilationAlarm.checkProgress(options, graph) : () -> CompilationAlarm.checkProgress(graph);
                while (!graph.eventCounterOverflows(CompilationAlarm.CHECK_BAILOUT_COUNTER)) {
                    // Drain graph construction events to start at a fresh counter period.
                }
                polls.set(0);
                for (int i = 0; i < CompilationAlarm.CHECK_BAILOUT_COUNTER; i++) {
                    progress.run();
                }
                Assert.assertEquals("Cancellation queries must be throttled", 0, polls.get());
                progress.run();
                progress.run();
                Assert.assertEquals(1, polls.get());
                cancelled.set(true);
                CancellationBailoutException bailout = assertThrows(CancellationBailoutException.class, () -> {
                    for (int i = 0; i < CompilationAlarm.CHECK_BAILOUT_COUNTER + 2; i++) {
                        progress.run();
                    }
                });
                assertFalse(bailout.isPermanent());
                Assert.assertEquals(2, polls.get());
            }
        }
    }

    @Test
    public void copiesInheritCancellation() {
        AtomicBoolean cancelled = new AtomicBoolean();
        Graph graph = new Graph(null, getInitialOptions(), getDebugContext(), false, cancelled::get);
        StructuredGraph structuredGraph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext(), AllowAssumptions.YES).cancellable(cancelled::get).build();
        for (Graph original : new Graph[]{graph, structuredGraph}) {
            Graph copy = original.copy(getDebugContext());
            Assert.assertSame(original.getCancellable(), copy.getCancellable());
            copy.checkCancellation();
            cancelled.set(true);
            assertThrows(CancellationBailoutException.class, original::checkCancellation);
            assertThrows(CancellationBailoutException.class, copy::checkCancellation);
            CancellationBailoutException bailout = assertThrows(CancellationBailoutException.class, () -> {
                for (int i = 0; i < CompilationAlarm.CHECK_BAILOUT_COUNTER + 2; i++) {
                    CompilationAlarm.checkProgress(copy);
                }
            });
            assertFalse(bailout.isPermanent());
            cancelled.set(false);
        }
    }

    public static int loopSnippet(int count) {
        int result = 0;
        for (int i = 0; i < count; i++) {
            result += i;
        }
        return result;
    }

    @Test
    public void cancelledDuringDecoding() {
        StructuredGraph original = parseEager("loopSnippet", AllowAssumptions.YES);
        GraphEncoder encoder = new GraphEncoder(getTarget().arch);
        encoder.prepare(original);
        encoder.finishPrepare();
        int offset = encoder.encode(original);
        EncodedGraph encoded = new EncodedGraph(encoder.getEncoding(), offset, encoder.getObjects(), encoder.getNodeClasses(), original);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean processedNode = new AtomicBoolean();
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext(), AllowAssumptions.YES).method(original.method()).cancellable(cancelled::get).build();
        GraphDecoder decoder = new GraphDecoder(getTarget().arch, graph) {
            @Override
            protected LoopScope processNextNode(MethodScope methodScope, LoopScope loopScope) {
                processedNode.set(true);
                LoopScope next = super.processNextNode(methodScope, loopScope);
                cancelled.set(true);
                return next;
            }

            @Override
            protected void cleanupGraph(MethodScope methodScope) {
                throw new AssertionError("A cancelled compilation must not enter graph cleanup");
            }
        };
        assertThrows(CancellationBailoutException.class, () -> decoder.decode(encoded));
        assertTrue(processedNode.get());
    }
}
