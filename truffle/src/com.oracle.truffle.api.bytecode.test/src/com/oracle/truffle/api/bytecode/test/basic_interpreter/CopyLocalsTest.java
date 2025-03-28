/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.frame.Frame;

@RunWith(Parameterized.class)
public class CopyLocalsTest extends AbstractBasicInterpreterTest {

    public CopyLocalsTest(TestRun run) {
        super(run);
    }

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

        BasicInterpreter foo = parseNode("foo", b -> {
            b.beginRoot();

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
            b.emitCopyLocalsToFrame(0L);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        Frame frame = (Frame) foo.getCallTarget().call();
        BytecodeNode bytecode = foo.getBytecodeNode();
        Instruction instr = bytecode.getInstructionsAsList().get(6);
        Object[] locals = foo.getBytecodeNode().getLocalValues(instr.getBytecodeIndex(), frame);
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

        BasicInterpreter foo = parseNode("foo", b -> {
            b.beginRoot();

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
            b.emitCopyLocalsToFrame(2L);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        Frame frame = (Frame) foo.getCallTarget().call();
        BytecodeNode bytecode = foo.getBytecodeNode();
        Instruction instr = bytecode.getInstructionsAsList().get(6);
        Object[] locals = bytecode.getLocalValues(instr.getBytecodeIndex(), frame);
        assertArrayEquals(new Object[]{42L, "abcd", run.getDefaultLocalValue()}, locals);
    }
}
