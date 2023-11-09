/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import java.lang.ref.Reference;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateTruffleGraphBuilderPlugins {
    static void registerInvocationPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification, SubstrateKnownTruffleTypes types) {
        registerCompilationFinalReferencePlugins(plugins, canDelayIntrinsification, types);
    }

    private static void registerCompilationFinalReferencePlugins(InvocationPlugins plugins, boolean canDelayIntrinsification, SubstrateKnownTruffleTypes types) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, Reference.class);
        r.register(new RequiredInvocationPlugin("get", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (!canDelayIntrinsification && receiver.isConstant()) {
                    JavaConstant reference = (JavaConstant) receiver.get().asConstant();
                    if (reference.isNonNull()) {
                        JavaConstant referent = b.getConstantReflection().readFieldValue(types.Reference_referent, reference);
                        b.addPush(JavaKind.Object, ConstantNode.forConstant(referent, b.getMetaAccess()));
                        return true;
                    }
                }
                return false;
            }
        });
    }
}
