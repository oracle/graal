/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.replacements;

import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.core.common.type.StampPair;
import jdk.compiler.graal.nodes.CallTargetNode;
import jdk.compiler.graal.nodes.Invoke;
import jdk.compiler.graal.nodes.InvokeWithExceptionNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.compiler.graal.replacements.nodes.MacroInvokable;
import jdk.compiler.graal.replacements.nodes.MacroNode;
import jdk.compiler.graal.replacements.nodes.MethodHandleWithExceptionNode;

import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodHandleWithExceptionPlugin extends MethodHandlePlugin {
    public MethodHandleWithExceptionPlugin(MethodHandleAccessProvider methodHandleAccess, boolean safeForDeoptimization) {
        super(methodHandleAccess, safeForDeoptimization);
    }

    @Override
    protected Invoke createInvoke(CallTargetNode callTarget, int bci, Stamp stamp) {
        InvokeWithExceptionNode invoke = new InvokeWithExceptionNode(callTarget, null, bci);
        invoke.setStamp(stamp);
        return invoke;
    }

    @Override
    protected MacroInvokable createMethodHandleNode(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args,
                    MethodHandleAccessProvider.IntrinsicMethod intrinsicMethod, CallTargetNode.InvokeKind invokeKind, StampPair invokeReturnStamp) {
        return new MethodHandleWithExceptionNode(intrinsicMethod, MacroNode.MacroParams.of(invokeKind, b.getMethod(), method, b.bci(), invokeReturnStamp, args));
    }
}
