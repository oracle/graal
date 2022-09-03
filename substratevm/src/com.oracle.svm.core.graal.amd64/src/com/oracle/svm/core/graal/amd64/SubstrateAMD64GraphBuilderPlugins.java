/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.nodes.VectorizedMismatchNode;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
@Platforms(Platform.AMD64.class)
public class SubstrateAMD64GraphBuilderPlugins implements InternalFeature {

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (!SubstrateOptions.useLLVMBackend()) {
            registerArraysSupportPlugins(plugins.getInvocationPlugins(), providers.getReplacements());
        }
    }

    private static void registerArraysSupportPlugins(InvocationPlugins plugins, Replacements replacements) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, "jdk.internal.util.ArraysSupport", replacements);
        r.register(new InvocationPlugin("vectorizedMismatch", Object.class, long.class, Object.class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode aObject, ValueNode aOffset, ValueNode bObject, ValueNode bOffset, ValueNode length, ValueNode log2ArrayIndexScale) {
                ValueNode aAddr = b.add(new ComputeObjectAddressNode(aObject, aOffset));
                ValueNode bAddr = b.add(new ComputeObjectAddressNode(bObject, bOffset));
                b.addPush(JavaKind.Int, new VectorizedMismatchNode(aAddr, bAddr, length, log2ArrayIndexScale));
                return true;
            }
        });
    }
}
