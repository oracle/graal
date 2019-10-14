/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.CheckedExceptionTestFactory.Default1NodeGen;
import com.oracle.truffle.api.dsl.test.CheckedExceptionTestFactory.Default2NodeGen;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("unused")
public class CheckedExceptionTest {

    @SuppressWarnings("serial")
    static class CheckedException extends Exception {
    }

    abstract static class Default1Node extends Node {

        abstract void execute(Object arg) throws CheckedException;

        @Specialization
        void s0(int a) throws CheckedException {
            throw new CheckedException();
        }

        @Specialization
        void s1(double a) throws CheckedException {
            throw new CheckedException();
        }
    }

    @Test
    public void testDefault1() {
        try {
            Default1NodeGen.create().execute(42);
            Assert.fail();
        } catch (CheckedException e) {
        }
    }

    abstract static class Default2Node extends Node {

        abstract void execute(Object arg) throws CheckedException;

        @Specialization
        void s0(int a) throws CheckedException {
            throw new CheckedException();
        }

        @Specialization(guards = "guard()")
        void s1(double a) throws CheckedException {
            throw new CheckedException();
        }

        static boolean guard() throws CheckedException {
            throw new CheckedException();
        }

        @Fallback
        void f(Object a) {
        }
    }

    @Test
    public void testDefault2() {
        try {
            Default2NodeGen.create().execute(42);
            Assert.fail();
        } catch (CheckedException e) {
        }
    }

    abstract static class Error1Node extends Node {

        abstract void execute();

        @ExpectError("Specialization guard method or cache initializer declares an undeclared checked exception [com.oracle.truffle.api.dsl.test.CheckedExceptionTest.CheckedException]. " +
                        "Only checked exceptions are allowed that were declared in the execute signature. Allowed exceptions are: [].")
        @Specialization
        void s0() throws CheckedException {
            throw new CheckedException();
        }

    }

    abstract static class Error2Node extends Node {

        abstract void execute();

        @ExpectError("Specialization guard method or cache initializer declares an undeclared checked exception [com.oracle.truffle.api.dsl.test.CheckedExceptionTest.CheckedException]. " +
                        "Only checked exceptions are allowed that were declared in the execute signature. Allowed exceptions are: [].")
        @Specialization(guards = "guard()")
        void s0() {
        }

        static boolean guard() throws CheckedException {
            throw new CheckedException();
        }

    }

    abstract static class Error3Node extends Node {

        abstract void execute();

        @ExpectError("Specialization guard method or cache initializer declares an undeclared checked exception [com.oracle.truffle.api.dsl.test.CheckedExceptionTest.CheckedException]. " +
                        "Only checked exceptions are allowed that were declared in the execute signature. Allowed exceptions are: [].")
        @Specialization
        void s0(@Cached("initializer()") Object o) {
        }

        static Object initializer() throws CheckedException {
            return null;
        }

    }

    abstract static class ExceptionTestNode extends Node {

        protected Node createSubnode() throws Exception {
            return null;
        }

        public abstract Object execute() throws Exception;

        @Specialization
        Object callDirectCached(@Cached("createSubnode()") Node subNode) {
            return null;
        }
    }

}
