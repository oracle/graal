/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.hotspot.nfi;

import static jdk.internal.jvmci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.ResolvedJavaField;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;
import jdk.internal.jvmci.meta.ResolvedJavaType;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.BoxNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.memory.address.OffsetAddressNode;
import com.oracle.graal.nodes.virtual.EscapeObjectState;
import com.oracle.graal.word.nodes.WordCastNode;

/**
 * Utility creating a graph for a stub used to call a native function.
 */
public class NativeCallStubGraphBuilder {

    /**
     * Creates a graph for a stub used to call a native function.
     *
     * @param functionPointer a native function pointer
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the graph that represents the call stub
     */
    public static StructuredGraph getGraph(HotSpotProviders providers, RawNativeCallNodeFactory factory, long functionPointer, Class<?> returnType, Class<?>... argumentTypes) {
        try {
            ResolvedJavaMethod method = providers.getMetaAccess().lookupJavaMethod(NativeCallStubGraphBuilder.class.getMethod("libCall", Object.class, Object.class, Object.class));
            StructuredGraph g = new StructuredGraph(method, AllowAssumptions.NO);
            ParameterNode arg0 = g.unique(new ParameterNode(0, StampFactory.forKind(JavaKind.Object)));
            ParameterNode arg1 = g.unique(new ParameterNode(1, StampFactory.forKind(JavaKind.Object)));
            ParameterNode arg2 = g.unique(new ParameterNode(2, StampFactory.forKind(JavaKind.Object)));
            FrameState frameState = g.add(new FrameState(null, method, 0, Arrays.asList(new ValueNode[]{arg0, arg1, arg2}), 3, 0, false, false, null, new ArrayList<EscapeObjectState>()));
            g.start().setStateAfter(frameState);
            List<ValueNode> parameters = new ArrayList<>();
            FixedWithNextNode fixedWithNext = getParameters(g, arg0, argumentTypes.length, argumentTypes, parameters, providers);
            JavaConstant functionPointerNode = JavaConstant.forLong(functionPointer);

            ValueNode[] arguments = new ValueNode[parameters.size()];

            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = parameters.get(i);
            }

            FixedWithNextNode callNode = g.add(factory.createRawCallNode(getKind(returnType), functionPointerNode, arguments));

            if (fixedWithNext == null) {
                g.start().setNext(callNode);
            } else {
                fixedWithNext.setNext(callNode);
            }

            // box result
            BoxNode boxedResult;
            if (callNode.getStackKind() != JavaKind.Void) {
                if (callNode.getStackKind() == JavaKind.Object) {
                    throw new IllegalArgumentException("Return type not supported: " + returnType.getName());
                }
                ResolvedJavaType type = providers.getMetaAccess().lookupJavaType(callNode.getStackKind().toBoxedJavaClass());
                boxedResult = g.add(new BoxNode(callNode, type, callNode.getStackKind()));
            } else {
                boxedResult = g.add(new BoxNode(ConstantNode.forLong(0, g), providers.getMetaAccess().lookupJavaType(Long.class), JavaKind.Long));
            }

            callNode.setNext(boxedResult);
            ReturnNode returnNode = g.add(new ReturnNode(boxedResult));
            boxedResult.setNext(returnNode);
            return g;
        } catch (NoSuchMethodException e) {
            throw JVMCIError.shouldNotReachHere("Call Stub method not found");
        }
    }

    private static FixedWithNextNode getParameters(StructuredGraph g, ParameterNode argumentsArray, int numArgs, Class<?>[] argumentTypes, List<ValueNode> args, HotSpotProviders providers) {
        assert numArgs == argumentTypes.length;
        FixedWithNextNode last = null;
        for (int i = 0; i < numArgs; i++) {
            // load boxed array element:
            LoadIndexedNode boxedElement = g.add(new LoadIndexedNode(argumentsArray, ConstantNode.forInt(i, g), JavaKind.Object));
            if (i == 0) {
                g.start().setNext(boxedElement);
                last = boxedElement;
            } else {
                last.setNext(boxedElement);
                last = boxedElement;
            }
            Class<?> type = argumentTypes[i];
            JavaKind kind = getKind(type);
            if (kind == JavaKind.Object) {
                // array value
                JavaKind arrayElementKind = getElementKind(type);
                int displacement = getArrayBaseOffset(arrayElementKind);
                AddressNode arrayAddress = g.unique(new OffsetAddressNode(boxedElement, ConstantNode.forLong(displacement, g)));
                WordCastNode cast = g.add(WordCastNode.addressToWord(arrayAddress, providers.getWordTypes().getWordKind()));
                last.setNext(cast);
                last = cast;
                args.add(cast);
            } else {
                // boxed primitive value
                try {
                    ResolvedJavaField field = providers.getMetaAccess().lookupJavaField(kind.toBoxedJavaClass().getDeclaredField("value"));
                    LoadFieldNode loadFieldNode = g.add(new LoadFieldNode(boxedElement, field));
                    last.setNext(loadFieldNode);
                    last = loadFieldNode;
                    args.add(loadFieldNode);
                } catch (NoSuchFieldException e) {
                    throw new JVMCIError(e);
                }
            }
        }
        return last;
    }

    public static JavaKind getElementKind(Class<?> clazz) {
        Class<?> componentType = clazz.getComponentType();
        if (componentType == null) {
            throw new IllegalArgumentException("Parameter type not supported: " + clazz);
        }
        if (componentType.isPrimitive()) {
            return JavaKind.fromJavaClass(componentType);
        }
        throw new IllegalArgumentException("Parameter type not supported: " + clazz);
    }

    private static JavaKind getKind(Class<?> clazz) {
        if (clazz == int.class || clazz == Integer.class) {
            return JavaKind.Int;
        } else if (clazz == long.class || clazz == Long.class) {
            return JavaKind.Long;
        } else if (clazz == char.class || clazz == Character.class) {
            return JavaKind.Char;
        } else if (clazz == byte.class || clazz == Byte.class) {
            return JavaKind.Byte;
        } else if (clazz == float.class || clazz == Float.class) {
            return JavaKind.Float;
        } else if (clazz == double.class || clazz == Double.class) {
            return JavaKind.Double;
        } else if (clazz == int[].class || clazz == long[].class || clazz == char[].class || clazz == byte[].class || clazz == float[].class || clazz == double[].class) {
            return JavaKind.Object;
        } else if (clazz == void.class) {
            return JavaKind.Void;
        } else {
            throw new IllegalArgumentException("Type not supported: " + clazz);
        }
    }

    @SuppressWarnings("unused")
    public static Object libCall(Object argLoc, Object unused1, Object unused2) {
        throw JVMCIError.shouldNotReachHere("GNFI libCall method must not be called");
    }
}
