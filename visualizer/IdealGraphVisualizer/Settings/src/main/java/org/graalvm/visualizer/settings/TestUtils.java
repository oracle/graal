/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Ondřej Douda <ondrej.douda@oracle.com>
 */
public class TestUtils {
    private TestUtils() {
    }

    public static boolean checkNotNulls(Object a, Object b) {
        if (a == null || b == null) {
            assertEquals(a, b);
            return false;
        }
        return true;
    }

    public static <T> void assertNot(T a, T b, BiConsumer<T, T> assertion) {
        assertNot(a, b, assertion, "");
    }

    public static <T> void assertNot(T a, T b, BiConsumer<T, T> assertion, String failMessage) {
        try {
            assertion.accept(a, b);
        } catch (AssertionError e) {
            return;
        }
        fail(failMessage);
    }

    public static <T> void assertCollections_NoOrder(Collection<? extends T> a, Collection<? extends T> b, BiConsumer<T, T> assertion) {
        assertCollections_NoOrder(a, b, assertion, Objects::hashCode);
    }

    public static <T, M> void assertCollections_NoOrder(Collection<? extends T> a, Collection<? extends T> b, BiConsumer<T, T> assertion, Function<T, M> mapFunc) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.size(), b.size());
            Map<M, T> objs = new HashMap<>();
            b.forEach((obj) -> objs.put(mapFunc.apply(obj), obj));
            a.forEach((obj) -> assertion.accept(obj, objs.get(mapFunc.apply(obj))));
        }
    }

    public static <T> void assertCollections_InOrder(Collection<? extends T> a, Collection<? extends T> b, BiConsumer<T, T> assertion) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.size(), b.size());
            List<T> objs = new ArrayList<>();
            b.forEach(objs::add);
            int i = 0;
            for (T obj : a) {
                assertion.accept(obj, objs.get(i++));
            }
        }
    }

    public static <T> void assertSetEquals(Set<? extends T> a, Set<? extends T> b) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.size(), b.size());
            b.forEach((obj) -> assertTrue(a.contains(obj)));
        }
    }

    public static <T> void assertListEquals(List<? extends T> a, List<? extends T> b, BiConsumer<T, T> assertion) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); ++i) {
                assertion.accept(a.get(i), b.get(i));
            }
        }
    }
}
