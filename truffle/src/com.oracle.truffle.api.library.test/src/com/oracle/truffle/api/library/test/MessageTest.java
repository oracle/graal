/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.library.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.Message;

public class MessageTest {

    @GenerateLibrary
    public abstract static class MessageLibrary extends Library {

        public abstract String m0(Object receiver, String arg0, int arg1);

        public String m1(@SuppressWarnings("unused") Object receiver) {
            return "m1_default";
        }
    }

    @Test
    public void testM0Properties() throws ClassNotFoundException {
        Message m0 = Message.resolve(MessageLibrary.class, "m0");
        assertSame(MessageLibrary.class, m0.getLibraryClass());
        assertEquals(MessageLibrary.class.getName(), m0.getLibraryName());
        assertEquals(Arrays.asList(Object.class, String.class, int.class), m0.getParameterTypes());
        assertEquals(MessageLibrary.class.getName() + ".m0", m0.getQualifiedName());
        assertSame(Object.class, m0.getReceiverType());
        assertSame(String.class, m0.getReturnType());
        assertSame(m0.getSimpleName().intern(), m0.getSimpleName());
        assertSame(m0.getQualifiedName().intern(), m0.getQualifiedName());
        assertSame(Class.forName(MessageLibrary.class.getName()), m0.getLibraryClass());
        assertEquals("m0", m0.getSimpleName());

        Message m1 = Message.resolve(MessageLibrary.class, "m1");
        assertNotEquals(m0.hashCode(), m1.hashCode());
        assertEquals(m0.hashCode(), m0.hashCode());
        assertTrue(m0.equals(m0));
        assertFalse(m0.equals(m1));
        assertNotNull(m0.toString());
        assertNotNull(m1.toString());
    }

    abstract static class DummyLibrary extends Library {

    }

    @Test
    public void testResolve() {
        assertSame(Message.resolve(MessageLibrary.class, "m0"), Message.resolve(MessageLibrary.class, "m0"));

        assertNPE(() -> Message.resolve(MessageLibrary.class, null));

        assertNPE(() -> Message.resolve((Class<? extends Library>) null, "m0"));

        assertIAE(() -> Message.resolve(DummyLibrary.class, "m0"),
                        String.format("Class '%s' is not a registered library. Truffle libraries must be annotated with @GenerateLibrary to be registered. Did the Truffle annotation processor run?",
                                        DummyLibrary.class.getName()));
        assertIAE(() -> Message.resolve(MessageLibrary.class, "invalid"), "Unknown message 'invalid' for library 'com.oracle.truffle.api.library.test.MessageTest$MessageLibrary' specified.");
    }

    static void assertNPE(Runnable r) {
        try {
            r.run();
        } catch (NullPointerException e) {
            return;
        }
        fail();
    }

    static void assertIAE(Runnable r, String message) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            if (message != null) {
                assertEquals(message, e.getMessage());
            }
            return;
        }
        fail();
    }

}
