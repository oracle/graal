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
package com.oracle.graal.nfi.hotspot.amd64.util;

import java.util.*;

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nfi.hotspot.amd64.*;
import com.oracle.graal.nfi.hotspot.amd64.node.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.word.phases.*;

/**
 * Utility that creates a Graal graph. The graph represents the native call stub for a foreign
 * target function.
 * 
 */
public class NativeCallStubGraphBuilder {

    private static final ResolvedJavaType integerJavaType = HotSpotResolvedObjectType.fromClass(Integer.class);
    private static final ResolvedJavaField[] integerInstanceFields = integerJavaType.getInstanceFields(false);
    private static final ResolvedJavaField integerValueField = getValueField(integerInstanceFields);

    private static final ResolvedJavaType longJavaType = HotSpotResolvedObjectType.fromClass(Long.class);
    private static final ResolvedJavaField[] longInstanceFields = longJavaType.getInstanceFields(false);
    private static final ResolvedJavaField longValueField = getValueField(longInstanceFields);

    private static final ResolvedJavaType charJavaType = HotSpotResolvedObjectType.fromClass(Character.class);
    private static final ResolvedJavaField[] charInstanceFields = charJavaType.getInstanceFields(false);
    private static final ResolvedJavaField charValueField = getValueField(charInstanceFields);

    private static final ResolvedJavaType byteJavaType = HotSpotResolvedObjectType.fromClass(Byte.class);
    private static final ResolvedJavaField[] byteInstanceFields = byteJavaType.getInstanceFields(false);
    private static final ResolvedJavaField byteValueField = getValueField(byteInstanceFields);

    private static final ResolvedJavaType floatJavaType = HotSpotResolvedObjectType.fromClass(Float.class);
    private static final ResolvedJavaField[] floatInstanceFields = floatJavaType.getInstanceFields(false);
    private static final ResolvedJavaField floatValueField = getValueField(floatInstanceFields);

    private static final ResolvedJavaType doubleJavaType = HotSpotResolvedObjectType.fromClass(Double.class);
    private static final ResolvedJavaField[] doubleInstanceFields = doubleJavaType.getInstanceFields(false);
    private static final ResolvedJavaField doubleValueField = getValueField(doubleInstanceFields);

