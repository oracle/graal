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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Properties.EqualityPropertyMatcher;
import jdk.graal.compiler.graphio.parsing.model.Properties.InvertPropertyMatcher;
import jdk.graal.compiler.graphio.parsing.model.Properties.PropertyMatcher;
import jdk.graal.compiler.graphio.parsing.model.Properties.PropertySelector;
import jdk.graal.compiler.graphio.parsing.model.Properties.RegexpPropertyMatcher;
import jdk.graal.compiler.graphio.parsing.model.Property;

public class PropertiesTest {
    /**
     * Test of equals/hashCode method, of class Properties.
     */
    @Test
    public void testEqualsHashCode() {
        Properties a = Properties.newProperties();
        assertNotEquals(a, null);
        assertEquals(a, a);
        assertEquals(a.hashCode(), a.hashCode());

        Properties b = Properties.newProperties();
        assertEquals(a, b);
        assertEquals(b, a);
        assertNotSame(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        a.setProperty("p1", 1);
        assertEquals(a, a);
        assertEquals(a.hashCode(), a.hashCode());
        assertNotEquals(a, b);
        assertNotEquals(b, a);
        assertNotEquals(a.hashCode(), b.hashCode());

        b.setProperty("p1", 1);
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());

        Properties c = Properties.newProperties(a);
        assertEquals(c, a);
        assertEquals(c, b);
        assertEquals(b, c);
        assertEquals(b, a);
        assertNotSame(a, c);
        assertNotSame(b, c);
        assertEquals(c.hashCode(), b.hashCode());
        assertEquals(c.hashCode(), a.hashCode());

        c.setProperty("p1", new int[]{1, 2, 3});
        assertNotEquals(c, b);
        assertNotEquals(b, c);
        assertNotEquals(c.hashCode(), b.hashCode());
        assertNotEquals(c, a);
        assertNotEquals(a, c);
        assertNotEquals(c.hashCode(), a.hashCode());

        b.setProperty("p1", new int[]{1, 2, 3});
        assertEquals(c, b);
        assertEquals(b, c);
        assertEquals(c.hashCode(), b.hashCode());
        assertNotEquals(b, a);
        assertNotEquals(a, b);
        assertNotEquals(b.hashCode(), a.hashCode());
    }

    /**
     * Test of selectSingle method, of class Properties.
     */
    @Test
    public void testSelectSingle() {

        final boolean[] called = new boolean[1];
        final int[] v = new int[]{1, 2, 3};
        final String n = "p2";

        PropertyMatcher matcher = new PropertyMatcher() {

            @Override
            public String getName() {
                assertFalse(called[0]);
                called[0] = true;
                return n;
            }

            @Override
            public boolean match(Object value) {
                assertTrue(Objects.deepEquals(value, v));
                return true;
            }
        };

        Properties instance = Properties.newProperties();
        instance.setProperty("p1", "1");
        instance.setProperty(n, v);
        instance.setProperty("p3", "3");
        Property<?> result = instance.selectSingle(matcher);
        assertEquals(new Property<>(n, v), result);
        assertTrue(called[0]);

        called[0] = false;
        PropertyMatcher matcher2 = new PropertyMatcher() {

            @Override
            public String getName() {
                assertFalse(called[0]);
                called[0] = true;
                return n;
            }

            @Override
            public boolean match(Object value) {
                assertTrue(Objects.deepEquals(value, v));
                return false;
            }
        };

        Property<?> result2 = instance.selectSingle(matcher2);
        assertTrue(called[0]);
        assertNull(result2);
    }

    /**
     * Test of add method, of class Properties.
     */
    @Test
    public void testAdd() {
        Properties a = Properties.newProperties();
        a.setProperty("p1", new int[]{1, 2, 3});
        a.setProperty("p2", "2");

        Properties b = Properties.newProperties();
        b.setProperty("p1", new int[]{1, 2, 3});

        Properties c = Properties.newProperties();
        c.setProperty("p2", "2");

        assertNotEquals(a, b);
        b.add(c);

        assertEquals(a, b);

        b.setProperty("p3", null);
        assertNotEquals(a, b);

        a.setProperty("p3", null);
        assertEquals(a, b);

        Properties empty = Properties.newProperties();
        b.add(empty);
        assertEquals(a, b);

        empty.add(b);
        assertEquals(a, empty);

        try {
            a.add(null);
            fail();
        } catch (NullPointerException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }
    }

