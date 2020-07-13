/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Exercise
 * {@link org.graalvm.compiler.nodes.spi.Replacements#getIntrinsicGraph(ResolvedJavaMethod, CompilationIdentifier, DebugContext, AllowAssumptions, Cancellable)}
 * with regular method substitutions and encoded graphs.
 */
@RunWith(Parameterized.class)
public class RootMethodSubstitutionTest extends GraalCompilerTest {

    private final ResolvedJavaMethod method;
    private final InvocationPlugin plugin;

    public RootMethodSubstitutionTest(ResolvedJavaMethod method, InvocationPlugin plugin) {
        this.method = method;
        this.plugin = plugin;
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();

        Backend backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        Providers providers = backend.getProviders();

        MapCursor<String, List<InvocationPlugins.Binding>> cursor = providers.getReplacements().getGraphBuilderPlugins().getInvocationPlugins().getBindings(true).getEntries();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        while (cursor.advance()) {
            String className = cursor.getKey();
            ResolvedJavaType type = null;
            try {
                String typeName = className.substring(1, className.length() - 1).replace('/', '.');
                ClassLoader cl = ClassLoader.getSystemClassLoader();
                Class<?> clazz = Class.forName(typeName, true, cl);
                type = metaAccess.lookupJavaType(clazz);
            } catch (ClassNotFoundException e) {
                continue;
            }

            for (InvocationPlugins.Binding binding : cursor.getValue()) {
                if (!binding.plugin.inlineOnly()) {
                    ResolvedJavaMethod original = null;
                    original = findMethod(binding, type.getDeclaredMethods());
                    if (original == null) {
                        original = findMethod(binding, type.getDeclaredConstructors());
                    }
                    if (original == null) {
                        continue;
                    }
                    if (!original.isNative()) {
                        // Make sure the plugin we found hasn't been overridden.
                        InvocationPlugin plugin = providers.getReplacements().getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(original);
                        if (plugin == binding.plugin) {
                            ret.add(new Object[]{original, plugin});
                        }
                    }
                }
            }
        }
        return ret;
    }

    private static ResolvedJavaMethod findMethod(InvocationPlugins.Binding binding, ResolvedJavaMethod[] methods) {
        ResolvedJavaMethod original = null;
        for (ResolvedJavaMethod declared : methods) {
            if (declared.getName().equals(binding.name)) {
                if (declared.isStatic() == binding.isStatic) {
                    if (declared.getSignature().toMethodDescriptor().startsWith(binding.argumentsDescriptor)) {
                        original = declared;
                        break;
                    }
                }
            }
        }
        return original;
    }

    private StructuredGraph getIntrinsicGraph(boolean useEncodedGraphs) {
        OptionValues options = new OptionValues(getDebugContext().getOptions(), GraalOptions.UseEncodedGraphs, useEncodedGraphs);
        DebugContext debugContext = new Builder(options, getDebugHandlersFactories()).description(getDebugContext().getDescription()).build();
        return getReplacements().getIntrinsicGraph(method, CompilationIdentifier.INVALID_COMPILATION_ID, debugContext, AllowAssumptions.YES, null);
    }

    StructuredGraph expectedGraph;
    StructuredGraph actualGraph;

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        // Capture the graphs after high tier
        if (expectedGraph == null) {
            expectedGraph = (StructuredGraph) graph.copy(graph.getDebug());
        } else {
            assert actualGraph == null;
            actualGraph = (StructuredGraph) graph.copy(graph.getDebug());
        }
        super.checkHighTierGraph(graph);
    }

    @Test
    public void test() {
        StructuredGraph regularGraph = getIntrinsicGraph(false);
        if (regularGraph != null) {
            getCode(method, regularGraph);
        }

        if (plugin instanceof MethodSubstitutionPlugin) {
            assertTrue(regularGraph != null, "MethodSubstitutionPlugin must produce a graph");
            StructuredGraph encodedGraph = getIntrinsicGraph(true);
            assertTrue(encodedGraph != null, "must produce a graph");
            getCode(method, encodedGraph);

            // Compare the high tier graphs since the final graph might have scheduler
            // differences because of different usage ordering.
            assertEquals(expectedGraph, actualGraph, true, false);
        }
    }

}
