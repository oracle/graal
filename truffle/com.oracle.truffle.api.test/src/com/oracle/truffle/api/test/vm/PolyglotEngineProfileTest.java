/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import static com.oracle.truffle.api.test.ReflectionUtils.invoke;
import static com.oracle.truffle.api.test.ReflectionUtils.loadRelative;
import static com.oracle.truffle.api.test.ReflectionUtils.newInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.concurrent.Executors;

import org.junit.Test;

import com.oracle.truffle.api.vm.PolyglotEngine;

public class PolyglotEngineProfileTest {

    private static final Class<?> CONTEXT_STORE_PROFILE = loadRelative(PolyglotEngineProfileTest.class, "PolyglotEngineProfile");

    @Test
    public void switchFromConstantToMultipleThreads() throws Exception {
        final Object profile = newContextStoreProfile();
        Object store1 = newContextStore();
        final Object store2 = newContextStore();

        Object prev = enter(profile, store1);
        assertEquals(store1, get(profile));
        leave(profile, prev);

        prev = enter(profile, store1);
        assertEquals(store1, get(profile));
        leave(profile, prev);

        Object prevInner = enter(profile, store2);
        assertSame(store2, get(profile));
        leave(profile, prevInner);

        leave(profile, prev);

        prev = enter(profile, store1);
        assertSame(store1, get(profile));

        final Object[] check = {null, null};
        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                Object prevInner2 = enter(profile, store2);
                check[0] = get(profile);
                leave(profile, prevInner2);

                prevInner2 = enter(profile, store2);
                check[1] = get(profile);
                leave(profile, prevInner2);
            }
        }).get();
        assertEquals(store2, check[0]);
        assertEquals(store2, check[1]);
        assertEquals(store1, get(profile));
        leave(profile, prev);

        prev = enter(profile, store1);
        assertSame(store1, get(profile));
        leave(profile, prev);
        assertNull(get(profile));
    }

    @Test
    public void switchFromDynamicToMultipleThreads() throws Exception {
        final Object profile = newContextStoreProfile();
        Object store1 = newContextStore();
        final Object store2 = newContextStore();
        final Object store3 = newContextStore();
        enter(profile, store1);
        assertEquals("Store associated", store1, get(profile));
        enter(profile, store2);
        assertEquals("Store associated", store2, get(profile));
        final Object[] check = {null};
        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                enter(profile, store3);
                check[0] = get(profile);
            }
        }).get();
        assertEquals("Store on other thread associated", store3, check[0]);
        assertEquals("1st thread store is still there", store2, get(profile));
    }

    private static Object enter(Object profile, Object store) {
        return invoke(profile, "enter", store);
    }

    private static void leave(Object profile, Object prev) {
        invoke(profile, "leave", new Class<?>[]{PolyglotEngine.class}, prev);
    }

    private static Object newContextStore() {
        return PolyglotEngine.newBuilder().build();
    }

    private static Object newContextStoreProfile() {
        return newInstance(CONTEXT_STORE_PROFILE, new Class<?>[]{PolyglotEngine.class}, (Object) null);
    }

    private static Object get(Object profile) {
        return invoke(profile, "get");
    }

}
