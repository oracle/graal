/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data;

import org.graalvm.visualizer.data.Property;
import static org.junit.Assert.*;
import org.junit.*;

public class PropertyTest {

    public PropertyTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of getName method, of class Property.
     */
    @Test
    public void testGetNameAndValue() {
        final Property p = new Property("name", "value");
        assertEquals(p.getName(), "name");
        assertEquals(p.getValue(), "value");

        try {
            Property property = new Property(null, "value");
            fail();
        } catch (IllegalArgumentException e) {
        } catch (Throwable t) {
            fail();
        }

        final Property p2 = new Property("name", null);
        assertEquals(p2.getName(), "name");
        assertEquals(p2.getValue(), null);
    }

    /**
     * Test of toString method, of class Property.
     */
    @Test
    public void testToString() {
        final Property p = new Property("name", "value");
        assertEquals(p.toString(), "name=value");
        final Property p2 = new Property("name", null);
        assertEquals(p2.toString(), "name=null");
        final Property p3 = new Property("name", new Object[]{1, 2, "a"});
        assertEquals(p3.toString(), "name=[1, 2, a]");
    }

    /**
     * Test of equals/hashCode method, of class Property.
     */
    @Test
    public void testEqualsHashCode() {
        final Property p = new Property("name", "value");
        final Object o = new Object();
        assertNotEquals(p, o);
        assertNotEquals(p, null);
        assertEquals(p, p);
        assertEquals(p.hashCode(), Property.makeHash(p.getName(), p.getValue()));

        final Property p2 = new Property("name", new int[]{1, 2, 3});
        assertNotEquals(p, p2);
        assertNotEquals(p.hashCode(), p2.hashCode());

        final Property p3 = new Property("name2", "value");
        assertNotEquals(p, p3);
        assertNotEquals(p2, p3);
        assertNotEquals(p.hashCode(), p3.hashCode());
        assertNotEquals(p2.hashCode(), p3.hashCode());

        final Property p4 = new Property("name", "value");
        assertEquals(p, p4);
        assertEquals(p.hashCode(), p4.hashCode());
        assertNotEquals(p2, p4);
        assertNotEquals(p3, p4);
        assertNotEquals(p2.hashCode(), p4.hashCode());
        assertNotEquals(p3.hashCode(), p4.hashCode());

        final Property p5 = new Property("value", "name");
        assertNotEquals(p, p5);
        assertNotEquals(p.hashCode(), p5.hashCode());

        final Property p6 = new Property("name", new int[]{1, 2, 3});
        assertEquals(p2, p6);
        assertEquals(p2.hashCode(), p6.hashCode());

        try {
            Property.makeHash(null, "");
            fail();
        } catch (NullPointerException e) {
        } catch (Throwable t) {
            fail();
        }
    }
}
