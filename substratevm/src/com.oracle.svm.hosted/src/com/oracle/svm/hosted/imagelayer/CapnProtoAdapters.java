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
package com.oracle.svm.hosted.imagelayer;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.PrimitiveArray;
import com.oracle.svm.shaded.org.capnproto.PrimitiveList;
import com.oracle.svm.shaded.org.capnproto.StructList;
import com.oracle.svm.shaded.org.capnproto.StructReader;
import com.oracle.svm.shaded.org.capnproto.TextList;

/**
 * Collection of adapters to interact with the Cap'n Proto internal value representation.
 */
public class CapnProtoAdapters {
    /**
     * Iterate values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Int} reader
     * and pass them to the action.
     */
    static void forEach(PrimitiveList.Int.Reader reader, IntConsumer action) {
        for (int i = 0; i < reader.size(); i++) {
            action.accept(reader.get(i));
        }
    }

    /**
     * Iterate values from a {@link com.oracle.svm.shaded.org.capnproto.TextList} reader and pass
     * them to the action.
     */
    public static void forEach(TextList.Reader reader, Consumer<String> action) {
        for (int i = 0; i < reader.size(); i++) {
            action.accept(reader.get(i).toString());
        }
    }

    /**
     * Iterate values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Int} reader,
     * apply the mapping function, then store them in the supplied array at the same index.
     */
    static <T> T[] toArray(PrimitiveList.Int.Reader reader, IntFunction<? extends T> mapper, IntFunction<T[]> arrayGenerator) {
        T[] array = arrayGenerator.apply(reader.size());
        for (int i = 0; i < reader.size(); i++) {
            array[i] = mapper.apply(reader.get(i));
        }
        return array;
    }

    /**
     * Iterate values from a {@link com.oracle.svm.shaded.org.capnproto.StructList} reader, apply
     * the mapping function, then store them in the supplied array at the same index.
     */
    static <R, T> T[] toArray(StructList.Reader<R> reader, Function<? super R, ? extends T> mapper, IntFunction<T[]> arrayGenerator) {
        T[] array = arrayGenerator.apply(reader.size());
        for (int i = 0; i < reader.size(); i++) {
            array[i] = mapper.apply(reader.get(i));
        }
        return array;
    }

    /**
     * Iterate values from a {@link com.oracle.svm.shaded.org.capnproto.TextList} reader, convert
     * them to {@link String}, apply the mapping function, then store them in the supplied array at
     * the same index.
     */
    static <T> T[] toArray(TextList.Reader reader, Function<String, ? extends T> mapper, IntFunction<T[]> arrayGenerator) {
        T[] array = arrayGenerator.apply(reader.size());
        for (int i = 0; i < reader.size(); i++) {
            array[i] = mapper.apply(reader.get(i).toString());
        }
        return array;
    }

    /**
     * Iterate values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Int} reader,
     * apply the mapping function, then collect them in the supplied collection.
     */
    public static <T, U extends Collection<T>> U toCollection(PrimitiveList.Int.Reader reader, IntFunction<? extends T> mapper, Supplier<U> collectionFactory) {
        U collection = collectionFactory.get();
        for (int i = 0; i < reader.size(); i++) {
            collection.add(mapper.apply(reader.get(i)));
        }
        return collection;
    }

    /**
     * Iterate values from a {@link com.oracle.svm.shaded.org.capnproto.TextList} reader, convert
     * them to {@link String}, then collect them in the supplied collection.
     */
    public static <U extends Collection<String>> U toCollection(TextList.Reader reader, Supplier<U> collectionFactory) {
        return toCollection(reader, (s) -> s, collectionFactory);
    }

    /**
     * Iterate values from a {@link com.oracle.svm.shaded.org.capnproto.TextList} reader, convert
     * them to {@link String}, apply the mapping function, then collect them in the supplied
     * collection.
     */
    public static <T, U extends Collection<T>> U toCollection(TextList.Reader reader, Function<String, ? extends T> mapper, Supplier<U> collectionFactory) {
        U collection = collectionFactory.get();
        for (int i = 0; i < reader.size(); i++) {
            collection.add(mapper.apply(reader.get(i).toString()));
        }
        return collection;
    }

    /**
     * Extract values from a {@link PrimitiveArray} reader to the corresponding Java primitive
     * array.
     */
    static Object toArray(PrimitiveArray.Reader reader) {
        return switch (reader.which()) {
            case Z -> toBooleanArray(reader.getZ());
            case B -> toByteArray(reader.getB());
            case S -> toShortArray(reader.getS());
            case C -> toCharArray(reader.getC());
            case I -> toIntArray(reader.getI());
            case F -> toFloatArray(reader.getF());
            case J -> toLongArray(reader.getJ());
            case D -> toDoubleArray(reader.getD());
            case _NOT_IN_SCHEMA -> throw new IllegalArgumentException("Unsupported kind: " + reader.which());
        };
    }

