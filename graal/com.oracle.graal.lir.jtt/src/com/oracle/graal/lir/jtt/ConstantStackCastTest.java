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

import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import jdk.internal.jvmci.code.StackSlotValue;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.Value;

import org.junit.Test;

import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGeneratorTool;

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
            Value srcValue;
            if (isJavaConstant(value)) {
                srcValue = getConstant(srcKind, asJavaConstant(value));
            } else {
                srcValue = value;
            }
            gen.emitMove(s1, srcValue);
            gen.emitBlackhole(s1);
            setResult(gen.emitMove(s1));
        }

        private static ConstantValue getConstant(LIRKind srcKind, JavaConstant c) {

            switch ((JavaKind) srcKind.getPlatformKind()) {
                case Byte:
                    JavaConstant byteConst = JavaConstant.forByte((byte) c.asInt());
                    return new ConstantValue(srcKind, byteConst);
                default:
                    throw JVMCIError.shouldNotReachHere("Kind not supported: " + srcKind);
            }
        }
    }

    private static final LoadConstantStackSpec stackCopyByte = new LoadConstantStackSpec(LIRKind.value(JavaKind.Int), LIRKind.value(JavaKind.Byte));

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
