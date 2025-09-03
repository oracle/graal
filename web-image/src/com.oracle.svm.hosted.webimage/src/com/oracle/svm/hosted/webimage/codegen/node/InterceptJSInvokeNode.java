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

package com.oracle.svm.hosted.webimage.codegen.node;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This node is inserted before any callsite that targets a JS-annotated method (special, static, or
 * virtual invoke, or a virtual invoke that could target a JS-annotated method), and its purpose is
 * to (during the reachability analysis) react to certain types that may leak to JavaScript code.
 *
 * Concretely, this node is used to ensure the following:
 *
 * <ul>
 * <li>Passing a functional interface value to JavaScript makes its single-abstract method
 * reachable.</li>
 * <li>Passing any value for which a Java Proxy is created results in recording the signatures of
 * the methods of the corresponding class.</li>
 * </ul>
 *
 * The node itself translates to a no-op during code generation.
 */
@NodeInfo(size = NodeSize.SIZE_0, cycles = NodeCycles.CYCLES_0)
public class InterceptJSInvokeNode extends FixedWithNextNode {
    public static final NodeClass<InterceptJSInvokeNode> TYPE = NodeClass.create(InterceptJSInvokeNode.class);

    private final int bci;
    private final ResolvedJavaMethod targetMethod;
    @Input private NodeInputList<ValueNode> arguments;

    @SuppressWarnings("this-escape")
    public InterceptJSInvokeNode(ResolvedJavaMethod targetMethod, int bci) {
        super(TYPE, StampFactory.forVoid());

        this.targetMethod = targetMethod;
        this.bci = bci;
        this.arguments = new NodeInputList<>(this);
    }

    public ResolvedJavaMethod targetMethod() {
        return targetMethod;
    }

    public NodeInputList<ValueNode> arguments() {
        return arguments;
    }

    public int bci() {
        return bci;
    }
}
