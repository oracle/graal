/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.replacements.ArrayIndexOf;
import org.graalvm.compiler.replacements.JDK9StringSubstitutions;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOfWithMaskNode;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayRegionEqualsWithMaskNode;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.truffle.compiler.substitutions.ArrayUtilsSubstitutions;

import jdk.vm.ci.meta.JavaKind;

@ClassSubstitution(className = "com.oracle.truffle.api.ArrayUtils", optional = true)
public class AMD64ArrayUtilsSubstitutions extends ArrayUtilsSubstitutions {

    @MethodSubstitution
    public static int runIndexOfWithOrMask(byte[] haystack, int fromIndex, int maxIndex, byte needle, byte mask) {
        if (mask == 0) {
            return ArrayIndexOf.indexOf1Byte(haystack, maxIndex, fromIndex, needle);
        }
        return AMD64ArrayIndexOfWithMaskNode.indexOfWithMask(haystack, maxIndex, fromIndex, needle, mask);
    }

    @MethodSubstitution
    public static int runIndexOfWithOrMask(char[] haystack, int fromIndex, int maxIndex, char needle, char mask) {
        if (mask == 0) {
            return ArrayIndexOf.indexOf1Char(haystack, maxIndex, fromIndex, needle);
        }
        return AMD64ArrayIndexOfWithMaskNode.indexOfWithMask(haystack, maxIndex, fromIndex, needle, mask);
    }

