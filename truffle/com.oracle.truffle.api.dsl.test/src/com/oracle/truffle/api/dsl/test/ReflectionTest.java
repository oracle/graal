/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Reflectable;
import com.oracle.truffle.api.dsl.Reflection;
import com.oracle.truffle.api.dsl.Reflection.ReflectedSpecialization;
import com.oracle.truffle.api.dsl.Reflection.ReflectedSpecialization.State;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.internal.DSLOptions;
import com.oracle.truffle.api.dsl.internal.DSLOptions.DSLGenerator;
import com.oracle.truffle.api.dsl.test.ReflectionTestFactory.FallbackNodeGen;
import com.oracle.truffle.api.dsl.test.ReflectionTestFactory.Reflection1NodeGen;
import com.oracle.truffle.api.nodes.Node;

public class ReflectionTest {

    @TypeSystem
    @DSLOptions(defaultGenerator = DSLGenerator.FLAT)
    public static class ReflectionTypeSystem {

    }

    @TypeSystemReference(ReflectionTypeSystem.class)
    @Reflectable
    public static class ReflectableNode extends Node {

    }

    public abstract static class Reflection1Node extends ReflectableNode {

        abstract Object execute(Object o);

        @Specialization(guards = "cachedO == o")
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
        Reflection1Node node = Reflection1NodeGen.create();
        ReflectedSpecialization specialization = Reflection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertEquals(State.INACTIVE, specialization.getState());
        assertEquals(0, specialization.getInstances());
        try {
            assertEquals(0, specialization.getCachedData(0));
            fail();
        } catch (IllegalArgumentException e) {
        }

        specialization = Reflection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertEquals(State.INACTIVE, specialization.getState());
        assertEquals(0, specialization.getInstances());

        try {
            node.execute("");
            fail();
        } catch (UnsupportedSpecializationException e) {
        }

        node.execute(1);

        specialization = Reflection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertEquals(State.ACTIVE, specialization.getState());
        assertEquals(1, specialization.getInstances());
        assertEquals(1, specialization.getCachedData(0).get(0));

        specialization = Reflection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertEquals(State.INACTIVE, specialization.getState());
        assertEquals(0, specialization.getInstances());

        node.execute(1);

        specialization = Reflection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertEquals(State.ACTIVE, specialization.getState());
        assertEquals(1, specialization.getInstances());
        assertEquals(1, specialization.getCachedData(0).get(0));

        specialization = Reflection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertEquals(State.INACTIVE, specialization.getState());
        assertEquals(0, specialization.getInstances());

        node.execute(2);

        specialization = Reflection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertEquals(State.ACTIVE, specialization.getState());
        assertEquals(2, specialization.getInstances());
        assertEquals(2, specialization.getCachedData(0).get(0));
        assertEquals(1, specialization.getCachedData(1).get(0));

        specialization = Reflection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertEquals(State.INACTIVE, specialization.getState());
        assertEquals(0, specialization.getInstances());

        node.execute(3);

        specialization = Reflection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertEquals(State.ACTIVE, specialization.getState());
        assertEquals(3, specialization.getInstances());
        assertEquals(3, specialization.getCachedData(0).get(0));
        assertEquals(2, specialization.getCachedData(1).get(0));
        assertEquals(1, specialization.getCachedData(2).get(0));

        specialization = Reflection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertEquals(State.INACTIVE, specialization.getState());
        assertEquals(0, specialization.getInstances());

        node.execute(4);

        specialization = Reflection.getSpecialization(node, "doInt");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(0));
        assertEquals("doInt", specialization.getMethodName());
        assertEquals(State.EXCLUDED, specialization.getState());
        assertEquals(0, specialization.getInstances());

        specialization = Reflection.getSpecialization(node, "doGeneric");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(1));
        assertEquals("doGeneric", specialization.getMethodName());
        assertEquals(State.ACTIVE, specialization.getState());
        assertEquals(1, specialization.getInstances());
        assertEquals(0, specialization.getCachedData(0).size());
    }

    @Test
    public void testFallbackReflection() {
        FallbackNode node = FallbackNodeGen.create();

        ReflectedSpecialization specialization = Reflection.getSpecialization(node, "doFallback");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(1));
        assertEquals("doFallback", specialization.getMethodName());
        assertEquals(State.INACTIVE, specialization.getState());
        assertEquals(0, specialization.getInstances());

        node.execute("fallback");
        specialization = Reflection.getSpecialization(node, "doFallback");
        assertSpecializationEquals(specialization, Reflection.getSpecializations(node).get(1));
        assertEquals("doFallback", specialization.getMethodName());
        assertEquals(State.ACTIVE, specialization.getState());
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

    private static void assertSpecializationEquals(ReflectedSpecialization s1, ReflectedSpecialization s2) {
        assertEquals(s1.getMethodName(), s2.getMethodName());
        assertEquals(s1.getState(), s2.getState());
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

    @ExpectError("Reflection is not supported by the used DSL layout. Only the flat DSL layout supports reflection.")
    @Reflectable
    public abstract static class SomeReflection1Node extends Node {

        abstract Object execute(Object o);

        @Specialization
        protected static int doInt(int o) {
            return o;
        }
    }

}
