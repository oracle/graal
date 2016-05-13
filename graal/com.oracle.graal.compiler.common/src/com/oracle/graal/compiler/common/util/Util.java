/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.util;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.debug.TTY;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The {@code Util} class contains a motley collection of utility methods used throughout the
 * compiler.
 */
public class Util {

    public static final int PRINTING_LINE_WIDTH = 40;
    public static final char SECTION_CHARACTER = '*';
    public static final char SUB_SECTION_CHARACTER = '=';
    public static final char SEPERATOR_CHARACTER = '-';

    public static <T> boolean replaceInList(T a, T b, List<T> list) {
        final int max = list.size();
        for (int i = 0; i < max; i++) {
            if (list.get(i) == a) {
                list.set(i, b);
                return true;
            }
        }
        return false;
    }

    /**
     * Statically cast an object to an arbitrary Object type. Dynamically checked.
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(@SuppressWarnings("unused") Class<T> type, Object object) {
        return (T) object;
    }

    /**
     * Statically cast an object to an arbitrary Object type. Dynamically checked.
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(Object object) {
        return (T) object;
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     *
     * @param hash the base hash
     * @param x the object to add to the hash
     * @return the combined hash
     */
    public static int hash1(int hash, Object x) {
        // always set at least one bit in case the hash wraps to zero
        return 0x10000000 | (hash + 7 * System.identityHashCode(x));
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     *
     * @param hash the base hash
     * @param x the first object to add to the hash
     * @param y the second object to add to the hash
     * @return the combined hash
     */
    public static int hash2(int hash, Object x, Object y) {
        // always set at least one bit in case the hash wraps to zero
        return 0x20000000 | (hash + 7 * System.identityHashCode(x) + 11 * System.identityHashCode(y));
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     *
     * @param hash the base hash
     * @param x the first object to add to the hash
     * @param y the second object to add to the hash
     * @param z the third object to add to the hash
     * @return the combined hash
     */
    public static int hash3(int hash, Object x, Object y, Object z) {
        // always set at least one bit in case the hash wraps to zero
        return 0x30000000 | (hash + 7 * System.identityHashCode(x) + 11 * System.identityHashCode(y) + 13 * System.identityHashCode(z));
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     *
     * @param hash the base hash
     * @param x the first object to add to the hash
     * @param y the second object to add to the hash
     * @param z the third object to add to the hash
     * @param w the fourth object to add to the hash
     * @return the combined hash
     */
    public static int hash4(int hash, Object x, Object y, Object z, Object w) {
        // always set at least one bit in case the hash wraps to zero
        return 0x40000000 | (hash + 7 * System.identityHashCode(x) + 11 * System.identityHashCode(y) + 13 * System.identityHashCode(z) + 17 * System.identityHashCode(w));
    }

    static {
        assert CodeUtil.log2(2) == 1;
        assert CodeUtil.log2(4) == 2;
        assert CodeUtil.log2(8) == 3;
        assert CodeUtil.log2(16) == 4;
        assert CodeUtil.log2(32) == 5;
        assert CodeUtil.log2(0x40000000) == 30;

        assert CodeUtil.log2(2L) == 1;
        assert CodeUtil.log2(4L) == 2;
        assert CodeUtil.log2(8L) == 3;
        assert CodeUtil.log2(16L) == 4;
        assert CodeUtil.log2(32L) == 5;
        assert CodeUtil.log2(0x4000000000000000L) == 62;

        assert !CodeUtil.isPowerOf2(3);
        assert !CodeUtil.isPowerOf2(5);
        assert !CodeUtil.isPowerOf2(7);
        assert !CodeUtil.isPowerOf2(-1);

        assert CodeUtil.isPowerOf2(2);
        assert CodeUtil.isPowerOf2(4);
        assert CodeUtil.isPowerOf2(8);
        assert CodeUtil.isPowerOf2(16);
        assert CodeUtil.isPowerOf2(32);
        assert CodeUtil.isPowerOf2(64);
    }

    public interface Stringify {
        String apply(Object o);
    }

    public static String join(Collection<?> c, String sep) {
        return join(c, sep, "", "", null);
    }

    public static String join(Collection<?> c, String sep, String prefix, String suffix, Stringify stringify) {
        StringBuilder buf = new StringBuilder(prefix);
        boolean first = true;
        for (Object e : c) {
            if (!first) {
                buf.append(sep);
            } else {
                first = false;
            }
            buf.append(stringify != null ? stringify.apply(e) : String.valueOf(e));
        }
        buf.append(suffix);
        return buf.toString();
    }

    /**
     * Sets the element at a given position of a list and ensures that this position exists. If the
     * list is current shorter than the position, intermediate positions are filled with a given
     * value.
     *
     * @param list the list to put the element into
     * @param pos the position at which to insert the element
     * @param x the element that should be inserted
     * @param filler the filler element that is used for the intermediate positions in case the list
     *            is shorter than pos
     */
    public static <T> void atPutGrow(List<T> list, int pos, T x, T filler) {
        if (list.size() < pos + 1) {
            while (list.size() < pos + 1) {
                list.add(filler);
            }
            assert list.size() == pos + 1;
        }

        assert list.size() >= pos + 1;
        list.set(pos, x);
    }

    public static void breakpoint() {
        // do nothing.
    }

    public static void guarantee(boolean b, String string) {
        if (!b) {
            throw new BailoutException(string);
        }
    }

    public static void warning(String string) {
        TTY.println("WARNING: " + string);
    }

    public static int safeToInt(long l) {
        assert (int) l == l;
        return (int) l;
    }

    public static int roundUp(int number, int mod) {
        return ((number + mod - 1) / mod) * mod;
    }

    public static void printSection(String name, char sectionCharacter) {

        String header = " " + name + " ";
        int remainingCharacters = PRINTING_LINE_WIDTH - header.length();
        int leftPart = remainingCharacters / 2;
        int rightPart = remainingCharacters - leftPart;
        for (int i = 0; i < leftPart; i++) {
            TTY.print(sectionCharacter);
        }

        TTY.print(header);

        for (int i = 0; i < rightPart; i++) {
            TTY.print(sectionCharacter);
        }

        TTY.println();
    }

    /**
     * Prints entries in a byte array as space separated hex values to {@link TTY}.
     *
     * @param address an address at which the bytes are located. This is used to print an address
     *            prefix per line of output.
     * @param array the array containing all the bytes to print
     * @param bytesPerLine the number of values to print per line of output
     */
    public static void printBytes(long address, byte[] array, int bytesPerLine) {
        printBytes(address, array, 0, array.length, bytesPerLine);
    }

    /**
     * Prints entries in a byte array as space separated hex values to {@link TTY}.
     *
     * @param address an address at which the bytes are located. This is used to print an address
     *            prefix per line of output.
     * @param array the array containing the bytes to print
     * @param offset the offset in {@code array} of the values to print
     * @param length the number of values from {@code array} print
     * @param bytesPerLine the number of values to print per line of output
     */
    public static void printBytes(long address, byte[] array, int offset, int length, int bytesPerLine) {
        assert bytesPerLine > 0;
        boolean newLine = true;
        for (int i = 0; i < length; i++) {
            if (newLine) {
                TTY.printf("%08x: ", address + i);
                newLine = false;
            }
            TTY.printf("%02x ", array[i]);
            if (i % bytesPerLine == bytesPerLine - 1) {
                TTY.println();
                newLine = true;
            }
        }

        if (length % bytesPerLine != bytesPerLine) {
            TTY.println();
        }
    }

    public static boolean isShiftCount(int x) {
        return 0 <= x && x < 32;
    }

    /**
     * Determines if a given {@code int} value is the range of unsigned byte values.
     */
    public static boolean isUByte(int x) {
        return (x & 0xff) == x;
    }

    /**
     * Determines if a given {@code int} value is the range of signed byte values.
     */
    public static boolean isByte(int x) {
        return (byte) x == x;
    }

    /**
     * Determines if a given {@code long} value is the range of unsigned byte values.
     */
    public static boolean isUByte(long x) {
        return (x & 0xffL) == x;
    }

    /**
     * Determines if a given {@code long} value is the range of signed byte values.
     */
    public static boolean isByte(long l) {
        return (byte) l == l;
    }

    /**
     * Determines if a given {@code long} value is the range of unsigned int values.
     */
    public static boolean isUInt(long x) {
        return (x & 0xffffffffL) == x;
    }

    /**
     * Determines if a given {@code long} value is the range of signed int values.
     */
    public static boolean isInt(long l) {
        return (int) l == l;
    }

    /**
     * Determines if a given {@code int} value is the range of signed short values.
     */
    public static boolean isShort(int x) {
        return (short) x == x;
    }

    public static boolean is32bit(long x) {
        return -0x80000000L <= x && x < 0x80000000L;
    }

    public static short safeToShort(int v) {
        assert isShort(v);
        return (short) v;
    }

    /**
     * Creates an array of integers of length "size", in which each number from 0 to (size - 1)
     * occurs exactly once. The integers are sorted using the given comparator. This can be used to
     * create a sorting for arrays that cannot be modified directly.
     *
     * @param size The size of the range to be sorted.
     * @param comparator A comparator that is used to compare indexes.
     * @return An array of integers that contains each number from 0 to (size - 1) exactly once,
     *         sorted using the comparator.
     */
    public static Integer[] createSortedPermutation(int size, Comparator<Integer> comparator) {
        Integer[] indexes = new Integer[size];
        for (int i = 0; i < size; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, comparator);
        return indexes;
    }

    /**
     * Prepends the String {@code indentation} to every line in String {@code lines}, including a
     * possibly non-empty line following the final newline.
     */
    public static String indent(String lines, String indentation) {
        if (lines.length() == 0) {
            return lines;
        }
        final String newLine = "\n";
        if (lines.endsWith(newLine)) {
            return indentation + (lines.substring(0, lines.length() - 1)).replace(newLine, newLine + indentation) + newLine;
        }
        return indentation + lines.replace(newLine, newLine + indentation);
    }

    /**
     * Turns an class name in internal format into a resolved Java type.
     */
    public static ResolvedJavaType classForName(String internal, MetaAccessProvider metaAccess, ClassLoader cl) {
        JavaKind k = JavaKind.fromTypeString(internal);
        try {
            String n = MetaUtil.internalNameToJava(internal, true, true);
            return metaAccess.lookupJavaType(k.isPrimitive() ? k.toJavaClass() : Class.forName(n, true, cl));
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException("could not instantiate class described by " + internal, cnfe);
        }
    }

    /**
     * Calls {@link JavaType#resolve(ResolvedJavaType)} on an array of types.
     */
    public static ResolvedJavaType[] resolveJavaTypes(JavaType[] types, ResolvedJavaType accessingClass) {
        ResolvedJavaType[] result = new ResolvedJavaType[types.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = types[i].resolve(accessingClass);
        }
        return result;
    }

    private static class ClassInfo {
        public long totalSize;
        public long instanceCount;

        @Override
        public String toString() {
            return "totalSize=" + totalSize + ", instanceCount=" + instanceCount;
        }
    }

    /**
     * Returns the number of bytes occupied by this constant value or constant object and
     * recursively all values reachable from this value.
     *
     * @param constant the constant whose bytes should be measured
     * @param printTopN print total size and instance count of the top n classes is desired
     * @return the number of bytes occupied by this constant
     */
    public static long getMemorySizeRecursive(MetaAccessProvider access, ConstantReflectionProvider constantReflection, JavaConstant constant, PrintStream out, int printTopN) {
        Set<JavaConstant> marked = new HashSet<>();
        Deque<JavaConstant> stack = new ArrayDeque<>();
        if (constant.getJavaKind() == JavaKind.Object && constant.isNonNull()) {
            marked.add(constant);
        }
        final HashMap<ResolvedJavaType, ClassInfo> histogram = new HashMap<>();
        stack.push(constant);
        long sum = 0;
        while (!stack.isEmpty()) {
            JavaConstant c = stack.pop();
            long memorySize = access.getMemorySize(constant);
            sum += memorySize;
            if (c.getJavaKind() == JavaKind.Object && c.isNonNull()) {
                ResolvedJavaType clazz = access.lookupJavaType(c);
                if (!histogram.containsKey(clazz)) {
                    histogram.put(clazz, new ClassInfo());
                }
                ClassInfo info = histogram.get(clazz);
                info.instanceCount++;
                info.totalSize += memorySize;
                ResolvedJavaType type = access.lookupJavaType(c);
                if (type.isArray()) {
                    if (!type.getComponentType().isPrimitive()) {
                        int length = constantReflection.readArrayLength(c);
                        for (int i = 0; i < length; i++) {
                            JavaConstant value = constantReflection.readArrayElement(c, i);
                            pushConstant(marked, stack, value);
                        }
                    }
                } else {
                    ResolvedJavaField[] instanceFields = type.getInstanceFields(true);
                    for (ResolvedJavaField f : instanceFields) {
                        if (f.getJavaKind() == JavaKind.Object) {
                            JavaConstant value = constantReflection.readFieldValue(f, c);
                            pushConstant(marked, stack, value);
                        }
                    }
                }
            }
        }
        ArrayList<ResolvedJavaType> clazzes = new ArrayList<>();
        clazzes.addAll(histogram.keySet());
        Collections.sort(clazzes, new Comparator<ResolvedJavaType>() {

            @Override
            public int compare(ResolvedJavaType o1, ResolvedJavaType o2) {
                long l1 = histogram.get(o1).totalSize;
                long l2 = histogram.get(o2).totalSize;
                if (l1 > l2) {
                    return -1;
                } else if (l1 == l2) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        int z = 0;
        for (ResolvedJavaType c : clazzes) {
            if (z > printTopN) {
                break;
            }
            out.println("Class " + c + ", " + histogram.get(c));
            ++z;
        }

        return sum;
    }

    private static void pushConstant(Set<JavaConstant> marked, Deque<JavaConstant> stack, JavaConstant value) {
        if (value.isNonNull()) {
            if (!marked.contains(value)) {
                marked.add(value);
                stack.push(value);
            }
        }
    }

    /**
     * Returns the zero value for a given numeric kind.
     */
    public static JavaConstant zero(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return JavaConstant.FALSE;
            case Byte:
                return JavaConstant.forByte((byte) 0);
            case Char:
                return JavaConstant.forChar((char) 0);
            case Double:
                return JavaConstant.DOUBLE_0;
            case Float:
                return JavaConstant.FLOAT_0;
            case Int:
                return JavaConstant.INT_0;
            case Long:
                return JavaConstant.LONG_0;
            case Short:
                return JavaConstant.forShort((short) 0);
            default:
                throw new IllegalArgumentException(kind.toString());
        }
    }

    /**
     * Returns the one value for a given numeric kind.
     */
    public static JavaConstant one(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return JavaConstant.TRUE;
            case Byte:
                return JavaConstant.forByte((byte) 1);
            case Char:
                return JavaConstant.forChar((char) 1);
            case Double:
                return JavaConstant.DOUBLE_1;
            case Float:
                return JavaConstant.FLOAT_1;
            case Int:
                return JavaConstant.INT_1;
            case Long:
                return JavaConstant.LONG_1;
            case Short:
                return JavaConstant.forShort((short) 1);
            default:
                throw new IllegalArgumentException(kind.toString());
        }
    }

    /**
     * Adds two numeric constants.
     */
    public static JavaConstant add(JavaConstant x, JavaConstant y) {
        assert x.getJavaKind() == y.getJavaKind();
        switch (x.getJavaKind()) {
            case Byte:
                return JavaConstant.forByte((byte) (x.asInt() + y.asInt()));
            case Char:
                return JavaConstant.forChar((char) (x.asInt() + y.asInt()));
            case Double:
                return JavaConstant.forDouble(x.asDouble() + y.asDouble());
            case Float:
                return JavaConstant.forFloat(x.asFloat() + y.asFloat());
            case Int:
                return JavaConstant.forInt(x.asInt() + y.asInt());
            case Long:
                return JavaConstant.forLong(x.asLong() + y.asLong());
            case Short:
                return JavaConstant.forShort((short) (x.asInt() + y.asInt()));
            default:
                throw new IllegalArgumentException(x.getJavaKind().toString());
        }
    }

    /**
     * Multiplies two numeric constants.
     */
    public static PrimitiveConstant mul(JavaConstant x, JavaConstant y) {
        assert x.getJavaKind() == y.getJavaKind();
        switch (x.getJavaKind()) {
            case Byte:
                return JavaConstant.forByte((byte) (x.asInt() * y.asInt()));
            case Char:
                return JavaConstant.forChar((char) (x.asInt() * y.asInt()));
            case Double:
                return JavaConstant.forDouble(x.asDouble() * y.asDouble());
            case Float:
                return JavaConstant.forFloat(x.asFloat() * y.asFloat());
            case Int:
                return JavaConstant.forInt(x.asInt() * y.asInt());
            case Long:
                return JavaConstant.forLong(x.asLong() * y.asLong());
            case Short:
                return JavaConstant.forShort((short) (x.asInt() * y.asInt()));
            default:
                throw new IllegalArgumentException(x.getJavaKind().toString());
        }
    }
}
