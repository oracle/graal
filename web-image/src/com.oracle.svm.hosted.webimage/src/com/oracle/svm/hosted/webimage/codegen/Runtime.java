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

import com.oracle.svm.webimage.functionintrinsics.JSFunctionDefinition;
import com.oracle.svm.webimage.functionintrinsics.JSGenericFunctionDefinition;

/**
 * Runtime functions.
 */
public class Runtime {
    public static final JSFunctionDefinition TO_INT = new JSGenericFunctionDefinition("toInt", 1, false, null, false);
    public static final JSFunctionDefinition TO_LONG = new JSGenericFunctionDefinition("toLong", 1, false, null, false);

    public static final JSFunctionDefinition LONG_BITS_TO_DOUBLE = new JSGenericFunctionDefinition("long64ToDouble", 1, false, null, false);
    public static final JSFunctionDefinition DOUBLE_BITS_TO_LONG = new JSGenericFunctionDefinition("doubleToLong64", 1, false, null, false);
    public static final JSFunctionDefinition FLOAT_BITS_TO_INT = new JSGenericFunctionDefinition("floatToRawInt", 1, false, null, false);
    public static final JSFunctionDefinition INT_BITS_TO_FLOAT = new JSGenericFunctionDefinition("intToRawFloat", 1, false, null, false);

    // unsigned math comparison
    public static final JSFunctionDefinition UnsignedIClI32 = new JSGenericFunctionDefinition("unsignedCompareLessI32", 2, false, null, false);

    public static final JSFunctionDefinition isA = new JSGenericFunctionDefinition("isA", 3, false, null, false);
    public static final JSFunctionDefinition isExact = new JSGenericFunctionDefinition("isExact", 3, false, null, false);
    public static final JSFunctionDefinition slotTypeCheck = new JSGenericFunctionDefinition("slotTypeCheck", 2, false, null, false);

    public static final JSFunctionDefinition ARRAY_EQUALS = new JSGenericFunctionDefinition("arrayequals", 3, false, null, false);
    public static final JSFunctionDefinition ARRAY_EQUALS_LONG = new JSGenericFunctionDefinition("arrayequalslong", 3, false, null, false);

    public static final JSFunctionDefinition NEW_MULTI_ARRAY = new JSGenericFunctionDefinition("_nma", -1, false, null, false);

    public static final JSFunctionDefinition CloneRuntime = new JSGenericFunctionDefinition("object_clone_runtime", 1, false, null, false);

    public static final JSFunctionDefinition UNSAFE_LOAD_RUNTIME = new JSGenericFunctionDefinition("unsafe_load_runtime", 2, false, null, false);
    public static final JSFunctionDefinition UNSAFE_STORE_RUNTIME = new JSGenericFunctionDefinition("unsafe_store_runtime", 2, false, null, false);

    public static final JSFunctionDefinition WRITE_BYTE = new JSGenericFunctionDefinition("write_byte", 2, false, null, false);
    public static final JSFunctionDefinition WRITE_CHAR = new JSGenericFunctionDefinition("write_char", 2, false, null, false);
    public static final JSFunctionDefinition WRITE_SHORT = new JSGenericFunctionDefinition("write_short", 2, false, null, false);
    public static final JSFunctionDefinition WRITE_INT = new JSGenericFunctionDefinition("write_int", 2, false, null, false);
    public static final JSFunctionDefinition WRITE_FLOAT = new JSGenericFunctionDefinition("write_float", 2, false, null, false);
    public static final JSFunctionDefinition WRITE_LONG = new JSGenericFunctionDefinition("write_long", 2, false, null, false);
    public static final JSFunctionDefinition WRITE_DOUBLE = new JSGenericFunctionDefinition("write_double", 2, false, null, false);

    public static final JSFunctionDefinition READ_BYTE = new JSGenericFunctionDefinition("read_byte", 1, false, null, false);
    public static final JSFunctionDefinition READ_CHAR = new JSGenericFunctionDefinition("read_char", 1, false, null, false);
    public static final JSFunctionDefinition READ_SHORT = new JSGenericFunctionDefinition("read_short", 1, false, null, false);
    public static final JSFunctionDefinition READ_INT = new JSGenericFunctionDefinition("read_int", 1, false, null, false);
    public static final JSFunctionDefinition READ_FLOAT = new JSGenericFunctionDefinition("read_float", 1, false, null, false);
    public static final JSFunctionDefinition READ_LONG = new JSGenericFunctionDefinition("read_long", 1, false, null, false);
    public static final JSFunctionDefinition READ_DOUBLE = new JSGenericFunctionDefinition("read_double", 1, false, null, false);

