/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nfi;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.word.phases.*;

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
            StructuredGraph g = new StructuredGraph(method);
            ParameterNode arg0 = g.unique(new ParameterNode(0, StampFactory.forKind(Kind.Object)));
            ParameterNode arg1 = g.unique(new ParameterNode(1, StampFactory.forKind(Kind.Object)));
            ParameterNode arg2 = g.unique(new ParameterNode(2, StampFactory.forKind(Kind.Object)));
            FrameState frameState = g.add(new FrameState(method, 0, Arrays.asList(new ValueNode[]{arg0, arg1, arg2}), 3, 0, false, false, new ArrayList<MonitorIdNode>(),
                            new ArrayList<EscapeObjectState>()));
            g.start().setStateAfter(frameState);
            List<ValueNode> parameters = new ArrayList<>();
            FixedWithNextNode fixedWithNext = getParameters(g, arg0, argumentTypes.length, argumentTypes, parameters, providers);
            Constant functionPointerNode = Constant.forLong(functionPointer);

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
            if (callNode.getKind() != Kind.Void) {
                if (callNode.getKind() == Kind.Object) {
                    throw new IllegalArgumentException("Return type not supported: " + returnType.getName());
                }
                ResolvedJavaType type = providers.getMetaAccess().lookupJavaType(callNode.getKind().toBoxedJavaClass());
                boxedResult = g.unique(new BoxNode(callNode, type, callNode.getKind()));
            } else {
                boxedResult = g.unique(new BoxNode(ConstantNode.forLong(0, g), providers.getMetaAccess().lookupJavaType(Long.class), Kind.Long));
            }

            ReturnNode returnNode = g.add(new ReturnNode(boxedResult));
            callNode.setNext(returnNode);
            (new WordTypeRewriterPhase(providers.getMetaAccess(), providers.getSnippetReflection(), Kind.Long)).apply(g);
            return g;
        } catch (NoSuchMethodException e) {
            throw GraalInternalError.shouldNotReachHere("Call Stub method not found");
        }
    }

    private static FixedWithNextNode getParameters(StructuredGraph g, ParameterNode argumentsArray, int numArgs, Class<?>[] argumentTypes, List<ValueNode> args, HotSpotProviders providers) {
        assert numArgs == argumentTypes.length;
        FixedWithNextNode last = null;
        for (int i = 0; i < numArgs; i++) {
            // load boxed array element:
            LoadIndexedNode boxedElement = g.add(new LoadIndexedNode(argumentsArray, ConstantNode.forInt(i, g), Kind.Object));
            if (i == 0) {
                g.start().setNext(boxedElement);
                last = boxedElement;
            } else {
                last.setNext(boxedElement);
                last = boxedElement;
            }
            Class<?> type = argumentTypes[i];
            Kind kind = getKind(type);
            if (kind == Kind.Object) {
                // array value
                Kind arrayElementKind = getElementKind(type);
                LocationIdentity locationIdentity = NamedLocationIdentity.getArrayLocation(arrayElementKind);
                int displacement = getArrayBaseOffset(arrayElementKind);
                ConstantNode index = ConstantNode.forInt(0, g);
                int indexScaling = getArrayIndexScale(arrayElementKind);
                IndexedLocationNode locationNode = IndexedLocationNode.create(locationIdentity, arrayElementKind, displacement, index, g, indexScaling);
                Stamp wordStamp = StampFactory.forKind(providers.getCodeCache().getTarget().wordKind);
                ComputeAddressNode arrayAddress = g.unique(new ComputeAddressNode(boxedElement, locationNode, wordStamp));
                args.add(arrayAddress);
            } else {
                // boxed primitive value
                try {
                    ResolvedJavaField field = providers.getMetaAccess().lookupJavaField(kind.toBoxedJavaClass().getDeclaredField("value"));
                    LoadFieldNode loadFieldNode = g.add(new LoadFieldNode(boxedElement, field));
                    last.setNext(loadFieldNode);
                    last = loadFieldNode;
                    args.add(loadFieldNode);
                } catch (NoSuchFieldException e) {
                    throw new GraalInternalError(e);
                }
            }
        }
        return last;
    }

    public static Kind getElementKind(Class<?> clazz) {
        Class<?> componentType = clazz.getComponentType();
        if (componentType == null) {
            throw new IllegalArgumentException("Parameter type not supported: " + clazz);
        }
        if (componentType.isPrimitive()) {
            return Kind.fromJavaClass(componentType);
        }
        throw new IllegalArgumentException("Parameter type not supported: " + clazz);
    }

    private static Kind getKind(Class<?> clazz) {
        if (clazz == int.class || clazz == Integer.class) {
            return Kind.Int;
        } else if (clazz == long.class || clazz == Long.class) {
            return Kind.Long;
        } else if (clazz == char.class || clazz == Character.class) {
            return Kind.Char;
        } else if (clazz == byte.class || clazz == Byte.class) {
            return Kind.Byte;
        } else if (clazz == float.class || clazz == Float.class) {
            return Kind.Float;
        } else if (clazz == double.class || clazz == Double.class) {
            return Kind.Double;
        } else if (clazz == int[].class || clazz == long[].class || clazz == char[].class || clazz == byte[].class || clazz == float[].class || clazz == double[].class) {
            return Kind.Object;
        } else if (clazz == void.class) {
            return Kind.Void;
        } else {
            throw new IllegalArgumentException("Type not supported: " + clazz);
        }
    }

    @SuppressWarnings("unused")
    public static Object libCall(Object argLoc, Object unused1, Object unused2) {
        throw GraalInternalError.shouldNotReachHere("GNFI libCall method must not be called");
    }
}
