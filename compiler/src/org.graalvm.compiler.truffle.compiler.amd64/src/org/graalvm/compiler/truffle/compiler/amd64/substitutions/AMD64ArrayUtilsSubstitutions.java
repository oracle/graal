/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.amd64.substitutions;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.replacements.JDK9StringSubstitutions;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOf;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

@ClassSubstitution(className = "com.oracle.truffle.api.ArrayUtils", optional = true)
public class AMD64ArrayUtilsSubstitutions {

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    static final MetaAccessProvider INJECTED_METAACCESS = null;

    @MethodSubstitution(optional = true)
    public static int runIndexOf(String str, int fromIndex, int maxIndex, char... chars) {
        if (fromIndex >= str.length()) {
            return -1;
        }
        if (chars.length <= 4) {
            if (JavaVersionUtil.JAVA_SPEC <= 8) {
                return indexOfChar(StringSubstitutions.getValue(str), maxIndex, fromIndex, chars);
            } else {
                byte[] sourceArray = JDK9StringSubstitutions.getValue(str);
                if (JDK9StringSubstitutions.isCompactString(str)) {
                    int values = 0;
                    int nValues = 0;
                    for (int i = 0; i < chars.length; i++) {
                        if (chars[i] <= 0xff) {
                            values = (values << 8) | chars[i];
                            nValues++;
                        }
                    }
                    return nValues == 0 ? -1 : indexOfByte(sourceArray, maxIndex, fromIndex, values, nValues);
                } else {
                    return indexOfChar(sourceArray, maxIndex, fromIndex, chars);
                }
            }
        } else {
            return runIndexOf(str, fromIndex, maxIndex, chars);
        }
    }

    @MethodSubstitution(optional = true)
    public static int runIndexOf(char[] array, int fromIndex, int maxIndex, char... chars) {
        if (fromIndex >= array.length) {
            return -1;
        }
        if (chars.length <= 4) {
            return indexOfChar(array, maxIndex, fromIndex, chars);
        } else {
            return runIndexOf(array, fromIndex, maxIndex, chars);
        }
    }

    @MethodSubstitution(optional = true)
    public static int runIndexOf(byte[] array, int fromIndex, int maxIndex, byte... bytes) {
        if (fromIndex >= array.length) {
            return -1;
        }
        if (bytes.length <= 4) {
            return indexOfByte(array, maxIndex, fromIndex, bytes, bytes.length);
        } else {
            return runIndexOf(array, fromIndex, maxIndex, bytes);
        }
    }

    private static int indexOfChar(char[] array, int arrayLength, int fromIndex, char[] chars) {
        if (chars.length == 1) {
            return AMD64ArrayIndexOf.indexOf1Char(array, arrayLength, fromIndex, chars[0]);
        } else if (chars.length == 2) {
            return AMD64ArrayIndexOf.indexOf2Chars(array, arrayLength, fromIndex, chars[0], chars[1]);
        } else if (chars.length == 3) {
            return AMD64ArrayIndexOf.indexOf3Chars(array, arrayLength, fromIndex, chars[0], chars[1], chars[2]);
        } else {
            return AMD64ArrayIndexOf.indexOf4Chars(array, arrayLength, fromIndex, chars[0], chars[1], chars[2], chars[3]);
        }
    }

    private static int indexOfChar(byte[] array, int arrayLength, int fromIndex, char[] chars) {
        if (chars.length == 1) {
            return AMD64ArrayIndexOf.indexOf1Char(array, arrayLength, fromIndex, chars[0]);
        } else if (chars.length == 2) {
            return AMD64ArrayIndexOf.indexOf2Chars(array, arrayLength, fromIndex, chars[0], chars[1]);
        } else if (chars.length == 3) {
            return AMD64ArrayIndexOf.indexOf3Chars(array, arrayLength, fromIndex, chars[0], chars[1], chars[2]);
        } else {
            return AMD64ArrayIndexOf.indexOf4Chars(array, arrayLength, fromIndex, chars[0], chars[1], chars[2], chars[3]);
        }
    }

