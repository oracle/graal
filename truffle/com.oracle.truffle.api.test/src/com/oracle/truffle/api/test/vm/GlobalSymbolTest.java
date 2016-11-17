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

import static com.oracle.truffle.api.test.vm.ImplicitExplicitExportTest.L3;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

public class GlobalSymbolTest {

    private PolyglotEngine vm;

    @After
    public void after() {
        if (vm != null) {
            vm.dispose();
        }
    }

    @Test
    public void globalSymbolFoundByLanguage() {
        vm = createEngineBuilder().globalSymbol("ahoj", "42").build();
        // @formatter:off
        Object ret = vm.eval(Source.newBuilder("return=ahoj").name("Return").mimeType(L3).build()
        ).get();
        // @formatter:on
        assertEquals("42", ret);
    }

    @Test
    public void globalSymbolFoundByVMUser() {
        vm = createEngineBuilder().globalSymbol("ahoj", "42").build();
        PolyglotEngine.Value ret = vm.findGlobalSymbol("ahoj");
        assertNotNull("Symbol found", ret);
        assertEquals("42", ret.get());
    }

    protected PolyglotEngine.Builder createEngineBuilder() {
        return PolyglotEngine.newBuilder();
    }

    @Test
    public void passingArray() {
        vm = createEngineBuilder().globalSymbol("arguments", new Object[]{"one", "two", "three"}).build();
        PolyglotEngine.Value value = vm.findGlobalSymbol("arguments");
        assertTrue("Get unwraps to original instance of array", value.get() instanceof Object[]);
        List<?> args = value.as(List.class);
        assertNotNull("Can be converted to List", args);
        assertEquals("Three items", 3, args.size());
        assertEquals("one", args.get(0));
        assertEquals("two", args.get(1));
        assertEquals("three", args.get(2));
        String[] arr = args.toArray(new String[0]);
        assertEquals("Three items in array", 3, arr.length);
        assertEquals("one", arr[0]);
        assertEquals("two", arr[1]);
        assertEquals("three", arr[2]);
    }
}
