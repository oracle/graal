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

package com.oracle.svm.webimage.truffle;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.replacements.nodes.IntrinsicMethodNodeInterface;
import jdk.graal.compiler.truffle.substitutions.TruffleInvocationPlugins;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * {@link TruffleInvocationPlugins} registers various Truffle-specific graph build plugins that
 * produce {@link IntrinsicMethodNodeInterface} nodes. These do not have lowerings and require
 * custom code generation in each backend.
 * <p>
 * For Web Image, we simply turn off all these plugins. For performance some of these replacements
 * make sense, but they would negatively impact code size and require handwritten code to be
 * generated for all Web Image backends.
 */
public class WebImageTruffleGraphBuilderPlugins {

    public static void register(InvocationPlugins plugins) {
        registerExactMathPlugins(plugins);
    }

    public static void registerExactMathPlugins(InvocationPlugins plugins) {
        plugins.registerIntrinsificationPredicate(t -> t.getName().equals("Lcom/oracle/truffle/api/ExactMath;"));
        var r = new InvocationPlugins.Registration(plugins, "com.oracle.truffle.api.ExactMath").setAllowOverwrite(true);

        // TODO GR-65897 Remove this once we support unsigned float conversions in Wasm backend
        for (JavaKind floatKind : new JavaKind[]{JavaKind.Float, JavaKind.Double}) {
            for (JavaKind integerKind : new JavaKind[]{JavaKind.Int, JavaKind.Long}) {
                r.register(new InvocationPlugin.OptionalInvocationPlugin(
                                integerKind == JavaKind.Long ? "truncateToUnsignedLong" : "truncateToUnsignedInt",
                                floatKind.toJavaClass()) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                        return false;
                    }
                });
            }

            r.register(new InvocationPlugin.OptionalInvocationPlugin(
                            floatKind == JavaKind.Double ? "unsignedToDouble" : "unsignedToFloat",
                            long.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                    return false;
                }
            });
        }
    }
}
