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
package jdk.graal.compiler.core.test;

import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.graal.compiler.phases.common.InitMemoryVerificationPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;

/**
 * Tests that {@link InitMemoryVerificationPhase} actually verifies the properties it aims to.
 */
public class InitMemoryVerificationTest extends GraalCompilerTest {

    @SuppressWarnings("unused")
    private static class TestClass {
        private final int field;

        TestClass(int field) {
            this.field = field;
        }
    }

    public static Object allocate(int val) {
        return new TestClass(val + 2);
    }

    private StructuredGraph getMidTierGraph(String snippet, Suites suites) {
        StructuredGraph g = parseEager(snippet, AllowAssumptions.NO, getInitialOptions());
        suites.getHighTier().apply(g, getDefaultHighTierContext());
        suites.getMidTier().apply(g, getDefaultMidTierContext());
        return g;
    }

    @Test
    public void testVerificationPasses() {
        StructuredGraph g = getMidTierGraph("allocate", createSuites(getInitialOptions()));
        assertInGraph(g, PublishWritesNode.class, MembarNode.class);
        new InitMemoryVerificationPhase().apply(g, getDefaultLowTierContext());
    }

    @Test
    public void testVerificationFailsWithoutPublish() {
        Suites s = createSuites(getInitialOptions());
        s.getMidTier().appendPhase(new BasePhase<>() {

            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, MidTierContext context) {
                for (PublishWritesNode publish : graph.getNodes().filter(PublishWritesNode.class)) {
                    publish.replaceAtUsages(publish.allocation());
                    GraphUtil.removeFixedWithUnusedInputs(publish);
                }
            }
        });

        StructuredGraph g = getMidTierGraph("allocate", s);
        assertNotInGraph(g, PublishWritesNode.class);
        try (DebugContext.Scope _ = getDebugContext().disable()) {
            new InitMemoryVerificationPhase().apply(g, getDefaultLowTierContext());
            throw new GraalError("Should fail init memory verification");
        } catch (VerifyPhase.VerificationError e) {
            Assert.assertTrue(e.getMessage().contains("unpublished allocations"));
        }
    }

    @Test
    public void testVerificationFailsWithoutMembar() {
        Suites s = createSuites(getInitialOptions());
        s.getMidTier().appendPhase(new BasePhase<>() {
            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, MidTierContext context) {
                for (MembarNode memBar : graph.getNodes().filter(MembarNode.class)) {
                    GraphUtil.removeFixedWithUnusedInputs(memBar);
                }
            }
        });

        StructuredGraph g = getMidTierGraph("allocate", s);
        assertNotInGraph(g, GraphUtil.class);
        try (DebugContext.Scope _ = getDebugContext().disable()) {
            new InitMemoryVerificationPhase().apply(g, getDefaultLowTierContext());
            throw new GraalError("Should fail init memory verification");
        } catch (VerifyPhase.VerificationError e) {
            Assert.assertTrue(e.getMessage().contains("writes to init memory not guarded by an init barrier"));
        }
    }
}
