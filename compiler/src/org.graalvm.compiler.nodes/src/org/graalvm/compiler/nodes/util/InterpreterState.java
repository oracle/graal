/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.util;

import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.interpreter.value.InterpreterValue;
import org.graalvm.compiler.interpreter.value.JVMContext;
import org.graalvm.compiler.interpreter.value.InterpreterValueFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ValueNode;

import java.util.List;

/**
 * Represents the current state of a Graal IR interpreter.
 *
 * This includes a representation of a heap and a stack of Activation frames,
 * and a factory for creating new runtime values within the interpreter.
 *
 * TODO: the heap needs more thought, as the new-instance node may not be the best key?
 */
public interface InterpreterState {

    // Used for nodes such NewArrayNode and NewInstanceNode
    void setHeapValue(Node node, InterpreterValue value);

    InterpreterValue getHeapValue(Node node);

    /**
     * Associate a control flow node with its current value.
     *
     * For example, after a method is invoked, the InvokeNode of the method call is
     * mapped to the value returned from the method, so that expressions can use the return value.
     *
     * (This is similar to mapping local variables of the current method to their current values,
     * but the keys are Node pointers, rather than variable names.)
     */
    void setNodeLookupValue(Node node, InterpreterValue value);

    /** True iff the given node has a lookup value in the current method. */
    boolean hasNodeLookupValue(Node node);

    /**
     * Get the value currently associated with a node.
     *
     * @param node must satisfy hasNodeLookupValue(node).
     */
    InterpreterValue getNodeLookupValue(Node node);

    /**
     * Record which of the incoming control-flow paths were taken to reach this merge node.
     *
     * (This is used later by the merge node to evaluate all its associated Phi nodes).
     *
     * @param node some kind of merge node.
     * @param index the index (from 0) of the incoming path.
     */
    void setMergeNodeIncomingIndex(AbstractMergeNode node, int index);

    /** Get the index of which incoming control-flow path was most recently taken to reach the merge node. */
    int getMergeNodeIncomingIndex(AbstractMergeNode node);

    /** Lookup the current value of a static field. */
    InterpreterValue loadStaticFieldValue(ResolvedJavaField field);

    /** Set the current value of a static field. */
    void storeStaticFieldValue(ResolvedJavaField field, InterpreterValue value);

    /** Get the input value for one of the parameters to the current method. */
    InterpreterValue getParameter(int index);

    /** Evaluate a pure expression and return the resulting value. */
    InterpreterValue interpretExpr(Node node);

    /** Interpret the graph of the given method with argument values. */
    InterpreterValue interpretMethod(CallTargetNode target, List<ValueNode> argumentNodes);

    /** Get the factory for creating interpreter values. */
    InterpreterValueFactory getRuntimeValueFactory();

    // 获取 JVMContext，在 LoadFieldNode , StoreFieldNode, LoadIndexedNode, StoreIndexedNode 里会用到
    JVMContext getJVMContext();
}
