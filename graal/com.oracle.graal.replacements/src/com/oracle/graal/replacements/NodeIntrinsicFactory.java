/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import java.lang.reflect.Array;
import java.util.List;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;

@NodeInfo
public final class NodeIntrinsicFactory extends FixedWithNextNode {

    public static final NodeClass<NodeIntrinsicFactory> TYPE = NodeClass.create(NodeIntrinsicFactory.class);

    public static final Object GRAPH_MARKER = new Object();

    private final ResolvedJavaMethod constructor;
    private final boolean setStampFromReturnType;

    private final Object[] injectedArguments;

    private final Class<?>[] argumentTypes;
    @Input NodeInputList<ValueNode> arguments;

    private final Class<?> varargsType;
    @Input NodeInputList<ValueNode> varargs;

    public NodeIntrinsicFactory(Stamp stamp, ResolvedJavaMethod constructor, boolean setStampFromReturnType, Object[] injectedArguments, Class<?>[] argumentTypes, List<ValueNode> arguments) {
        this(stamp, constructor, setStampFromReturnType, injectedArguments, argumentTypes, arguments, null, null);
    }

    public NodeIntrinsicFactory(Stamp stamp, ResolvedJavaMethod constructor, boolean setStampFromReturnType, Object[] injectedArguments, Class<?>[] argumentTypes, List<ValueNode> arguments,
                    Class<?> varargsType, List<ValueNode> varargs) {
        super(TYPE, stamp);

        this.constructor = constructor;
        this.setStampFromReturnType = setStampFromReturnType;

        this.injectedArguments = injectedArguments;

        this.argumentTypes = argumentTypes;
        this.arguments = new NodeInputList<>(this, arguments);

        this.varargsType = varargsType;
        if (varargs == null) {
            this.varargs = new NodeInputList<>(this);
        } else {
            this.varargs = new NodeInputList<>(this, varargs);
        }
    }

    private static Object mkConstArgument(Class<?> argType, Constant constant, ConstantReflectionProvider constantReflection, SnippetReflectionProvider snippetReflection) {
        if (argType == ResolvedJavaType.class) {
            ResolvedJavaType type = constantReflection.asJavaType(constant);
            assert type != null;
            return type;
        } else {
            JavaConstant javaConstant = (JavaConstant) constant;
            switch (JavaKind.fromJavaClass(argType)) {
                case Boolean:
                    return Boolean.valueOf(javaConstant.asInt() != 0);
                case Byte:
                    return Byte.valueOf((byte) javaConstant.asInt());
                case Short:
                    return Short.valueOf((short) javaConstant.asInt());
                case Char:
                    return Character.valueOf((char) javaConstant.asInt());
                case Object:
                    return snippetReflection.asObject(argType, javaConstant);
                default:
                    return javaConstant.asBoxedPrimitive();
            }
        }
    }

    public ValueNode intrinsify(StructuredGraph graph, ConstantReflectionProvider constantReflection, SnippetReflectionProvider snippetReflection) {
        int totalArgCount = arguments.count();
        if (injectedArguments != null) {
            totalArgCount += injectedArguments.length;
        }
        if (varargsType != null) {
            totalArgCount++;
        }

        Object[] args = new Object[totalArgCount];
        int idx = 0;

        if (injectedArguments != null) {
            for (int i = 0; i < injectedArguments.length; i++) {
                if (injectedArguments[i] == GRAPH_MARKER) {
                    args[idx++] = graph;
                } else {
                    args[idx++] = injectedArguments[i];
                }
            }
        }

        for (int i = 0; i < arguments.size(); i++) {
            ValueNode node = arguments.get(i);
            if (argumentTypes[i] == ValueNode.class) {
                args[idx++] = node;
            } else {
                if (!node.isConstant()) {
                    return null;
                }

                args[idx++] = mkConstArgument(argumentTypes[i], node.asConstant(), constantReflection, snippetReflection);
            }
        }

        if (varargsType != null) {
            if (varargsType == ValueNode.class) {
                args[idx++] = varargs.toArray(new ValueNode[0]);
            } else {
                Object array = Array.newInstance(varargsType, varargs.size());
                args[idx++] = array;

                for (int i = 0; i < varargs.size(); i++) {
                    ValueNode node = varargs.get(i);
                    if (!node.isConstant()) {
                        return null;
                    }

                    Object arg = mkConstArgument(varargsType, node.asConstant(), constantReflection, snippetReflection);
                    if (varargsType.isPrimitive()) {
                        Array.set(array, i, arg);
                    } else {
                        ((Object[]) array)[i] = arg;
                    }
                }
            }
        }

        assert idx == totalArgCount;

        ValueNode node = (ValueNode) snippetReflection.invoke(constructor, null, args);
        if (setStampFromReturnType) {
            node.setStamp(this.stamp());
        }
        return node;
    }
}
