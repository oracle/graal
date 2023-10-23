/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import static jdk.graal.compiler.core.GraalCompiler.compileGraph;
import static jdk.graal.compiler.core.common.GraalOptions.TrackNodeSourcePosition;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.SourceMapping;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Test that various substitution implementations produce the expected {@link NodeSourcePosition}
 * structure. Method substitutions and method plugins should leave behind a frame for the
 * substitution. Method substitutions should have bytecodes below the substitution. Snippet
 * lowerings should just have the bytecodes without a marker frame.
 */
public class SubstitutionNodeSourcePositionTest extends ReplacementsTest {

    public void snippetLowering(String[] array, String value) {
        array[0] = value;
    }

    @Test
    public void testSnippetLowering() {
        // This test is checking for the source positions from the snippet lowered write barrier so
        // it only works for GCs that have write barriers.
        assumeTrue("current gc has no write barrier", getProviders().getPlatformConfigurationProvider().getBarrierSet().hasWriteBarrier());

        // @formatter:off
        // Expect mappings of the form:
        //   at jdk.graal.compiler.hotspot.replacements.WriteBarrierSnippets.serialWriteBarrier(WriteBarrierSnippets.java:140) [bci: 18]
        //   at jdk.graal.compiler.hotspot.replacements.WriteBarrierSnippets.serialPreciseWriteBarrier(WriteBarrierSnippets.java:158) [bci: 5] Substitution
        //   at jdk.graal.compiler.hotspot.replacements.WriteBarrierSnippets.serialPreciseWriteBarrier(AddressNode$Address, WriteBarrierSnippets$Counters) [bci: -1] Substitution
        //   at jdk.graal.compiler.test.replacements.compiler.SubstitutionNodeSourcePositionTest.snippetLowering(SubstitutionNodeSourcePositionTest.java:99) [bci: 3]
        // @formatter:on
        //
        // The precise snippet bytecodes don't matter, just ensure that some actually appear after
        // lowering.
        checkMappings("snippetLowering", true, SubstitutionNodeSourcePositionTest.class, "snippetLowering");
    }

    public int methodPlugin(int i) {
        GraalDirectives.blackhole(i);
        return i;
    }

    @Test
    public void testMethodPlugin() {
        // @formatter:off
        // Expect mappings of the form:
        //   at jdk.graal.compiler.api.directives.GraalDirectives.blackhole(int) [bci: -1]
        //   at jdk.graal.compiler.test.replacements.compiler.SubstitutionNodeSourcePositionTest.methodPlugin(SubstitutionNodeSourcePositionTest.java:109) [bci: 1]
        // @formatter:on
        checkMappings("methodPlugin", false, GraalDirectives.class, "blackhole");
    }

    @SuppressWarnings("unlikely-arg-type")
    private void checkMappings(String snippetMethod, boolean hasBytecodes, Class<?> boundaryClass, String boundaryMethod) {
        List<SourceMapping> mappings = getSourceMappings(snippetMethod);
        ResolvedJavaType resolvedJavaType = getMetaAccess().lookupJavaType(boundaryClass);
        boolean found = false;
        Assert.assertTrue("must have mappings", !mappings.isEmpty());
        for (SourceMapping mapping : mappings) {
            // test SourceMapping class
            assertTrue(mapping.getStartOffset() <= mapping.getEndOffset());
            assertTrue(mapping.equals(mapping));
            assertFalse(mapping.equals(resolvedJavaType));
            assertTrue(mapping.toString().length() > 0); // checks for NPE, not content

            // check mappings
            NodeSourcePosition callee = null;
            for (NodeSourcePosition pos = mapping.getSourcePosition(); pos != null; pos = pos.getCaller()) {
                ResolvedJavaMethod method = pos.getMethod();
                if (method.getName().equals(boundaryMethod) && method.getDeclaringClass().equals(resolvedJavaType)) {
                    if ((callee != null) == hasBytecodes) {
                        if (hasBytecodes) {
                            assertTrue(callee.isSubstitution());
                        }
                        assertTrue(pos.trim() == pos);
                        assertTrue(mapping.getSourcePosition().trim() == pos);
                        found = true;
                    }
                }
                callee = pos;
            }
        }
        Assert.assertTrue("must have substitution for " + resolvedJavaType + "." + boundaryMethod, found);
    }

    private List<SourceMapping> getSourceMappings(String name) {
        final ResolvedJavaMethod method = getResolvedJavaMethod(name);
        final OptionValues options = new OptionValues(getInitialOptions(), TrackNodeSourcePosition, true);
        final StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES, options);
        final CompilationResult cr = compileGraph(graph, graph.method(), getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL, graph.getProfilingInfo(),
                        createSuites(graph.getOptions()), createLIRSuites(graph.getOptions()), new CompilationResult(graph.compilationId()), CompilationResultBuilderFactory.Default, true);
        return cr.getSourceMappings();
    }
}
