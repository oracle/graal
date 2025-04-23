/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasmgc;

import com.oracle.svm.hosted.webimage.JSGraphBuilderPlugins;
import com.oracle.svm.hosted.webimage.wasm.WasmLMGraphBuilderPlugins;
import com.oracle.svm.hosted.webimage.wasmgc.snippets.WasmGCAllocationSnippets;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetSubstitutionInvocationPlugin;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class WasmGCGraphBuilderPlugins implements TargetGraphBuilderPlugins {
    @Override
    public void register(GraphBuilderConfiguration.Plugins plugins, Replacements replacements, Architecture arch, boolean registerForeignCallMath, OptionValues options) {

        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(() -> {
            JSGraphBuilderPlugins.registerStringPlugins(invocationPlugins, replacements);
            JSGraphBuilderPlugins.registerCurrentIsolatePlugins(invocationPlugins, replacements);
            JSGraphBuilderPlugins.registerThreadPlugins(invocationPlugins, replacements);
            WasmLMGraphBuilderPlugins.registerIntegerLongPlugins(invocationPlugins, JavaKind.Int, replacements);
            WasmLMGraphBuilderPlugins.registerIntegerLongPlugins(invocationPlugins, JavaKind.Long, replacements);
            WasmLMGraphBuilderPlugins.registerShortPlugins(invocationPlugins, replacements);
            WasmLMGraphBuilderPlugins.registerCharacterPlugins(invocationPlugins, replacements);
            WasmLMGraphBuilderPlugins.registerArraysPlugins(invocationPlugins, replacements);
            WasmLMGraphBuilderPlugins.registerBigIntegerPlugins(invocationPlugins, replacements);
            WasmLMGraphBuilderPlugins.registerMathPlugins(Math.class, invocationPlugins, replacements);
            WasmLMGraphBuilderPlugins.registerMathPlugins(StrictMath.class, invocationPlugins, replacements);
            // TODO GR-61725 Support ArrayFillNodes
            WasmLMGraphBuilderPlugins.unregisterArrayFillPlugins(invocationPlugins, replacements);
            registerAllocationPlugins(invocationPlugins, replacements);
            registerArraysSupportPlugins(invocationPlugins, replacements);
        });
    }

    private static void registerAllocationPlugins(InvocationPlugins plugins, Replacements replacements) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, WasmGCAllocationSupport.class, replacements);
        r.register(new SnippetSubstitutionInvocationPlugin<>(WasmGCAllocationSnippets.Templates.class,
                        "dynamicNewArrayImpl", Class.class, int.class) {
            @Override
            public SnippetTemplate.SnippetInfo getSnippet(WasmGCAllocationSnippets.Templates templates) {
                return templates.dynamicNewArraySnippet;
            }
        });
    }

    private static void registerArraysSupportPlugins(InvocationPlugins plugins, Replacements replacements) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, "jdk.internal.util.ArraysSupport", replacements).setAllowOverwrite(true);
        /*
         * Under WasmGC, the vectorizedMismatch will be much slower than just element-wise checking
         * because there exists no efficient way to load large chunks of arbitrary primitive arrays
         * that then can be compared. Doing that (which is what the default implementation does) is
         * under the hood done by performing multiple byte reads on the array.
         *
         * The contract of the method states that it can return the bitwise complement of the
         * remaining number of elements that still need to be checked. Having the method return
         * ~length, will cause callers to manually check all elements in some other way.
         */
        r.register(new InvocationPlugin("vectorizedMismatch", Object.class, long.class, Object.class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode aObject, ValueNode aOffset, ValueNode bObject, ValueNode bOffset, ValueNode length, ValueNode log2ArrayIndexScale) {
                b.addPush(JavaKind.Int, NotNode.create(length));
                return true;
            }
        });
    }
}
