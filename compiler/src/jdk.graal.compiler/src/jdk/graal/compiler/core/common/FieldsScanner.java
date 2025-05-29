/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import jdk.internal.misc.Unsafe;

/**
 * Scans the fields in a class hierarchy.
 */
public class FieldsScanner {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * Describes a field in a class during {@linkplain FieldsScanner scanning}.
     */
    public static class FieldInfo {

        public final long offset;
        public final String name;
        public final Class<?> type;
        public final Class<?> declaringClass;

        public FieldInfo(long offset, String name, Class<?> type, Class<?> declaringClass) {
            this.offset = offset;
            this.name = name;
            this.type = type;
            this.declaringClass = declaringClass;
        }

        @Override
        public String toString() {
            return "[" + offset + "]" + name + ":" + type.getSimpleName();
        }
    }

    /**
     * Fields not belonging to a more specific category defined by scanner subclasses are added to
     * this list.
     */
    public final List<FieldInfo> data = new ArrayList<>();

    /**
     * Scans the non-static fields in the class hierarchy bounded by {@code startSubclass}
     * (inclusive) and {@code endSuperclass} (exclusive). The classes are processed from superclass
     * to subclass. The fields of a class are processed in the order returned by
     * {@link Class#getDeclaredFields()}.
     */
    public void scan(Class<?> startSubclass, Class<?> endSuperclass) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = startSubclass; c != endSuperclass; c = c.getSuperclass()) {
            hierarchy.add(c);
        }
        for (Class<?> currentClazz : hierarchy.reversed()) {
            for (Field field : currentClazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                long offset = UNSAFE.objectFieldOffset(field);
                scanField(field, offset);
            }
        }
    }

    protected void scanField(Field field, long offset) {
        data.add(new FieldsScanner.FieldInfo(offset, field.getName(), field.getType(), field.getDeclaringClass()));
    }

    public Fields createData() {
        return Fields.create(data);
    }
}
