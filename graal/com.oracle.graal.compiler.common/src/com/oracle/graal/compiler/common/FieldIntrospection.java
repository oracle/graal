/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public abstract class FieldIntrospection extends UnsafeAccess {

    /**
     * Interface used by {@link #rescanAllFieldOffsets(CalcOffset)} to determine the offset (in
     * bytes) of a field.
     */
    public interface CalcOffset {

        long getOffset(Field field);
    }

    public static class DefaultCalcOffset implements CalcOffset {

        @Override
        public long getOffset(Field field) {
            return unsafe.objectFieldOffset(field);
        }
    }

    protected static final ConcurrentHashMap<Class<?>, FieldIntrospection> allClasses = new ConcurrentHashMap<>();

    private final Class<?> clazz;
    protected long[] dataOffsets;
    protected Map<Long, String> fieldNames;
    protected Map<Long, Class<?>> fieldTypes;

    public FieldIntrospection(Class<?> clazz) {
        this.clazz = clazz;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public static void rescanAllFieldOffsets(CalcOffset calc) {
        for (FieldIntrospection nodeClass : allClasses.values()) {
            nodeClass.rescanFieldOffsets(calc);
        }
    }

    protected abstract void rescanFieldOffsets(CalcOffset calc);

    public abstract static class BaseFieldScanner {

        private final CalcOffset calc;

        /** The offsets of fields that are not specially handled by subclasses. */
        public final ArrayList<Long> dataOffsets = new ArrayList<>();

        public final Map<Long, String> fieldNames = new HashMap<>();
        public final Map<Long, Class<?>> fieldTypes = new HashMap<>();

        protected BaseFieldScanner(CalcOffset calc) {
            this.calc = calc;
        }

        public void scan(Class<?> clazz) {
            Class<?> currentClazz = clazz;
            do {
                for (Field field : currentClazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }
                    Class<?> type = field.getType();
                    long offset = calc.getOffset(field);

                    // scanField() may overwrite the name with a customized name.
                    fieldNames.put(offset, field.getName());
                    fieldTypes.put(offset, type);

                    scanField(field, type, offset);
                }
                currentClazz = currentClazz.getSuperclass();
            } while (currentClazz.getSuperclass() != Object.class);
        }

        protected abstract void scanField(Field field, Class<?> type, long offset);
    }

    protected static void copyInto(long[] dest, long[] src) {
        assert dest.length == src.length;
        for (int i = 0; i < dest.length; i++) {
            dest[i] = src[i];
        }
    }

    protected static <T> void copyInto(T[] dest, T[] src) {
        assert dest.length == src.length;
        for (int i = 0; i < dest.length; i++) {
            dest[i] = src[i];
        }
    }

    protected static <T> void copyInto(T[] dest, List<T> src) {
        assert dest.length == src.size();
        for (int i = 0; i < dest.length; i++) {
            dest[i] = src.get(i);
        }
    }

    protected static <T> T[] arrayUsingSortedOffsets(Map<Long, T> map, long[] sortedOffsets, T[] result) {
        for (int i = 0; i < sortedOffsets.length; i++) {
            result[i] = map.get(sortedOffsets[i]);
        }
        return result;
    }

    protected static long[] sortedLongCopy(ArrayList<Long> list1) {
        Collections.sort(list1);
        long[] result = new long[list1.size()];
        for (int i = 0; i < list1.size(); i++) {
            result[i] = list1.get(i);
        }
        return result;
    }

    protected static long[] sortedLongCopy(ArrayList<Long> list1, ArrayList<Long> list2) {
        Collections.sort(list1);
        Collections.sort(list2);
        long[] result = new long[list1.size() + list2.size()];
        for (int i = 0; i < list1.size(); i++) {
            result[i] = list1.get(i);
        }
        for (int i = 0; i < list2.size(); i++) {
            result[list1.size() + i] = list2.get(i);
        }
        return result;
    }
}
