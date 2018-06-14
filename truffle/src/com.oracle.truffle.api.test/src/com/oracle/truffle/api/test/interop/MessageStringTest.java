/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import org.junit.Test;

import com.oracle.truffle.api.interop.Message;

public class MessageStringTest {

    @Test
    public void testFields() throws Exception {
        for (Field f : Message.class.getFields()) {
            if (f.getType() != Message.class) {
                continue;
            }
            if ((f.getModifiers() & Modifier.STATIC) == 0) {
                continue;
            }
            Message msg = (Message) f.get(null);

            String persistent = Message.toString(msg);
            assertNotNull("Found name for " + f, persistent);
            assertEquals("It is in upper case", persistent, persistent.toUpperCase(Locale.ENGLISH));

            Message newMsg = Message.valueOf(persistent);

            assertSame("Same for " + f, msg, newMsg);

            assertEquals("Same toString()", persistent, msg.toString());
        }
    }

    @Test
    public void testFactoryMethods() throws Exception {
        for (Method m : Message.class.getMethods()) {
            if (m.getReturnType() != Message.class) {
                continue;
            }
            if (!m.getName().startsWith("create")) {
                continue;
            }
            if ((m.getModifiers() & Modifier.STATIC) == 0) {
                continue;
            }
            Message msg = (Message) m.invoke(null, 0);

            String persistent = Message.toString(msg);
            assertNotNull("Found name for " + m, persistent);
            assertEquals("It is in upper case", persistent, persistent.toUpperCase(Locale.ENGLISH));

            Message newMsg = Message.valueOf(persistent);

            assertEquals("Same for " + m, msg, newMsg);

            assertEquals("Same toString()", persistent, msg.toString());
            assertEquals("Same toString() for new one", persistent, newMsg.toString());
        }
    }

    @Test
    public void specialMessagePersitance() {
        SpecialMsg msg = new SpecialMsg();
        String persistent = Message.toString(msg);
        Message newMsg = Message.valueOf(persistent);
        assertEquals("Message reconstructed", msg, newMsg);
    }

    public static final class SpecialMsg extends Message {

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
