/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(allowedUsageTypes = {InputType.Extension})
public abstract class CallTargetNode extends ValueNode implements LIRLowerable {
    public enum InvokeKind {
        Interface,
        Special,
        Static,
        Virtual;

        public boolean hasReceiver() {
            return this != Static;
        }
    }

    @Input protected NodeInputList<ValueNode> arguments;
    private ResolvedJavaMethod targetMethod;
    private InvokeKind invokeKind;

    public CallTargetNode(ValueNode[] arguments, ResolvedJavaMethod targetMethod, InvokeKind invokeKind) {
        super(StampFactory.forVoid());
        this.targetMethod = targetMethod;
        this.invokeKind = invokeKind;
        this.arguments = new NodeInputList<>(this, arguments);
    }

    public CallTargetNode(List<ValueNode> arguments, ResolvedJavaMethod targetMethod, InvokeKind invokeKind) {
        super(StampFactory.forVoid());
        this.targetMethod = targetMethod;
        this.invokeKind = invokeKind;
        this.arguments = new NodeInputList<>(this, arguments);
    }

    public NodeInputList<ValueNode> arguments() {
        return arguments;
    }

    public abstract Stamp returnStamp();

    /**
     * A human-readable representation of the target, used for debug printing only.
     */
    public abstract String targetName();

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // nop
    }

    public void setTargetMethod(ResolvedJavaMethod method) {
        targetMethod = method;
    }

    /**
     * Gets the target method for this invocation instruction.
     *
     * @return the target method
     */
    public ResolvedJavaMethod targetMethod() {
        return targetMethod;
    }

    public InvokeKind invokeKind() {
        return invokeKind;
    }

    public void setInvokeKind(InvokeKind kind) {
        this.invokeKind = kind;
    }
}
