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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.Property;

public class PropertyTest {
    /**
     * Test of getName method, of class Property.
     */
    @Test
    @SuppressWarnings("unused")
    public void testGetNameAndValue() {
        final Property<String> p = new Property<>("name", "value");
        assertEquals(p.getName(), "name");
        assertEquals(p.getValue(), "value");

        try {
            new Property<>(null, "value");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        final Property<?> p2 = new Property<>("name", null);
        assertEquals(p2.getName(), "name");
        assertNull(p2.getValue());
    }

    /**
     * Test of toString method, of class Property.
     */
    @Test
    public void testToString() {
        final Property<String> p = new Property<>("name", "value");
        assertEquals(p.toString(), "name=value");
        final Property<?> p2 = new Property<>("name", null);
        assertEquals(p2.toString(), "name=null");
        final Property<Object[]> p3 = new Property<>("name", new Object[]{1, 2, "a"});
        assertEquals(p3.toString(), "name=[1, 2, a]");
    }

    /**
     * Test of equals/hashCode method, of class Property.
     */
    @Test
    public void testEqualsHashCode() {
        final Property<String> p = new Property<>("name", "value");
        final Object o = new Object();
        assertNotEquals(p, o);
        assertNotEquals(p, null);
        assertEquals(p, p);

        final Property<int[]> p2 = new Property<>("name", new int[]{1, 2, 3});
        assertNotEquals(p, p2);
        assertNotEquals(p.hashCode(), p2.hashCode());

        final Property<String> p3 = new Property<>("name2", "value");
        assertNotEquals(p, p3);
        assertNotEquals(p2, p3);
        assertNotEquals(p.hashCode(), p3.hashCode());
        assertNotEquals(p2.hashCode(), p3.hashCode());

        final Property<String> p4 = new Property<>("name", "value");
        assertEquals(p, p4);
        assertEquals(p.hashCode(), p4.hashCode());
        assertNotEquals(p2, p4);
        assertNotEquals(p3, p4);
        assertNotEquals(p2.hashCode(), p4.hashCode());
        assertNotEquals(p3.hashCode(), p4.hashCode());

        final Property<String> p5 = new Property<>("value", "name");
        assertNotEquals(p, p5);
        assertNotEquals(p.hashCode(), p5.hashCode());

        final Property<int[]> p6 = new Property<>("name", new int[]{1, 2, 3});
        assertEquals(p2, p6);
        assertEquals(p2.hashCode(), p6.hashCode());
    }
}
