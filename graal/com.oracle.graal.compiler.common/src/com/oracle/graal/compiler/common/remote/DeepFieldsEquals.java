/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.remote;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.compiler.common.*;

/**
 * Utility for structurally comparing two object graphs for equality. The comparison handles cycles
 * in the graphs. It also {@linkplain Handler#unproxifyObject(Object) unproxifies} any proxy objects
 * encountered.
 */
public class DeepFieldsEquals {

    /**
     * Compares the object graphs rooted at {@code a} and {@code b} for equality. This comparison
     * handles cycles in the graphs.
     *
     * @param a
     * @param b
     * @return a path to the first differing nodes in each graph or {@code null} if the graphs are
     *         equal
     */
    public static String equals(Object a, Object b) {
        return equals(a, b, new HashMap<>());
    }

    /**
     * Compares the object graphs rooted at {@code a} and {@code b} for equality. This comparison
     * handles cycles in the graphs.
     *
     * @param a
     * @param b
     * @param fieldsMap
     * @return a path to the first differing nodes in each graph or {@code null} if the graphs are
     *         equal
     */
    public static String equals(Object a, Object b, Map<Class<?>, Fields> fieldsMap) {

        Set<Item> visited = new HashSet<>();
        LinkedList<Item> worklist = new LinkedList<>();
        worklist.addFirst(new Item("", a, b));

        while (!worklist.isEmpty()) {
            Item item = worklist.removeFirst();
            visited.add(item);

            Object f1 = item.f1;
            Object f2 = item.f2;

            if (f1 == f2) {
                continue;
            }
            if (f1 == null || f2 == null) {
                return String.format("%s: %s != %s", item.path, f1, f2);
            }
            Class<?> f1Class = f1.getClass();
            Class<?> f2Class = f2.getClass();
            if (f1Class != f2Class) {
                return String.format("%s: %s != %s", item.path, f1Class, f2Class);
            }

            Class<?> componentType = f1Class.getComponentType();
            if (componentType != null) {
                int f1Len = Array.getLength(f1);
                int f2Len = Array.getLength(f2);
                if (f1Len != f2Len) {
                    return String.format("%s.length: %s != %s", item.path, f1Len, f2Len);
                }
                if (componentType.isPrimitive()) {
                    for (int i = 0; i < f1Len; i++) {
                        Object e1 = Array.get(f1, i);
                        Object e2 = Array.get(f2, i);
                        if (!e1.equals(e2)) {
                            return String.format("%s[%d]: %s != %s", item.path, i, f1, f2);
                        }
                    }
                } else {
                    for (int i = 0; i < f1Len; i++) {
                        String nextPath = item.path.length() == 0 ? "[" + i + "]" : item.path + "[" + i + "]";
                        Item next = new Item(nextPath, Array.get(f1, i), Array.get(f2, i));
                        if (!visited.contains(next)) {
                            worklist.addFirst(next);
                        }
                    }
                }
            } else {
                Fields fields = fieldsMap.get(f1Class);
                if (fields == null) {
                    fields = Fields.forClass(f1Class, Object.class, true, null);
                    fieldsMap.put(f1Class, fields);
                }

                for (int i = 0; i < fields.getCount(); ++i) {
                    Class<?> type = fields.getType(i);
                    Object e1 = fields.get(f1, i);
                    Object e2 = fields.get(f2, i);
                    if (type.isPrimitive()) {
                        if (!e1.equals(e2)) {
                            return String.format("%s.%s: %s != %s", item.path, fields.getName(i), e1, e2);
                        }
                    } else {
                        String nextPath = item.path.length() == 0 ? fields.getName(i) : item.path + "." + fields.getName(i);
                        Item next = new Item(nextPath, e1, e2);
                        if (!visited.contains(next)) {
                            worklist.addFirst(next);
                        }
                    }
                }
            }
        }
        return null;
    }

    static class Item {
        final String path;
        final Object f1;
        final Object f2;

        public Item(String path, Object f1, Object f2) {
            this.path = path;
            this.f1 = Handler.unproxifyObject(f1);
            this.f2 = Handler.unproxifyObject(f2);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((f1 == null) ? 0 : System.identityHashCode(f1));
            result = prime * result + ((f2 == null) ? 0 : System.identityHashCode(f2));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Item) {
                Item that = (Item) obj;
                return this.f1 == that.f1 && this.f2 == that.f2;
            }
            return false;
        }

        @Override
        public String toString() {
            return path;
        }
    }

}
