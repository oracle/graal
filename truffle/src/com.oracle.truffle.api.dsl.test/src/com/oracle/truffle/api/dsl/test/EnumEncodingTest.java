/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.EnumEncodingTestFactory.DefaultHandlingTestNodeGen;
import com.oracle.truffle.api.dsl.test.EnumEncodingTestFactory.GuaranteedNeverDefaultNodeGen;
import com.oracle.truffle.api.dsl.test.EnumEncodingTestFactory.NeverDefaultNodeGen;
import com.oracle.truffle.api.dsl.test.EnumEncodingTestFactory.SpecializationClassNodeGen;
import com.oracle.truffle.api.dsl.test.EnumEncodingTestFactory.WithDefaultNodeGen;
import com.oracle.truffle.api.dsl.test.EnumEncodingTestFactory.WithSharedClassNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class EnumEncodingTest extends AbstractPolyglotTest {

    enum Enum1 {
        E0,
    }

    enum Enum2 {
        E0,
        E1,
    }

    enum Enum3 {
        E0,
        E1,
        E2,
    }

    enum Enum8 {
        E0,
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
        E7,
    }

    @Introspectable
    @GenerateInline(value = true, inherit = true)
    @GenerateCached(value = true, inherit = true)
    @GenerateUncached(value = true, inherit = true)
    static class BaseNode extends Node implements SlowPathListener {

        static int specializeCounter = 0;

        public void afterSpecialize() {
            specializeCounter++;
        }

    }

    @SuppressWarnings("unused")
    abstract static class GuaranteedNeverDefaultNode extends BaseNode {

        abstract Enum1 execute(Node node, Object value);

        @Specialization
        Enum1 s0(Enum1 value,
                        @Cached("value") Enum1 enumValue) {
            return enumValue;
        }

    }

    @Test
    public void testGuaranteedNeverDefaultNode() {
        BaseNode.specializeCounter = 0;
        GuaranteedNeverDefaultNode node = GuaranteedNeverDefaultNodeGen.create();
        assertEquals(Enum1.E0, node.execute(null, Enum1.E0));
        assertEquals(Enum1.E0, node.execute(null, Enum1.E0));
        assertEquals(1, BaseNode.specializeCounter);
    }

    @SuppressWarnings("unused")
    abstract static class NeverDefaultNode extends BaseNode {

        abstract Enum8 execute(Node node, Enum8 value);

        @Specialization
        Enum8 s0(Enum8 value, @Cached("value") Enum8 enumValue) {
            return enumValue;
        }

    }

    @Test
    public void testNeverDefaultNode() {
        BaseNode.specializeCounter = 0;
        NeverDefaultNode node = NeverDefaultNodeGen.create();
        assertEquals(Enum8.E0, node.execute(null, Enum8.E0));
        assertEquals(Enum8.E0, node.execute(null, Enum8.E1));
        assertEquals(1, BaseNode.specializeCounter);
    }

    @SuppressWarnings("unused")
    abstract static class WithDefaultNode extends BaseNode {

        abstract Enum3 execute(Node node, Enum3 value);

        @Specialization
        Enum3 s0(Enum3 value, @Cached(value = "value", neverDefault = false) Enum3 enumValue) {
            return enumValue;
        }

    }

    @Test
    public void testWithDefaultNode() {
        BaseNode.specializeCounter = 0;
        WithDefaultNode node = WithDefaultNodeGen.create();
        assertEquals(Enum3.E0, node.execute(null, Enum3.E0));
        assertEquals(Enum3.E0, node.execute(null, Enum3.E1));
        assertEquals(1, BaseNode.specializeCounter);

        BaseNode.specializeCounter = 0;
        node = WithDefaultNodeGen.create();
        assertEquals(null, node.execute(null, null));
        assertEquals(null, node.execute(null, Enum3.E1));
        assertEquals(1, BaseNode.specializeCounter);
    }

    @SuppressWarnings("unused")
    @ImportStatic(Enum2.class)
    abstract static class WithSharedClassNode extends BaseNode {

        abstract Enum2 execute(Node node, Enum2 value);

        @Specialization(guards = "value == E0")
        Enum2 s0(Enum2 value, @Shared("cache") @Cached(value = "value", neverDefault = false) Enum2 enumValue) {
            return enumValue;
        }

        @Specialization(guards = "value == E1")
        Enum2 s1(Enum2 value, @Shared("cache") @Cached(value = "value", neverDefault = false) Enum2 enumValue) {
            return enumValue;
        }
    }

    @Test
    public void testWithSharedClassNode() {
        BaseNode.specializeCounter = 0;
        WithSharedClassNode node = WithSharedClassNodeGen.create();
        assertEquals(Enum2.E0, node.execute(null, Enum2.E0));
        assertEquals(Enum2.E0, node.execute(null, Enum2.E1));
        assertEquals(Enum2.E0, node.execute(null, Enum2.E0));
        assertEquals(Enum2.E0, node.execute(null, Enum2.E1));
        assertEquals(2, BaseNode.specializeCounter);

        BaseNode.specializeCounter = 0;
        node = WithSharedClassNodeGen.create();
        assertEquals(Enum2.E1, node.execute(null, Enum2.E1));
        assertEquals(Enum2.E1, node.execute(null, Enum2.E0));
        assertEquals(Enum2.E1, node.execute(null, Enum2.E1));
        assertEquals(Enum2.E1, node.execute(null, Enum2.E0));
        assertEquals(2, BaseNode.specializeCounter);
    }

    @SuppressWarnings("unused")
    @ImportStatic(Enum2.class)
    abstract static class SpecializationClassNode extends BaseNode {

        abstract Enum8 execute(Node node, Enum8 value);

        @Specialization(guards = "value == cachedValue", limit = "2")
        Enum8 s0(Enum8 value, @Cached("value") Enum8 cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testSpecializationClassNode() {
        BaseNode.specializeCounter = 0;
        SpecializationClassNode node = SpecializationClassNodeGen.create();
        assertEquals(Enum8.E0, node.execute(null, Enum8.E0));
        assertEquals(Enum8.E0, node.execute(null, Enum8.E0));
        assertEquals(Enum8.E1, node.execute(null, Enum8.E1));
        assertEquals(Enum8.E1, node.execute(null, Enum8.E1));
        assertEquals(2, BaseNode.specializeCounter);
    }

    /*
     * This class name triggers a naming confict with a class in the parent node. No need to execute
     * this node. The test is that the generate code compiles correctly.
     */
    @SuppressWarnings("unused")
    abstract static class NamingConflictNode extends BaseNode {

        abstract Object execute(Node node, Object value);

        enum Enum1 {
            E0,
        }

        @Specialization
        int s0(Enum1 value,
                        @Cached("value") Enum1 enumValue) {
            assertEquals(value, enumValue);
            return 42;
        }

    }

    @SuppressWarnings("unused")
    @ImportStatic(Enum2.class)
    abstract static class DefaultHandlingTestNode extends BaseNode {

        abstract Enum8 execute(Node node, Enum8 value);

        @Specialization
        Enum8 s0(Enum8 value, @Cached("value") Enum8 cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testDefaultHandlingTestNode() {
        BaseNode.specializeCounter = 0;
        DefaultHandlingTestNode node = DefaultHandlingTestNodeGen.create();
        assertEquals(Enum8.E0, node.execute(null, Enum8.E0));
        assertEquals(Enum8.E0, node.execute(null, Enum8.E1));
        assertEquals(1, BaseNode.specializeCounter);

        BaseNode.specializeCounter = 0;
        node = DefaultHandlingTestNodeGen.create();
        assertEquals(null, node.execute(null, null));
        assertEquals(null, node.execute(null, Enum8.E1));
        assertEquals(1, BaseNode.specializeCounter);
    }

}
