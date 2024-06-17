/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.MethodGuardIdempotentTestFactory.FinalReadAssertionNodeGen;
import com.oracle.truffle.api.dsl.test.MethodGuardIdempotentTestFactory.MethodIdempotentNodeGen;
import com.oracle.truffle.api.dsl.test.MethodGuardIdempotentTestFactory.MethodNonIdempotentNodeGen;
import com.oracle.truffle.api.dsl.test.MethodGuardIdempotentTestFactory.MethodUnkownNodeGen;
import com.oracle.truffle.api.dsl.test.MethodGuardIdempotentTestFactory.NonFinalReadAssertionNodeGen;

public class MethodGuardIdempotentTest {

    static class TestObject {

        final int finalValue = 42;

        int nonFinalValue = 42;

        @NonIdempotent
        int nonIdempotent() {
            return nonFinalValue;
        }

        @Idempotent
        int idempotent() {
            return nonFinalValue;
        }

        int unknown() {
            return nonFinalValue;
        }
    }

    @ImportStatic(Assumption.class)
    @SuppressWarnings("unused")
    abstract static class FinalReadAssertionNode extends SlowPathListenerNode {

        abstract String execute(Object arg);

        @Specialization(guards = "o.finalValue == 42")
        public String s0(
                        Object arg,
                        @Cached("new()") TestObject o) {
            return "s0";
        }

        @Specialization
        public String s1(Object arg) {
            return "s1";
        }

    }

    @Test
    public void testFinalReadAssertionNode() {
        FinalReadAssertionNode node = FinalReadAssertionNodeGen.create();

        assertEquals("s0", node.execute(0));
        assertEquals("s0", node.execute(0));

        assertEquals(1, node.specializeCount);
    }

    @ImportStatic(Assumption.class)
    @SuppressWarnings("unused")
    abstract static class NonFinalReadAssertionNode extends SlowPathListenerNode {

        abstract String execute(TestObject arg);

        @Specialization(guards = "o.nonFinalValue == 42")
        public String s0(
                        TestObject arg,
                        @Cached(value = "arg", neverDefault = true) TestObject o) {
            return "s0";
        }

        @Specialization
        public String s1(TestObject arg) {
            return "s1";
        }

    }

    @Test
    public void testNonFinalReadAssertionNode() {
        TestObject o = new TestObject();
        NonFinalReadAssertionNode node = NonFinalReadAssertionNodeGen.create();

        assertEquals("s0", node.execute(o));
        assertEquals("s0", node.execute(o));

        o.nonFinalValue = 41;

        assertEquals("s1", node.execute(o));
        assertEquals("s1", node.execute(o));

        assertEquals(2, node.specializeCount);
    }

    @SuppressWarnings("unused")
    abstract static class MethodUnkownNode extends SlowPathListenerNode {

        abstract String execute(TestObject arg);

        @ExpectError("The guard '(o.unknown() == 42)' invokes methods that would benefit from the @Idempotent or @NonIdempotent annotations:%")
        @Specialization(guards = "o.unknown() == 42")
        public String s0(
                        TestObject arg,
                        @Cached(value = "arg", neverDefault = true) TestObject o) {
            return "s0";
        }

        @Specialization
        public String s1(TestObject arg) {
            return "s1";
        }

    }

    @Test
    public void testMethodUnkownNode() {
        TestObject o = new TestObject();
        MethodUnkownNode node = MethodUnkownNodeGen.create();

        assertEquals("s0", node.execute(o));
        assertEquals("s0", node.execute(o));

        o.nonFinalValue = 41;

        assertFails(() -> node.execute(o), AssertionError.class, (e) -> assertEquals("A guard was assumed idempotent, but returned a different value for a consecutive execution.", e.getMessage()));
        assertFails(() -> node.execute(o), AssertionError.class, (e) -> assertEquals("A guard was assumed idempotent, but returned a different value for a consecutive execution.", e.getMessage()));

        assertEquals(1, node.specializeCount);
    }

    @SuppressWarnings("unused")
    abstract static class MethodIdempotentNode extends SlowPathListenerNode {

        abstract String execute(TestObject arg);

        @Specialization(guards = "o.idempotent() == 42")
        public String s0(
                        TestObject arg,
                        @Cached(value = "arg", neverDefault = true) TestObject o) {
            return "s0";
        }

        @Specialization
        public String s1(TestObject arg) {
            return "s1";
        }

    }

