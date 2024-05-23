/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graph.test.graphio.parsing.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.LocationCache;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputMethod;

public class InputMethodTest {
    /**
     * Test of getBytecodes method, of class InputMethod.
     */
    @Test
    public void testGetSetBytecodes() {

        final String input = "0 iload_0\n" + "1 iconst_1\n" + "2 if_icmpne 7\n" + "5 iconst_1\n" + "6 ireturn\n" + "7 iconst_0\n" + "8 ireturn";
        final byte[] bytecode = {
                        (byte) 0x1a,
                        (byte) 0x4,
                        (byte) 0xa0, (byte) 0x0, (byte) 0x07,
                        (byte) 0x4,
                        (byte) 0xac,
                        (byte) 0x3,
                        (byte) 0xac};
        final Group g = new Group(null);
        InputMethod m = new InputMethod(g, "name", "shortName", -1, null);
        m.setBytecodes(input);

        assertEquals(7, m.getBytecodes().size());

        assertEquals(0, m.getBytecodes().get(0).getBci());
        assertEquals(1, m.getBytecodes().get(1).getBci());
        assertEquals(2, m.getBytecodes().get(2).getBci());
        assertEquals(5, m.getBytecodes().get(3).getBci());

        assertEquals("iload_0", m.getBytecodes().get(0).getName());
        assertEquals("iconst_1", m.getBytecodes().get(1).getName());
        assertEquals("if_icmpne", m.getBytecodes().get(2).getName());
        assertEquals("ireturn", m.getBytecodes().get(6).getName());

        assertNull(m.getBytecodes().get(2).getInlined());
        assertNull(m.getBytecodes().get(6).getInlined());

        m = new InputMethod(g, "name", "shortName", -1, LocationCache.createMethod(null, null, bytecode));

        assertEquals(7, m.getBytecodes().size());

        assertEquals(0, m.getBytecodes().get(0).getBci());
        assertEquals(1, m.getBytecodes().get(1).getBci());
        assertEquals(2, m.getBytecodes().get(2).getBci());
        assertEquals(5, m.getBytecodes().get(3).getBci());

        assertEquals("iload_0", m.getBytecodes().get(0).getName());
        assertEquals("iconst_1", m.getBytecodes().get(1).getName());
        assertEquals("if_icmpne", m.getBytecodes().get(2).getName());
        assertEquals("ireturn", m.getBytecodes().get(6).getName());

        assertNull(m.getBytecodes().get(2).getInlined());
        assertNull(m.getBytecodes().get(6).getInlined());
    }
}