    /**
     * Test of the multiple argument constructors, of class Properties.
     */
    @Test
    public void testConstructors() {
        Properties a = Properties.newProperties("p1", "1", "p2", "2", "p3", "3");
        Properties b = Properties.newProperties("p1", "1", "p2", "2");
        Properties c = Properties.newProperties("p1", "1");

        assertEquals(a.get("p3"), "3");
        assertEquals(b.get("p2"), "2");
        assertEquals(b.get("p1"), "1");

        b.setProperty("p3", "3");
        c.setProperty("p2", "2");
        c.setProperty("p3", "3");

        assertEquals(a, b);
        assertEquals(a, c);
    }

    /**
     * Test of the Entity/SharedProperties nested classes, of class Properties.
     */
    @Test
    public void testEntitySharedProperties() {
        Properties p = Properties.newProperties();

        Properties.Entity entity = new Properties.Entity();
        assertEquals(entity.getProperties(), p);

        entity.getProperties().setProperty("p1", "1");
        assertNotEquals(entity.getProperties(), p);

        Properties.Entity entity2 = new Properties.Entity(entity);
        assertEquals(entity.getProperties(), entity2.getProperties());
        assertNotSame(entity.getProperties(), entity2.getProperties());

        Properties.Entity entity3 = new Properties.Entity();
        entity3.getProperties().setProperty("p2", "2");
        entity3.getProperties().setProperty("p1", "1");
        assertNotEquals(entity3.getProperties(), entity2.getProperties());

        entity.internProperties();
        entity2.internProperties();
        entity3.internProperties();

        assertSame(entity.getProperties(), entity2.getProperties());
        assertNotEquals(entity3.getProperties(), entity2.getProperties());

        Properties.Entity entity4 = new Properties.Entity(entity);
        entity4.getProperties().setProperty("p2", "2");
        assertEquals(entity3.getProperties(), entity4.getProperties());
        assertNotSame(entity3.getProperties(), entity4.getProperties());

        List<Property<?>> p1 = new ArrayList<>();
        List<Property<?>> p2 = new ArrayList<>();
        entity3.getProperties().forEach(p1::add);
        entity4.getProperties().forEach(p2::add);
        assertNotEquals(p1, p2);
        assertEquals(p1.get(0), p2.get(1));
        assertEquals(p1.get(1), p2.get(0));

        entity4.internProperties();
        assertSame(entity4.getProperties(), entity3.getProperties());

        try {
            entity.getProperties().setProperty("", "");
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        try {
            entity.getProperties().add(p);
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        try {
            entity.getProperties().clear();
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        try {
            entity.getProperties().putAll(new HashMap<>());
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        try {
            entity.getProperties().remove("");
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }
    }

    /**
     * Test of PropertySelector nested class, of class Properties.
     */
    @Test
    public void testPropertySelector() {
        final Collection<Properties.Entity> c = new ArrayList<>();

        final Properties.Entity e1 = new Properties.Entity();
        e1.getProperties().setProperty("p1", "1");
        e1.getProperties().setProperty("p2", "2");
        c.add(e1);

        final Properties.Entity e2 = new Properties.Entity();
        e2.getProperties().setProperty("p2", "2");
        e2.getProperties().setProperty("p1", "1");
        e2.getProperties().setProperty("p3", new int[]{1, 2, 3});
        c.add(e2);

        final Properties.Entity e3 = new Properties.Entity();
        e3.getProperties().setProperty("p3", new int[]{1, 2, 3});
        e3.getProperties().setProperty("p4", "4");
        c.add(e3);

        final PropertySelector<Properties.Entity> sel = new PropertySelector<>(c);

        final EqualityPropertyMatcher matcher1 = new EqualityPropertyMatcher("p2", "2");
        List<? extends Properties.Provider> selected = sel.selectMultiple(matcher1);
        assertEquals(selected.size(), 2);
        assertTrue(selected.contains(e1));
        assertTrue(selected.contains(e2));
        Properties.Provider select = sel.selectSingle(matcher1);
        assertTrue(select.equals(e1) || select.equals(e2));

        final EqualityPropertyMatcher matcher2 = new EqualityPropertyMatcher("p3", new int[]{1, 2, 3});
        selected = sel.selectMultiple(matcher2);
        assertEquals(selected.size(), 2);
        assertTrue(selected.contains(e2));
        assertTrue(selected.contains(e3));
        select = sel.selectSingle(matcher2);
        assertTrue(select.equals(e2) || select.equals(e3));

        final EqualityPropertyMatcher matcher3 = new EqualityPropertyMatcher("p4", "4");
        selected = sel.selectMultiple(matcher3);
        assertEquals(selected.size(), 1);
        assertTrue(selected.contains(e3));
        select = sel.selectSingle(matcher3);
        assertEquals(select, e3);

        final EqualityPropertyMatcher matcher4 = new EqualityPropertyMatcher("p5", "5");
        assertEquals(sel.selectMultiple(matcher4).size(), 0);
        assertNull(sel.selectSingle(matcher4));
    }

    /**
     * Test of the PropertyMatchers nested classes, of class Properties.
     */
    @Test
    @SuppressWarnings("unused")
    public void testPropertyMatchers() {
        final EqualityPropertyMatcher matcher = new EqualityPropertyMatcher("p1", new int[]{1, 2, 3});
        assertEquals("p1", matcher.getName());
        assertTrue(matcher.match(new int[]{1, 2, 3}));
        assertFalse(matcher.match("2"));
        assertFalse(matcher.match(null));

        try {
            new EqualityPropertyMatcher(null, "**");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        EqualityPropertyMatcher equalityPropertyMatcher = new EqualityPropertyMatcher("p1", null);
        assertEquals("p1", equalityPropertyMatcher.getName());
        assertFalse(equalityPropertyMatcher.match("1"));
        assertFalse(equalityPropertyMatcher.match("2"));
        assertTrue(equalityPropertyMatcher.match(null));

        final RegexpPropertyMatcher matcher2 = new RegexpPropertyMatcher("p1", "C.*");
        assertEquals("p1", matcher2.getName());
        assertTrue(matcher2.match("C"));
        assertTrue(matcher2.match("Casdf"));
        assertFalse(matcher2.match(" C"));
        assertFalse(matcher2.match("c"));
        assertFalse(matcher2.match("asdfC"));
        assertFalse(matcher2.match(null));

        try {
            new RegexpPropertyMatcher("p1", "**");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        try {
            new RegexpPropertyMatcher(null, "1");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        try {
            new RegexpPropertyMatcher("p1", null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        final InvertPropertyMatcher matcher3 = new InvertPropertyMatcher(matcher);
        assertEquals("p1", matcher3.getName());
        assertFalse(matcher3.match(new int[]{1, 2, 3}));
        assertTrue(matcher3.match("2"));
        assertTrue(matcher3.match(null));

        final InvertPropertyMatcher matcher4 = new InvertPropertyMatcher(matcher2);
        assertEquals("p1", matcher4.getName());
        assertFalse(matcher4.match("C"));
        assertFalse(matcher4.match("Casdf"));
        assertTrue(matcher4.match(" C"));
        assertTrue(matcher4.match("c"));
        assertTrue(matcher4.match("asdfC"));
        assertTrue(matcher4.match(1));
    }

    @Test
    public void testToString() {
        Properties p = Properties.newProperties();
        assertEquals("[]", p.toString());

        p.setProperty("p1", "1");
        assertEquals("[p1=1]", p.toString());

        Properties p2 = Properties.newProperties();
        p2.setProperty("p1", "1");
        p2.setProperty("p2", new int[]{1, 2, 3});
        assertEquals("[p1=1, p2=[1, 2, 3]]", p2.toString());

        Properties p3 = Properties.newProperties();
        p3.setProperty("p2", new int[]{1, 2, 3});
        p3.setProperty("p1", "1");
        assertEquals("[p1=1, p2=[1, 2, 3]]", p3.toString());

        p3.setProperty("p0", "0");
        assertEquals(p3.toString(), "[p0=0, p1=1, p2=[1, 2, 3]]");

        p2.setProperty("p1", null);
        assertEquals("[p1=null, p2=[1, 2, 3]]", p2.toString());

        p2.setProperty("p2", null);
        assertEquals("[p1=null, p2=null]", p2.toString());

        p2.remove("p1");
        assertEquals("[p2=null]", p2.toString());

        p2.remove("p2");
        assertEquals("[]", p2.toString());
    }

    /**
     * Test of size method, of class Properties.
     */
    @Test
    public void testSize() {
        Properties instance = Properties.newProperties();
        assertEquals(0, instance.size());

        instance.setProperty("p1", null);
        assertEquals(1, instance.size());

        instance.setProperty("p1", null);
        assertEquals(1, instance.size());

        instance.setProperty("p2", null);
        assertEquals(2, instance.size());

        Properties instance2 = Properties.newProperties();
        instance2.setProperty("p1", null);
        instance2.setProperty("p3", null);
        instance2.setProperty("p4", null);
        assertEquals(3, instance2.size());

        instance.add(instance2);
        assertEquals(4, instance.size());
    }

    /**
     * Test of the hashCode method of class Properties.
     */
    @Test
    public void testHashCode() {
        Properties p1 = Properties.newProperties("name1", "val1");
        Properties p2 = Properties.newProperties("name1", "val1");
        assertEquals(p2.hashCode(), p1.hashCode());

        p2.setProperty("name2", "val2");
        assertNotEquals(p1.hashCode(), p2.hashCode());

        p1.setProperty("name2", "val2");
        assertEquals(p1.hashCode(), p2.hashCode());

        p2.setProperty("name2", "val3");
        assertNotEquals(p1.hashCode(), p2.hashCode());

        p1.setProperty("name2", "val3");
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    /**
     * Test of toMap method, of class Properties.
     */
    @Test
    public void testToMap() {
        Map<String, Object> props = new HashMap<>();
        Set<String> excludes = new HashSet<>(List.of("p2"));
        Properties instance = Properties.newProperties("p1", "1", "p2", "2", "p3", 3);

        Map<String, Object> expResult = new HashMap<>();
        expResult.put("p1", "1");
        expResult.put("p3", 3);

        Map<String, Object> result = instance.toMap(props, excludes);
        assertEquals(expResult, result);
    }

    /**
     * Test of isEmpty method, of class Properties.
     */
    @Test
    public void testIsEmpty() {
        Properties instance = Properties.newProperties();
        assertTrue(instance.isEmpty());
        assertEquals(0, instance.size());

        instance.setProperty("p1", null);
        assertFalse(instance.isEmpty());
        assertNotEquals(0, instance.size());

        instance.remove("p1");
        assertTrue(instance.isEmpty());
        assertEquals(0, instance.size());
    }

    /**
     * Test of the containsKey/containsValue/remove methods, of class Properties.
     */
    @Test
    public void testContainsRemove() {
        Properties instance = Properties.newProperties("p1", "1", "p2", new int[]{1, 2, 3});
        assertTrue(instance.containsName("p1"));
        assertTrue(instance.containsName("p2"));
        assertFalse(instance.containsName("p3"));

        assertTrue(instance.containsValue("1"));
        assertTrue(instance.containsValue(new int[]{1, 2, 3}));
        assertFalse(instance.containsValue("3"));

        Object obj = instance.remove("p2");
        assertTrue(Objects.deepEquals(obj, new int[]{1, 2, 3}));
        assertTrue(instance.containsName("p1"));
        assertFalse(instance.containsName("p2"));
        assertFalse(instance.containsName("p3"));

        assertTrue(instance.containsValue("1"));
        assertFalse(instance.containsValue(new int[]{1, 2, 3}));
        assertFalse(instance.containsValue("3"));

        instance.setProperty("p3", "3");
        assertTrue(instance.containsName("p1"));
        assertFalse(instance.containsName("p2"));
        assertTrue(instance.containsName("p3"));

        assertTrue(instance.containsValue("1"));
        assertFalse(instance.containsValue(new int[]{1, 2, 3}));
        assertTrue(instance.containsValue("3"));

        obj = instance.remove("p1");
        assertEquals("1", obj);
        assertFalse(instance.containsName("p1"));
        assertFalse(instance.containsName("p2"));
        assertTrue(instance.containsName("p3"));

        assertFalse(instance.containsValue("1"));
        assertFalse(instance.containsValue(new int[]{1, 2, 3}));
        assertTrue(instance.containsValue("3"));

        obj = instance.remove("p3");
        assertEquals("3", obj);
        assertFalse(instance.containsName("p1"));
        assertFalse(instance.containsName("p2"));
        assertFalse(instance.containsName("p3"));

        assertFalse(instance.containsValue("1"));
        assertFalse(instance.containsValue(new int[]{1, 2, 3}));
        assertFalse(instance.containsValue("3"));

        assertTrue(instance.isEmpty());
        assertEquals(0, instance.size());
    }

    /**
     * Test of putAll method, of class Properties.
     */
    @Test
    public void testPutAll() {
        Map<String, Object> m = new HashMap<>();
        m.put("p1", "1");
        m.put("p2", new int[]{1, 2, 3});
        m.put("p4", "4");

        Properties instance = Properties.newProperties("p1", null, "p2", 2, "p3", "3");
        assertTrue(instance.containsName("p1"));
        assertTrue(instance.containsName("p2"));
        assertTrue(instance.containsName("p3"));
        assertFalse(instance.containsName("p4"));
        assertFalse(instance.containsValue("1"));
        assertFalse(instance.containsValue(new int[]{1, 2, 3}));
        assertTrue(instance.containsValue(null));
        assertTrue(instance.containsValue(2));
        assertTrue(instance.containsValue("3"));
        assertFalse(instance.containsValue("4"));

        instance.putAll(m);
        assertTrue(instance.containsName("p1"));
        assertTrue(instance.containsName("p2"));
        assertTrue(instance.containsName("p3"));
        assertTrue(instance.containsName("p4"));
        assertFalse(instance.containsValue(null));
        assertFalse(instance.containsValue(2));
        assertTrue(instance.containsValue("1"));
        assertTrue(instance.containsValue(new int[]{1, 2, 3}));
        assertTrue(instance.containsValue("3"));
        assertTrue(instance.containsValue("4"));

        try {
            instance.putAll(null);
            fail();
        } catch (NullPointerException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }
    }

    /**
     * Test of clear method, of class Properties.
     */
    @Test
    public void testClear() {
        Properties instance = Properties.newProperties("p1", "1", "p2", "2", "p3", "3");
        assertFalse(instance.isEmpty());
        assertEquals(3, instance.size());

        instance.clear();
        assertTrue(instance.isEmpty());
        assertEquals(0, instance.size());
    }

    /**
     * Test of get method, of class Properties.
     */
    @Test
    public void testGet() {
        Properties instance = Properties.newProperties("p1", "1", "p2", null, "p3", "3");
        assertEquals("1", instance.get("p1"));
        assertNull(instance.get("p2"));
        assertEquals("3", instance.get("p3"));
        assertNull(instance.get("p4"));
    }

    /**
     * Test of get method, of class Properties.
     */
    @Test
    public void testGetTyped() {
        Properties instance = Properties.newProperties("p1", "1", "p2", 2, "p3", 3.0);

        assertEquals("1", instance.get("p1", String.class));
        try {
            instance.get("p1", Integer.class);
            fail();
        } catch (AssertionError e) {
            assertEquals("Property value is of different class: " + String.class.getName(), e.getMessage());
        } catch (Throwable t) {
            fail();
        }

        assertEquals((Integer) 2, instance.get("p2", Integer.class));
        try {
            instance.get("p2", String.class);
            fail();
        } catch (AssertionError e) {
            assertEquals("Property value is of different class: " + Integer.class.getName(), e.getMessage());
        } catch (Throwable t) {
            fail();
        }

        assertEquals((Double) 3.0, instance.get("p3", Double.class));
        try {
            instance.get("p3", String.class);
            fail();
        } catch (AssertionError e) {
            assertEquals("Property value is of different class: " + Double.class.getName(), e.getMessage());
        } catch (Throwable t) {
            fail();
        }

        assertNull(instance.get(null, String.class));

        try {
            instance.get("p3", null);
            fail();
        } catch (NullPointerException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }
    }

    /**
     * Test of setProperty method, of class Properties.
     */
    @Test
    public void testSetProperty() {
        Properties instance = Properties.newProperties();

        assertTrue(instance.isEmpty());
        assertEquals(0, instance.size());

        instance.setProperty("p1", "1");

        assertFalse(instance.isEmpty());
        assertEquals(1, instance.size());

        assertTrue(instance.containsName("p1"));
        assertTrue(instance.containsValue("1"));

        assertFalse(instance.containsName("p2"));
        assertFalse(instance.containsValue(null));

        assertEquals("1", instance.get("p1"));
        assertNull(instance.get("p2"));

        assertEquals("1", instance.get("p1", String.class));
        assertNull(instance.get("p2", String.class));

        instance.setProperty("p2", new int[]{1, 2, 3});

        assertFalse(instance.isEmpty());
        assertEquals(2, instance.size());

        assertTrue(instance.containsName("p1"));
        assertTrue(instance.containsValue("1"));

        assertTrue(instance.containsName("p2"));
        assertTrue(instance.containsValue(new int[]{1, 2, 3}));
        assertFalse(instance.containsValue(2));

        assertEquals("1", instance.get("p1"));
        assertTrue(Objects.deepEquals(new int[]{1, 2, 3}, instance.get("p2")));

        assertEquals("1", instance.get("p1", String.class));
        assertTrue(Objects.deepEquals(new int[]{1, 2, 3}, instance.get("p2", int[].class)));

        try {
            instance.setProperty(null, "");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }
    }

    /**
     * Test of iterator method, of class Properties.
     */
    @Test
    public void testIterator() {
        Properties instance = Properties.newProperties("p1", "1", "p2", new int[]{1, 2, 3}, "p3", 3.0);
        List<Property<?>> objs = new ArrayList<>();
        for (Property<?> p : instance) {
            objs.add(p);
        }
        assertEquals(objs.size(), 3);

        assertEquals(objs.get(0).getName(), "p1");
        assertEquals(objs.get(1).getName(), "p2");
        assertEquals(objs.get(2).getName(), "p3");

        assertEquals(objs.get(0).getValue(), "1");
        assertTrue(Objects.deepEquals(objs.get(1).getValue(), new int[]{1, 2, 3}));
        assertEquals(objs.get(2).getValue(), 3.0);

        Iterator<Property<Object>> result = instance.iterator();
        assertTrue(result.hasNext());
        assertEquals(new Property<>("p1", "1"), result.next());
        assertTrue(result.hasNext());
        assertEquals(new Property<>("p2", new int[]{1, 2, 3}), result.next());
        assertTrue(result.hasNext());
        assertEquals(new Property<>("p3", 3.0), result.next());
        assertFalse(result.hasNext());
        try {
            result.next();
            fail();
        } catch (NoSuchElementException ex) {
            // expected
        } catch (Throwable t) {
            fail();
        }
        try {
            result.remove();
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }
    }

    /**
     * Test of iterator method, of class Properties.
     */
    @Test
    @SuppressWarnings("unused")
    public void testTypedIterator() {
        Properties instance = Properties.newProperties("p1", "1", "p2", new int[]{1, 2, 3}, "p3", 3.0);
        instance.setProperty("null", null);
        List<Property<?>> objs = new ArrayList<>();
        for (Property<String> obj : instance.typedIter(String.class)) {
            objs.add(obj);
        }
        assertEquals(objs.size(), 1);

        assertEquals(objs.get(0).getName(), "p1");
        assertEquals(objs.get(0).getValue(), "1");

        Iterator<Property<String>> resultString = instance.typedIter(String.class).iterator();
        assertTrue(resultString.hasNext());
        assertEquals(new Property<>("p1", "1"), resultString.next());
        assertFalse(resultString.hasNext());
        try {
            resultString.next();
            fail();
        } catch (NoSuchElementException ex) {
            // expected
        } catch (Throwable t) {
            fail();
        }
        try {
            resultString.remove();
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        objs.clear();
        for (Property<int[]> obj : instance.typedIter(int[].class)) {
            objs.add(obj);
        }
        assertEquals(objs.size(), 1);

        assertEquals(objs.get(0).getName(), "p2");
        assertTrue(Objects.deepEquals(objs.get(0).getValue(), new int[]{1, 2, 3}));

        Iterator<Property<int[]>> resultInteger = instance.typedIter(int[].class).iterator();
        assertTrue(resultInteger.hasNext());
        assertEquals(new Property<>("p2", new int[]{1, 2, 3}), resultInteger.next());
        assertFalse(resultInteger.hasNext());
        try {
            resultInteger.next();
            fail();
        } catch (NoSuchElementException ex) {
            // expected
        } catch (Throwable t) {
            fail();
        }
        try {
            resultInteger.remove();
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        objs.clear();
        for (Property<Double> obj : instance.typedIter(Double.class)) {
            objs.add(obj);
        }
        assertEquals(objs.size(), 1);

        assertEquals(objs.get(0).getName(), "p3");
        assertEquals(objs.get(0).getValue(), 3.0);

        Iterator<Property<Double>> resultDouble = instance.typedIter(Double.class).iterator();
        assertTrue(resultDouble.hasNext());
        assertEquals(new Property<>("p3", 3.0), resultDouble.next());
        assertFalse(resultDouble.hasNext());
        try {
            resultDouble.next();
            fail();
        } catch (NoSuchElementException ex) {
            // expected
        } catch (Throwable t) {
            fail();
        }
        try {
            resultDouble.remove();
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        objs.clear();
        for (Property<Object> obj : instance.typedIter(Object.class)) {
            objs.add(obj);
        }
        assertEquals(objs.size(), 3);

        assertEquals(objs.get(0).getName(), "p1");
        assertEquals(objs.get(1).getName(), "p2");
        assertEquals(objs.get(2).getName(), "p3");

        assertEquals(objs.get(0).getValue(), "1");
        assertTrue(Objects.deepEquals(objs.get(1).getValue(), new int[]{1, 2, 3}));
        assertEquals(objs.get(2).getValue(), 3.0);

        Iterator<Property<Object>> resultObject = instance.typedIter(Object.class).iterator();
        assertTrue(resultObject.hasNext());
        assertEquals(new Property<>("p1", "1"), resultObject.next());
        assertTrue(resultObject.hasNext());
        assertEquals(new Property<>("p2", new int[]{1, 2, 3}), resultObject.next());
        assertTrue(resultObject.hasNext());
        assertEquals(new Property<>("p3", 3.0), resultObject.next());
        assertFalse(resultObject.hasNext());
        try {
            resultObject.next();
            fail();
        } catch (NoSuchElementException ex) {
            // expected
        } catch (Throwable t) {
            fail();
        }
        try {
            resultObject.remove();
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        objs.clear();
        for (Property<Long> obj : instance.typedIter(Long.class)) {
            objs.add(obj);
        }
        assertTrue(objs.isEmpty());

        Iterator<Property<Long>> resultLong = instance.typedIter(Long.class).iterator();
        assertFalse(resultLong.hasNext());
        try {
            resultLong.next();
            fail();
        } catch (NoSuchElementException ex) {
            // expected
        } catch (Throwable t) {
            fail();
        }
        try {
            resultLong.remove();
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }

        try {
            for (Property<?> ignored : instance.typedIter(null)) {
                fail();
            }
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        } catch (Throwable t) {
            fail();
        }
    }
}
