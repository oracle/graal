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

import static com.oracle.truffle.api.test.vm.ImplicitExplicitExportTest.L1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.vm.ImplicitExplicitExportTest.Ctx;
import com.oracle.truffle.api.vm.PolyglotEngine;

public class ExceptionDuringParsingTest {
    public static Accessor API;

    @Before
    @After
    public void cleanTheSet() {
        Ctx.disposed.clear();
    }

    @Test
    public void canGetAccessToOwnLanguageInstance() throws Exception {
        PolyglotEngine vm = PolyglotEngine.newBuilder().build();
        PolyglotEngine.Language language = vm.getLanguages().get(L1);
        assertNotNull("L1 language is defined", language);

        final Source src = Source.newBuilder("parse=No, no, no!").name("Fail on parsing").mimeType(L1).build();
        try {
            vm.eval(src);
            fail("Exception thrown");
        } catch (RuntimeException ex) {
            assertEquals(ex.getCause().getMessage(), "No, no, no!");
        }

        assertEquals("No dispose yet", 0, Ctx.disposed.size());

        vm.dispose();

        assertEquals("One context disposed", 1, Ctx.disposed.size());

        try {
            vm.eval(src);
            fail("Should throw an exception");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("disposed"));
        }
        try {
            vm.findGlobalSymbol("nothing");
            fail("Should throw an exception");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("disposed"));
        }
        try {
            vm.dispose();
            fail("Should throw an exception");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("disposed"));
        }
    }
}
