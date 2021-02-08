/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.descriptors;

import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.jni.ModifiedUtf8;

public final class Validation {
    private Validation() {
        /* no instances */
    }

    /**
     * Names of methods, fields, local variables, and formal parameters are stored as unqualified
     * names. An unqualified name must contain at least one Unicode code point and must not contain
     * any of the ASCII characters . ; [ / (that is, period or semicolon or left square bracket or
     * forward slash).
     *
     * Does not check that the byte sequence is well-formed modified-UTF8 string.
     */
    public static boolean validUnqualifiedName(ByteSequence bytes) {
        if (bytes.length() == 0) {
            return false;
        }
        if (bytes.byteAt(0) == '(') { // maybe a signature
            return false;
        }
        for (int i = 0; i < bytes.length(); ++i) {
            char ch = (char) bytes.byteAt(i);
            if (invalidUnqualifiedNameChar(ch)) {
                return false;
            }
        }
        return true;
    }

    public static boolean validUnqualifiedName(CharSequence chars) {
        if (chars.length() == 0) {
            return false;
        }
        if (chars.charAt(0) == '(') { // maybe a signature
            return false;
        }
        for (int i = 0; i < chars.length(); ++i) {
            char ch = chars.charAt(i);
            if (invalidUnqualifiedNameChar(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean invalidUnqualifiedNameChar(char ch) {
        switch (ch) {
            case '.':
            case ';':
            case '[':
            case '/':
                return true;
            default:
                return false;
        }
    }

    /**
     * Method names are further constrained so that, with the exception of the special method names
     * <init> and <clinit> (&sect;2.9), they must not contain the ASCII characters < or > (that is,
     * left angle bracket or right angle bracket).
     *
     * Does not check that the byte sequence is well-formed modified-UTF8 string.
     */
    public static boolean validMethodName(ByteSequence bytes, boolean allowClinit) {
        if (bytes.length() == 0) {
            return false;
        }
        char first = (char) bytes.byteAt(0);
        if (first == '<') {
            return bytes.contentEquals(Name._init_) || (allowClinit && bytes.contentEquals(Name._clinit_));
        }
        for (int i = 0; i < bytes.length(); ++i) {
            char ch = (char) bytes.byteAt(i);
            if (invalidMethodNameChar(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean invalidMethodNameChar(char ch) {
        switch (ch) {
            case '.':
            case ';':
            case '[':
            case '/':
            case '<':
            case '>':
                return true;
            default:
                return false;
        }
    }

    /**
     * For historical reasons, the syntax of binary names that appear in class file structures
     * differs from the syntax of binary names documented in JLS &sect;13.1. In this internal form,
     * the ASCII periods (.) that normally separate the identifiers which make up the binary name
     * are replaced by ASCII forward slashes (/). The identifiers themselves must be unqualified
     * names (&sect;4.2.2).
     */
    public static boolean validBinaryName(ByteSequence bytes) {
        if (bytes.length() == 0) {
            return false;
        }
        if (bytes.byteAt(0) == '/') {
            return false;
        }
        int prev = 0;
        int i = 0;
        while (i < bytes.length()) {
            while (i < bytes.length() && bytes.byteAt(i) != '/') {
                ++i;
            }
            if (!validUnqualifiedName(bytes.subSequence(prev, i - prev))) {
                return false;
            }
            prev = i + 1;
            ++i;
        }
        return prev != bytes.length();
    }

    public static boolean validBinaryName(CharSequence chars) {
        if (chars.length() == 0) {
            return false;
        }
        if (chars.charAt(0) == '/') {
            return false;
        }
        int prev = 0;
        int i = 0;
        while (i < chars.length()) {
            while (i < chars.length() && chars.charAt(i) != '/') {
                ++i;
            }
            if (!validUnqualifiedName(chars.subSequence(prev, i))) {
                return false;
            }
            prev = i + 1;
            ++i;
        }
        return prev != chars.length();
    }

    /**
     * Because arrays are objects, the opcodes anewarray and multianewarray can reference array
     * "classes" via CONSTANT_Class_info structures in the constant_pool table. For such array
     * classes, the name of the class is the descriptor of the array type.
     */
    public static boolean validClassNameEntry(ByteSequence bytes) {
        if (bytes.length() == 0) {
            return false;
        }
        if (bytes.byteAt(0) == '[') {
            return validTypeDescriptor(bytes, false);
        }
        return validBinaryName(bytes);
    }

    public static boolean validFieldDescriptor(ByteSequence bytes) {
        return validTypeDescriptor(bytes, false);
    }

    /**
     * Validates a type descriptor in internal form.
     *
     * <pre>
     * TypeDescriptor: BaseType | ObjectType | ArrayType
     * BaseType: B | C | D | F | I | J | S | Z | V (if allowVoid is true)
     * ObjectType: L ClassName ;
     * ArrayType: [ ComponentType
     * ComponentType: FieldDescriptor
     * </pre>
     * 
     * @param bytes Sequence to validate
     * @param allowVoid true, if the void (V) type is allowed. Fields and method parameters do not
     *            allow void.
     * @return true, if the descriptor represents a valid type descriptor in internal form.
     */
    public static boolean validTypeDescriptor(ByteSequence bytes, boolean allowVoid) {
        if (bytes.length() == 0) {
            return false;
        }
        byte first = bytes.byteAt(0);
        if (bytes.length() == 1) {
            return ((first == 'V' && allowVoid) || validPrimitiveChar(first));
        }
        if (first == '[') {
            int dimensions = 0;
            while (dimensions < bytes.length() && bytes.byteAt(dimensions) == '[') {
                ++dimensions;
            }
            if (dimensions > Constants.MAX_ARRAY_DIMENSIONS) {
                return false;
            }
            // Arrays of void (V) are never allowed.
            return validFieldDescriptor(bytes.subSequence(dimensions, bytes.length() - dimensions));
        }
        if (first == 'L') {
            char last = (char) bytes.byteAt(bytes.length() - 1);
            if (last != ';') {
                return false;
            }
            return validBinaryName(bytes.subSequence(1, bytes.length() - 2));
        }
        return false;
    }

    public static boolean validSignatureDescriptor(ByteSequence bytes) {
        return validSignatureDescriptor(bytes, false);
    }

    public static boolean validSignatureDescriptor(ByteSequence bytes, boolean isInitOrClinit) {
        return validSignatureDescriptorGetSlots(bytes, isInitOrClinit) >= 0;
    }

    private static final int INVALID_SIGNATURE = -1;

    /**
     * A method descriptor contains zero or more parameter descriptors, representing the types of
     * parameters that the method takes, and a return descriptor, representing the type of the value
     * (if any) that the method returns.
     * 
     * <pre>
     *     SignatureDescriptor: ( {FieldDescriptor} ) TypeDescriptor
     * </pre>
     */
    public static int validSignatureDescriptorGetSlots(ByteSequence bytes, boolean isInitOrClinit) {
        if (bytes.length() < 3) { // shortest descriptor e.g. ()V
            return INVALID_SIGNATURE;
        }
        if (bytes.byteAt(0) != '(') { // not a signature
            return INVALID_SIGNATURE;
        }
        int slots = 0;
        int currentSlot = -1;

        int index = 1;
        while (index < bytes.length() && bytes.byteAt(index) != ')') {
            int prev = index;
            // skip array
            while (index < bytes.length() && bytes.byteAt(index) == '[') {
                ++index;
            }
            int dimensions = index - prev;
            if (dimensions > Constants.MAX_ARRAY_DIMENSIONS) {
                return INVALID_SIGNATURE;
            }
            if (index >= bytes.length()) {
                return INVALID_SIGNATURE;
            }
            if (dimensions > 0) {
                currentSlot = 1;
            }
            if (bytes.byteAt(index) == 'L') {
                ++index;
                while (index < bytes.length() && bytes.byteAt(index) != ';') {
                    ++index;
                }
                if (index >= bytes.length()) {
                    return INVALID_SIGNATURE;
                }
                assert bytes.byteAt(index) == ';';
                if (!validFieldDescriptor(bytes.subSequence(prev, index - prev + 1))) {
                    return INVALID_SIGNATURE;
                }
                ++index; // skip ;
                currentSlot = 1;
            } else {
                // Must be a non-void primitive.
                byte ch = bytes.byteAt(index);
                if (!validPrimitiveChar(ch)) {
                    return INVALID_SIGNATURE;
                }
                if (currentSlot <= 0) {
                    if (ch == 'D' || ch == 'J') {
                        currentSlot = 2;
                    } else {
                        currentSlot = 1;
                    }
                }
                ++index; // skip
            }
            assert currentSlot > 0;
            slots += currentSlot;
            currentSlot = -1;
        }
        if (index >= bytes.length()) {
            return INVALID_SIGNATURE;
        }
        assert bytes.byteAt(index) == ')';
        // Validate return type.
        if (isInitOrClinit) {
            return (bytes.byteAt(index + 1) == 'V' && bytes.length() == index + 2) ? slots : INVALID_SIGNATURE;
        } else {
            return (validTypeDescriptor(bytes.subSequence(index + 1, bytes.length() - index - 1), true)) ? slots : INVALID_SIGNATURE;
        }
    }

    private static boolean validPrimitiveChar(byte ch) {
        switch (ch) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
                return true;
            default:
                return false;
        }
    }

    public static boolean validModifiedUTF8(ByteSequence bytes) {
        return ModifiedUtf8.isValid(bytes.getUnderlyingBytes(), bytes.offset(), bytes.length());
    }
}
