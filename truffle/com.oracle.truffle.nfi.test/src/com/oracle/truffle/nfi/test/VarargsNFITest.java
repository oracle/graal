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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.junit.Assert;
import org.junit.Test;

public class VarargsNFITest extends NFITest {

    private static class PrintfNode extends Node {

        @Child Node bind = Message.createInvoke(1).createNode();
        @Child Node execute = Message.createExecute(0).createNode();

        private String execute(TruffleObject snprintf, Object... args) {
            assert args[0] == null && args[1] == null;
            byte[] buffer = new byte[128];
            args[0] = JavaInterop.asTruffleObject(buffer);
            args[1] = buffer.length;

            int size;
            try {
                size = (Integer) ForeignAccess.sendExecute(execute, snprintf, args);
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

    private static class SimplePrintfRoot extends TestRootNode {

        private final TruffleObject snprintf;
        @Child PrintfNode printf = new PrintfNode();

        SimplePrintfRoot(TruffleObject snprintf) {
            this.snprintf = snprintf;
        }

        @Override
        public Object executeTest(VirtualFrame frame) {
            return printf.execute(snprintf, null, null, "Hello, World %2.2f", 42.0);
        }
    }

    @Test
    public void testSimplePrintf() {
        TruffleObject snprintf = lookupAndBind("snprintf", "([sint8], uint64, string, ...double) : sint32");
        Object ret = run(new SimplePrintfRoot(snprintf));
        Assert.assertEquals("return value", "Hello, World 42.00", ret);
    }

    private static final class PrintfSpec {

        final TruffleObject snprintf;
        final Object[] args;

        final String expectedRet;

        PrintfSpec(String expectedRet, String signature, Object... args) {
            this.snprintf = lookupAndBind("snprintf", signature);
            this.expectedRet = expectedRet;

            this.args = new Object[args.length + 2];
            System.arraycopy(args, 0, this.args, 2, args.length);
        }

    }

    private static class MultiPrintfRoot extends TestRootNode {

        @Child PrintfNode printf = new PrintfNode();

        @CompilationFinal(dimensions = 1) final PrintfSpec[] specs;

        public MultiPrintfRoot(PrintfSpec... specs) {
            this.specs = specs;
        }

        @Override
        @ExplodeLoop
        public Object executeTest(VirtualFrame frame) {
            for (int i = 0; i < specs.length; i++) {
                String ret = printf.execute(specs[i].snprintf, specs[i].args);
                assertEquals(specs[i].expectedRet, ret);
            }
            return null;
        }
    }

    @Test
    public void testMultiSignaturePrintf() {
        MultiPrintfRoot root = new MultiPrintfRoot(
                        new PrintfSpec("15 37 28", "([sint8], uint64, string, ...sint64, sint64, sint64) : sint32", "%i %i %i", 15, 37, 28),
                        new PrintfSpec("15 37 28.00", "([sint8], uint64, string, ...sint64, sint64, double) : sint32", "%i %i %2.2f", 15, 37, 28),
                        new PrintfSpec("15 37.00 28", "([sint8], uint64, string, ...sint64, double, sint64) : sint32", "%i %2.2f %i", 15, 37, 28),
                        new PrintfSpec("15 37.00 28.00", "([sint8], uint64, string, ...sint64, double, double) : sint32", "%i %2.2f %2.2f", 15, 37, 28),
                        new PrintfSpec("15.00 37 28", "([sint8], uint64, string, ...double, sint64, sint64) : sint32", "%2.2f %i %i", 15, 37, 28),
                        new PrintfSpec("15.00 37 28.00", "([sint8], uint64, string, ...double, sint64, double) : sint32", "%2.2f %i %2.2f", 15, 37, 28),
                        new PrintfSpec("15.00 37.00 28", "([sint8], uint64, string, ...double, double, sint64) : sint32", "%2.2f %2.2f %i", 15, 37, 28),
                        new PrintfSpec("15.00 37.00 28.00", "([sint8], uint64, string, ...double, double, double) : sint32", "%2.2f %2.2f %2.2f", 15, 37, 28));
        run(root);
    }

    @Test
    public void testMultiTypesPrintf() {
        TruffleObject nil = JavaInterop.asTruffleObject(null);
        MultiPrintfRoot root = new MultiPrintfRoot(
                        new PrintfSpec("42 8472", "([sint8], uint64, string, ...sint64, sint64) : sint32", "%i %i", 42, 8472),
                        new PrintfSpec("0 8472", "([sint8], uint64, string, ...pointer, sint64) : sint32", "%i %i", nil, 8472),
                        new PrintfSpec("42.00 8472", "([sint8], uint64, string, ...double, sint64) : sint32", "%2.2f %i", 42, 8472),
                        new PrintfSpec("hello 8472", "([sint8], uint64, string, ...string, sint64) : sint32", "%s %i", "hello", 8472),
                        new PrintfSpec("42 world", "([sint8], uint64, string, ...sint64, string) : sint32", "%i %s", 42, "world"),
                        new PrintfSpec("0 world", "([sint8], uint64, string, ...pointer, string) : sint32", "%i %s", nil, "world"),
                        new PrintfSpec("42.00 world", "([sint8], uint64, string, ...double, string) : sint32", "%2.2f %s", 42, "world"),
                        new PrintfSpec("hello world", "([sint8], uint64, string, ...string, string) : sint32", "%s %s", "hello", "world"));
        run(root);
    }

    @Test
    public void testVariableArgCountPrintf() {
        TruffleObject nil = JavaInterop.asTruffleObject(null);
        TruffleObject boxedX = JavaInterop.asTruffleObject("x");
        MultiPrintfRoot root = new MultiPrintfRoot(
                        new PrintfSpec("42", "([sint8], uint64, string, ...sint64) : sint32", "%i", 42),
                        new PrintfSpec("42 x", "([sint8], uint64, string, ...sint64, string) : sint32", "%i %s", 42, "x"),
                        new PrintfSpec("42 x 0", "([sint8], uint64, string, ...sint64, string, pointer) : sint32", "%i %s %i", 42, "x", nil),
                        new PrintfSpec("42 x 0 42.00", "([sint8], uint64, string, ...sint64, string, pointer, double) : sint32", "%i %s %i %2.2f", 42, "x", nil, 42),
                        new PrintfSpec("x", "([sint8], uint64, string, ...string) : sint32", "%s", boxedX),
                        new PrintfSpec("x 0", "([sint8], uint64, string, ...string, pointer) : sint32", "%s %i", boxedX, nil),
                        new PrintfSpec("x 0 42.00", "([sint8], uint64, string, ...string, pointer, double) : sint32", "%s %i %2.2f", boxedX, nil, 42),
                        new PrintfSpec("x 0 42.00 42", "([sint8], uint64, string, ...string, pointer, double, sint64) : sint32", "%s %i %2.2f %i", boxedX, nil, 42, 42));
        run(root);
    }
}
