/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.test;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import java.lang.reflect.Constructor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

public class EventContextObjectTest {
    private Object eventContextObject;
    private InteropLibrary iop;

    @Before
    public void createInstancesForTheTest() throws Exception {
        Class<?> ecoClass = Class.forName("com.oracle.truffle.tools.agentscript.impl.EventContextObject");
        Constructor<?> newEco = ecoClass.getDeclaredConstructor(EventContext.class);
        newEco.setAccessible(true);
        eventContextObject = newEco.newInstance(new Object[]{null});

        iop = InteropLibrary.getFactory().getUncached();
    }

    @Test
    public void checkNameAttr() throws Exception {
        assertTrue(iop.isMemberReadable(eventContextObject, "name"));
        assertTrue(iop.isMemberExisting(eventContextObject, "name"));
        try {
            Object name = iop.readMember(eventContextObject, "name");
            assertNotNull(name);
        } catch (NullPointerException ex) {
            // acceptable error, as we don't have mock version of EventContext
        }
    }

    @Test
    public void checkUnknownAttr() throws Exception {
        try {
            Object nothing = iop.readMember(eventContextObject, "unknownMember");
            fail("unknownMember shouldn't be available: " + nothing);
        } catch (UnknownIdentifierException ex) {
            // OK
        }
        assertFalse(iop.isMemberReadable(eventContextObject, "unknownMember"));
        assertFalse(iop.isMemberExisting(eventContextObject, "unknownMember"));
    }

    @Test
    public void enumerateAttributes() throws Exception {
        assertTrue("It has members", iop.hasMembers(eventContextObject));
        Object members = iop.getMembers(eventContextObject);

        String[] expectedNames = {
                        "name", "source", "characters",
                        "line", "startLine", "endLine",
                        "column", "startColumn", "endColumn"
        };

        assertEquals(expectedNames.length, iop.getArraySize(members));
        for (int i = 0; i < expectedNames.length; i++) {
            assertEquals(expectedNames[i], iop.readArrayElement(members, i));
        }
    }
}