    /**
     * Compare and Set.
     * <p>
     * See {@link java.lang.invoke.VarHandle#compareAndSet(Object...)} for the semantics of the
     * operation.
     */
    public static final JSFunctionDefinition CAS = new JSGenericFunctionDefinition("compare_and_swap_runtime", 5, false, null, false);
    /**
     * Compare and Exchange.
     * <p>
     * See {@link java.lang.invoke.VarHandle#compareAndExchange(Object...)} for the semantics of the
     * operation.
     */
    public static final JSFunctionDefinition CAX = new JSGenericFunctionDefinition("compare_and_exchange_runtime", 5, false, null, false);

    public static final JSFunctionDefinition ATOMIC_READ_AND_WRITE = new JSGenericFunctionDefinition("atomic_read_and_write", 3, false, null, false);
    public static final JSFunctionDefinition ATOMIC_READ_AND_ADD = new JSGenericFunctionDefinition("atomic_read_and_add", 3, false, null, false);

    public static final JSFunctionDefinition MATH_ABS = new JSGenericFunctionDefinition("abs", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_COS = new JSGenericFunctionDefinition("cos", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_LOG = new JSGenericFunctionDefinition("log", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_LOG10 = new JSGenericFunctionDefinition("log10", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_SIN = new JSGenericFunctionDefinition("sin", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_SQRT = new JSGenericFunctionDefinition("sqrt", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_TAN = new JSGenericFunctionDefinition("tan", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_EXP = new JSGenericFunctionDefinition("exp", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_FLOOR = new JSGenericFunctionDefinition("floor", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_CEIL = new JSGenericFunctionDefinition("ceil", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_TRUNC = new JSGenericFunctionDefinition("trunc", 1, true, "Math", false);
    public static final JSFunctionDefinition MATH_SIGN = new JSGenericFunctionDefinition("sign", 1, true, "Math", false);

    public static final JSFunctionDefinition TO_JAVA_STRING = new JSGenericFunctionDefinition("toJavaString", 1, false, null, false);
    public static final JSFunctionDefinition ADD_TO_FUNTAB = new JSGenericFunctionDefinition("runtime.addToFuntab", 1, false, null, false);

    /**
     * See `string-extensions.js`.
     */
    public static final JSFunctionDefinition TO_JAVA_STRING_LAZY = new JSGenericFunctionDefinition("toJavaStringLazy", 1, false, null, false);
    public static final JSFunctionDefinition INITIALIZE_JAVA_STRINGS = new JSGenericFunctionDefinition("initializeJavaStrings", 0, false, null, false);

    public static final JSFunctionDefinition SET_ENDIANNESS_FUN = new JSGenericFunctionDefinition("runtime.setEndianness", 1, false, null, false);

    /*
     * Definitions for array constructors.
     */
    public static final JSGenericFunctionDefinition JsArray = new JSGenericFunctionDefinition("Array", 1, false, null, true);
    public static final JSGenericFunctionDefinition Int8Array = new JSGenericFunctionDefinition("Int8Array", 1, false, null, true);
    public static final JSGenericFunctionDefinition Uint8Array = new JSGenericFunctionDefinition("Uint8Array", 1, false, null, true);
    public static final JSGenericFunctionDefinition Int16Array = new JSGenericFunctionDefinition("Int16Array", 1, false, null, true);
    public static final JSGenericFunctionDefinition Uint16Array = new JSGenericFunctionDefinition("Uint16Array", 1, false, null, true);
    public static final JSGenericFunctionDefinition Int32Array = new JSGenericFunctionDefinition("Int32Array", 1, false, null, true);
    public static final JSGenericFunctionDefinition BigInt64Array = new JSGenericFunctionDefinition("BigInt64Array", 1, false, null, true);
    public static final JSGenericFunctionDefinition Float32Array = new JSGenericFunctionDefinition("Float32Array", 1, false, null, true);
    public static final JSGenericFunctionDefinition Float64Array = new JSGenericFunctionDefinition("Float64Array", 1, false, null, true);

    /*
     * Definitions for array creation functions.
     *
     * Construct and initialize arrays.
     */
    public static final JSFunctionDefinition ArrayCreateHub = new JSGenericFunctionDefinition("_aH", 2, false, null, false);
    public static final JSFunctionDefinition ArrayCreateObject = new JSGenericFunctionDefinition("_aO", 2, false, null, false);
    public static final JSFunctionDefinition ArrayCreateInt8 = new JSGenericFunctionDefinition("_ai8", 2, false, null, false);
    public static final JSFunctionDefinition ArrayCreateUint8 = new JSGenericFunctionDefinition("_au8", 2, false, null, false);
    public static final JSFunctionDefinition ArrayCreateInt16 = new JSGenericFunctionDefinition("_ai16", 2, false, null, false);
    public static final JSFunctionDefinition ArrayCreateUint16 = new JSGenericFunctionDefinition("_au16", 2, false, null, false);
    public static final JSFunctionDefinition ArrayCreateInt32 = new JSGenericFunctionDefinition("_ai32", 2, false, null, false);
    public static final JSFunctionDefinition ArrayCreateLong64 = new JSGenericFunctionDefinition("_ai64", 2, false, null, false);
    public static final JSFunctionDefinition ArrayCreateFloat32 = new JSGenericFunctionDefinition("_af32", 2, false, null, false);
    public static final JSFunctionDefinition ArrayCreateFloat64 = new JSGenericFunctionDefinition("_af64", 2, false, null, false);

    /*
     * Definitions for array initialization functions.
     */
    public static final JSFunctionDefinition ArrayInitFunWithHub = new JSGenericFunctionDefinition("_a$h_", 5, false, null, false);
    public static final JSFunctionDefinition ArrayInitFunWithHubAndValue = new JSGenericFunctionDefinition("_a$hi_", 5, false, null, false);
    public static final JSFunctionDefinition ArrayInitFunWithHubBigInt64 = new JSGenericFunctionDefinition("_a$h_BigInt64Array_", 5, false, null, false);
    public static final JSFunctionDefinition ArrayInitFunWithHubAndValueBigInt64 = new JSGenericFunctionDefinition("_a$hi_BigInt64Array_", 5, false, null, false);
    public static final JSFunctionDefinition PackedArrayInitFunWitHubAndValue = new JSGenericFunctionDefinition("_a$hip_", 7, false, null, false);
    public static final JSFunctionDefinition PackedArrayInitFunWitHubAndBase64Value = new JSGenericFunctionDefinition("_a$hipb64_", 7, false, null, false);

    public static final JSFunctionDefinition IsArray = new JSGenericFunctionDefinition("isArray", 1, false, null, false);

    /*
     * Definitions for BigInt64Array store/load.
     */
    public static final JSFunctionDefinition BigInt64ArrayStore = new JSGenericFunctionDefinition("bigInt64ArrayStore", 3, false, null, false);
    public static final JSFunctionDefinition BigInt64ArrayLoad = new JSGenericFunctionDefinition("bigInt64ArrayLoad", 2, false, null, false);

    /*
     * Definitions for Java/JavaScript interop.
     */
    public static final JSFunctionDefinition JavaToJavaScript = new JSGenericFunctionDefinition("conversion.javaToJavaScript", 1, false, null, false);
    public static final JSFunctionDefinition JavaScriptToJava = new JSGenericFunctionDefinition("conversion.javaScriptToJava", 1, false, null, false);
    public static final JSFunctionDefinition BoxIfNeeded = new JSGenericFunctionDefinition("conversion.boxIfNeeded", 2, false, null, false);
    public static final JSFunctionDefinition UnboxIfNeeded = new JSGenericFunctionDefinition("conversion.unboxIfNeeded", 2, false, null, false);

    public static final JSFunctionDefinition arrayVTableInitialization = new JSGenericFunctionDefinition("setVTable", 3, false, null, false);

    public static final String CONFIG_CLASS_NAME = "Config";
}
