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
package com.oracle.truffle.api.bytecode.test.example;

import static com.oracle.truffle.api.bytecode.test.example.BytecodeDSLExampleCommon.parseNode;
import static org.junit.Assert.assertArrayEquals;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.frame.Frame;

@RunWith(Parameterized.class)
public class BytecodeDSLExampleCopyLocalsTest {
    protected static final BytecodeDSLExampleLanguage LANGUAGE = null;

    @Parameters(name = "{0}")
    public static List<Class<? extends BytecodeDSLExample>> getInterpreterClasses() {
        return BytecodeDSLExampleCommon.allInterpreters();
    }

    @Parameter(0) public Class<? extends BytecodeDSLExample> interpreterClass;

    @Test
    public void testCopyAllLocals() {
        /**
         * @formatter:off
         * def foo(arg0) {
         *   local1 = 42L
         *   local2 = "abcd"
         *   local3 = true
         *   CopyLocalsToFrame(<frame>, null) // copy all locals
         * }
         * @formatter:on
         */

        BytecodeDSLExample foo = parseNode(interpreterClass, "foo", b -> {
            b.beginRoot(LANGUAGE);

            b.beginBlock();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant("abcd");
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant(true);
            b.endStoreLocal();

            b.beginReturn();
            b.beginCopyLocalsToFrame();
            b.emitLoadConstant(null);
            b.endCopyLocalsToFrame();
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        Frame frame = (Frame) foo.getCallTarget().call();
        Object[] locals = foo.getLocals(frame);
        assertArrayEquals(new Object[]{42L, "abcd", true}, locals);
    }

    @Test
    public void testCopySomeLocals() {
        /**
         * @formatter:off
         * def foo(arg0) {
         *   local1 = 42L
         *   local2 = "abcd"
         *   local3 = true
         *   CopyLocalsToFrame(<frame>, 2) // copy first two locals
         * }
         * @formatter:on
         */

        BytecodeDSLExample foo = parseNode(interpreterClass, "foo", b -> {
            b.beginRoot(LANGUAGE);

            b.beginBlock();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant("abcd");
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant(true);
            b.endStoreLocal();

            b.beginReturn();
            b.beginCopyLocalsToFrame();
            b.emitLoadConstant(2);
            b.endCopyLocalsToFrame();
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        Frame frame = (Frame) foo.getCallTarget().call();
        Object[] locals = foo.getLocals(frame);
        assertArrayEquals(new Object[]{42L, "abcd", null}, locals);
    }
}
