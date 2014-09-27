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
     * Interface used to determine the offset (in bytes) of a field.
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

    /**
     * The set of fields in {@link #clazz} that do long belong to a more specific category.
     */
    protected Fields data;

    public FieldIntrospection(Class<?> clazz) {
        this.clazz = clazz;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    /**
     * Gets the fields in {@link #getClazz()} that do long belong to specific category.
     */
    public Fields getData() {
        return data;
    }

    /**
     * Describes a field in a class during scanning.
     */
    public static class FieldInfo implements Comparable<FieldInfo> {
        public final long offset;
        public final String name;
        public final Class<?> type;

        public FieldInfo(long offset, String name, Class<?> type) {
            this.offset = offset;
            this.name = name;
            this.type = type;
        }

        /**
         * Sorts fields in ascending order by their {@link #offset}s.
         */
        public int compareTo(FieldInfo o) {
            return offset < o.offset ? -1 : (offset > o.offset ? 1 : 0);
        }

        @Override
        public String toString() {
            return "[" + offset + "]" + name + ":" + type.getSimpleName();
        }
    }

    public abstract static class BaseFieldScanner {

        private final CalcOffset calc;

        /**
         * Fields not belonging to a more specific category defined by scanner subclasses are added
         * to this list.
         */
        public final ArrayList<FieldInfo> data = new ArrayList<>();

        protected BaseFieldScanner(CalcOffset calc) {
            this.calc = calc;
        }

        /**
         * Scans the fields in a class hierarchy.
         */
        public void scan(Class<?> clazz) {
            Class<?> currentClazz = clazz;
            do {
                for (Field field : currentClazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }
                    long offset = calc.getOffset(field);
                    scanField(field, offset);
                }
                currentClazz = currentClazz.getSuperclass();
            } while (currentClazz.getSuperclass() != Object.class);
        }

        protected abstract void scanField(Field field, long offset);
    }
}
