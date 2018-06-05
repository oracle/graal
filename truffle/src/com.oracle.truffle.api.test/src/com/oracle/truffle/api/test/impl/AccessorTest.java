/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.impl;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.TruffleLocator;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;

public class AccessorTest {
    private static Method find;
    private static Field instrumenthandler;

    /**
     * Reflection access to package-private members.
     *
     * @see Accessor#findLanguageByClass(Object, Class)
     * @see Accessor#INSTRUMENTHANDLER
     */
    @BeforeClass
    public static void initAccessors() throws Exception {
        find = Accessor.class.getDeclaredMethod("findLanguageByClass", Object.class, Class.class);
        ReflectionUtils.setAccessible(find, true);
        instrumenthandler = Accessor.class.getDeclaredField("INSTRUMENTHANDLER");
        ReflectionUtils.setAccessible(instrumenthandler, true);
    }

    /**
     * Test that {@link CallTarget}s can be called even when the instrumentation framework is not
     * initialized (e.g., when not using the {@link PolyglotEngine}).
     */
    @Test
    public void testAccessorInstrumentationHandlerNotInitialized() throws IllegalAccessException {
        Object savedAccessor = instrumenthandler.get(null);
        instrumenthandler.set(null, null);
        try {
            CallTarget testCallTarget = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
            assertEquals(42, testCallTarget.call(new Object[0]));
        } finally {
            instrumenthandler.set(null, savedAccessor);
        }
    }

    @Test
    public void loadClassExistsInTruffleLocator() throws Exception {
        Method method = TruffleLocator.class.getDeclaredMethod("loadClass", String.class);
        assertEquals(Class.class, method.getReturnType());
    }
}
