/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing.model;

import java.lang.reflect.Array;
import java.util.Objects;

public class Property<T> {
    private final String name;
    private final T value;

    public Property(String name, T value) {
        if (name == null) {
            throw new IllegalArgumentException("Property name must not be null!");
        }

        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    @Override
    public String toString() {
        return toString(name, value);
    }

    public static <T> String toString(String name, T value) {
        StringBuilder sb = new StringBuilder(name).append("=");
        if (value == null || !value.getClass().isArray()) {
            sb.append(value);
        } else {
            // maybe better pattern should be used
            sb.append("[");
            int length = Array.getLength(value);
            for (int i = 0; i < length; ++i) {
                sb.append(Array.get(value, i)).append(", ");
            }
            sb.setLength(sb.length() == 1 ? 1 : sb.length() - 2);
            sb.append("]");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Property<?>)) {
            return false;
        }
        Property<?> p2 = (Property<?>) o;
        return name.equals(p2.name) && Objects.deepEquals(value, p2.value);
    }

    @Override
    public int hashCode() {
        return makeHash(name, value);
    }

    protected static <T> int makeHash(String name, T value) {
        int hash = name.hashCode();
        if (value == null || !value.getClass().isArray()) {
            return hash * 3 + Objects.hashCode(value);
        }
        int length = Array.getLength(value);
        for (int i = 0; i < length; ++i) {
            hash = hash * 3 + Objects.hashCode(Array.get(value, i));
        }
        return hash;
    }
}
