/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

import org.junit.After;

public class ValueTest implements Executor {
    private List<Runnable> pending = new LinkedList<>();
    private PolyglotEngine engine;

    @After
    public void dispose() {
        pending = null;
        if (engine != null) {
            engine.dispose();
        }
    }

    @Test
    public void valueToStringValue() throws Exception {
        engine = PolyglotEngine.newBuilder().build();
        PolyglotEngine.Language language1 = engine.getLanguages().get("application/x-test-import-export-1");
        PolyglotEngine.Language language2 = engine.getLanguages().get("application/x-test-import-export-2");
        language2.eval(Source.newBuilder("explicit.value=42").name("define 42").mimeType("content/unknown").build());
        PolyglotEngine.Value value = language1.eval(Source.newBuilder("return=value").name("42.value").mimeType("content/unknown").build());
        assertEquals("It's fourtytwo", "42", value.get());

        String textual = value.toString();
        assertTrue("Contains the value " + textual, textual.contains("value=42"));
        assertTrue("Is computed " + textual, textual.contains("computed=true"));
        assertTrue("No error " + textual, textual.contains("exception=null"));
    }

    @Test
    public void valueToStringException() throws Exception {
        engine = PolyglotEngine.newBuilder().build();
        PolyglotEngine.Language language1 = engine.getLanguages().get("application/x-test-import-export-1");
        PolyglotEngine.Value value = null;
        try {
            value = language1.eval(Source.newBuilder("parse=does not work").name("error.value").mimeType("content/unknown").build());
            Object res = value.get();
            fail("Should throw an exception: " + res);
        } catch (Exception ex) {
            assertTrue("Message contains the right text: " + ex.getMessage(), ex.getMessage().contains("does not work"));
        }

        assertNull("No value returned", value);
    }

    @Test
    public void valueToStringValueAsync() throws Exception {
        engine = PolyglotEngine.newBuilder().executor(this).build();
        PolyglotEngine.Language language1 = engine.getLanguages().get("application/x-test-import-export-1");
        PolyglotEngine.Language language2 = engine.getLanguages().get("application/x-test-import-export-2");
        language2.eval(Source.newBuilder("explicit.value=42").name("define 42").mimeType("content/unknown").build());
        flush();

        PolyglotEngine.Value value = language1.eval(Source.newBuilder("return=value").name("42.value").mimeType("content/unknown").build());

        String textual = value.toString();
        assertFalse("Doesn't contain the value " + textual, textual.contains("value=42"));
        assertTrue("Is not computed " + textual, textual.contains("computed=false"));
        assertTrue("No error " + textual, textual.contains("exception=null"));
        assertTrue("No value yet " + textual, textual.contains("value=null"));

        flush();

        textual = value.toString();
        assertTrue("Is computed " + textual, textual.contains("computed=true"));
        assertTrue("No error " + textual, textual.contains("exception=null"));
        assertTrue("value computed " + textual, textual.contains("value=42"));
    }

    @Test
    public void valueToStringExceptionAsync() throws Exception {
        engine = PolyglotEngine.newBuilder().executor(this).build();
        PolyglotEngine.Language language1 = engine.getLanguages().get("application/x-test-import-export-1");
        PolyglotEngine.Value value = language1.eval(Source.newBuilder("parse=does not work").name("error.value").mimeType("content/unknown").build());
        assertNotNull("Value returned", value);

        String textual = value.toString();
        assertTrue("Is not computed " + textual, textual.contains("computed=false"));
        assertTrue("No error " + textual, textual.contains("exception=null"));
        assertTrue("No value yet " + textual, textual.contains("value=null"));

        flush();

        textual = value.toString();
        assertTrue("Is computed " + textual, textual.contains("computed=true"));
        assertTrue("No value at all" + textual, textual.contains("value=null"));
        assertTrue("Error " + textual, textual.contains("exception=java.io.IOException: does not work"));
    }

    @Override
    public void execute(Runnable command) {
        if (pending != null) {
            pending.add(command);
        } else {
            command.run();
        }
    }

    private void flush() {
        for (Runnable r : pending) {
            r.run();
        }
    }
}
