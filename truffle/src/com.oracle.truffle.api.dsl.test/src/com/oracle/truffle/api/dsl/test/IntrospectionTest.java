/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.IntrospectionTestFactory.FallbackNodeGen;
import com.oracle.truffle.api.dsl.test.IntrospectionTestFactory.Introspection1NodeGen;
import com.oracle.truffle.api.dsl.test.IntrospectionTestFactory.TrivialNodeGen;
import com.oracle.truffle.api.nodes.Node;

public class IntrospectionTest {

    @TypeSystem
    public static class IntrospectionTypeSystem {

    }

    @TypeSystemReference(IntrospectionTypeSystem.class)
    @Introspectable
    public static class ReflectableNode extends Node {

    }

    @SuppressWarnings("unused")
    @TypeSystemReference(IntrospectionTypeSystem.class)
    // BEGIN: com.oracle.truffle.api.dsl.test.IntrospectionTest
    @Introspectable
    abstract static class NegateNode extends Node {

        abstract Object execute(Object o);

        @Specialization(guards = "cachedvalue == value", limit = "1")
        protected static int doInt(int value,
                        @Cached("value") int cachedvalue) {
            return -cachedvalue;
        }

        @Specialization(replaces = "doInt")
        protected static int doGeneric(int value) {
            return -value;
        }
    }

    @Test
    public void testUsingIntrospection() {
        NegateNode node = IntrospectionTestFactory.NegateNodeGen.create();
        SpecializationInfo info;

        node.execute(1);
        info = Introspection.getSpecialization(node, "doInt");
        assertEquals(1, info.getInstances());

        node.execute(1);
        info = Introspection.getSpecialization(node, "doInt");
        assertEquals(1, info.getInstances());

        node.execute(2);
        info = Introspection.getSpecialization(node, "doInt");
        assertEquals(0, info.getInstances());

        info = Introspection.getSpecialization(node, "doGeneric");
        assertEquals(1, info.getInstances());
    }
    // END: com.oracle.truffle.api.dsl.test.IntrospectionTest

    public abstract static class Introspection1Node extends ReflectableNode {

        abstract Object execute(Object o);

        @Specialization(guards = "cachedO == o", limit = "3")
        protected static int doInt(int o, @SuppressWarnings("unused") @Cached("o") int cachedO) {
            return o;
        }

        @Specialization(replaces = "doInt")
        protected static int doGeneric(int o) {
            return o;
        }
    }

    @Test
    public void testReflection1() {
        Introspection1Node node = Introspection1NodeGen.create();
        SpecializationInfo specialization = Introspection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertFalse(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(0, specialization.getInstances());
        try {
            assertEquals(0, specialization.getCachedData(0));
            fail();
        } catch (IllegalArgumentException e) {
        }

        specialization = Introspection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertFalse(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(0, specialization.getInstances());

        try {
            node.execute("");
            fail();
        } catch (UnsupportedSpecializationException e) {
        }

        node.execute(1);

        specialization = Introspection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertTrue(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(1, specialization.getInstances());
        assertEquals(1, specialization.getCachedData(0).get(0));

        specialization = Introspection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertFalse(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(0, specialization.getInstances());

        node.execute(1);

        specialization = Introspection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertTrue(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(1, specialization.getInstances());
        assertEquals(1, specialization.getCachedData(0).get(0));

        specialization = Introspection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertFalse(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(0, specialization.getInstances());

        node.execute(2);

        specialization = Introspection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertTrue(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(2, specialization.getInstances());
        assertEquals(2, specialization.getCachedData(0).get(0));
        assertEquals(1, specialization.getCachedData(1).get(0));

        specialization = Introspection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertFalse(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(0, specialization.getInstances());

        node.execute(3);

        specialization = Introspection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertTrue(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(3, specialization.getInstances());
        assertEquals(3, specialization.getCachedData(0).get(0));
        assertEquals(2, specialization.getCachedData(1).get(0));
        assertEquals(1, specialization.getCachedData(2).get(0));

        specialization = Introspection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertFalse(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(0, specialization.getInstances());

        node.execute(4);

        specialization = Introspection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertFalse(specialization.isActive());
        assertTrue(specialization.isExcluded());
        assertEquals(0, specialization.getInstances());

        specialization = Introspection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertTrue(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(1, specialization.getInstances());
        assertEquals(0, specialization.getCachedData(0).size());
    }

    @Test
    public void testFallbackReflection() {
        FallbackNode node = FallbackNodeGen.create();

        SpecializationInfo specialization = Introspection.getSpecialization(node, "doFallback");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(1));
        assertEquals("doFallback", specialization.getMethodName());
        assertFalse(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(0, specialization.getInstances());

        node.execute("fallback");
        specialization = Introspection.getSpecialization(node, "doFallback");
        assertSpecializationEquals(specialization, Introspection.getSpecializations(node).get(1));
        assertEquals("doFallback", specialization.getMethodName());
        assertTrue(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(1, specialization.getInstances());

    }

    public abstract static class FallbackNode extends ReflectableNode {

        abstract Object execute(Object o);

        @Specialization
        protected static int doGeneric(int o) {
            return o;
        }

        @Fallback
        protected static Object doFallback(Object fallback) {
            return fallback;
        }
    }

    private static void assertSpecializationEquals(SpecializationInfo s1, SpecializationInfo s2) {
        assertEquals(s1.getMethodName(), s2.getMethodName());
        assertEquals(s1.isActive(), s2.isActive());
        assertEquals(s1.isExcluded(), s2.isExcluded());
        assertEquals(s1.getInstances(), s2.getInstances());

        for (int i = 0; i < s1.getInstances(); i++) {
            List<Object> cachedData1 = s1.getCachedData(i);
            List<Object> cachedData2 = s2.getCachedData(i);
            assertEquals(cachedData1.size(), cachedData2.size());
            for (int j = 0; j < cachedData1.size(); j++) {
                assertEquals(cachedData1.get(j), cachedData2.get(j));
            }
        }
    }

    @Test
    public void testTrivialNode() {
        TrivialNode node = TrivialNodeGen.create();

        SpecializationInfo specialization = Introspection.getSpecialization(node, "doGeneric");
        assertTrue(specialization.isActive());
        assertFalse(specialization.isExcluded());
        assertEquals(1, specialization.getInstances());
    }

    @Introspectable
    abstract static class TrivialNode extends Node {

        abstract Object execute(Object o);

        @Specialization
        protected static Object doGeneric(Object value) {
            return value;
        }
    }

}