    private static ResolvedJavaField getValueField(ResolvedJavaField[] fields) {
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals("value")) {
                return field;
            }
        }
        throw new AssertionError("value field not found!");
    }

    /**
     * Creates a Graal graph that represents the call stub for a foreign target function.
     * 
     * @param providers the HotSpot providers
     * @param functionPointer a function pointer that points to the foreign target function
     * @param returnType the type of the return value
     * @param argumentTypes the types of the arguments
     * @return the graph that represents the call stub
     */
    public static StructuredGraph getGraph(HotSpotProviders providers, AMD64HotSpotNativeFunctionPointer functionPointer, Class returnType, Class[] argumentTypes) {
        ResolvedJavaMethod method;
        try {
            method = providers.getMetaAccess().lookupJavaMethod(NativeCallStubGraphBuilder.class.getMethod("libCall", Object.class, Object.class, Object.class));
            StructuredGraph g = new StructuredGraph(method);
            ParameterNode arg0 = g.unique(new ParameterNode(0, StampFactory.forKind(Kind.Object)));
            ParameterNode arg1 = g.unique(new ParameterNode(1, StampFactory.forKind(Kind.Object)));
            ParameterNode arg2 = g.unique(new ParameterNode(2, StampFactory.forKind(Kind.Object)));
            FrameState frameState = g.add(new FrameState(method, 0, Arrays.asList(new ValueNode[]{arg0, arg1, arg2}), 3, 0, false, false, new ArrayList<MonitorIdNode>(),
                            new ArrayList<EscapeObjectState>()));
            g.start().setStateAfter(frameState);
            List<ValueNode> parameters = new ArrayList<>();
            FixedWithNextNode fixedWithNext = getParameters(g, arg0, argumentTypes.length, argumentTypes, parameters, providers);
            Constant functionPointerNode = Constant.forLong(functionPointer.asRawValue());

            ValueNode[] arguments = new ValueNode[parameters.size()];

            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = parameters.get(i);
            }

            AMD64RawNativeCallNode callNode = g.add(new AMD64RawNativeCallNode(getKind(returnType), functionPointerNode, arguments));

            if (fixedWithNext == null) {
                g.start().setNext(callNode);
            } else {
                fixedWithNext.setNext(callNode);
            }

            // box result
            BoxNode boxedResult;
            if (callNode.kind() != Kind.Void) {
                ResolvedJavaType type = getResolvedJavaType(callNode.kind());
                boxedResult = new BoxNode(callNode, type, callNode.kind());
            } else {
                boxedResult = new BoxNode(ConstantNode.forLong(0, g), longJavaType, Kind.Long);
            }

            // box result:
            BoxNode boxNode = g.unique(boxedResult);

            ReturnNode returnNode = g.add(new ReturnNode(boxNode));
            callNode.setNext(returnNode);
            (new WordTypeRewriterPhase(providers.getMetaAccess(), Kind.Long)).apply(g);
            return g;
        } catch (NoSuchMethodException | SecurityException e) {
            throw GraalInternalError.shouldNotReachHere("Call Stub method not found");
        }
    }

    private static FixedWithNextNode getParameters(StructuredGraph g, ParameterNode argumentsArray, int numArgs, Class[] argumentClass, List<ValueNode> args, HotSpotProviders providers) {
        assert numArgs == argumentClass.length;
        FixedWithNextNode fixedWithNext = null;
        for (int i = 0; i < numArgs; i++) {
            // load boxed array element:
            LoadIndexedNode boxedElement = g.add(new LoadIndexedNode(argumentsArray, ConstantNode.forInt(i, g), Kind.Object));
            if (i == 0) {
                g.start().setNext(boxedElement);
                fixedWithNext = boxedElement;
            } else {
                fixedWithNext.setNext(boxedElement);
                fixedWithNext = boxedElement;
            }
            if (getKind(argumentClass[i]) == Kind.Object) {
                // array value
                Kind arrayElementKind = getArrayValuesKind(argumentClass[i]);
                LocationIdentity locationIdentity = NamedLocationIdentity.getArrayLocation(arrayElementKind);
                IndexedLocationNode locationNode = IndexedLocationNode.create(locationIdentity, arrayElementKind, HotSpotGraalRuntime.getArrayBaseOffset(arrayElementKind), ConstantNode.forInt(0, g),
                                g, HotSpotGraalRuntime.getArrayIndexScale(arrayElementKind));
                ComputeAddressNode arrayAddress = g.unique(new ComputeAddressNode(boxedElement, locationNode, StampFactory.forKind(providers.getCodeCache().getTarget().wordKind)));
                args.add(arrayAddress);
            } else {
                // boxed primitive value
                LoadFieldNode loadFieldNode = g.add(new LoadFieldNode(boxedElement, getResolvedJavaField(argumentClass[i])));
                fixedWithNext.setNext(loadFieldNode);
                fixedWithNext = loadFieldNode;
                args.add(loadFieldNode);
            }
        }
        return fixedWithNext;
    }

    public static int getArrayValuesObjectOffset(Class clazz) {
        if (clazz == int[].class) {
            return Unsafe.ARRAY_INT_BASE_OFFSET;
        } else if (clazz == long[].class) {
            return Unsafe.ARRAY_LONG_BASE_OFFSET;
        } else if (clazz == char[].class) {
            return Unsafe.ARRAY_CHAR_BASE_OFFSET;
        } else if (clazz == byte[].class) {
            return Unsafe.ARRAY_BYTE_BASE_OFFSET;
        } else if (clazz == float[].class) {
            return Unsafe.ARRAY_FLOAT_BASE_OFFSET;
        } else if (clazz == double[].class) {
            return Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
        } else {
            throw new IllegalArgumentException("Array Type not supported: " + clazz);
        }
    }

    public static Kind getArrayValuesKind(Class clazz) {
        if (clazz == int[].class) {
            return Kind.Int;
        } else if (clazz == long[].class) {
            return Kind.Long;
        } else if (clazz == char[].class) {
            return Kind.Char;
        } else if (clazz == byte[].class) {
            return Kind.Byte;
        } else if (clazz == float[].class) {
            return Kind.Float;
        } else if (clazz == double[].class) {
            return Kind.Double;
        } else {
            throw new IllegalArgumentException("Array Type not supported: " + clazz);
        }
    }

    private static Kind getKind(Class clazz) {
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

    private static ResolvedJavaField getResolvedJavaField(Class containerClass) {
        if (containerClass == int.class || containerClass == Integer.class) {
            return integerValueField;
        } else if (containerClass == long.class || containerClass == Long.class) {
            return longValueField;
        } else if (containerClass == char.class || containerClass == Character.class) {
            return charValueField;
        } else if (containerClass == byte.class || containerClass == Byte.class) {
            return byteValueField;
        } else if (containerClass == float.class || containerClass == Float.class) {
            return floatValueField;
        } else if (containerClass == double.class || containerClass == Double.class) {
            return doubleValueField;
        } else {
            throw new IllegalArgumentException("Type not supported: " + containerClass);
        }
    }

    private static ResolvedJavaType getResolvedJavaType(Kind kind) {
        if (kind == Kind.Int) {
            return integerJavaType;
        } else if (kind == Kind.Long) {
            return longJavaType;
        } else if (kind == Kind.Char) {
            return charJavaType;
        } else if (kind == Kind.Byte) {
            return byteJavaType;
        } else if (kind == Kind.Float) {
            return floatJavaType;
        } else if (kind == Kind.Double) {
            return doubleJavaType;
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    @SuppressWarnings("unused")
    public static Object libCall(Object argLoc, Object unused1, Object unused2) {
        throw GraalInternalError.shouldNotReachHere("GNFI Callstub method must not be called!");
    }
}
