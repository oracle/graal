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

import com.oracle.truffle.api.interop.java.JavaInterop;
import java.util.Date;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public class ValueAPITest {

    private Context context;

    @Before
    public void setUp() {
        context = Context.create();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testInstantiate() {
        context.exportSymbol("Date.class", JavaInterop.asTruffleObject(java.util.Date.class));
        Value classValue = context.importSymbol("Date.class");
        assertTrue(classValue.canInstantiate());
        Value dateInstance = classValue.newInstance();
        Date date = dateInstance.asHostObject();
        assertNotNull(date);

        long ms = 1_000_000_000L;
        dateInstance = classValue.newInstance(ms);
        date = dateInstance.asHostObject();
        assertEquals(ms, date.getTime());
    }
}
