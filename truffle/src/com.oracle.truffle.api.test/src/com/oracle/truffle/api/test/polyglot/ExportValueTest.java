/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExportValueTest {
    private Context ctx;

    @Before
    public void setUp() {
        this.ctx = Context.create();
    }

    @After
    public void tearDown() {
        ctx.close();
    }

    @Test
    public void testToString() {
        ctx.exportSymbol("tmp", 10);
        Value value = ctx.importSymbol("tmp");
        value.toString();
    }

    @Test
    public void testGetMetaObject() {
        ctx.exportSymbol("tmp", 10);
        Value value = ctx.importSymbol("tmp");
        value.getMetaObject();
    }

    @Test
    public void testUnsupported() {
        ctx.exportSymbol("tmp", 10);
        Value value = ctx.importSymbol("tmp");
        try {
            value.getArraySize();
            Assert.assertTrue("Should not reach here.", false);
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }
}
