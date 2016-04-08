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
package com.oracle.truffle.api.impl;

import java.util.concurrent.Executors;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ContextStoreProfileTest {
    @Test
    public void switchFromConstantToMultipleThreads() throws Exception {
        final ContextStoreProfile profile = new ContextStoreProfile(null);
        ContextStore store1 = new ContextStore(null, 0);
        final ContextStore store2 = new ContextStore(null, 0);
        profile.enter(store1);
        assertEquals("Store associated", store1, profile.get());
        final ContextStore[] check = {null};
        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                profile.enter(store2);
                check[0] = profile.get();
            }
        }).get();
        assertEquals("Store on other thread associated", store2, check[0]);
        assertEquals("1st thread store is still there", store1, profile.get());
    }

    @Test
    public void switchFromDynamicToMultipleThreads() throws Exception {
        final ContextStoreProfile profile = new ContextStoreProfile(null);
        ContextStore store1 = new ContextStore(null, 0);
        ContextStore store2 = new ContextStore(null, 0);
        final ContextStore store3 = new ContextStore(null, 0);
        profile.enter(store1);
        assertEquals("Store associated", store1, profile.get());
        profile.enter(store2);
        assertEquals("Store associated", store2, profile.get());
        final ContextStore[] check = {null};
        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                profile.enter(store3);
                check[0] = profile.get();
            }
        }).get();
        assertEquals("Store on other thread associated", store3, check[0]);
        assertEquals("1st thread store is still there", store2, profile.get());
    }
}
