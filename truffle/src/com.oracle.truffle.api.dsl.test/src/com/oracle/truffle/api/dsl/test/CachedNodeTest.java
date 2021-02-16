/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.CachedNodeTestFactory.Cached1NodeGen;
import com.oracle.truffle.api.dsl.test.CachedNodeTestFactory.Cached2NodeGen;
import com.oracle.truffle.api.dsl.test.CachedNodeTestFactory.CustomCreateTakesPrecedenceCallNodeGen;
import com.oracle.truffle.api.dsl.test.CachedNodeTestFactory.CustomCreateTakesPrecedenceNodeGen;
import com.oracle.truffle.api.dsl.test.CachedNodeTestFactory.ValidDSLCachedNodeGen;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("unused")
public class CachedNodeTest {

    public static class ValidNode1 extends Node {

        private String result;

        ValidNode1(String result) {
            this.result = result;
        }

        String execute(Object arg0) {
            return result + arg0;
        }

        public static ValidNode1 create() {
            return new ValidNode1("cached ");
        }

        public static ValidNode1 getUncached() {
            return new ValidNode1("uncached ");
        }

    }

    public abstract static class Cached1Node extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @Cached ValidNode1 foo) {
            return foo.execute(arg0);
        }
    }

    @Test
    public void testDefaultNode1() {
        assertEquals("cached 42", Cached1NodeGen.create().execute(42));
    }

    public static class ValidNode2 extends Node {

        private String result;

        static int createCalls = 0;
        static int uncachedCalls = 0;

        ValidNode2(String result) {
            this.result = result;
        }

        String execute() {
            return result;
        }

        public static ValidNode2 create(Object arg0) {
            createCalls++;
            return new ValidNode2("cached " + arg0);
        }

        public static ValidNode2 getUncached(Object arg0) {
            uncachedCalls++;
            return new ValidNode2("uncached " + arg0);
        }

    }

    @GenerateUncached
    public abstract static class Cached2Node extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @Cached(parameters = "arg0") ValidNode2 foo) {
            return foo.execute();
        }
    }

    @Test
    public void testDefaultNode2() {
        ValidNode2.createCalls = 0;
        ValidNode2.uncachedCalls = 0;
        Cached2Node cachedNode = Cached2NodeGen.create();
        assertEquals(0, ValidNode2.createCalls);
        assertEquals("cached 42", cachedNode.execute(42));
        assertEquals(1, ValidNode2.createCalls);
        assertEquals("cached 42", cachedNode.execute(43));
        assertEquals(1, ValidNode2.createCalls);

        Cached2Node uncachedNode = Cached2NodeGen.getUncached();
        assertEquals(0, ValidNode2.uncachedCalls);
        assertEquals("uncached 42", uncachedNode.execute(42));
        assertEquals(1, ValidNode2.uncachedCalls);
        assertEquals("uncached 43", uncachedNode.execute(43));
        assertEquals(2, ValidNode2.uncachedCalls);
    }

    @GenerateUncached
    public abstract static class ValidDSLNode extends Node {

        abstract String execute(Object arg);

        @Specialization
        static String s0(int arg) {
            return "s0";
        }

        @Specialization
        static String s0(double arg) {
            return "s1";
        }

    }

    @GenerateUncached
    public abstract static class ValidDSLCachedNode extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @Cached ValidDSLNode foo) {
            return foo.execute(arg0);
        }
    }

    @Test
    public void testValidDSLNode2() {
        ValidDSLCachedNode cachedNode = ValidDSLCachedNodeGen.create();
        assertEquals("s0", cachedNode.execute(42));
        assertEquals("s1", cachedNode.execute(42d));

        ValidDSLCachedNode uncachedNode = ValidDSLCachedNodeGen.getUncached();
        assertEquals("s0", uncachedNode.execute(42));
        assertEquals("s1", uncachedNode.execute(42d));
    }

    @GenerateUncached
    public abstract static class CustomCreateTakesPrecedence extends Node {

        abstract String execute(Object arg);

        @Specialization
        static String s0(int arg) {
            return "s0";
        }

        @Specialization
        static String s0(double arg) {
            return "s1";
        }

        static int createCalled;
        static int uncachedCalled;

        public static CustomCreateTakesPrecedence create() {
            createCalled++;
            return CustomCreateTakesPrecedenceNodeGen.create();
        }

        public static CustomCreateTakesPrecedence getUncached() {
            uncachedCalled++;
            return CustomCreateTakesPrecedenceNodeGen.getUncached();
        }

    }

    @GenerateUncached
    public abstract static class CustomCreateTakesPrecedenceCallNode extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @Cached CustomCreateTakesPrecedence foo) {
            return foo.execute(arg0);
        }
    }

    @Test
    public void testCustomCreateTakesPrecedence() {
        CustomCreateTakesPrecedence.createCalled = 0;
        CustomCreateTakesPrecedence.uncachedCalled = 0;
        CustomCreateTakesPrecedenceCallNode cachedNode = CustomCreateTakesPrecedenceCallNodeGen.create();
        assertEquals("s0", cachedNode.execute(42));
        assertEquals(1, CustomCreateTakesPrecedence.createCalled);
        assertEquals(0, CustomCreateTakesPrecedence.uncachedCalled);

        CustomCreateTakesPrecedenceCallNode uncachedNode = CustomCreateTakesPrecedenceCallNodeGen.getUncached();
        assertEquals("s0", uncachedNode.execute(42));
        assertEquals(1, CustomCreateTakesPrecedence.createCalled);
        assertEquals(1, CustomCreateTakesPrecedence.uncachedCalled);
    }

    @GenerateUncached
    public abstract static class SupportTrivialUncached1 extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @Cached("arg0") Object foo) {
            return "cached";
        }
    }

    @GenerateUncached
    public abstract static class SupportTrivialUncached2 extends Node {
        abstract Object execute(Object arg0);

        @Specialization(guards = "cachedArg0 == arg0.getClass()")
        static Object s0(Object arg0,
                        @Cached("arg0.getClass()") Class<?> cachedArg0) {
            return "cached";
        }
    }

    public static class InvalidNode1 {

    }

    public abstract static class ErrorNode1 extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @ExpectError("Error parsing expression 'create()': The method create is undefined for the enclosing scope.") //
                        @Cached InvalidNode1 foo) {
            return arg0;
        }
    }

    public static class InvalidNode2 extends Node {

    }

    public abstract static class ErrorNode2 extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @ExpectError("Error parsing expression 'create()': The method create is undefined for the enclosing scope.") //
                        @Cached InvalidNode2 foo) {
            return arg0;
        }
    }

    public static class InvalidNode3 extends Node {

        public static Object create() {
            return null;
        }

    }

    public abstract static class ErrorNode3 extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @ExpectError("Incompatible return type Object. The expression type must be equal to the parameter type InvalidNode3.") //
                        @Cached InvalidNode3 foo) {
            return arg0;
        }
    }

    public static class InvalidNode4 extends Node {

        public static InvalidNode4 create() {
            return null;
        }

    }

    @GenerateUncached
    public abstract static class ErrorNode4 extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @ExpectError("Failed to generate code for @GenerateUncached: The specialization uses @Cached without valid uncached expression. " +
                                        "Error parsing expression 'getUncached()': The method getUncached is undefined for the enclosing scope.. " +
                                        "To resolve this specify the uncached or allowUncached attribute in @Cached.") //
                        @Cached InvalidNode4 foo) {
            return arg0;
        }
    }

    public static class InvalidNode5 extends Node {

        public static InvalidNode5 create() {
            return null;
        }

    }

    public abstract static class ErrorNode5 extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @ExpectError("Error parsing expression 'create(arg0)': The method create() in the type InvalidNode5 is not applicable for the arguments Object.") //
                        @Cached(parameters = "arg0") InvalidNode5 foo) {
            return arg0;
        }
    }

    public static class InvalidNode6 extends Node {

        public static InvalidNode6 create(Object arg0) {
            return null;
        }

        public static InvalidNode6 getUncached() {
            return null;
        }

    }

    @GenerateUncached
    public abstract static class ErrorNode6 extends Node {
        abstract Object execute(Object arg0);

        @Specialization
        static Object s0(Object arg0,
                        @ExpectError("Failed to generate code for @GenerateUncached: %") //
                        @Cached(parameters = "arg0") InvalidNode6 foo) {
            return arg0;
        }
    }

    public abstract static class InvalidNode7 extends Node {

        abstract String execute(Object arg);

        @Specialization
        static String s0(int arg) {
            return "s0";
        }

        @Specialization
        static String s0(double arg) {
            return "s1";
        }

    }

    @GenerateUncached
    public abstract static class ErrorNode7 extends Node {
        abstract Object execute(Object arg0);

        // InvalidNode7 is not uncached
        @Specialization
        static Object s0(Object arg0,
                        @ExpectError("Failed to generate code for @GenerateUncached:%") @Cached InvalidNode7 foo) {
            return foo.execute(arg0);
        }
    }

}
