/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.replacements.JDK9StringSubstitutions;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOf;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

@ClassSubstitution(className = "com.oracle.truffle.api.ArrayUtils", optional = true)
public class AMD64ArrayUtilsSubstitutions {

    @Fold
    static int byteArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Byte);
    }

    @Fold
    static int byteArrayIndexScale(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayIndexScale(JavaKind.Byte);
    }

    @Fold
    static int charArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Char);
    }

    @Fold
    static int charArrayIndexScale(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayIndexScale(JavaKind.Char);
    }

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    static final MetaAccessProvider INJECTED = null;

    @MethodSubstitution(optional = true)
    public static int runIndexOf(String str, int fromIndex, int maxIndex, char... chars) {
        if (fromIndex >= str.length()) {
            return -1;
        }
        if (chars.length <= 4) {
            int arrayLength = maxIndex - fromIndex;
            int result;
            if (JavaVersionUtil.JAVA_SPEC <= 8) {
                char[] sourceArray = StringSubstitutions.getValue(str);
                result = indexOfChar(arrayToPointer(sourceArray, fromIndex), arrayLength, chars);
            } else {
                byte[] sourceArray = JDK9StringSubstitutions.getValue(str);
                if (JDK9StringSubstitutions.isCompactString(str)) {
                    result = indexOfByte(arrayToPointer(sourceArray, fromIndex), arrayLength, chars);
                } else {
                    Pointer pointer = Word.objectToTrackedPointer(sourceArray).add(byteArrayBaseOffset(INJECTED)).add(fromIndex * byteArrayIndexScale(INJECTED) * 2);
                    result = indexOfChar(pointer, arrayLength, chars);
                }
            }
            if (result >= 0) {
                return result + fromIndex;
            }
            return result;
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
            int arrayLength = maxIndex - fromIndex;
            int result = indexOfChar(arrayToPointer(array, fromIndex), arrayLength, chars);
            if (result >= 0) {
                return result + fromIndex;
            }
            return result;
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
            int arrayLength = maxIndex - fromIndex;
            int result = indexOfByte(arrayToPointer(array, fromIndex), arrayLength, bytes);
            if (result >= 0) {
                return result + fromIndex;
            }
            return result;
        } else {
            return runIndexOf(array, fromIndex, maxIndex, bytes);
        }
    }

    private static Word arrayToPointer(byte[] array, int fromIndex) {
        return Word.objectToTrackedPointer(array).add(byteArrayBaseOffset(INJECTED)).add(fromIndex * byteArrayIndexScale(INJECTED));
    }

    private static Word arrayToPointer(char[] array, int fromIndex) {
        return Word.objectToTrackedPointer(array).add(charArrayBaseOffset(INJECTED)).add(fromIndex * charArrayIndexScale(INJECTED));
    }

    private static int indexOfChar(Pointer arrayPointer, int arrayLength, char[] chars) {
        if (chars.length == 1) {
            return AMD64ArrayIndexOf.indexOf1Char(arrayPointer, arrayLength, chars[0]);
        } else if (chars.length == 2) {
            return AMD64ArrayIndexOf.indexOf2Chars(arrayPointer, arrayLength, chars[0], chars[1]);
        } else if (chars.length == 3) {
            return AMD64ArrayIndexOf.indexOf3Chars(arrayPointer, arrayLength, chars[0], chars[1], chars[2]);
        } else {
            return AMD64ArrayIndexOf.indexOf4Chars(arrayPointer, arrayLength, chars[0], chars[1], chars[2], chars[3]);
        }
    }

    private static int indexOfByte(Pointer arrayPointer, int arrayLength, byte[] bytes) {
        if (bytes.length == 1) {
            return AMD64ArrayIndexOf.indexOf1Byte(arrayPointer, arrayLength, bytes[0]);
        } else if (bytes.length == 2) {
            return AMD64ArrayIndexOf.indexOf2Bytes(arrayPointer, arrayLength, bytes[0], bytes[1]);
        } else if (bytes.length == 3) {
            return AMD64ArrayIndexOf.indexOf3Bytes(arrayPointer, arrayLength, bytes[0], bytes[1], bytes[2]);
        } else {
            return AMD64ArrayIndexOf.indexOf4Bytes(arrayPointer, arrayLength, bytes[0], bytes[1], bytes[2], bytes[3]);
        }
    }

    private static int indexOfByte(Pointer arrayPointer, int arrayLength, char[] bytes) {
        if (bytes.length == 1) {
            return AMD64ArrayIndexOf.indexOf1Byte(arrayPointer, arrayLength, (byte) bytes[0]);
        } else if (bytes.length == 2) {
            return AMD64ArrayIndexOf.indexOf2Bytes(arrayPointer, arrayLength, (byte) bytes[0], (byte) bytes[1]);
        } else if (bytes.length == 3) {
            return AMD64ArrayIndexOf.indexOf3Bytes(arrayPointer, arrayLength, (byte) bytes[0], (byte) bytes[1], (byte) bytes[2]);
        } else {
            return AMD64ArrayIndexOf.indexOf4Bytes(arrayPointer, arrayLength, (byte) bytes[0], (byte) bytes[1], (byte) bytes[2], (byte) bytes[3]);
        }
    }
}
