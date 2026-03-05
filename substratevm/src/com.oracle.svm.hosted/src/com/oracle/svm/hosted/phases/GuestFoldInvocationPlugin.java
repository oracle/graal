/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.OriginalMethodProvider;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class GuestFoldInvocationPlugin implements NodePlugin {

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode[] args) {
        if (!AnnotationUtil.isAnnotationPresent(targetMethod, GuestFold.class)) {
            return false;
        }
        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        if (returnKind == JavaKind.Void) {
            throw VMError.shouldNotReachHere("@GuestFold is not supported for void methods: %s", targetMethod.format("%H.%n(%p)"));
        }

        JavaConstant receiver = null;
        int argOffset = 0;
        if (targetMethod.hasReceiver()) {
            if (args.length == 0) {
                throw VMError.shouldNotReachHere("@GuestFold invocation has no receiver: %s", targetMethod.format("%H.%n(%p)"));
            }
            receiver = asRequiredConstant(b, targetMethod, args[0], "receiver", -1);
            argOffset = 1;
        }

        JavaConstant[] constantArgs = new JavaConstant[args.length - argOffset];
        for (int i = 0; i < constantArgs.length; i++) {
            constantArgs[i] = asRequiredConstant(b, targetMethod, args[i + argOffset], "argument", i);
        }

        JavaConstant result;
        try {
            ResolvedJavaMethod methodToInvoke = OriginalMethodProvider.getOriginalMethod(targetMethod);
            result = GuestAccess.get().invoke(methodToInvoke, receiver, constantArgs);
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(String.format("Guest invocation failed while folding @GuestFold method %s", targetMethod.format("%H.%n(%p)")), t);
        }

        if (b.getMetaAccess() instanceof UniverseMetaAccess uMetaAccess) {
            result = uMetaAccess.getUniverse().lookup(result);
        }

        ConstantNode node = ConstantNode.forConstant(result, b.getMetaAccess(), b.getGraph());
        b.push(returnKind, node);
        return true;
    }

    private static JavaConstant asRequiredConstant(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode node, String kind, int index) {
        if (!node.isJavaConstant()) {
            String site = b.getMethod() != null ? b.getMethod().format("%H.%n(%p)") : "<unknown>";
            String msg;
            if (index < 0) {
                msg = String.format("Non-constant %s passed to @GuestFold call. Callsite=%s Target=%s%n%s", kind, site, targetMethod.format("%H.%n(%p)"), b);
            } else {
                msg = String.format("Non-constant %s %d passed to @GuestFold call. Callsite=%s Target=%s%n%s", kind, index, site, targetMethod.format("%H.%n(%p)"), b);
            }
            throw VMError.shouldNotReachHere(msg);
        }
        JavaConstant c = node.asJavaConstant();
        if (c instanceof ImageHeapConstant ihc) {
            c = ihc.getHostedObject();
        }
        return c;
    }
}
