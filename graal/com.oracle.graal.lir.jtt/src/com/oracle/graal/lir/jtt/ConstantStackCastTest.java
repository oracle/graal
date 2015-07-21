/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.jtt;

import static jdk.internal.jvmci.code.ValueUtil.*;
import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;

import org.junit.*;

import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;

/**
 * Tests move from a constant to a wider stack slot (e.g. byte constant to integer stack slot).
 */
public class ConstantStackCastTest extends LIRTest {

    private static class LoadConstantStackSpec extends LIRTestSpecification {
        protected final LIRKind dstKind;
        protected final LIRKind srcKind;

        public LoadConstantStackSpec(LIRKind dstKind, LIRKind srcKind) {
            this.dstKind = dstKind;
            this.srcKind = srcKind;
        }

        @Override
        public void generate(LIRGeneratorTool gen, Value value) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            // create slots
            StackSlotValue s1 = frameMapBuilder.allocateSpillSlot(dstKind);
            // move stuff around
            Value srcValue = isConstant(value) ? getConstant(srcKind, value) : value;
            gen.emitMove(s1, srcValue);
            gen.emitBlackhole(s1);
            setResult(gen.emitMove(s1));
        }

        private static PrimitiveConstant getConstant(LIRKind srcKind, Value value) {

            switch ((Kind) srcKind.getPlatformKind()) {
                case Byte:
                    return JavaConstant.forByte((byte) asConstant(value).asInt());
                default:
                    throw JVMCIError.shouldNotReachHere("Kind not supported: " + srcKind);
            }
        }
    }

    private static final LoadConstantStackSpec stackCopyByte = new LoadConstantStackSpec(LIRKind.value(Kind.Int), LIRKind.value(Kind.Byte));

    @LIRIntrinsic
    public static byte testCopyByte(@SuppressWarnings("unused") LoadConstantStackSpec spec, byte value) {
        return value;
    }

    public byte testByte(byte value) {
        return testCopyByte(stackCopyByte, value);
    }

    @Test
    public void runByte() throws Throwable {
        runTest("testByte", (byte) 0);
    }

}
