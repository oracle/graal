/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.test.heap;

import com.oracle.truffle.api.test.GCUtils;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import org.graalvm.tools.insight.heap.instrument.WeakIdentityHashMap;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Test of WeakIdentityHashMap.
 */
public class WeakIdentityHashMapTest {

    @Test
    public void testIdentity() {
        class IsEqual {
            @Override
            public int hashCode() {
                return 1;
            }

            @Override
            public boolean equals(Object obj) {
                return true;
            }
        }
        WeakIdentityHashMap<Object, Boolean> map = new WeakIdentityHashMap<>();
        Object k1 = new IsEqual();
        Object k2 = new IsEqual();
        map.put(k1, Boolean.TRUE);
        map.put(k2, Boolean.FALSE);
        assertEquals(Boolean.TRUE, map.get(k1));
        assertEquals(Boolean.FALSE, map.get(k2));
    }

    @Test
    public void testWeakness() {
        Object k = new Object();
        WeakIdentityHashMap<Object, Boolean> map = new WeakIdentityHashMap<>();
        map.put(k, Boolean.TRUE);
        Reference<Object> ref = new WeakReference<>(k);
        k = null;
        GCUtils.assertGc("The key must be collectible.", ref);
    }
}