    @Test
    public void testMethodIdempotentNode() {
        TestObject o = new TestObject();
        MethodIdempotentNode node = MethodIdempotentNodeGen.create();

        assertEquals("s0", node.execute(o));
        assertEquals("s0", node.execute(o));

        o.nonFinalValue = 41;

        assertFails(() -> node.execute(o), AssertionError.class, (e) -> assertEquals("A guard was assumed idempotent, but returned a different value for a consecutive execution.", e.getMessage()));
        assertFails(() -> node.execute(o), AssertionError.class, (e) -> assertEquals("A guard was assumed idempotent, but returned a different value for a consecutive execution.", e.getMessage()));

        assertEquals(1, node.specializeCount);
    }

    @SuppressWarnings("unused")
    abstract static class MethodNonIdempotentNode extends SlowPathListenerNode {

        abstract String execute(TestObject arg);

        @Specialization(guards = "o.nonIdempotent() == 42")
        public String s0(
                        TestObject arg,
                        @Cached(value = "arg", neverDefault = true) TestObject o) {
            return "s0";
        }

        @Specialization
        public String s1(TestObject arg) {
            return "s1";
        }

    }

    @Test
    public void testMethodNonIdempotentNode() {
        TestObject o = new TestObject();
        MethodNonIdempotentNode node = MethodNonIdempotentNodeGen.create();

        assertEquals("s0", node.execute(o));
        assertEquals("s0", node.execute(o));

        o.nonFinalValue = 41;

        assertEquals("s1", node.execute(o));
        assertEquals("s1", node.execute(o));

        assertEquals(2, node.specializeCount);
    }

    @SuppressWarnings("unused")
    @ImportStatic(Assumption.class)
    abstract static class MethodNonIdempotentBuiltinNode extends SlowPathListenerNode {

        abstract String execute(TestObject arg);

        static final ContextReference<TruffleLanguage<?>> CONTEXT_REF = null;
        static final ContextThreadLocal<Object> CONTEXT_THREAD_LOCAL = null;

        // now warning here we know these methods are non-idempotent
        @Specialization(guards = {"c.isValid()", "ALWAYS_VALID.isValid()", "b.isValid()", "isValidAssumption(b)",
                        "CONTEXT_REF.get($node) == null", "CONTEXT_THREAD_LOCAL.get() == null"})
        public String s0(
                        TestObject arg,
                        @Bind("ALWAYS_VALID") Assumption b,
                        @Cached("ALWAYS_VALID") Assumption c) {
            return "s0";
        }

        @Specialization
        public String s1(TestObject arg) {
            return "s1";
        }

    }

    @SuppressWarnings("unused")
    @ImportStatic(Assumption.class)
    abstract static class MethodIdempotentBuiltinNode extends SlowPathListenerNode {

        abstract String execute(TestObject arg);

        static final LanguageReference<TruffleLanguage<?>> REF = null;

        // now warning here. We know these guards are idempotent
        @Specialization(guards = {"REF.get($node) != null"})
        public String s0(TestObject arg) {
            return "s0";
        }

        @Specialization
        public String s1(TestObject arg) {
            return "s1";
        }

    }

    @SuppressWarnings("unused")
    abstract static class MethodUnkownAndIdempotentNode extends SlowPathListenerNode {

        abstract String execute(TestObject arg);

        @TruffleBoundary
        static Assumption getAssumption() {
            return Truffle.getRuntime().createAssumption();
        }

        // now warning here. One guard is non-idempotent, no guards are needed.
        @Specialization(guards = {"getAssumption().isValid()"})
        public String s0(TestObject arg) {
            return "s0";
        }

        @Specialization
        public String s1(TestObject arg) {
            return "s1";
        }

    }

    @SuppressWarnings("unused")
    abstract static class MethodOverloadIdempotentNode extends SlowPathListenerNode {

        static final TestObject VALUE = null;

        abstract String execute(TestObject arg);

        // no warning here: guard(TestObject arg) should get selected by overload.
        @Specialization(guards = {"guard(VALUE)"})
        public String s0(TestObject arg) {
            return "s0";
        }

        public static boolean guard(Object arg) {
            return arg == null;
        }

        @NonIdempotent
        public static boolean guard(TestObject arg) {
            return arg == null;
        }

        @Specialization
        public String s1(TestObject arg) {
            return "s1";
        }

    }

}
