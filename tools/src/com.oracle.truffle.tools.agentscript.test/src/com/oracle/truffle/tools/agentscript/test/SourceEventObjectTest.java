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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Constructor;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

public class SourceEventObjectTest {
    private Object sourceEventObject;
    private InteropLibrary iop;

    @Before
    public void createInstancesForTheTest() throws Exception {
        Class<?> seoClass = Class.forName("com.oracle.truffle.tools.agentscript.impl.SourceEventObject");
        Constructor<?> newSeoClass = seoClass.getDeclaredConstructor(Source.class);
        newSeoClass.setAccessible(true);
        sourceEventObject = newSeoClass.newInstance(Source.newBuilder("x", "char", "name").build());

        iop = InteropLibrary.getFactory().getUncached();
    }

    @Test
    public void checkUriAttr() throws Exception {
        Object uri = iop.readMember(sourceEventObject, "uri");
        assertNotNull(uri);
        assertTrue(iop.isMemberReadable(sourceEventObject, "uri"));
        assertTrue(iop.isMemberExisting(sourceEventObject, "uri"));
    }

    @Test
    public void checkUnknownAttr() throws Exception {
        try {
            Object nothing = iop.readMember(sourceEventObject, "unknownMember");
            fail("unknownMember shouldn't be available: " + nothing);
        } catch (UnknownIdentifierException ex) {
            // OK
        }
        assertFalse(iop.isMemberReadable(sourceEventObject, "unknownMember"));
        assertFalse(iop.isMemberExisting(sourceEventObject, "unknownMember"));
    }

}
