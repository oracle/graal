/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.redefinition.InnerClassRedefiner;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public class ConstantPoolPatcher {
    public static void getDirectInnerAnonymousClassNames(Symbol<Name> fileSystemName, byte[] bytes, Set<Symbol<Name>> innerNames, EspressoContext context)
                    throws ClassFormatError {
        ClassfileStream stream = new ClassfileStream(bytes, null);
        // skip magic and version - 8 bytes
        stream.skip(8);
        final int length = stream.readU2();

        int i = 1;
        while (i < length) {
            final int tagByte = stream.readU1();
            final ConstantPool.Tag tag = ConstantPool.Tag.fromValue(tagByte);

            switch (tag) {
                case UTF8:
                    ByteSequence byteSequence = stream.readByteSequenceUTF();
                    if (isDirectAnonymousInnerClass(fileSystemName, byteSequence)) {
                        assert InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(byteSequence.toString()).matches();
                        innerNames.add(context.getNames().getOrCreate(byteSequence));
                    }
                    break;
                case CLASS:
                case STRING:
                case METHODTYPE:
                    stream.readU2();
                    break;
                case FIELD_REF:
                case METHOD_REF:
                case INTERFACE_METHOD_REF:
                case NAME_AND_TYPE:
                case DYNAMIC:
                case INVOKEDYNAMIC:
                    stream.readU2();
                    stream.readU2();
                    break;
                case INTEGER:
                    stream.readS4();
                    break;
                case FLOAT:
                    stream.readFloat();
                    break;
                case LONG:
                    stream.readS8();
                    ++i;
                    break;
                case DOUBLE:
                    stream.readDouble();
                    ++i;
                    break;
                case METHODHANDLE:
                    stream.readU1();
                    stream.readU2();
                    break;
                default:
                    throw new ClassFormatError();
            }
            i++;
        }
    }

    private static boolean isDirectAnonymousInnerClass(ByteSequence outer, ByteSequence inner) {
        if (!inner.contentStartsWith(outer) || inner.length() < outer.length() + 2) {
            return false;
        }
        int i = outer.length();
        if (inner.byteAt(i++) != '$') {
            return false;
        }
        do {
            if (!isDecimalDigit(inner.byteAt(i++))) {
                return false;
            }
        } while (i < inner.length());
        return true;
    }

    private static boolean isDecimalDigit(byte c) {
        return '0' <= c && c <= '9';
    }

    public static byte[] patchConstantPool(byte[] bytes, Map<Symbol<Name>, Symbol<Name>> rules, EspressoContext context) throws ClassFormatError {
        byte[] result = Arrays.copyOf(bytes, bytes.length);
        ClassfileStream stream = new ClassfileStream(bytes, null);

        // skip magic and version - 8 bytes
        stream.skip(8);
        final int length = stream.readU2();

        int byteArrayGrowth = 0;
        int i = 1;
        while (i < length) {
            final int tagByte = stream.readU1();
            final ConstantPool.Tag tag = ConstantPool.Tag.fromValue(tagByte);

            switch (tag) {
                case UTF8:
                    int position = stream.getPosition() + 2; // utfLength is first two bytes
                    ByteSequence byteSequence = stream.readByteSequenceUTF();
                    Symbol<Name> asSymbol = context.getNames().getOrCreate(byteSequence);

                    if (rules.containsKey(asSymbol)) {
                        int originalLength = byteSequence.length();
                        Symbol<Name> replacedSymbol = rules.get(asSymbol);
                        if (originalLength == replacedSymbol.length()) {
                            replacedSymbol.writeTo(result, position + byteArrayGrowth);
                        } else {
                            int diff = replacedSymbol.length() - originalLength;
                            byteArrayGrowth += diff;

                            // make room for the longer class name
                            result = Arrays.copyOf(result, result.length + diff);

                            int currentPosition = stream.getPosition();
                            // shift the tail of array
                            System.arraycopy(bytes, currentPosition, result, currentPosition + byteArrayGrowth, bytes.length - currentPosition);

                            // update utfLength
                            char utfLength = (char) replacedSymbol.length();
                            int utfLengthPosition = position - 2 + byteArrayGrowth - diff;
                            result[utfLengthPosition] = (byte) (utfLength >> 8);
                            result[utfLengthPosition + 1] = (byte) (utfLength);

                            // insert patched byte array
                            replacedSymbol.writeTo(result, utfLengthPosition + 2);
                        }
                    }
                    break;
                case CLASS:
                case STRING:
                case METHODTYPE:
                    stream.readU2();
                    break;
                case FIELD_REF:
                case METHOD_REF:
                case INTERFACE_METHOD_REF:
                case NAME_AND_TYPE:
                case DYNAMIC:
                case INVOKEDYNAMIC:
                    stream.readU2();
                    stream.readU2();
                    break;
                case INTEGER:
                    stream.readS4();
                    break;
                case FLOAT:
                    stream.readFloat();
                    break;
                case LONG:
                    stream.readS8();
                    ++i;
                    break;
                case DOUBLE:
                    stream.readDouble();
                    ++i;
                    break;
                case METHODHANDLE:
                    stream.readU1();
                    stream.readU2();
                    break;
                default:
                    throw new ClassFormatError();
            }
            i++;
        }
        return result;
    }
}
