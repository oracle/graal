/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;

import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.graal.snippets.SubstrateSharedGraphBuilderPlugins;
import com.oracle.svm.interpreter.ristretto.IsInRuntimeCompiledCodeNode;
import com.oracle.svm.interpreter.ristretto.RistrettoDirectives;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Registers Ristretto-specific graph builder plugins for hosted and runtime parse configurations.
 *
 * <p>
 * Hosted SVM already wires many of these semantics while building the image. Runtime parsing needs
 * a small parallel set because the caller may be a runtime-loaded class whose lookup, assertion, or
 * class-name behavior cannot be taken from hosted metadata.
 */
public final class RistrettoGraphBuilderPlugins {
    /*
     * Recreates the caller-sensitive MethodHandles.lookup() constant that hosted parsing normally
     * bakes into graphs for image methods. Runtime parsing cannot reuse a hosted singleton because
     * the lookup class depends on the runtime-loaded caller currently being compiled.
     */
    private static final Constructor<MethodHandles.Lookup> LOOKUP_CONSTRUCTOR = ReflectionUtil.lookupConstructor(MethodHandles.Lookup.class, Class.class);

    /**
     * Registers hosted-only Ristretto directive plugins.
     */
    public static void setHostedGraphBuilderPlugins(GraphBuilderConfiguration.Plugins plugins) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        registerRistrettoDirectivePlugins(invocationPlugins);
    }

    /**
     * Registers runtime plugins required for Crema and Ristretto compiled code.
     */
    public static void setRuntimeGraphBuilderPlugins(GraphBuilderConfiguration.Plugins plugins) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        registerRistrettoDirectivePlugins(invocationPlugins);
        registerSharedRuntimePlugins(invocationPlugins);
    }

    /**
     * Registers {@link RistrettoDirectives} plugins that identify runtime-compiled execution.
     */
    private static void registerRistrettoDirectivePlugins(InvocationPlugins invocationPlugins) {
        // use strings here to avoid pulling in actual Class<?> constants into the image for the
        // sake of invocation plugins
        Registration r = new Registration(invocationPlugins, RistrettoDirectives.class.getName());
        r.register(new InvocationPlugin.RequiredInlineOnlyInvocationPlugin("inRuntimeCompiledCode") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver) {
                b.addPush(JavaKind.Boolean, new IsInRuntimeCompiledCodeNode());
                return true;
            }
        });
    }

    /**
     * Registers the hosted-derived runtime-safe plugins shared with hosted SVM code paths.
     */
    private static void registerSharedRuntimePlugins(InvocationPlugins invocationPlugins) {
        SubstrateSharedGraphBuilderPlugins.registerSecurityManagerPlugin(invocationPlugins);
        SubstrateSharedGraphBuilderPlugins.registerSystemIdentityHashCodePlugin(invocationPlugins);
        SubstrateSharedGraphBuilderPlugins.registerObjectPlugins(invocationPlugins);
        registerMethodHandlesPlugins(invocationPlugins);
        /*
         * registerClassPlugins(...) accepts two optional hosted hooks. We intentionally disable
         * both here for semantic correctness of runtime-loaded code, not just because hosted
         * services are absent in runtime parsing.
         *
         * - classNameEncoder: hosted path can canonicalize names for image metadata/symbol
         * encoding. Runtime-loaded classes must keep runtime class-loader-visible naming semantics,
         * so this parse path must not apply hosted class-name encoding policy. -
         * desiredAssertionStatusProvider: hosted path can answer assertion status from hosted
         * metadata. Runtime-loaded classes derive assertion state from runtime loader directives,
         * so using hosted state here would incorrectly freeze image-build semantics into runtime
         * compilation.
         *
         * Passing null for both keeps shared runtime-safe class plugins while avoiding hosted-only
         * semantic assumptions on runtime-loaded classes.
         */
        SubstrateSharedGraphBuilderPlugins.registerClassPlugins(invocationPlugins, null, null);
    }

    private static void registerMethodHandlesPlugins(InvocationPlugins invocationPlugins) {
        /*
         * Hosted SVM can bake MethodHandles.lookup() as a constant for the image-built caller
         * during image generation. Runtime compilation must synthesize that same constant from the
         * runtime-loaded caller currently being parsed; otherwise lookupClass() and private-access
         * semantics drift back to hosted metadata.
         */
        Registration registration = new Registration(invocationPlugins, MethodHandles.class);
        registration.register(new InvocationPlugin.RequiredInlineOnlyInvocationPlugin("lookup") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver) {
                if (StackTraceUtils.ignoredBySecurityStackWalk(b.getMetaAccess(), b.getMetaAccessExtensionProvider(), b.getMethod())) {
                    return false;
                }
                // Build the lookup from the current parse caller, not from image-build metadata.
                MethodHandles.Lookup lookup = ReflectionUtil.newInstance(LOOKUP_CONSTRUCTOR, RistrettoUtils.getDeclaringJavaClass(b.getMethod()));
                b.addPush(JavaKind.Object, ConstantNode.forConstant(b.getSnippetReflection().forObject(lookup), b.getMetaAccess(), b.getGraph()));
                return true;
            }
        });
    }
}
