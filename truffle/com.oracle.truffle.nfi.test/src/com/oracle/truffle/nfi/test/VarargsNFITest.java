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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.test.interop.BoxedPrimitive;
import com.oracle.truffle.nfi.test.interop.NullObject;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.junit.Assert;
import org.junit.Test;

public class VarargsNFITest extends NFITest {

    private static class FormatNode extends Node {

        @Child Node bind = Message.createInvoke(1).createNode();
        @Child Node execute = Message.createExecute(0).createNode();

        private String execute(TruffleObject formatString, Object... args) {
            assert args[0] == null && args[1] == null;
            byte[] buffer = new byte[128];
            args[0] = JavaInterop.asTruffleObject(buffer);
            args[1] = buffer.length;

            int size;
            try {
                size = (Integer) ForeignAccess.sendExecute(execute, formatString, args);
            } catch (InteropException e) {
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

    private static class SimpleFormatRoot extends TestRootNode {

        private final TruffleObject formatString;
        @Child FormatNode printf = new FormatNode();

        SimpleFormatRoot(TruffleObject formatString) {
            this.formatString = formatString;
        }

        @Override
        public Object executeTest(VirtualFrame frame) {
            return printf.execute(formatString, null, null, "Hello, World %f", 42.0);
        }
    }

    @Test
    public void testSimpleFormat() {
        TruffleObject formatString = lookupAndBind("format_string", "([sint8], uint64, string, ...double) : sint32");
        Object ret = run(new SimpleFormatRoot(formatString));
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

    private static class MultiFormatRoot extends TestRootNode {

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

    @Test
    public void testMultiSignatureFormat() {
        MultiFormatRoot root = new MultiFormatRoot(
                        new FormatSpec("15 37 28", "([sint8], uint64, string, ...sint64, sint64, sint64) : sint32", "%d %d %d", 15, 37, 28),
                        new FormatSpec("15 37 28.00", "([sint8], uint64, string, ...sint64, sint64, double) : sint32", "%d %d %f", 15, 37, 28),
                        new FormatSpec("15 37.00 28", "([sint8], uint64, string, ...sint64, double, sint64) : sint32", "%d %f %d", 15, 37, 28),
                        new FormatSpec("15 37.00 28.00", "([sint8], uint64, string, ...sint64, double, double) : sint32", "%d %f %f", 15, 37, 28),
                        new FormatSpec("15.00 37 28", "([sint8], uint64, string, ...double, sint64, sint64) : sint32", "%f %d %d", 15, 37, 28),
                        new FormatSpec("15.00 37 28.00", "([sint8], uint64, string, ...double, sint64, double) : sint32", "%f %d %f", 15, 37, 28),
                        new FormatSpec("15.00 37.00 28", "([sint8], uint64, string, ...double, double, sint64) : sint32", "%f %f %d", 15, 37, 28),
                        new FormatSpec("15.00 37.00 28.00", "([sint8], uint64, string, ...double, double, double) : sint32", "%f %f %f", 15, 37, 28));
        run(root);
    }

    @Test
    public void testMultiTypesFormat() {
        TruffleObject nil = new NullObject();
        MultiFormatRoot root = new MultiFormatRoot(
                        new FormatSpec("42 8472", "([sint8], uint64, string, ...sint64, sint64) : sint32", "%d %d", 42, 8472),
                        new FormatSpec("(nil) 8472", "([sint8], uint64, string, ...pointer, sint64) : sint32", "%p %d", nil, 8472),
                        new FormatSpec("42.00 8472", "([sint8], uint64, string, ...double, sint64) : sint32", "%f %d", 42, 8472),
                        new FormatSpec("hello 8472", "([sint8], uint64, string, ...string, sint64) : sint32", "%s %d", "hello", 8472),
                        new FormatSpec("42 world", "([sint8], uint64, string, ...sint64, string) : sint32", "%d %s", 42, "world"),
                        new FormatSpec("(nil) world", "([sint8], uint64, string, ...pointer, string) : sint32", "%p %s", nil, "world"),
                        new FormatSpec("42.00 world", "([sint8], uint64, string, ...double, string) : sint32", "%f %s", 42, "world"),
                        new FormatSpec("hello world", "([sint8], uint64, string, ...string, string) : sint32", "%s %s", "hello", "world"));
        run(root);
    }

    @Test
    public void testVariableArgCountFormat() {
        TruffleObject nil = new NullObject();
        TruffleObject boxedX = new BoxedPrimitive("x");
        MultiFormatRoot root = new MultiFormatRoot(
                        new FormatSpec("42", "([sint8], uint64, string, ...sint64) : sint32", "%d", 42),
                        new FormatSpec("42 x", "([sint8], uint64, string, ...sint64, string) : sint32", "%d %s", 42, "x"),
                        new FormatSpec("42 x (nil)", "([sint8], uint64, string, ...sint64, string, pointer) : sint32", "%d %s %p", 42, "x", nil),
                        new FormatSpec("42 x (nil) 42.00", "([sint8], uint64, string, ...sint64, string, pointer, double) : sint32", "%d %s %p %f", 42, "x", nil, 42),
                        new FormatSpec("x", "([sint8], uint64, string, ...string) : sint32", "%s", boxedX),
                        new FormatSpec("x (nil)", "([sint8], uint64, string, ...string, pointer) : sint32", "%s %p", boxedX, nil),
                        new FormatSpec("x (nil) 42.00", "([sint8], uint64, string, ...string, pointer, double) : sint32", "%s %p %f", boxedX, nil, 42),
                        new FormatSpec("x (nil) 42.00 42", "([sint8], uint64, string, ...string, pointer, double, sint64) : sint32", "%s %p %f %d", boxedX, nil, 42, 42));
        run(root);
    }
}
