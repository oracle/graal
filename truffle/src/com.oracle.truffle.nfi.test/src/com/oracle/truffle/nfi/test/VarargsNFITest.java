/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.test.VarargsNFITestFactory.FormatNodeGen;
import com.oracle.truffle.nfi.test.interop.BoxedPrimitive;
import com.oracle.truffle.nfi.test.interop.NullObject;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class VarargsNFITest extends NFITest {

    abstract static class FormatNode extends Node {

        protected final String execute(TruffleObject formatString, Object... args) {
            return executeImpl(formatString, args);
        }

        protected abstract String executeImpl(TruffleObject formatString, Object[] args);

        @Specialization(limit = "3")
        String doFormat(TruffleObject formatString, Object[] args,
                        @CachedLibrary("formatString") InteropLibrary interop,
                        @CachedLibrary(limit = "1") InteropLibrary num) {
            assert args[0] == null && args[1] == null;
            byte[] buffer = new byte[128];
            args[0] = runWithPolyglot.getTruffleTestEnv().asGuestValue(buffer);
            args[1] = buffer.length;

            int size;
            try {
                size = num.asInt(interop.execute(formatString, args));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            } finally {
                args[0] = args[1] = null;
            }

            return toString(buffer, size);
        }

        @TruffleBoundary
        private static String toString(byte[] array, int length) {
            ByteBuffer buffer = ByteBuffer.wrap(array, 0, length);
            return Charset.forName("utf-8").decode(buffer).toString();
        }
    }

    public static class SimpleFormatRoot extends NFITestRootNode {

        private final TruffleObject formatString;
        @Child FormatNode printf = FormatNodeGen.create();

        public SimpleFormatRoot() {
            this.formatString = lookupAndBind("format_string", "([sint8], uint64, string, ...double) : sint32");
        }

        @Override
        public Object executeTest(VirtualFrame frame) {
            return printf.execute(formatString, null, null, "Hello, World %f", 42.0);
        }
    }

    @Test
    public void testSimpleFormat(@Inject(SimpleFormatRoot.class) CallTarget callTarget) {
        Object ret = callTarget.call();
        Assert.assertEquals("return value", "Hello, World 42.00", ret);
    }

    private static final class FormatSpec {

        final TruffleObject formatString;
        final Object[] args;

        final String expectedRet;

        FormatSpec(String expectedRet, String signature, Object... args) {
            this.formatString = lookupAndBind("format_string", signature);
            this.expectedRet = expectedRet;

            this.args = new Object[args.length + 2];
            System.arraycopy(args, 0, this.args, 2, args.length);
        }

    }

    private static class MultiFormatRoot extends NFITestRootNode {

        @Child FormatNode printf = FormatNodeGen.create();

        @CompilationFinal(dimensions = 1) final FormatSpec[] specs;

        MultiFormatRoot(FormatSpec... specs) {
            this.specs = specs;
        }

        @Override
        @ExplodeLoop
        public Object executeTest(VirtualFrame frame) {
            for (int i = 0; i < specs.length; i++) {
                String ret = printf.execute(specs[i].formatString, specs[i].args);
                assertEquals(specs[i].expectedRet, ret);
            }
            return null;
        }
    }

    public static class MultiSignatureRoot extends MultiFormatRoot {

        public MultiSignatureRoot() {
            super(
                            new FormatSpec("15 37 28", "([sint8], uint64, string, ...sint64, sint64, sint64) : sint32", "%d %d %d", 15, 37, 28),
                            new FormatSpec("15 37 28.00", "([sint8], uint64, string, ...sint64, sint64, double) : sint32", "%d %d %f", 15, 37, 28),
                            new FormatSpec("15 37.00 28", "([sint8], uint64, string, ...sint64, double, sint64) : sint32", "%d %f %d", 15, 37, 28),
                            new FormatSpec("15 37.00 28.00", "([sint8], uint64, string, ...sint64, double, double) : sint32", "%d %f %f", 15, 37, 28),
                            new FormatSpec("15.00 37 28", "([sint8], uint64, string, ...double, sint64, sint64) : sint32", "%f %d %d", 15, 37, 28),
                            new FormatSpec("15.00 37 28.00", "([sint8], uint64, string, ...double, sint64, double) : sint32", "%f %d %f", 15, 37, 28),
                            new FormatSpec("15.00 37.00 28", "([sint8], uint64, string, ...double, double, sint64) : sint32", "%f %f %d", 15, 37, 28),
                            new FormatSpec("15.00 37.00 28.00", "([sint8], uint64, string, ...double, double, double) : sint32", "%f %f %f", 15, 37, 28));
        }
    }

    @Test
    public void testMultiSignatureFormat(@Inject(MultiSignatureRoot.class) CallTarget callTarget) {
        callTarget.call();
    }

    public static class MultiTypesRoot extends MultiFormatRoot {

        public MultiTypesRoot() {
            super(
                            new FormatSpec("42 8472", "([sint8], uint64, string, ...sint64, sint64) : sint32", "%d %d", 42, 8472),
                            new FormatSpec("(nil) 8472", "([sint8], uint64, string, ...pointer, sint64) : sint32", "%p %d", new NullObject(), 8472),
                            new FormatSpec("42.00 8472", "([sint8], uint64, string, ...double, sint64) : sint32", "%f %d", 42, 8472),
                            new FormatSpec("hello 8472", "([sint8], uint64, string, ...string, sint64) : sint32", "%s %d", "hello", 8472),
                            new FormatSpec("42 world", "([sint8], uint64, string, ...sint64, string) : sint32", "%d %s", 42, "world"),
                            new FormatSpec("(nil) world", "([sint8], uint64, string, ...pointer, string) : sint32", "%p %s", new NullObject(), "world"),
                            new FormatSpec("42.00 world", "([sint8], uint64, string, ...double, string) : sint32", "%f %s", 42, "world"),
                            new FormatSpec("hello world", "([sint8], uint64, string, ...string, string) : sint32", "%s %s", "hello", "world"));
        }
    }

    @Test
    public void testMultiTypesFormat(@Inject(MultiTypesRoot.class) CallTarget callTarget) {
        callTarget.call();
    }

    public static class VariableArgCountFormatRoot extends MultiFormatRoot {

        public VariableArgCountFormatRoot() {
            super(
                            new FormatSpec("42", "([sint8], uint64, string, ...sint64) : sint32", "%d", 42),
                            new FormatSpec("42 x", "([sint8], uint64, string, ...sint64, string) : sint32", "%d %s", 42, "x"),
                            new FormatSpec("42 x (nil)", "([sint8], uint64, string, ...sint64, string, pointer) : sint32", "%d %s %p", 42, "x", new NullObject()),
                            new FormatSpec("42 x (nil) 42.00", "([sint8], uint64, string, ...sint64, string, pointer, double) : sint32", "%d %s %p %f", 42, "x", new NullObject(), 42),
                            new FormatSpec("x", "([sint8], uint64, string, ...string) : sint32", "%s", new BoxedPrimitive("x")),
                            new FormatSpec("x (nil)", "([sint8], uint64, string, ...string, pointer) : sint32", "%s %p", new BoxedPrimitive("x"), new NullObject()),
                            new FormatSpec("x (nil) 42.00", "([sint8], uint64, string, ...string, pointer, double) : sint32", "%s %p %f", new BoxedPrimitive("x"), new NullObject(), 42),
                            new FormatSpec("x (nil) 42.00 42", "([sint8], uint64, string, ...string, pointer, double, sint64) : sint32", "%s %p %f %d", new BoxedPrimitive("x"), new NullObject(), 42,
                                            42),
                            new FormatSpec("x (nil)", "([sint8], uint64, string, ...string, nullable) : sint32", "%s %p", new BoxedPrimitive("x"), new NullObject()));
        }
    }

    @Test
    public void testVariableArgCountFormat(@Inject(VariableArgCountFormatRoot.class) CallTarget callTarget) {
        callTarget.call();
    }
}