    /**
     * Extract values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Boolean}
     * reader to a {@code boolean[]} array.
     */
    protected static boolean[] toBooleanArray(PrimitiveList.Boolean.Reader booleanReader) {
        boolean[] booleanArray = new boolean[booleanReader.size()];
        for (int i = 0; i < booleanReader.size(); i++) {
            booleanArray[i] = booleanReader.get(i);
        }
        return booleanArray;
    }

    /**
     * Extract values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Byte} reader
     * to a {@code byte[]} array.
     */
    static byte[] toByteArray(PrimitiveList.Byte.Reader byteReader) {
        byte[] byteArray = new byte[byteReader.size()];
        for (int i = 0; i < byteReader.size(); i++) {
            byteArray[i] = byteReader.get(i);
        }
        return byteArray;
    }

    /**
     * Extract values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Short} reader
     * to a {@code short[]} array.
     */
    static short[] toShortArray(PrimitiveList.Short.Reader shortReader) {
        short[] shortArray = new short[shortReader.size()];
        for (int i = 0; i < shortReader.size(); i++) {
            shortArray[i] = shortReader.get(i);
        }
        return shortArray;
    }

    /**
     * Extract values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Short} reader
     * to a {@code char[]} array.
     */
    static char[] toCharArray(PrimitiveList.Short.Reader charReader) {
        char[] charArray = new char[charReader.size()];
        for (int i = 0; i < charReader.size(); i++) {
            charArray[i] = (char) charReader.get(i);
        }
        return charArray;
    }

    /**
     * Extract values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Int} reader to
     * a {@code int[]} array.
     */
    public static int[] toIntArray(PrimitiveList.Int.Reader intReader) {
        int[] intArray = new int[intReader.size()];
        for (int i = 0; i < intReader.size(); i++) {
            intArray[i] = intReader.get(i);
        }
        return intArray;
    }

    /**
     * Extract values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Float} reader
     * to a {@code float[]} array.
     */
    static float[] toFloatArray(PrimitiveList.Float.Reader floatReader) {
        float[] floatArray = new float[floatReader.size()];
        for (int i = 0; i < floatReader.size(); i++) {
            floatArray[i] = floatReader.get(i);
        }
        return floatArray;
    }

    /**
     * Extract values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Long} reader
     * to a {@code long[]} array.
     */
    static long[] toLongArray(PrimitiveList.Long.Reader longReader) {
        long[] longArray = new long[longReader.size()];
        for (int i = 0; i < longReader.size(); i++) {
            longArray[i] = longReader.get(i);
        }
        return longArray;
    }

    /**
     * Extract values from a {@link com.oracle.svm.shaded.org.capnproto.PrimitiveList.Double} reader
     * to a {@code double[]} array.
     */
    static double[] toDoubleArray(PrimitiveList.Double.Reader doubleReader) {
        double[] doubleArray = new double[doubleReader.size()];
        for (int i = 0; i < doubleReader.size(); i++) {
            doubleArray[i] = doubleReader.get(i);
        }
        return doubleArray;
    }

    /**
     * Extract values from a {@link com.oracle.svm.shaded.org.capnproto.TextList} reader to a
     * {@code String[]} array.
     */
    public static String[] toStringArray(TextList.Reader reader) {
        return toArray(reader, (s) -> s, String[]::new);
    }

    /**
     * Find the value containing the given {@code key} in a
     * {@link com.oracle.svm.shaded.org.capnproto.StructList} reader, applying the
     * {@code keyExtractor} to searched values. The input list should be sorted by the {@code key}
     * and contain no duplicates.
     * 
     * @return the found value or {@code null}
     */
    static <T extends StructReader> T binarySearchUnique(int key, StructList.Reader<T> sortedList, ToIntFunction<T> keyExtractor) {
        int low = 0;
        int high = sortedList.size() - 1;

        int prevMid = -1;
        int prevKey = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midStruct = sortedList.get(mid);
            int midKey = keyExtractor.applyAsInt(midStruct);

            assert prevMid == -1 || (mid < prevMid && midKey < prevKey) || (mid > prevMid && midKey > prevKey) : "unsorted or contains duplicates";

            if (midKey < key) {
                low = mid + 1;
            } else if (midKey > key) {
                high = mid - 1;
            } else {
                return midStruct;
            }

            prevMid = mid;
            prevKey = midKey;
        }
        return null;
    }
}
