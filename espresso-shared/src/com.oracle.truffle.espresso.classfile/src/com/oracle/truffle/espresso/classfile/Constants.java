/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

public final class Constants {

    /* Access Flags */
    // @formatter:off
    public static final int ACC_PUBLIC               = 0x00000001;
    public static final int ACC_PRIVATE              = 0x00000002;
    public static final int ACC_PROTECTED            = 0x00000004;
    public static final int ACC_STATIC               = 0x00000008;
    public static final int ACC_FINAL                = 0x00000010;
    public static final int ACC_SYNCHRONIZED         = 0x00000020;
    public static final int ACC_SUPER                = 0x00000020;
    public static final int ACC_VOLATILE             = 0x00000040;
    public static final int ACC_TRANSIENT            = 0x00000080;
    public static final int ACC_NATIVE               = 0x00000100;
    public static final int ACC_INTERFACE            = 0x00000200;
    public static final int ACC_ABSTRACT             = 0x00000400;
    public static final int ACC_STRICT               = 0x00000800;
    public static final int ACC_EXPLICIT             = 0x00001000;

    public static final int ACC_BRIDGE               = 0x00000040;
    public static final int ACC_VARARGS              = 0x00000080;
    public static final int ACC_SYNTHETIC            = 0x00001000;
    public static final int ACC_ANNOTATION           = 0x00002000;
    public static final int ACC_ENUM                 = 0x00004000;
    public static final int ACC_MANDATED             = 0x00008000;
    public static final int ACC_MODULE               = 0x00008000;

    // Not part of the spec, used internally by the VM.
    // Methods
    public static final int ACC_FORCE_INLINE         = 0x00020000;
    public static final int ACC_LAMBDA_FORM_COMPILED = 0x00040000;
    public static final int ACC_CALLER_SENSITIVE     = 0x00080000;
    public static final int ACC_HIDDEN               = 0x00100000; // also for fields
    public static final int ACC_SCOPED               = 0x00200000;
    public static final int ACC_DONT_INLINE          = 0x00400000;
    // Classes
    public static final int ACC_FINALIZER            = 0x00010000;
    public static final int ACC_IS_HIDDEN_CLASS      = 0x04000000; // synchronized with JVM_ACC_IS_HIDDEN_CLASS
    public static final int ACC_VALUE_BASED          = 0x00020000;
    // Fields
    public static final int ACC_STABLE               = 0x00010000;

    public static final int FIELD_ID_TYPE            = 0x01000000;
    public static final int FIELD_ID_OBFUSCATE       = 0x02000000;

    public static final int JVM_ACC_WRITTEN_FLAGS    = 0x00007FFF;
    // @formatter:on

    // Table 4.1-A. Class access and property modifiers.
    public static final int JVM_RECOGNIZED_CLASS_MODIFIERS = ACC_PUBLIC |
                    ACC_FINAL |
                    ACC_SUPER | // Only very old compilers.
                    ACC_INTERFACE |
                    ACC_ABSTRACT |
                    ACC_ANNOTATION |
                    ACC_ENUM |
                    ACC_SYNTHETIC;

    // Inner classes can be static, private or protected (classic VM does this)
    public static final int RECOGNIZED_INNER_CLASS_MODIFIERS = (JVM_RECOGNIZED_CLASS_MODIFIERS | ACC_PRIVATE | ACC_PROTECTED | ACC_STATIC);

    // Table 4.5-A. Field access and property flags.
    public static final int JVM_RECOGNIZED_FIELD_MODIFIERS = ACC_PUBLIC |
                    ACC_PRIVATE |
                    ACC_PROTECTED |
                    ACC_STATIC |
                    ACC_FINAL |
                    ACC_VOLATILE |
                    ACC_TRANSIENT |
                    ACC_ENUM |
                    ACC_SYNTHETIC;

    // Table 4.6-A. Method access and property flags.
    public static final int JVM_RECOGNIZED_METHOD_MODIFIERS = ACC_PUBLIC |
                    ACC_PRIVATE |
                    ACC_PROTECTED |
                    ACC_STATIC |
                    ACC_FINAL |
                    ACC_SYNCHRONIZED |
                    ACC_BRIDGE |
                    ACC_VARARGS |
                    ACC_NATIVE |
                    ACC_ABSTRACT |
                    ACC_STRICT |
                    ACC_SYNTHETIC;

    /* Type codes for StackMap attribute */
    public static final int ITEM_Bogus = 0; // an unknown or uninitialized value
    public static final int ITEM_Integer = 1; // a 32-bit integer
    public static final int ITEM_Float = 2; // not used
    public static final int ITEM_Double = 3; // not used
    public static final int ITEM_Long = 4; // a 64-bit integer
    public static final int ITEM_Null = 5; // the type of null
    public static final int ITEM_InitObject = 6; // "this" in constructor
    public static final int ITEM_Object = 7; // followed by 2-byte index of class name
    public static final int ITEM_NewObject = 8; // followed by 2-byte ref to "new"

    /* Constants used in StackMapTable attribute */
    public static final int SAME_FRAME_BOUND = 64;
    public static final int SAME_LOCALS_1_STACK_ITEM_BOUND = 128;
    public static final int SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247;
    public static final int CHOP_BOUND = 251;
    public static final int SAME_FRAME_EXTENDED = 251;
    public static final int APPEND_FRAME_BOUND = 255;
    public static final int FULL_FRAME = 255;

    public static final int MAX_ARRAY_DIMENSIONS = 255;

    /**
     * Constant pool reference-kind codes, as used by CONSTANT_MethodHandle CP entries.
     */
    public static final byte REF_NONE = 0; // null value
    public static final byte REF_getField = 1;
    public static final byte REF_getStatic = 2;
    public static final byte REF_putField = 3;
    public static final byte REF_putStatic = 4;
    public static final byte REF_invokeVirtual = 5;
    public static final byte REF_invokeStatic = 6;
    public static final byte REF_invokeSpecial = 7;
    public static final byte REF_newInvokeSpecial = 8;
    public static final byte REF_invokeInterface = 9;
    public static final byte REF_LIMIT = 10;

    /* ArrayType constants */

    public static final int JVM_ArrayType_Boolean = 4;
    public static final int JVM_ArrayType_Char = 5;
    public static final int JVM_ArrayType_Float = 6;
    public static final int JVM_ArrayType_Double = 7;
    public static final int JVM_ArrayType_Byte = 8;
    public static final int JVM_ArrayType_Short = 9;
    public static final int JVM_ArrayType_Int = 10;
    public static final int JVM_ArrayType_Long = 11;
    public static final int JVM_ArrayType_Object = 12;
    public static final int JVM_ArrayType_Void = 14;
    public static final int JVM_ArrayType_ReturnAddress = 98;
    public static final int JVM_ArrayType_Illegal = 99;

}
