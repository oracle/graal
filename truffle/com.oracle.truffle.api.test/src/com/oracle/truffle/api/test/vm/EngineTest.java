/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class EngineTest {
    protected PolyglotEngine.Builder createBuilder() {
        return PolyglotEngine.newBuilder();
    }

    @Test
    public void npeWhenCastingAs() throws Exception {
        PolyglotEngine tvm = createBuilder().build();

        PolyglotEngine.Language language1 = tvm.getLanguages().get("application/x-test-import-export-1");
        PolyglotEngine.Language language2 = tvm.getLanguages().get("application/x-test-import-export-2");
        language2.eval(Source.fromText("explicit.value=42", "define 42"));

        PolyglotEngine.Value value = language1.eval(Source.fromText("return=value", "42.value"));
        String res = value.as(String.class);
        assertNotNull(res);
    }

    protected Thread forbiddenThread() {
        return null;
    }

    private interface AccessArray {
        AccessArray dupl();

        List<? extends Number> get(int index);
    }

    @Test
    public void wrappedAsArray() throws Exception {
        Object[][] matrix = {{1, 2, 3}};

        PolyglotEngine tvm = createBuilder().globalSymbol("arr", new ArrayTruffleObject(matrix, forbiddenThread())).build();
        PolyglotEngine.Language language1 = tvm.getLanguages().get("application/x-test-import-export-1");
        AccessArray access = language1.eval(Source.fromText("return=arr", "get the array")).as(AccessArray.class);
        assertNotNull("Array converted to list", access);
        access = access.dupl();
        List<? extends Number> list = access.get(0);
        assertEquals("Size 3", 3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
        Integer[] arr = list.toArray(new Integer[0]);
        assertEquals("Three items in array", 3, arr.length);
        assertEquals(1, arr[0].intValue());
        assertEquals(2, arr[1].intValue());
        assertEquals(3, arr[2].intValue());
    }
}
