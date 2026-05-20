/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.replacements.nodes.MethodHandleNode;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tests the type-stamp checks used when method-handle intrinsification rewrites a signature-polymorphic call
 * to its resolved target.
 */
public class MethodHandleNodeTest {

    private final Map<Class<?>, ResolvedJavaType> types = new LinkedHashMap<>();
    private final Map<ResolvedJavaType, Class<?>> classes = new LinkedHashMap<>();

    /**
     * Exercises the case that differs from the previous predicate: an argument stamped as {@code String}
     * and a target parameter type of {@code Integer}. These are unrelated concrete types, so the argument
     * is not already known to satisfy the target type and a guard must be emitted.
     */
    @Test
    public void maybeCastArgumentGuardsUnrelatedReferenceStamp() {
        TestGraphAdder adder = new TestGraphAdder();
        ValueNode argument = newParameter(String.class);
        ValueNode[] arguments = {argument};

        MethodHandleNode.maybeCastArgument(adder, arguments, 0, type(Integer.class));

        Assert.assertEquals(1, adder.count(FixedGuardNode.class));
        Assert.assertTrue(arguments[0] instanceof PiNode);
    }

    /**
     * Checks a concrete subtype case that remains guard-free. An argument stamped as {@code Integer}
     * already satisfies a {@code Number} target parameter, so adding a guard would only duplicate type
     * information already present in the stamp.
     */
    @Test
    public void maybeCastArgumentSkipsKnownSubtypeStamp() {
        TestGraphAdder adder = new TestGraphAdder();
        ValueNode argument = newParameter(Integer.class);
        ValueNode[] arguments = {argument};

        MethodHandleNode.maybeCastArgument(adder, arguments, 0, type(Number.class));

        Assert.assertEquals(0, adder.count(FixedGuardNode.class));
        Assert.assertSame(argument, arguments[0]);
    }

    private ValueNode newParameter(Class<?> type) {
        ResolvedJavaType resolvedType = type(type);
        TypeReference typeReference = TypeReference.createWithoutAssumptions(resolvedType);
        return new ParameterNode(0, StampPair.createSingle(StampFactory.object(typeReference)));
    }

    private static final class TestGraphAdder extends MethodHandleNode.GraphAdder {
        private final List<ValueNode> addedNodes = new ArrayList<>();

        TestGraphAdder() {
            super(null);
        }

        @Override
        public <T extends ValueNode> T add(T node) {
            addedNodes.add(node);
            return node;
        }

        @Override
        public Assumptions getAssumptions() {
            return null;
        }

        int count(Class<?> nodeClass) {
            int result = 0;
            for (ValueNode node : addedNodes) {
                if (nodeClass.isInstance(node)) {
                    result++;
                }
            }
            return result;
        }
    }

    private ResolvedJavaType type(Class<?> clazz) {
        return types.computeIfAbsent(clazz, this::newType);
    }

    private ResolvedJavaType newType(Class<?> clazz) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString", "toJavaName" -> clazz.getTypeName();
            case "getName" -> "L" + clazz.getName().replace('.', '/') + ";";
            case "getJavaKind" -> JavaKind.Object;
            case "getModifiers" -> clazz.getModifiers();
            case "isAbstract" -> Modifier.isAbstract(clazz.getModifiers());
            case "isArray" -> clazz.isArray();
            case "isAssignableFrom" -> clazz.isAssignableFrom(toClass(args[0]));
            case "isInterface" -> clazz.isInterface();
            case "isJavaLangObject" -> clazz == Object.class;
            case "isLeaf" -> Modifier.isFinal(clazz.getModifiers());
            case "isPrimitive" -> clazz.isPrimitive();
            case "getArrayClass" -> type(java.lang.reflect.Array.newInstance(clazz, 0).getClass());
            case "getComponentType" -> type(clazz.getComponentType());
            case "getElementalType" -> type(elementalType(clazz));
            case "getSuperclass" -> type(clazz.getSuperclass());
            case "getInterfaces" -> interfaces(clazz);
            case "findLeafConcreteSubtype" -> null;
            case "findLeastCommonAncestor" -> leastCommonAncestor(clazz, toClass(args[0]));
            default -> throw new UnsupportedOperationException(method.toString());
        };
        ResolvedJavaType result = (ResolvedJavaType) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{ResolvedJavaType.class}, handler);
        classes.put(result, clazz);
        return result;
    }

    private Class<?> toClass(Object type) {
        return classes.get(type);
    }

    private ResolvedJavaType[] interfaces(Class<?> clazz) {
        Class<?>[] interfaces = clazz.getInterfaces();
        ResolvedJavaType[] result = new ResolvedJavaType[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            result[i] = type(interfaces[i]);
        }
        return result;
    }

    private ResolvedJavaType leastCommonAncestor(Class<?> a, Class<?> b) {
        Class<?> result = a;
        while (result != null && !result.isAssignableFrom(b)) {
            result = result.getSuperclass();
        }
        return type(result == null ? Object.class : result);
    }

    private static Class<?> elementalType(Class<?> clazz) {
        Class<?> result = clazz;
        while (result.isArray()) {
            result = result.getComponentType();
        }
        return result;
    }
}
