/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Exercise the compilation of intrinsic method substitutions.
 */
public class TestIntrinsicCompiles extends GraalCompilerTest {

    @Test
    @SuppressWarnings("try")
    public void test() throws ClassNotFoundException {
        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        HotSpotProviders providers = rt.getHostBackend().getProviders();
        Plugins graphBuilderPlugins = providers.getGraphBuilderPlugins();
        InvocationPlugins invocationPlugins = graphBuilderPlugins.getInvocationPlugins();

        EconomicMap<String, List<InvocationPlugin>> invocationPluginsMap = invocationPlugins.getInvocationPlugins(true);
        HotSpotVMConfigStore store = rt.getVMConfig().getStore();
        List<VMIntrinsicMethod> intrinsics = store.getIntrinsics();
        OptionValues options = getInitialOptions();
        DebugContext debug = getDebugContext(options);
        for (VMIntrinsicMethod intrinsic : intrinsics) {
            InvocationPlugin plugin = CheckGraalIntrinsics.findPlugin(invocationPluginsMap, intrinsic);
            if (plugin != null && !plugin.inlineOnly()) {
                ResolvedJavaMethod method = CheckGraalIntrinsics.resolveIntrinsic(getMetaAccess(), intrinsic);
                if (!method.isNative()) {
                    try {
                        StructuredGraph graph = providers.getReplacements().getIntrinsicGraph(method, INVALID_COMPILATION_ID, debug, AllowAssumptions.YES, null);
                        if (graph != null) {
                            boolean canCompile = true;
                            for (ForeignCallNode foreignCall : graph.getNodes().filter(ForeignCallNode.class)) {
                                if (foreignCall.canDeoptimize()) {
                                    /*
                                     * We cannot guarantee a valid framestate for this call when
                                     * parsed in an intrinsic context.
                                     */
                                    canCompile = false;
                                    break;
                                }
                            }
                            if (!canCompile) {
                                break;
                            }
                        }
                        getCode(method, graph);
                    } catch (AssertionError e) {
                        throw new GraalError(e, "Assertion error at %s", intrinsic.toString());
                    }
                }
            }
        }
    }
}
