/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.test;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.oracle.graal.api.test.Graal;
import com.oracle.graal.compiler.common.CompilationIdentifier;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.hotspot.HotSpotGraalCompiler;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import com.oracle.graal.runtime.RuntimeProvider;

import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;

/**
 * Exercise the compilation of intrinsic method substitutions.
 */
public class TestIntrinsicCompiles extends GraalCompilerTest {

    private static boolean match(ResolvedJavaMethod method, VMIntrinsicMethod intrinsic) {
        if (intrinsic.name.equals(method.getName())) {
            if (intrinsic.descriptor.equals(method.getSignature().toMethodDescriptor())) {
                String declaringClass = method.getDeclaringClass().toClassName().replace('.', '/');
                if (declaringClass.equals(intrinsic.declaringClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ResolvedJavaMethod findMethod(Set<ResolvedJavaMethod> methods, VMIntrinsicMethod intrinsic) {
        for (ResolvedJavaMethod method : methods) {
            if (match(method, intrinsic)) {
                return method;
            }
        }
        return null;
    }

    @Test
    @SuppressWarnings("try")
    public void test() {
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) JVMCI.getRuntime().getCompiler();
        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        HotSpotProviders providers = rt.getHostBackend().getProviders();
        Plugins graphBuilderPlugins = providers.getGraphBuilderPlugins();
        InvocationPlugins invocationPlugins = graphBuilderPlugins.getInvocationPlugins();

        Set<ResolvedJavaMethod> pluginMethods = invocationPlugins.getMethods();
        HotSpotVMConfigStore store = rt.getVMConfig().getStore();
        List<VMIntrinsicMethod> intrinsics = store.getIntrinsics();
        for (VMIntrinsicMethod intrinsic : intrinsics) {
            ResolvedJavaMethod method = findMethod(pluginMethods, intrinsic);
            if (method != null) {
                InvocationPlugin plugin = invocationPlugins.lookupInvocation(method);
                if (plugin instanceof MethodSubstitutionPlugin && !method.isNative()) {
                    StructuredGraph graph = compiler.getIntrinsicGraph(method, providers, CompilationIdentifier.INVALID_COMPILATION_ID);
                    getCode(method, graph);
                }
            }
        }
    }
}
