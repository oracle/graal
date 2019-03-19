/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import org.junit.Test;

@SuppressWarnings("deprecation")
public class MessageStringTest {

    @Test
    public void testFields() throws Exception {
        for (Field f : com.oracle.truffle.api.interop.Message.class.getFields()) {
            if (f.getType() != com.oracle.truffle.api.interop.Message.class) {
                continue;
            }
            if ((f.getModifiers() & Modifier.STATIC) == 0) {
                continue;
            }
            com.oracle.truffle.api.interop.Message msg = (com.oracle.truffle.api.interop.Message) f.get(null);

            String persistent = com.oracle.truffle.api.interop.Message.toString(msg);
            assertNotNull("Found name for " + f, persistent);
            assertEquals("It is in upper case", persistent, persistent.toUpperCase(Locale.ENGLISH));

            com.oracle.truffle.api.interop.Message newMsg = com.oracle.truffle.api.interop.Message.valueOf(persistent);

            assertSame("Same for " + f, msg, newMsg);

            assertEquals("Same toString()", persistent, msg.toString());
        }
    }

    @Test
    public void testFactoryMethods() throws Exception {
        for (Method m : com.oracle.truffle.api.interop.Message.class.getMethods()) {
            if (m.getReturnType() != com.oracle.truffle.api.interop.Message.class) {
                continue;
            }
            if (!m.getName().startsWith("create")) {
                continue;
            }
            if ((m.getModifiers() & Modifier.STATIC) == 0) {
                continue;
            }
            com.oracle.truffle.api.interop.Message msg = (com.oracle.truffle.api.interop.Message) m.invoke(null, 0);

            String persistent = com.oracle.truffle.api.interop.Message.toString(msg);
            assertNotNull("Found name for " + m, persistent);
            assertEquals("It is in upper case", persistent, persistent.toUpperCase(Locale.ENGLISH));

            com.oracle.truffle.api.interop.Message newMsg = com.oracle.truffle.api.interop.Message.valueOf(persistent);

            assertEquals("Same for " + m, msg, newMsg);

            assertEquals("Same toString()", persistent, msg.toString());
            assertEquals("Same toString() for new one", persistent, newMsg.toString());
        }
    }

    @Test
    public void specialPersistance() {
        SpecialMsg msg = new SpecialMsg();
        String persistent = com.oracle.truffle.api.interop.Message.toString(msg);
        com.oracle.truffle.api.interop.Message newMsg = com.oracle.truffle.api.interop.Message.valueOf(persistent);
        assertEquals("com.oracle.truffle.api.interop.Message reconstructed", msg, newMsg);
    }

    public static final class SpecialMsg extends com.oracle.truffle.api.interop.Message {

        @Override
        public boolean equals(Object message) {
            return message instanceof SpecialMsg;
        }

        @Override
        public int hashCode() {
            return 5425432;
        }
    }
}
