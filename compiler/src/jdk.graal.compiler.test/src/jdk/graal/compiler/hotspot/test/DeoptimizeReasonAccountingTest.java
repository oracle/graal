/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.runtime.RuntimeProvider;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that DeoptimizationReason accounting in HotSpot is working properly.
 */
public class DeoptimizeReasonAccountingTest extends GraalCompilerTest {

    /**
     * Snippet that is compiled twice for each {@link DeoptimizationReason} value, once as a normal
     * compilation and again as an OSR compilation.
     */
    public static boolean deoptimizeSnippet() {
        GraalDirectives.deoptimize(DeoptimizationAction.InvalidateRecompile, getReason(), false);
        return GraalDirectives.inCompiledCode();
    }

    /**
     * Forces recompilation of {@link #deoptimizeSnippet()} for each call to
     * {@link #test(String, Object...)}.
     */
    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        return super.getCode(installedCodeOwner, graph, true, installAsDefault, options);
    }

    /**
     * Replaces {@link #getReason()} with {@link #reason} during compilation of
     * {@link #deoptimizeSnippet()}.
     */
    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, DeoptimizeReasonAccountingTest.class);
        r.register(new InvocationPlugin("getReason") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                SnippetReflectionProvider snippetReflection = getSnippetReflection();
                b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReflection.forObject(reason), getMetaAccess(), b.getGraph()));
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }

    /**
     * Modifies {@code compilationResult} to install it as an OSR compiled method if {@link #isOSR
     * == true}.
     */
    @Override
    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId, OptionValues options) {
        if (isOSR) {
            compilationResult.setEntryBCI(0);
        }
        return super.compile(installedCodeOwner, graph, compilationResult, compilationId, options);
    }

    private static DeoptimizationReason getReason() {
        return DeoptimizationReason.UnreachedCode;
    }

    private DeoptimizationReason reason;

    /**
     * Simulate deoptimization in an OSR compiler method?
     */
    private boolean isOSR;

    @Test
    public void testDeoptimize() {
        HotSpotVMConfigStore store = ((HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class)).getVMConfig().getStore();
        boolean has8278871 = store.getConstants().containsKey("Deoptimization::Reason_TRAP_HISTORY_LENGTH"); // JDK-8278871
        Assume.assumeTrue("release".equals(System.getProperty("jdk.debug")) || has8278871);
        for (DeoptimizationReason r : DeoptimizationReason.values()) {
            for (boolean osr : new boolean[]{false}) {
                this.reason = r;
                this.isOSR = osr;
                test("deoptimizeSnippet");
                ProfilingInfo info = lastCompiledGraph.method().getProfilingInfo(!isOSR, isOSR);
                int count = info.getDeoptimizationCount(reason);
                Assert.assertEquals(String.format("reason:%s, osr:%s", r, osr), 1, count);
            }
        }
    }
}