    private static int indexOfByte(byte[] array, int arrayLength, int fromIndex, byte[] bytes, int nValues) {
        if (nValues == 1) {
            return AMD64ArrayIndexOf.indexOf1Byte(array, arrayLength, fromIndex, bytes[0]);
        } else if (nValues == 2) {
            return AMD64ArrayIndexOf.indexOf2Bytes(array, arrayLength, fromIndex, bytes[0], bytes[1]);
        } else if (nValues == 3) {
            return AMD64ArrayIndexOf.indexOf3Bytes(array, arrayLength, fromIndex, bytes[0], bytes[1], bytes[2]);
        } else {
            return AMD64ArrayIndexOf.indexOf4Bytes(array, arrayLength, fromIndex, bytes[0], bytes[1], bytes[2], bytes[3]);
        }
    }

    private static int indexOfByte(byte[] array, int arrayLength, int fromIndex, int values, int nValues) {
        if (nValues == 1) {
            return AMD64ArrayIndexOf.indexOf1Byte(array, arrayLength, fromIndex, (byte) values);
        } else if (nValues == 2) {
            return AMD64ArrayIndexOf.indexOf2Bytes(array, arrayLength, fromIndex, (byte) values, (byte) (values >>> 8));
        } else if (nValues == 3) {
            return AMD64ArrayIndexOf.indexOf3Bytes(array, arrayLength, fromIndex, (byte) values, (byte) (values >>> 8), (byte) (values >>> 16));
        } else {
            return AMD64ArrayIndexOf.indexOf4Bytes(array, arrayLength, fromIndex, (byte) values, (byte) (values >>> 8), (byte) (values >>> 16), (byte) (values >>> 24));
        }
    }

    @MethodSubstitution(optional = true)
    private static boolean runRegionEquals(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length) {
        return ArrayRegionEqualsNode.regionEquals(bytePointer(a1, fromIndex1), bytePointer(a2, fromIndex2), length, JavaKind.Byte);
    }

    @MethodSubstitution(optional = true)
    private static boolean runRegionEquals(char[] a1, int fromIndex1, char[] a2, int fromIndex2, int length) {
        return ArrayRegionEqualsNode.regionEquals(charPointer(a1, fromIndex1), charPointer(a2, fromIndex2), length, JavaKind.Char);
    }

    @MethodSubstitution(optional = true)
    private static boolean runRegionEquals(String a1, int fromIndex1, String a2, int fromIndex2, int length) {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            boolean compact1 = JDK9StringSubstitutions.isCompactString(a1);
            if (compact1 != JDK9StringSubstitutions.isCompactString(a2)) {
                return ArrayRegionEqualsNode.regionEquals(
                                compact1 ? charPointer(a2, fromIndex2) : charPointer(a1, fromIndex1),
                                compact1 ? bytePointer(a1, fromIndex1) : bytePointer(a2, fromIndex2), length, JavaKind.Char, JavaKind.Byte);
            } else if (compact1) {
                return ArrayRegionEqualsNode.regionEquals(bytePointer(a1, fromIndex1), bytePointer(a2, fromIndex2), length, JavaKind.Byte, JavaKind.Byte);
            } else {
                return ArrayRegionEqualsNode.regionEquals(charPointer(a1, fromIndex1), charPointer(a2, fromIndex2), length, JavaKind.Char, JavaKind.Char);
            }
        } else {
            return ArrayRegionEqualsNode.regionEquals(charPointer(a1, fromIndex1), charPointer(a2, fromIndex2), length, JavaKind.Char);
        }
    }

    private static Word bytePointer(byte[] a, int fromIndex) {
        return pointer(Word.objectToTrackedPointer(a), fromIndex, JavaKind.Byte);
    }

    private static Word bytePointer(String s, int fromIndex) {
        return pointer(Word.objectToTrackedPointer(getValue(s)), fromIndex, JavaKind.Byte);
    }

    private static Word charPointer(char[] a, int fromIndex) {
        return pointer(Word.objectToTrackedPointer(a), fromIndex, JavaKind.Char);
    }

    private static Word charPointer(String s, int fromIndex) {
        return pointer(Word.objectToTrackedPointer(getValue(s)), fromIndex, JavaKind.Char);
    }

    private static Word pointer(Word p, int fromIndex, JavaKind kind) {
        return p.add(ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, kind)).add(fromIndex * ReplacementsUtil.arrayIndexScale(INJECTED_METAACCESS, kind));
    }

    private static Object getValue(String s) {
        return JavaVersionUtil.JAVA_SPEC > 8 ? JDK9StringSubstitutions.getValue(s) : StringSubstitutions.getValue(s);
    }
}
