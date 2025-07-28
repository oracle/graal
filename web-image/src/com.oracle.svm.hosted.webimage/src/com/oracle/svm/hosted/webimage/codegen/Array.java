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
package com.oracle.svm.hosted.webimage.codegen;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.codegen.type.TypeVtableLowerer;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionDefinition;
import com.oracle.svm.webimage.functionintrinsics.JSGenericFunctionDefinition;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.JavaKind;

/**
 * Array-related lowering logic.
 */
public class Array {
    /**
     * This utility method sets the vtable for arrays.
     *
     * This allows us to use arrays as receiver objects. Otherwise we would need to check for each
     * invoke if the receiver is an array and reroute the call to the 'Object' class.
     */
    public static void lowerArrrayVTable(JSCodeGenTool jsLTools) {
        JSGenericFunctionDefinition[] constructors = new JSGenericFunctionDefinition[]{Runtime.JsArray, Runtime.Int8Array, Runtime.Uint8Array, Runtime.Int16Array, Runtime.Uint16Array,
                        Runtime.Int32Array, Runtime.BigInt64Array, Runtime.Float32Array, Runtime.Float64Array};

        jsLTools.getCodeBuffer().emitNewLine();

        for (JSGenericFunctionDefinition constructor : constructors) {
            jsLTools.getCodeBuffer().emitNewLine();
            Runtime.arrayVTableInitialization.emitCall(jsLTools, Emitter.of(constructor.getFunctionName()), Emitter.of("\"" + TypeVtableLowerer.VTAB_PROP + "\""), Emitter.of(Object.class));
        }

        jsLTools.getCodeBuffer().emitNewLine();
    }

    /**
     * Returns the correct array initialization function for the given component kind.
     */
    public static JSFunctionDefinition getInitializer(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return Runtime.ArrayCreateUint8;
            case Byte:
                return Runtime.ArrayCreateInt8;
            case Short:
                return Runtime.ArrayCreateInt16;
            case Char:
                return Runtime.ArrayCreateUint16;
            case Int:
                return Runtime.ArrayCreateInt32;
            case Float:
                return Runtime.ArrayCreateFloat32;
            case Double:
                return Runtime.ArrayCreateFloat64;
            case Long:
                return Runtime.ArrayCreateLong64;
            case Object:
                return Runtime.ArrayCreateObject;
            default:
                throw VMError.shouldNotReachHere(kind.toString());
        }
    }

    /**
     * Returns the correct array constructor for the given component kind.
     */
    public static JSFunctionDefinition getConstructor(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return Runtime.Uint8Array;
            case Byte:
                return Runtime.Int8Array;
            case Short:
                return Runtime.Int16Array;
            case Char:
                return Runtime.Uint16Array;
            case Int:
                return Runtime.Int32Array;
            case Long:
                return Runtime.BigInt64Array;
            case Double:
                return Runtime.Float64Array;
            case Float:
                return Runtime.Float32Array;
            default:
                return Runtime.JsArray;
        }
    }

    /**
     * Lowers code to construct and initialize a new array given a known element type.
     *
     * The correct constructor, default value, and hub are inferred from the element type.
     *
     * @param length Either a Node that is lowered or any other object that is converted to a
     *            string.
     */
    public static void lowerNewArray(HostedType elementType, IEmitter length, JSCodeGenTool jsLTools) {
        JavaKind kind = elementType.getJavaKind();
        JSFunctionDefinition init = getInitializer(kind);
        HostedType arrayClass = elementType.getArrayClass();

        String hubName = jsLTools.getJSProviders().typeControl().requestHubName(arrayClass);

        init.emitCall(jsLTools, length, Emitter.of(hubName));
    }

    /**
     * Lowers code to construct and initialize a new array where the element type is only known at
     * runtime.
     */
    public static void lowerNewArray(ValueNode elementType, IEmitter length, JSCodeGenTool jsLTools) {
        Runtime.ArrayCreateHub.emitCall(jsLTools, length, Emitter.of(elementType));
    }

    /**
     * Lowers the correct array constructor.
     */
    public static void lowerArrayConstructor(JavaKind kind, int length, JSCodeGenTool jsLTools) {
        getConstructor(kind).emitCall(jsLTools, Emitter.of(length));
    }
}
