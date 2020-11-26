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

import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.jni.ModifiedUtf8;

import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class ConstantPoolPatcher {
    public static void getDirectInnerClassNames(String fileSystemName, byte[] bytes, ArrayList<String> innerNames) throws IllegalClassFormatException {
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
                    String value = byteSequence.toString();
                    if (value.startsWith(fileSystemName) && !value.equals(fileSystemName)) {
                        if (InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(value).matches()) {
                            innerNames.add(value);
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
                    throw new IllegalClassFormatException();
            }
            i++;
        }
    }

    public static byte[] patchConstantPool(byte[] bytes, Map<String, String> rules) throws IllegalClassFormatException {
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
                    String value = byteSequence.toString();

                    if (rules.containsKey(value)) {
                        int originalLegth = byteSequence.length();
                        byte[] replacedBytes = ModifiedUtf8.fromJavaString(rules.get(value));
                        int newLength = replacedBytes.length;
                        if (originalLegth == newLength) {
                            System.arraycopy(replacedBytes, 0, result, position + byteArrayGrowth, replacedBytes.length);
                        } else {
                            int diff = newLength - originalLegth;
                            byteArrayGrowth += diff;

                            // make room for the longer class name
                            result = Arrays.copyOf(result, result.length + diff);

                            int currentPosition = stream.getPosition();
                            // shift the tail of array
                            System.arraycopy(bytes, currentPosition, result, currentPosition + byteArrayGrowth, bytes.length - currentPosition);

                            // update utfLength
                            char utfLength = (char) newLength;
                            int utfLengthPosition = position - 2 + byteArrayGrowth - diff;
                            result[utfLengthPosition] = (byte) (utfLength >> 8);
                            result[utfLengthPosition + 1] = (byte) (utfLength);

                            // insert patched byte array
                            System.arraycopy(replacedBytes, 0, result, utfLengthPosition + 2, replacedBytes.length);
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
                    throw new IllegalClassFormatException();
            }
            i++;
        }
        return result;
    }
}
