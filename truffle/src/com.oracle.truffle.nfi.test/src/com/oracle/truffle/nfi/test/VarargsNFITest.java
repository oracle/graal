/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.test.interop.BoxedPrimitive;
import com.oracle.truffle.nfi.test.interop.NullObject;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class VarargsNFITest extends NFITest {

    private static class FormatNode extends Node {

        @Child Node bind = Message.INVOKE.createNode();
        @Child Node execute = Message.EXECUTE.createNode();

        private String execute(TruffleObject formatString, Object... args) {
            assert args[0] == null && args[1] == null;
            byte[] buffer = new byte[128];
            args[0] = runWithPolyglot.getTruffleTestEnv().asGuestValue(buffer);
            args[1] = buffer.length;

            int size;
            try {
                size = (Integer) ForeignAccess.sendExecute(execute, formatString, args);
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
        @Child FormatNode printf = new FormatNode();

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

        @Child FormatNode printf = new FormatNode();

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
                                            42));
        }
    }

    @Test
    public void testVariableArgCountFormat(@Inject(VariableArgCountFormatRoot.class) CallTarget callTarget) {
        callTarget.call();
    }
}
