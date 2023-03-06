/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot;

import java.lang.ref.Reference;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.nodes.HotSpotLoadReservedReferenceNode;
import org.graalvm.compiler.hotspot.nodes.HotSpotStoreReservedReferenceNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

final class HotSpotTruffleGraphBuilderPlugins {

    /**
     * Installed for runtime compilation using partial evaluation.
     */
    static void registerCompilationFinalReferencePlugins(InvocationPlugins plugins, boolean canDelayIntrinsification, HotSpotKnownTruffleTypes types) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, Reference.class);
        r.register(new RequiredInvocationPlugin("get", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (!canDelayIntrinsification && receiver.isConstant()) {
                    JavaConstant constant = (JavaConstant) receiver.get().asConstant();
                    if (constant.isNonNull()) {
                        if (types.classWeakReference.isInstance(constant) || types.classSoftReference.isInstance(constant)) {
                            JavaConstant referent = b.getConstantReflection().readFieldValue(types.referenceReferent, constant);
                            b.addPush(JavaKind.Object, ConstantNode.forConstant(referent, b.getMetaAccess()));
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    /**
     * These HotSpot thread local plugins are intended for the interpreter access stubs.
     */
    static void registerHotspotThreadLocalStubPlugins(InvocationPlugins plugins, WordTypes wordTypes, int jvmciReservedReference0Offset) {
        GraalError.guarantee(jvmciReservedReference0Offset != -1, "jvmciReservedReference0Offset is not available but used.");

        InvocationPlugins.Registration tl = new InvocationPlugins.Registration(plugins, "org.graalvm.compiler.truffle.runtime.hotspot.HotSpotFastThreadLocal");
        tl.register(new RequiredInvocationPlugin("getJVMCIReservedReference") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new HotSpotLoadReservedReferenceNode(b.getMetaAccess(), wordTypes, jvmciReservedReference0Offset));
                return true;
            }
        });
        tl.register(new RequiredInvocationPlugin("setJVMCIReservedReference", Object[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode value) {
                b.add(new HotSpotStoreReservedReferenceNode(wordTypes, value, jvmciReservedReference0Offset));
                return true;
            }
        });

    }

}
