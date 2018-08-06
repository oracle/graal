/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.substitutions.amd64;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.replacements.JDK9StringSubstitutions;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import static org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import static org.graalvm.compiler.serviceprovider.GraalServices.Java8OrEarlier;

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
        if (chars.length <= 4) {
            int arrayLength = maxIndex - fromIndex;
            int result;
            if (Java8OrEarlier) {
                char[] sourceArray = StringSubstitutions.getValue(str);
                result = optimizedIndexOf(JavaKind.Char, arrayToPointer(sourceArray, fromIndex), arrayLength, chars);
            } else {
                byte[] sourceArray = JDK9StringSubstitutions.getValue(str);
                if (JDK9StringSubstitutions.getCoder(str) == 0) {
                    // compact string
                    result = optimizedIndexOf(JavaKind.Byte, arrayToPointer(sourceArray, fromIndex), arrayLength, chars);
                } else {
                    Pointer pointer = Word.objectToTrackedPointer(sourceArray).add(byteArrayBaseOffset(INJECTED)).add(fromIndex * byteArrayIndexScale(INJECTED) * 2);
                    result = optimizedIndexOf(JavaKind.Char, pointer, arrayLength, chars);
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
        if (chars.length <= 4) {
            int result = optimizedIndexOf(JavaKind.Char, arrayToPointer(array, fromIndex), maxIndex - fromIndex, chars);
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
        if (bytes.length <= 4) {
            int result = optimizedIndexOf(arrayToPointer(array, fromIndex), maxIndex - fromIndex, bytes);
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

    private static int optimizedIndexOf(JavaKind kind, Pointer arrayPointer, int arrayLength, char[] chars) {
        if (chars.length == 1) {
            return AMD64ArrayIndexOfNCharsNode.optimizedArrayIndexOf(kind, arrayPointer, arrayLength, chars[0]);
        } else if (chars.length == 2) {
            return AMD64ArrayIndexOfNCharsNode.optimizedArrayIndexOf(kind, arrayPointer, arrayLength, chars[0], chars[1]);
        } else if (chars.length == 3) {
            return AMD64ArrayIndexOfNCharsNode.optimizedArrayIndexOf(kind, arrayPointer, arrayLength, chars[0], chars[1], chars[2]);
        } else {
            return AMD64ArrayIndexOfNCharsNode.optimizedArrayIndexOf(kind, arrayPointer, arrayLength, chars[0], chars[1], chars[2], chars[3]);
        }
    }

    private static int optimizedIndexOf(Pointer arrayPointer, int arrayLength, byte[] chars) {
        if (chars.length == 1) {
            return AMD64ArrayIndexOfNCharsNode.optimizedArrayIndexOf(JavaKind.Byte, arrayPointer, arrayLength, chars[0]);
        } else if (chars.length == 2) {
            return AMD64ArrayIndexOfNCharsNode.optimizedArrayIndexOf(JavaKind.Byte, arrayPointer, arrayLength, chars[0], chars[1]);
        } else if (chars.length == 3) {
            return AMD64ArrayIndexOfNCharsNode.optimizedArrayIndexOf(JavaKind.Byte, arrayPointer, arrayLength, chars[0], chars[1], chars[2]);
        } else {
            return AMD64ArrayIndexOfNCharsNode.optimizedArrayIndexOf(JavaKind.Byte, arrayPointer, arrayLength, chars[0], chars[1], chars[2], chars[3]);
        }
    }
}
