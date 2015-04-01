/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import static sun.misc.Version.*;

import java.lang.invoke.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A call target that replaces itself in the graph when being lowered by restoring the original
 * {@link MethodHandle} invocation target. Prior to
 * https://bugs.openjdk.java.net/browse/JDK-8072008, this is required for when a
 * {@link MethodHandle} call is resolved to a constant target but the target was not inlined. In
 * that case, the original invocation must be restored with all of its original arguments. Why?
 * HotSpot linkage for {@link MethodHandle} intrinsics (see
 * {@code MethodHandles::generate_method_handle_dispatch}) expects certain implicit arguments to be
 * on the stack such as the MemberName suffix argument for a call to one of the MethodHandle.linkTo*
 * methods. An {@linkplain MethodHandleNode#tryResolveTargetInvoke resolved} {@link MethodHandle}
 * invocation drops these arguments which means the interpreter won't find them.
 */
@NodeInfo
public final class ResolvedMethodHandleCallTargetNode extends MethodCallTargetNode implements Lowerable {

    public static final NodeClass<ResolvedMethodHandleCallTargetNode> TYPE = NodeClass.create(ResolvedMethodHandleCallTargetNode.class);

    /**
     * Creates a call target for an invocation on a direct target derived by resolving a constant
     * {@link MethodHandle}.
     */
    public static MethodCallTargetNode create(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, JavaType returnType, ResolvedJavaMethod originalTargetMethod,
                    ValueNode[] originalArguments, JavaType originalReturnType) {
        if (jdkMajorVersion() >= 1 && jdkMinorVersion() >= 8 && jdkMicroVersion() >= 0 && jdkUpdateVersion() >= 60) {
            // https://bugs.openjdk.java.net/browse/JDK-8072008 is targeted for 8u60
            return new MethodCallTargetNode(invokeKind, targetMethod, arguments, returnType);
        }
        return new ResolvedMethodHandleCallTargetNode(invokeKind, targetMethod, arguments, returnType, originalTargetMethod, originalArguments, originalReturnType);
    }

    protected final ResolvedJavaMethod originalTargetMethod;
    protected final JavaType originalReturnType;
    @Input NodeInputList<ValueNode> originalArguments;

    protected ResolvedMethodHandleCallTargetNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, JavaType returnType, ResolvedJavaMethod originalTargetMethod,
                    ValueNode[] originalArguments, JavaType originalReturnType) {
        super(TYPE, invokeKind, targetMethod, arguments, returnType);
        this.originalTargetMethod = originalTargetMethod;
        this.originalReturnType = originalReturnType;
        this.originalArguments = new NodeInputList<>(this, originalArguments);
    }

    @Override
    public void lower(LoweringTool tool) {
        InvokeKind replacementInvokeKind = originalTargetMethod.isStatic() ? InvokeKind.Static : InvokeKind.Special;
        MethodCallTargetNode replacement = graph().add(
                        new MethodCallTargetNode(replacementInvokeKind, originalTargetMethod, originalArguments.toArray(new ValueNode[originalArguments.size()]), originalReturnType));

        // Replace myself...
        this.replaceAndDelete(replacement);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        throw GraalInternalError.shouldNotReachHere("should have replaced itself");
    }
}
