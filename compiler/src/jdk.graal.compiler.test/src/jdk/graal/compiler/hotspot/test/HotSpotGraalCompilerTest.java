/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import org.junit.Assume;
import org.junit.AssumptionViolatedException;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecode;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.nodes.Cancellable;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A compiler test that needs access to the {@link HotSpotGraalRuntimeProvider}.
 */
public abstract class HotSpotGraalCompilerTest extends GraalCompilerTest {

    /**
     * Gets the {@link HotSpotGraalRuntimeProvider}.
     */
    protected HotSpotGraalRuntimeProvider runtime() {
        return ((HotSpotBackend) getBackend()).getRuntime();
    }

    /**
     * Checks that the {@code UseJVMCICompiler} flag is false.
     *
     * @param message describes the reason the test should be ignored when Graal is the JIT
     * @throws AssumptionViolatedException if {@code UseJVMCICompiler == true}
     */
    public static void assumeGraalIsNotJIT(String message) {
        HotSpotVMConfigStore configStore = HotSpotJVMCIRuntime.runtime().getConfigStore();
        HotSpotVMConfigAccess access = new HotSpotVMConfigAccess(configStore);
        boolean useJVMCICompiler = access.getFlag("UseJVMCICompiler", Boolean.class);
        Assume.assumeFalse(message, useJVMCICompiler);
    }

    protected InstalledCode compileAndInstallSubstitution(Class<?> c, String methodName) {
        return compileAndInstallSubstitution(getMetaAccess().lookupJavaMethod(getMethod(c, methodName)));
    }

    protected InstalledCode compileAndInstallSubstitution(ResolvedJavaMethod method) {
        CompilationIdentifier compilationId = runtime().getHostBackend().getCompilationIdentifier(method);
        OptionValues options = getInitialOptions();
        StructuredGraph graph = getIntrinsicGraph(method, compilationId, getDebugContext(options), AllowAssumptions.YES, null);
        if (graph != null) {
            return getCode(method, graph, true, true, graph.getOptions());
        }
        return null;
    }

    @SuppressWarnings("unused")
    public StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, CompilationIdentifier compilationId, DebugContext debug, AllowAssumptions allowAssumptions, Cancellable cancellable) {
        GraphBuilderConfiguration.Plugins graphBuilderPlugins = getReplacements().getGraphBuilderPlugins();
        InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method, debug.getOptions());
        if (plugin != null && !plugin.inlineOnly()) {
            assert !plugin.isDecorator() : "lookupInvocation shouldn't return decorator plugins";
            Bytecode code = new ResolvedJavaMethodBytecode(method);
            OptionValues options = debug.getOptions();
            GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(graphBuilderPlugins);
            GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
            return new HotSpotReplacementsImpl.HotSpotIntrinsicGraphBuilder(options, debug, getProviders(), code, -1, StructuredGraph.AllowAssumptions.YES, config).buildGraph(plugin);
        }
        return null;
    }
}
