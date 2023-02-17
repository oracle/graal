/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.examples;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.DisableStateBitWidthModfication;
import com.oracle.truffle.api.dsl.test.examples.NodeInliningAndLazyInitExampleFactory.LazyInitExampleBefore2NodeGen;
import com.oracle.truffle.api.dsl.test.examples.NodeInliningAndLazyInitExampleFactory.LazyInitExampleBeforeNodeGen;
import com.oracle.truffle.api.dsl.test.examples.NodeInliningAndLazyInitExampleFactory.LazyInitExampleNodeGen;
import com.oracle.truffle.api.dsl.test.examples.NodeInliningAndLazyInitExampleFactory.RaiseErrorNodeGen;
import com.oracle.truffle.api.nodes.Node;

/**
 * See the tutorial description <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/DSLNodeObjectInlining.md">here</a>.
 */
@DisableStateBitWidthModfication
public class NodeInliningAndLazyInitExample {
    public static final class MyException extends RuntimeException {
        private static final long serialVersionUID = 3828202356980868918L;

        public MyException(String message) {
            super(message, null);
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class RaiseErrorNode extends Node {
        abstract void execute(Object type, String message);

        @Specialization
        static void doIt(@SuppressWarnings("unused") Object type, String message) {
            throw new MyException(message);
        }

        // For example: some more specializations with large caches that make
        // this node not suitable for inlining

    }

    // Usage before:

    static Object doSomeWork(Object value) {
        return value instanceof Integer ? null : value;
    }

    @GenerateInline(false)
    @GenerateUncached(false)
    public abstract static class LazyInitExampleBefore extends Node {
        abstract void execute(Object value);

        @Specialization
        void doIt(Object value,
                        @Cached RaiseErrorNode raiseError) {
            Object result = doSomeWork(value);
            if (result == null) {
                raiseError.execute(value, "Error: doSomeWork returned null");
            }
        }
    }

    @GenerateInline(false)
    @GenerateUncached(false)
    public abstract static class LazyInitExampleBefore2 extends Node {
        @Child RaiseErrorNode raiseError;

        abstract void execute(Object value);

        @Specialization
        void doIt(Object value) {
            Object result = doSomeWork(value);
            if (result == null) {
                if (raiseError == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raiseError = insert(RaiseErrorNodeGen.create());
                }
                raiseError.execute(value, "Error: doSomeWork returned null");
            }
        }
    }

    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class LazyRaiseNode extends Node {
        public final RaiseErrorNode get(Node node) {
            return execute(node);
        }

        abstract RaiseErrorNode execute(Node node);

        @Specialization
        static RaiseErrorNode doIt(@Cached(inline = false) RaiseErrorNode node) {
            return node;
        }
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class LazyInitExample extends Node {
        abstract void execute(Object value);

        @Specialization
        void doIt(Object value,
                        @Cached LazyRaiseNode raiseError) {
            Object result = doSomeWork(value);
            if (result == null) {
                raiseError.get(this).execute(value, "Error: doSomeWork returned null");
            }
        }
    }

    @Test(expected = MyException.class)
    public void testBeforeThrowsForInteger() {
        LazyInitExampleBeforeNodeGen.create().execute(42);
    }

    @Test(expected = MyException.class)
    public void testBefore2ThrowsForInteger() {
        LazyInitExampleBefore2NodeGen.create().execute(42);
    }

    @Test(expected = MyException.class)
    public void testLazyInitExampleThrowsForInteger() {
        LazyInitExampleNodeGen.create().execute(42);
    }

    @Test
    public void allDoNotThrowForString() {
        LazyInitExampleBeforeNodeGen.create().execute("str");
        LazyInitExampleBefore2NodeGen.create().execute("str");
        LazyInitExampleNodeGen.create().execute("str");
    }
}
