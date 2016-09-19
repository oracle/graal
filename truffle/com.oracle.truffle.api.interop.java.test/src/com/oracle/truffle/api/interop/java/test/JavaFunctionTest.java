/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.vm.PolyglotEngine;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class JavaFunctionTest {
    private PolyglotEngine engine;

    @Test
    public void invokeRunnable() throws Exception {
        final boolean[] called = {false};

        engine = PolyglotEngine.newBuilder().globalSymbol("test", JavaInterop.asTruffleFunction(Runnable.class, new Runnable() {
            @Override
            public void run() {
                called[0] = true;
            }
        })).build();
        engine.findGlobalSymbol("test").execute();

        assertTrue("Runnable has been called", called[0]);
    }

    @Test
    public void invokeIterable() throws Exception {
        final boolean[] called = {false};

        engine = PolyglotEngine.newBuilder().globalSymbol("test", JavaInterop.asTruffleFunction(Iterable.class, new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                called[0] = true;
                return Collections.emptyIterator();
            }
        })).build();
        engine.findGlobalSymbol("test").execute();

        assertTrue("Iterator has been called", called[0]);
    }

    @Test
    public void invokeHashableInterface() throws Exception {
        final boolean[] called = {false};

        engine = PolyglotEngine.newBuilder().globalSymbol("test", JavaInterop.asTruffleFunction(Hashable.class, new Hashable() {
            @Override
            public String name() {
                called[0] = true;
                return "no name";
            }
        })).build();
        engine.findGlobalSymbol("test").execute();

        assertTrue("Hashable name has been called", called[0]);
    }

    @Test
    public void invokeComparator() throws Exception {
        final boolean[] called = {false};

        engine = PolyglotEngine.newBuilder().globalSymbol("test", JavaInterop.asTruffleFunction(Comparator.class, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                called[0] = true;
                return o1.compareTo(o2);
            }
        })).build();
        engine.findGlobalSymbol("test").execute(1, 1);

        assertTrue("Iterator has been called", called[0]);
    }

    @Test
    public void failOnMultipleMethods() {
        HashSet<Object> set = new HashSet<>();
        IllegalArgumentException noSet = null;
        try {
            JavaInterop.asTruffleFunction(Set.class, set);
        } catch (IllegalArgumentException ex) {
            noSet = ex;
        }
        assertNotNull("Exception has ben thrown", noSet);
        IllegalArgumentException noInterface = null;
        try {
            JavaInterop.asTruffleFunction(HashSet.class, set);
        } catch (IllegalArgumentException ex) {
            noInterface = ex;
        }
        assertNotNull("Exception has ben thrown", noInterface);
        final int noSetDepth = noSet.getStackTrace().length;
        final int noInterfaceDepth = noInterface.getStackTrace().length;
        if (noSetDepth != noInterfaceDepth) {
            noSet.printStackTrace();
            noInterface.printStackTrace();
            assertEquals("Different stack depth in both exceptions", noSetDepth, noInterfaceDepth);
        }
    }

    @After
    public void dispose() {
        if (engine != null) {
            engine.dispose();
        }
    }

    public interface Hashable {
        @Override
        int hashCode();

        String name();
    }
}
