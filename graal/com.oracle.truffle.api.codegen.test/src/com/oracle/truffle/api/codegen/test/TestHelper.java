/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.codegen.test;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.api.codegen.test.NodeContainerTest.*;
import com.oracle.truffle.api.codegen.test.TypeSystemTest.*;

/**
 * Utility class to provide some test helper functions.
 */
class TestHelper {

    private static ArgumentNode[] arguments(int count) {
        ArgumentNode[] nodes = new ArgumentNode[count];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new ArgumentNode(i);
        }
        return nodes;
    }

    static <E extends ValueNode> E createNode(NodeFactory<E> factory, Object... constants) {
        ArgumentNode[] argumentNodes = arguments(factory.getExecutionSignature().size());

        List<Object> argumentList = new ArrayList<>();
        argumentList.addAll(Arrays.asList(constants));
        if (ChildrenNode.class.isAssignableFrom(factory.getNodeClass()) || BuiltinNode.class.isAssignableFrom(factory.getNodeClass())) {
            argumentList.add(argumentNodes);
        } else {
            argumentList.addAll(Arrays.asList(argumentNodes));
        }
        return factory.createNode(argumentList.toArray(new Object[argumentList.size()]));
    }

    static <E extends ValueNode> TestRootNode<E> createRoot(NodeFactory<E> factory, Object... constants) {
        return new TestRootNode<>(createNode(factory, constants));
    }

    static CallTarget createCallTarget(ValueNode node) {
        return createCallTarget(new TestRootNode<>(node));
    }

    static CallTarget createCallTarget(TestRootNode<? extends ValueNode> node) {
        return Truffle.getRuntime().createCallTarget(node);
    }

    static <E> Object executeWith(TestRootNode<? extends ValueNode> node, Object... values) {
        return createCallTarget(node).call(new TestArguments(values));
    }

}
