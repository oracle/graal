/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.amd64.test;

import static org.junit.Assume.assumeTrue;

import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.graph.iterators.NodePredicates;
import org.graalvm.compiler.hotspot.meta.HotSpotGraphBuilderPlugins;
import org.graalvm.compiler.hotspot.test.HotSpotGraalCompilerTest;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.ReachabilityFenceNode;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;

/**
 * Tests {@link Reference#reachabilityFence(Object)} input canonicalization.
 */
public class ReachabilityFenceCanonicalizationTest extends HotSpotGraalCompilerTest {

    private static final class Payload {
        AtomicInteger ref0 = new AtomicInteger();
        AtomicInteger ref1 = new AtomicInteger();
    }

    public static Object uncompressOnlyUsedByReachabilityFences(Payload[] inputs, boolean condition) {
        Payload input0 = inputs[0];
        AtomicInteger ref0 = input0.ref0;
        if (condition) {
            if (ref0 != input0.ref1) {
                ref0.incrementAndGet();
            }
            Reference.reachabilityFence(input0);
            return inputs[1];
        } else {
            if (ref0 != input0.ref1) {
                ref0.incrementAndGet();
            }
            Reference.reachabilityFence(input0);
            return inputs[2];
        }
    }

    @Test
    public void testUncompressOnlyUsedByReachabilityFences() {
        /*
         * Note: This test only works on AMD64 currently because it relies on uncompression to be
         * folded during address lowering, so that only ReachabilityFence usages remain.
         */
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
        assumeTrue("requires -XX:+UseCompressedOops", runtime().getVMConfig().useCompressedOops);
        assumeTrue("skipping because of oop encoding", AMD64Address.isScaleShiftSupported(runtime().getVMConfig().getOopEncoding().getShift()));

        OptionValues options = new OptionValues(getInitialOptions(), HotSpotGraphBuilderPlugins.Options.ForceExplicitReachabilityFence, true);
        Object[] inputs = new Payload[]{new Payload(), new Payload(), new Payload()};
        test(options, "uncompressOnlyUsedByReachabilityFences", inputs, false);
        test(options, "uncompressOnlyUsedByReachabilityFences", inputs, true);
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        String methodName = graph.method().getName();
        switch (methodName) {
            case "uncompressOnlyUsedByReachabilityFences":
                /*
                 * Prerequisite: ReachabilityFence nodes should have an Uncompress input to ensure
                 * we are actually testing canonicalization of compression inputs. If this is no
                 * longer the case, the test should be revised.
                 */
                Assert.assertTrue("Expected at least two ReachabilityFences with a compression input",
                                graph.getNodes().filter(ReachabilityFenceNode.class).filter(fence -> fence.inputs().filter(CompressionNode.class).isNotEmpty()).count() >= 2);
                break;
            default:
                Assert.fail("Unexpected test snippet: " + methodName);
                break;
        }
        super.checkMidTierGraph(graph);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        graph.getNodes().filter(CompressionNode.class).//
                        filter(uncompress -> uncompress.usages().filter(NodePredicates.isNotA(ReachabilityFenceNode.class)).isEmpty()).//
                        forEach(uncompress -> Assert.fail(uncompress + " has only ReachabilityFence usages and should have been removed by now " + uncompress.usages().snapshot()));
        graph.getNodes().filter(ReachabilityFenceNode.class).//
                        filter(fence -> fence.inputs().filter(CompressionNode.class).isNotEmpty()).//
                        forEach(fence -> Assert.fail(fence + " should not have any compression inputs anymore: " + fence.inputs().snapshot()));
        super.checkLowTierGraph(graph);
    }

}
