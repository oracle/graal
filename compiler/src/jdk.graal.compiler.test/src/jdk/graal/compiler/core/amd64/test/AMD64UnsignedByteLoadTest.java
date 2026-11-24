/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.amd64.test;

import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.core.test.MatchRuleTest;
import jdk.graal.compiler.lir.amd64.AMD64Unary;
import jdk.vm.ci.amd64.AMD64;

/**
 * Checks that unsigned byte loads do not require a separate extension of a register.
 */
public class AMD64UnsignedByteLoadTest extends MatchRuleTest {
    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    /**
     * Requires a single zero extending load rather than a signed load followed by an extension.
     * The only register MOV is the zero extension of the offset used in the first array address.
     */
    private void checkLoad(String method, String opcode) {
        checkLIRforAll(method, op -> op instanceof AMD64Unary.MemoryOp && op.name().equals(opcode), 1);
        checkLIRforAll(method, op -> op instanceof AMD64Unary.MemoryOp && op.name().startsWith("MOVSX"), 0);
        checkLIRforAll(method, op -> op instanceof AMD64Unary.RMOp && op.name().startsWith("MOVZX"), 0);
        checkLIRforAll(method, op -> op instanceof AMD64Unary.RMOp && op.name().equals("MOV"), 1);
    }

    /**
     * Exercises all byte values, including values with the sign bit set.
     */
    private void checkByteIndices(String method) {
        int[] locals = new int[256];
        for (int i = 0; i < locals.length; i++) {
            locals[i] = 42 + i;
        }
        for (int value = Byte.MIN_VALUE; value <= Byte.MAX_VALUE; value++) {
            byte input = (byte) value;
            test(method, supply(() -> new byte[]{input}), 0, locals);
        }
        test(method, supply(() -> new byte[]{(byte) 255}), 0, new int[128]);
        test(method, supply(() -> new byte[]{0}), 0, (int[]) null);
        test(method, (byte[]) null, 0, locals);
        test(method, new byte[0], 0, locals);
    }

    /**
     * Uses the unsigned value for both an array bounds check and an array address, as in a bytecode
     * handler that reads a local variable index.
     */
    public static int unsignedIndex(byte[] bytes, int offset, int[] locals) {
        int index = bytes[offset] & 0xff;
        return locals[index];
    }

    @Test
    public void testUnsignedIndex() {
        checkLoad("unsignedIndex", "MOVZXB");
        checkByteIndices("unsignedIndex");
    }

    public static int unsignedShortIndex(short[] words, int offset, int[] locals) {
        int index = words[offset] & 0xffff;
        return locals[index];
    }

    @Test
    public void testUnsignedShortIndex() {
        checkLoad("unsignedShortIndex", "MOVZX");
        int[] locals = new int[65536];
        for (int i = 0; i < locals.length; i++) {
            locals[i] = 42 + i;
        }
        for (int value : new int[]{0, 1, 127, 128, 255, 256, 32767, 32768, 65535}) {
            test("unsignedShortIndex", new short[]{(short) value}, 0, locals);
        }
        test("unsignedShortIndex", new short[]{(short) 65535}, 0, new int[32768]);
    }

    /**
     * Keeps a signed usage of the read, which needs a separate sign extension after the unsigned load.
     */
    public static int signedAndUnsignedIndex(byte[] bytes, int offset, int[] locals) {
        int value = bytes[offset];
        return value + locals[value & 0xff];
    }

    @Test
    public void testSignedAndUnsignedIndex() {
        checkLoad("signedAndUnsignedIndex", "MOVZXB");
        checkLIRforAll("signedAndUnsignedIndex", op -> op instanceof AMD64Unary.RMOp && op.name().equals("MOVSXB"), 1);
        checkByteIndices("signedAndUnsignedIndex");
    }

}