    @MethodSubstitution
    public static int runIndexOfWithOrMask(String haystack, int fromIndex, int maxIndex, char needle, char mask) {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            byte[] value = JDK9StringSubstitutions.getValue(haystack);
            if (mask == 0) {
                if (JDK9StringSubstitutions.isCompactString(haystack)) {
                    return needle <= 0xff ? ArrayIndexOf.indexOf1Byte(value, maxIndex, fromIndex, (byte) needle) : -1;
                }
                return ArrayIndexOf.indexOf1CharCompact(value, maxIndex, fromIndex, needle);
            }
            if (JDK9StringSubstitutions.isCompactString(haystack)) {
                return (needle ^ mask) <= 0xff ? AMD64ArrayIndexOfWithMaskNode.indexOfWithMask(value, maxIndex, fromIndex, (byte) needle, (byte) mask) : -1;
            }
            return AMD64ArrayIndexOfWithMaskNode.indexOfWithMask(value, maxIndex, fromIndex, needle, mask);
        } else {
            if (mask == 0) {
                return ArrayIndexOf.indexOf1Char(StringSubstitutions.getValue(haystack), maxIndex, fromIndex, needle);
            }
            return AMD64ArrayIndexOfWithMaskNode.indexOfWithMask(StringSubstitutions.getValue(haystack), maxIndex, fromIndex, needle, mask);
        }
    }

    @MethodSubstitution
    public static int runIndexOf2ConsecutiveWithOrMask(byte[] haystack, int fromIndex, int maxIndex, byte c1, byte c2, byte mask1, byte mask2) {
        int mask = (Byte.toUnsignedInt(mask2) << 8) | Byte.toUnsignedInt(mask1);
        if (mask == 0) {
            return ArrayIndexOf.indexOfTwoConsecutiveBytes(haystack, maxIndex, fromIndex, c1, c2);
        }
        return AMD64ArrayIndexOfWithMaskNode.indexOf2ConsecutiveBytesWithMask(haystack, maxIndex, fromIndex, (Byte.toUnsignedInt(c2) << 8) | Byte.toUnsignedInt(c1), mask);
    }

    @MethodSubstitution
    public static int runIndexOf2ConsecutiveWithOrMask(char[] haystack, int fromIndex, int maxIndex, char c1, char c2, char mask1, char mask2) {
        int mask = (mask2 << 16) | mask1;
        if (mask == 0) {
            return ArrayIndexOf.indexOfTwoConsecutiveChars(haystack, maxIndex, fromIndex, c1, c2);
        }
        return AMD64ArrayIndexOfWithMaskNode.indexOf2ConsecutiveCharsWithMask(haystack, maxIndex, fromIndex, (c2 << 16) | c1, mask);
    }

    @MethodSubstitution
    public static int runIndexOf2ConsecutiveWithOrMask(String haystack, int fromIndex, int maxIndex, char c1, char c2, char mask1, char mask2) {
        int mask = (mask2 << 16) | mask1;
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            byte[] value = JDK9StringSubstitutions.getValue(haystack);
            if (mask == 0) {
                if (JDK9StringSubstitutions.isCompactString(haystack)) {
                    return c1 <= 0xff && c2 <= 0xff ? ArrayIndexOf.indexOfTwoConsecutiveBytes(value, maxIndex, fromIndex, (byte) c1, (byte) c2) : -1;
                }
                return ArrayIndexOf.indexOfTwoConsecutiveChars(value, maxIndex, fromIndex, c1, c2);
            }
            if (JDK9StringSubstitutions.isCompactString(haystack)) {
                if ((c1 ^ mask1) <= 0xff && (c2 ^ mask2) <= 0xff) {
                    return AMD64ArrayIndexOfWithMaskNode.indexOf2ConsecutiveBytesWithMask(value, maxIndex, fromIndex, ((c2 & 0xff) << 8) | (c1 & 0xff),
                                    ((mask2 & 0xff) << 8) | (mask1 & 0xff));
                } else {
                    return -1;
                }
            }
            return AMD64ArrayIndexOfWithMaskNode.indexOf2ConsecutiveCharsWithMask(value, maxIndex, fromIndex, (c2 << 16) | c1, mask);
        } else {
            if (mask == 0) {
                return ArrayIndexOf.indexOfTwoConsecutiveChars(StringSubstitutions.getValue(haystack), maxIndex, fromIndex, c1, c2);
            }
            return AMD64ArrayIndexOfWithMaskNode.indexOf2ConsecutiveCharsWithMask(StringSubstitutions.getValue(haystack), maxIndex, fromIndex, (c2 << 16) | c1, mask);
        }
    }

    @MethodSubstitution
    public static boolean runRegionEqualsWithOrMask(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, byte[] mask) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(a1, fromIndex1, a2, fromIndex2, mask);
    }

    @MethodSubstitution
    public static boolean runRegionEqualsWithOrMask(char[] a1, int fromIndex1, char[] a2, int fromIndex2, char[] mask) {
        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(a1, fromIndex1, a2, fromIndex2, mask);
    }

    @MethodSubstitution
    public static boolean runRegionEqualsWithOrMask(String a1, int fromIndex1, String a2, int fromIndex2, String mask) {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            byte[] valueA1 = JDK9StringSubstitutions.getValue(a1);
            byte[] valueA2 = JDK9StringSubstitutions.getValue(a2);
            byte[] valueMask = JDK9StringSubstitutions.getValue(mask);
            boolean compact1 = JDK9StringSubstitutions.isCompactString(a1);
            boolean compact2 = JDK9StringSubstitutions.isCompactString(a2);
            boolean compactMask = JDK9StringSubstitutions.isCompactString(mask);
            if (compact2) {
                if (compactMask) {
                    if (compact1) {
                        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(valueA1, fromIndex1, valueA2, fromIndex2, valueMask, mask.length(), JavaKind.Byte, JavaKind.Byte, JavaKind.Byte);
                    } else {
                        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(valueA1, fromIndex1, valueA2, fromIndex2, valueMask, mask.length(), JavaKind.Char, JavaKind.Byte, JavaKind.Byte);
                    }
                } else {
                    return false;
                }
            } else {
                if (compactMask) {
                    if (compact1) {
                        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(valueA1, fromIndex1, valueA2, fromIndex2, valueMask, mask.length(), JavaKind.Byte, JavaKind.Char, JavaKind.Byte);
                    } else {
                        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(valueA1, fromIndex1, valueA2, fromIndex2, valueMask, mask.length(), JavaKind.Char, JavaKind.Char, JavaKind.Byte);
                    }
                } else {
                    if (compact1) {
                        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(valueA1, fromIndex1, valueA2, fromIndex2, valueMask, mask.length(), JavaKind.Byte, JavaKind.Char, JavaKind.Char);
                    } else {
                        return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(valueA1, fromIndex1, valueA2, fromIndex2, valueMask, mask.length(), JavaKind.Char, JavaKind.Char, JavaKind.Char);
                    }
                }
            }
        } else {
            return AMD64ArrayRegionEqualsWithMaskNode.regionEquals(StringSubstitutions.getValue(a1), fromIndex1, StringSubstitutions.getValue(a2), fromIndex2, StringSubstitutions.getValue(mask));
        }
    }
}
