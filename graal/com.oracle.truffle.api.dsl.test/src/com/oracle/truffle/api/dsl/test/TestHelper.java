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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ArgumentNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ChildrenNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

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
        if (ChildrenNode.class.isAssignableFrom(factory.getNodeClass())) {
            argumentList.add(argumentNodes);
        } else {
            argumentList.addAll(Arrays.asList(argumentNodes));
        }
        argumentList.addAll(Arrays.asList(constants));
        return factory.createNode(argumentList.toArray(new Object[argumentList.size()]));
    }

    static <E extends ValueNode> E createGenericNode(NodeFactory<E> factory, Object... constants) {
        Method createGenericMethod;
        try {
            createGenericMethod = factory.getClass().getMethod("createGeneric", factory.getNodeClass());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        try {
            return factory.getNodeClass().cast(createGenericMethod.invoke(null, createNode(factory, constants)));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    static <E extends ValueNode> TestRootNode<E> createRoot(NodeFactory<E> factory, Object... constants) {
        TestRootNode<E> rootNode = new TestRootNode<>(createNode(factory, constants));
        rootNode.adoptChildren();
        return rootNode;
    }

    static <E extends ValueNode> TestRootNode<E> createGenericRoot(NodeFactory<E> factory, Object... constants) {
        TestRootNode<E> rootNode = new TestRootNode<>(createGenericNode(factory, constants));
        rootNode.adoptChildren();
        return rootNode;
    }

    static CallTarget createCallTarget(ValueNode node) {
        return createCallTarget(new TestRootNode<>(node));
    }

    static CallTarget createCallTarget(TestRootNode<? extends ValueNode> node) {
        return Truffle.getRuntime().createCallTarget(node);
    }

    static <E> Object executeWith(TestRootNode<? extends ValueNode> node, Object... values) {
        return createCallTarget(node).call(values);
    }

    static Object array(Object... val) {
        return val;
    }

    static <E> List<List<E>> permutations(List<E> list) {
        return permutations(new ArrayList<E>(), list, new ArrayList<List<E>>());
    }

    static Object[][] permutations(Object... list) {
        List<List<Object>> permutations = permutations(Arrays.asList(list));

        Object[][] a = new Object[permutations.size()][];
        int index = 0;
        for (List<Object> p : permutations) {
            a[index] = p.toArray(new Object[p.size()]);
            index++;
        }

        return a;
    }

    static <E> List<List<E>> permutations(List<E> prefix, List<E> suffix, List<List<E>> output) {
        if (suffix.size() == 1) {
            ArrayList<E> newElement = new ArrayList<>(prefix);
            newElement.addAll(suffix);
            output.add(newElement);
            return output;
        }

        for (int i = 0; i < suffix.size(); i++) {
            List<E> newPrefix = new ArrayList<>(prefix);
            newPrefix.add(suffix.get(i));
            List<E> newSuffix = new ArrayList<>(suffix);
            newSuffix.remove(i);
            permutations(newPrefix, newSuffix, output);
        }

        return output;
    }

    /* Methods tests all test values in combinational order. */
    static void assertRuns(NodeFactory<? extends ValueNode> factory, Object result, Object... testValues) {
        // test each run by its own.
        for (int i = 0; i < testValues.length; i++) {
            assertValue(createRoot(factory), result, testValues);
        }

        // test all combinations of the test values
        List<List<Object>> permuts = permutations(Arrays.asList(testValues));
        for (List<Object> list : permuts) {
            TestRootNode<?> root = createRoot(factory);
            for (Object object : list) {
                assertValue(root, result, object);
            }
        }
    }

    static void assertValue(TestRootNode<? extends ValueNode> root, Object result, Object testValues) {
        if (testValues instanceof Object[]) {
            assertEquals(result, executeWith(root, (Object[]) testValues));
        } else {
            assertEquals(result, executeWith(root, testValues));
        }
    }

}
