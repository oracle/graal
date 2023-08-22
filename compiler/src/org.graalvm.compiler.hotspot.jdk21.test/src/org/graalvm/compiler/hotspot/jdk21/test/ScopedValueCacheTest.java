/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.jdk21.test;

import java.lang.reflect.Method;

import org.graalvm.compiler.hotspot.test.HotSpotGraalCompilerTest;
import org.graalvm.compiler.test.AddExports;

@AddExports("java.base/jdk.internal.misc")
public class ScopedValueCacheTest extends HotSpotGraalCompilerTest {

    private static boolean contains(Object[] array, Object value) {
        for (Object element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("preview")
    public static void testScopedValue() {
        ScopedValue<Integer> scopedValue = ScopedValue.newInstance();
        ScopedValue.where(scopedValue, 42).run(() -> {
            scopedValue.get();
            try {
                Method get = Thread.class.getDeclaredMethod("scopedValueCache");
                get.setAccessible(true);
                Object[] cache = (Object[]) get.invoke(null);
                assertTrue(contains(cache, scopedValue));
            } catch (ReflectiveOperationException e) {
                fail(e.getMessage());
            }
        });
    }
}
