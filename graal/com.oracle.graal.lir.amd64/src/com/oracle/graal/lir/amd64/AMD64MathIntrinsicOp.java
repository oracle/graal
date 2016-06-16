/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.STACK;
import static com.oracle.graal.lir.asm.ArrayDataPointerConstant.ArrayType.INT_ARRAY;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.amd64.AMD64Address;
import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.asm.amd64.AMD64MacroAssembler;
import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.ArrayDataPointerConstant;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

public final class AMD64MathIntrinsicOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64MathIntrinsicOp> TYPE = LIRInstructionClass.create(AMD64MathIntrinsicOp.class);

    public enum IntrinsicOpcode {
        SIN,
        COS,
        TAN,
        LOG,
        LOG10
    }

    @Opcode private final IntrinsicOpcode opcode;
    @Def protected Value result;
    @Use protected Value input;
    @Temp({REG, ILLEGAL}) protected Value xmm1Temp;
    @Temp({REG, ILLEGAL}) protected Value xmm2Temp;
    @Temp({REG, ILLEGAL}) protected Value xmm3Temp;
    @Temp({REG, ILLEGAL}) protected Value xmm4Temp;
    @Temp({REG, ILLEGAL}) protected Value xmm5Temp;
    @Temp({REG, ILLEGAL}) protected Value xmm6Temp;
    @Temp({REG, ILLEGAL}) protected Value xmm7Temp;
    @Temp({REG, ILLEGAL}) protected Value gpr1Temp;
    @Temp({REG, ILLEGAL}) protected Value gpr2Temp;
    @Temp({REG, ILLEGAL}) protected Value gpr3Temp;
    @Temp({REG, ILLEGAL}) protected Value gpr4Temp;
    @Temp({STACK, ILLEGAL}) protected Value stackTemp;

    public AMD64MathIntrinsicOp(LIRGeneratorTool tool, IntrinsicOpcode opcode, Value result, Value input, Value stackTemp) {
        super(TYPE);
        this.opcode = opcode;
        this.result = result;
        this.input = input;
        if (opcode == IntrinsicOpcode.LOG || opcode == IntrinsicOpcode.LOG10) {
            this.xmm1Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm2Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm3Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm4Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm5Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm6Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm7Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.gpr1Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.gpr2Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.gpr3Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.gpr4Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        }
        this.stackTemp = stackTemp;
    }

    public AMD64MathIntrinsicOp(LIRGeneratorTool tool, IntrinsicOpcode opcode, Value result, Value input) {
        this(tool, opcode, result, input, null);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        switch (opcode) {
            case LOG:
                logIntrinsic(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), crb, masm);
                break;
            case LOG10:
                log10Intrinsic(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), crb, masm);
                break;
            case SIN:
                masm.fsin(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE));
                break;
            case COS:
                masm.fcos(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE));
                break;
            case TAN:
                masm.ftan(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE));
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private static int[] logTwoTable = {
                    0xfefa3800, 0x3fe62e42, 0x93c76730, 0x3d2ef357, 0xaa241800,
                    0x3fe5ee82, 0x0cda46be, 0x3d220238, 0x5c364800, 0x3fe5af40,
                    0xac10c9fb, 0x3d2dfa63, 0x26bb8c00, 0x3fe5707a, 0xff3303dd,
                    0x3d09980b, 0x26867800, 0x3fe5322e, 0x5d257531, 0x3d05ccc4,
                    0x835a5000, 0x3fe4f45a, 0x6d93b8fb, 0xbd2e6c51, 0x6f970c00,
                    0x3fe4b6fd, 0xed4c541c, 0x3cef7115, 0x27e8a400, 0x3fe47a15,
                    0xf94d60aa, 0xbd22cb6a, 0xf2f92400, 0x3fe43d9f, 0x481051f7,
                    0xbcfd984f, 0x2125cc00, 0x3fe4019c, 0x30f0c74c, 0xbd26ce79,
                    0x0c36c000, 0x3fe3c608, 0x7cfe13c2, 0xbd02b736, 0x17197800,
                    0x3fe38ae2, 0xbb5569a4, 0xbd218b7a, 0xad9d8c00, 0x3fe35028,
                    0x9527e6ac, 0x3d10b83f, 0x44340800, 0x3fe315da, 0xc5a0ed9c,
                    0xbd274e93, 0x57b0e000, 0x3fe2dbf5, 0x07b9dc11, 0xbd17a6e5,
                    0x6d0ec000, 0x3fe2a278, 0xe797882d, 0x3d206d2b, 0x1134dc00,
                    0x3fe26962, 0x05226250, 0xbd0b61f1, 0xd8bebc00, 0x3fe230b0,
                    0x6e48667b, 0x3d12fc06, 0x5fc61800, 0x3fe1f863, 0xc9fe81d3,
                    0xbd2a7242, 0x49ae6000, 0x3fe1c078, 0xed70e667, 0x3cccacde,
                    0x40f23c00, 0x3fe188ee, 0xf8ab4650, 0x3d14cc4e, 0xf6f29800,
                    0x3fe151c3, 0xa293ae49, 0xbd2edd97, 0x23c75c00, 0x3fe11af8,
                    0xbb9ddcb2, 0xbd258647, 0x8611cc00, 0x3fe0e489, 0x07801742,
                    0x3d1c2998, 0xe2d05400, 0x3fe0ae76, 0x887e7e27, 0x3d1f486b,
                    0x0533c400, 0x3fe078bf, 0x41edf5fd, 0x3d268122, 0xbe760400,
                    0x3fe04360, 0xe79539e0, 0xbd04c45f, 0xe5b20800, 0x3fe00e5a,
                    0xb1727b1c, 0xbd053ba3, 0xaf7a4800, 0x3fdfb358, 0x3c164935,
                    0x3d0085fa, 0xee031800, 0x3fdf4aa7, 0x6f014a8b, 0x3d12cde5,
                    0x56b41000, 0x3fdee2a1, 0x5a470251, 0x3d2f27f4, 0xc3ddb000,
                    0x3fde7b42, 0x5372bd08, 0xbd246550, 0x1a272800, 0x3fde148a,
                    0x07322938, 0xbd1326b2, 0x484c9800, 0x3fddae75, 0x60dc616a,
                    0xbd1ea42d, 0x46def800, 0x3fdd4902, 0xe9a767a8, 0x3d235baf,
                    0x18064800, 0x3fdce42f, 0x3ec7a6b0, 0xbd0797c3, 0xc7455800,
                    0x3fdc7ff9, 0xc15249ae, 0xbd29b6dd, 0x693fa000, 0x3fdc1c60,
                    0x7fe8e180, 0x3d2cec80, 0x1b80e000, 0x3fdbb961, 0xf40a666d,
                    0x3d27d85b, 0x04462800, 0x3fdb56fa, 0x2d841995, 0x3d109525,
                    0x5248d000, 0x3fdaf529, 0x52774458, 0xbd217cc5, 0x3c8ad800,
                    0x3fda93ed, 0xbea77a5d, 0x3d1e36f2, 0x0224f800, 0x3fda3344,
                    0x7f9d79f5, 0x3d23c645, 0xea15f000, 0x3fd9d32b, 0x10d0c0b0,
                    0xbd26279e, 0x43135800, 0x3fd973a3, 0xa502d9f0, 0xbd152313,
                    0x635bf800, 0x3fd914a8, 0x2ee6307d, 0xbd1766b5, 0xa88b3000,
                    0x3fd8b639, 0xe5e70470, 0xbd205ae1, 0x776dc800, 0x3fd85855,
                    0x3333778a, 0x3d2fd56f, 0x3bd81800, 0x3fd7fafa, 0xc812566a,
                    0xbd272090, 0x687cf800, 0x3fd79e26, 0x2efd1778, 0x3d29ec7d,
                    0x76c67800, 0x3fd741d8, 0x49dc60b3, 0x3d2d8b09, 0xe6af1800,
                    0x3fd6e60e, 0x7c222d87, 0x3d172165, 0x3e9c6800, 0x3fd68ac8,
                    0x2756eba0, 0x3d20a0d3, 0x0b3ab000, 0x3fd63003, 0xe731ae00,
                    0xbd2db623, 0xdf596000, 0x3fd5d5bd, 0x08a465dc, 0xbd0a0b2a,
                    0x53c8d000, 0x3fd57bf7, 0xee5d40ef, 0x3d1faded, 0x0738a000,
                    0x3fd522ae, 0x8164c759, 0x3d2ebe70, 0x9e173000, 0x3fd4c9e0,
                    0x1b0ad8a4, 0xbd2e2089, 0xc271c800, 0x3fd4718d, 0x0967d675,
                    0xbd2f27ce, 0x23d5e800, 0x3fd419b4, 0xec90e09d, 0x3d08e436,
                    0x77333000, 0x3fd3c252, 0xb606bd5c, 0x3d183b54, 0x76be1000,
                    0x3fd36b67, 0xb0f177c8, 0x3d116ecd, 0xe1d36000, 0x3fd314f1,
                    0xd3213cb8, 0xbd28e27a, 0x7cdc9000, 0x3fd2bef0, 0x4a5004f4,
                    0x3d2a9cfa, 0x1134d800, 0x3fd26962, 0xdf5bb3b6, 0x3d2c93c1,
                    0x6d0eb800, 0x3fd21445, 0xba46baea, 0x3d0a87de, 0x635a6800,
                    0x3fd1bf99, 0x5147bdb7, 0x3d2ca6ed, 0xcbacf800, 0x3fd16b5c,
                    0xf7a51681, 0x3d2b9acd, 0x8227e800, 0x3fd1178e, 0x63a5f01c,
                    0xbd2c210e, 0x67616000, 0x3fd0c42d, 0x163ceae9, 0x3d27188b,
                    0x604d5800, 0x3fd07138, 0x16ed4e91, 0x3cf89cdb, 0x5626c800,
                    0x3fd01eae, 0x1485e94a, 0xbd16f08c, 0x6cb3b000, 0x3fcf991c,
                    0xca0cdf30, 0x3d1bcbec, 0xe4dd0000, 0x3fcef5ad, 0x65bb8e11,
                    0xbcca2115, 0xffe71000, 0x3fce530e, 0x6041f430, 0x3cc21227,
                    0xb0d49000, 0x3fcdb13d, 0xf715b035, 0xbd2aff2a, 0xf2656000,
                    0x3fcd1037, 0x75b6f6e4, 0xbd084a7e, 0xc6f01000, 0x3fcc6ffb,
                    0xc5962bd2, 0xbcf1ec72, 0x383be000, 0x3fcbd087, 0x595412b6,
                    0xbd2d4bc4, 0x575bd000, 0x3fcb31d8, 0x4eace1aa, 0xbd0c358d,
                    0x3c8ae000, 0x3fca93ed, 0x50562169, 0xbd287243, 0x07089000,
                    0x3fc9f6c4, 0x6865817a, 0x3d29904d, 0xdcf70000, 0x3fc95a5a,
                    0x58a0ff6f, 0x3d07f228, 0xeb390000, 0x3fc8beaf, 0xaae92cd1,
                    0xbd073d54, 0x6551a000, 0x3fc823c1, 0x9a631e83, 0x3d1e0ddb,
                    0x85445000, 0x3fc7898d, 0x70914305, 0xbd1c6610, 0x8b757000,
                    0x3fc6f012, 0xe59c21e1, 0xbd25118d, 0xbe8c1000, 0x3fc6574e,
                    0x2c3c2e78, 0x3d19cf8b, 0x6b544000, 0x3fc5bf40, 0xeb68981c,
                    0xbd127023, 0xe4a1b000, 0x3fc527e5, 0xe5697dc7, 0x3d2633e8,
                    0x8333b000, 0x3fc4913d, 0x54fdb678, 0x3d258379, 0xa5993000,
                    0x3fc3fb45, 0x7e6a354d, 0xbd2cd1d8, 0xb0159000, 0x3fc365fc,
                    0x234b7289, 0x3cc62fa8, 0x0c868000, 0x3fc2d161, 0xcb81b4a1,
                    0x3d039d6c, 0x2a49c000, 0x3fc23d71, 0x8fd3df5c, 0x3d100d23,
                    0x7e23f000, 0x3fc1aa2b, 0x44389934, 0x3d2ca78e, 0x8227e000,
                    0x3fc1178e, 0xce2d07f2, 0x3d21ef78, 0xb59e4000, 0x3fc08598,
                    0x7009902c, 0xbd27e5dd, 0x39dbe000, 0x3fbfe891, 0x4fa10afd,
                    0xbd2534d6, 0x830a2000, 0x3fbec739, 0xafe645e0, 0xbd2dc068,
                    0x63844000, 0x3fbda727, 0x1fa71733, 0x3d1a8940, 0x01bc4000,
                    0x3fbc8858, 0xc65aacd3, 0x3d2646d1, 0x8dad6000, 0x3fbb6ac8,
                    0x2bf768e5, 0xbd139080, 0x40b1c000, 0x3fba4e76, 0xb94407c8,
                    0xbd0e42b6, 0x5d594000, 0x3fb9335e, 0x3abd47da, 0x3d23115c,
                    0x2f40e000, 0x3fb8197e, 0xf96ffdf7, 0x3d0f80dc, 0x0aeac000,
                    0x3fb700d3, 0xa99ded32, 0x3cec1e8d, 0x4d97a000, 0x3fb5e95a,
                    0x3c5d1d1e, 0xbd2c6906, 0x5d208000, 0x3fb4d311, 0x82f4e1ef,
                    0xbcf53a25, 0xa7d1e000, 0x3fb3bdf5, 0xa5db4ed7, 0x3d2cc85e,
                    0xa4472000, 0x3fb2aa04, 0xae9c697d, 0xbd20b6e8, 0xd1466000,
                    0x3fb1973b, 0x560d9e9b, 0xbd25325d, 0xb59e4000, 0x3fb08598,
                    0x7009902c, 0xbd17e5dd, 0xc006c000, 0x3faeea31, 0x4fc93b7b,
                    0xbd0e113e, 0xcdddc000, 0x3faccb73, 0x47d82807, 0xbd1a68f2,
                    0xd0fb0000, 0x3faaaef2, 0x353bb42e, 0x3d20fc1a, 0x149fc000,
                    0x3fa894aa, 0xd05a267d, 0xbd197995, 0xf2d4c000, 0x3fa67c94,
                    0xec19afa2, 0xbd029efb, 0xd42e0000, 0x3fa466ae, 0x75bdfd28,
                    0xbd2c1673, 0x2f8d0000, 0x3fa252f3, 0xe021b67b, 0x3d283e9a,
                    0x89e74000, 0x3fa0415d, 0x5cf1d753, 0x3d0111c0, 0xec148000,
                    0x3f9c63d2, 0x3f9eb2f3, 0x3d2578c6, 0x28c90000, 0x3f984925,
                    0x325a0c34, 0xbd2aa0ba, 0x25980000, 0x3f9432a9, 0x928637fe,
                    0x3d098139, 0x58938000, 0x3f902056, 0x06e2f7d2, 0xbd23dc5b,
                    0xa3890000, 0x3f882448, 0xda74f640, 0xbd275577, 0x75890000,
                    0x3f801015, 0x999d2be8, 0xbd10c76b, 0x59580000, 0x3f700805,
                    0xcb31c67b, 0x3d2166af, 0x00000000, 0x00000000, 0x00000000,
                    0x80000000
    };

    private static int[] logTwoData = {
                    0xfefa3800, 0x3fa62e42, 0x93c76730, 0x3ceef357
    };

    private static int[] coeffLogTwoData = {
                    0x92492492, 0x3fc24924, 0x00000000, 0xbfd00000, 0x3d6fb175,
                    0xbfc5555e, 0x55555555, 0x3fd55555, 0x9999999a, 0x3fc99999,
                    0x00000000, 0xbfe00000
    };

    /*
     * Copyright (c) 2014, 2016, Intel Corporation. All rights reserved. Intel Math Library (LIBM)
     * Source Code
     *
     * ALGORITHM DESCRIPTION - LOG() ---------------------
     *
     * x=2^k * mx, mx in [1,2)
     *
     * Get B~1/mx based on the output of rcpps instruction (B0) B = int((B0*2^7+0.5))/2^7
     *
     * Reduced argument: r=B*mx-1.0 (computed accurately in high and low parts)
     *
     * Result: k*log(2) - log(B) + p(r) if |x-1| >= small value (2^-6) and p(r) is a degree 7
     * polynomial -log(B) read from data table (high, low parts) Result is formed from high and low
     * parts.
     *
     * Special cases: log(NaN) = quiet NaN, and raise invalid exception log(+INF) = that INF log(0)
     * = -INF with divide-by-zero exception raised log(1) = +0 log(x) = NaN with invalid exception
     * raised if x < -0, including -INF
     *
     */

    public void logIntrinsic(Register dest, Register value, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        ArrayDataPointerConstant logTwoTablePtr = new ArrayDataPointerConstant(logTwoTable, INT_ARRAY, crb, 16);
        ArrayDataPointerConstant logTwoDataPtr = new ArrayDataPointerConstant(logTwoData, INT_ARRAY, crb, 16);
        ArrayDataPointerConstant coeffLogTwoDataPtr = new ArrayDataPointerConstant(coeffLogTwoData, INT_ARRAY, crb, 16);

        Label bb0 = new Label();
        Label bb1 = new Label();
        Label bb2 = new Label();
        Label bb3 = new Label();
        Label bb4 = new Label();
        Label bb5 = new Label();
        Label bb6 = new Label();
        Label bb7 = new Label();
        Label bb8 = new Label();

        Register gpr1 = asRegister(gpr1Temp, AMD64Kind.QWORD);
        Register gpr2 = asRegister(gpr2Temp, AMD64Kind.QWORD);
        Register gpr3 = asRegister(gpr3Temp, AMD64Kind.QWORD);
        Register gpr4 = asRegister(gpr4Temp, AMD64Kind.QWORD);

        Register temp1 = asRegister(xmm1Temp, AMD64Kind.DOUBLE);
        Register temp2 = asRegister(xmm2Temp, AMD64Kind.DOUBLE);
        Register temp3 = asRegister(xmm3Temp, AMD64Kind.DOUBLE);
        Register temp4 = asRegister(xmm4Temp, AMD64Kind.DOUBLE);
        Register temp5 = asRegister(xmm5Temp, AMD64Kind.DOUBLE);
        Register temp6 = asRegister(xmm6Temp, AMD64Kind.DOUBLE);
        Register temp7 = asRegister(xmm7Temp, AMD64Kind.DOUBLE);

        AMD64Address stackSlot = (AMD64Address) crb.asAddress(stackTemp);

        masm.movdq(stackSlot, value);
        if (dest.encoding != value.encoding) {
            masm.movdqu(dest, value);
        }
        masm.movq(gpr1, 0x3ff0000000000000L);
        masm.movdq(temp2, gpr1);
        masm.movq(gpr3, 0x77f0000000000000L);
        masm.movdq(temp3, gpr3);
        masm.movl(gpr2, 32768);
        masm.movdl(temp4, gpr2);
        masm.movq(gpr2, 0xffffe00000000000L);
        masm.movdq(temp5, gpr2);
        masm.movdqu(temp1, value);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.movl(gpr2, 16352);
        masm.psrlq(dest, 27);
        masm.leaq(gpr4, (AMD64Address) crb.recordDataReferenceInCode(logTwoTablePtr));
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp1, 12);
        masm.pshufd(temp6, temp5, 0xE4);
        masm.psrlq(temp1, 12);
        masm.subl(gpr1, 16);
        masm.cmpl(gpr1, 32736);
        masm.jcc(ConditionFlag.AboveEqual, bb0);

        masm.bind(bb1);
        masm.paddd(dest, temp4);
        masm.por(temp1, temp3);
        masm.movdl(gpr3, dest);
        masm.psllq(dest, 29);
        masm.pand(temp5, temp1);
        masm.pand(dest, temp6);
        masm.subsd(temp1, temp5);
        masm.mulpd(temp5, dest);
        masm.andl(gpr1, 32752);
        masm.subl(gpr1, gpr2);
        masm.cvtsi2sdl(temp7, gpr1);
        masm.mulsd(temp1, dest);
        masm.movdq(temp6, (AMD64Address) crb.recordDataReferenceInCode(logTwoDataPtr));       // 0xfefa3800,
                                                                                              // 0x3fa62e42
        masm.movdqu(temp3, (AMD64Address) crb.recordDataReferenceInCode(coeffLogTwoDataPtr)); // 0x92492492,
                                                                                              // 0x3fc24924,
                                                                                              // 0x00000000,
                                                                                              // 0xbfd00000
        masm.subsd(temp5, temp2);
        masm.andl(gpr3, 16711680);
        masm.shrl(gpr3, 12);
        masm.movdqu(dest, new AMD64Address(gpr4, gpr3, Scale.Times1, 0));
        masm.leaq(gpr4, (AMD64Address) crb.recordDataReferenceInCode(coeffLogTwoDataPtr));
        masm.movdqu(temp4, new AMD64Address(gpr4, 16));                                       // 0x3d6fb175,
                                                                                              // 0xbfc5555e,
                                                                                              // 0x55555555,
                                                                                              // 0x3fd55555
        masm.addsd(temp1, temp5);
        masm.movdqu(temp2, new AMD64Address(gpr4, 32));                                       // 0x9999999a,
                                                                                              // 0x3fc99999,
                                                                                              // 0x00000000,
                                                                                              // 0xbfe00000
        masm.mulsd(temp6, temp7);
        if (masm.supports(CPUFeature.SSE3)) {
            masm.movddup(temp5, temp1);
        } else {
            masm.movdqu(temp5, temp1);
            masm.movlhps(temp5, temp5);
        }
        masm.leaq(gpr4, (AMD64Address) crb.recordDataReferenceInCode(logTwoDataPtr));
        masm.mulsd(temp7, new AMD64Address(gpr4, 8));                                         // 0x93c76730,
                                                                                              // 0x3ceef357
        masm.mulsd(temp3, temp1);
        masm.addsd(dest, temp6);
        masm.mulpd(temp4, temp5);
        masm.mulpd(temp5, temp5);
        if (masm.supports(CPUFeature.SSE3)) {
            masm.movddup(temp6, dest);
        } else {
            masm.movdqu(temp6, dest);
            masm.movlhps(temp6, temp6);
        }
        masm.addsd(dest, temp1);
        masm.addpd(temp4, temp2);
        masm.mulpd(temp3, temp5);
        masm.subsd(temp6, dest);
        masm.mulsd(temp4, temp1);
        masm.pshufd(temp2, dest, 0xEE);
        masm.addsd(temp1, temp6);
        masm.mulsd(temp5, temp5);
        masm.addsd(temp7, temp2);
        masm.addpd(temp4, temp3);
        masm.addsd(temp1, temp7);
        masm.mulpd(temp4, temp5);
        masm.addsd(temp1, temp4);
        masm.pshufd(temp5, temp4, 0xEE);
        masm.addsd(temp1, temp5);
        masm.addsd(dest, temp1);
        masm.jmp(bb8);

        masm.bind(bb0);
        masm.movdq(dest, stackSlot);
        masm.movdq(temp1, stackSlot);
        masm.addl(gpr1, 16);
        masm.cmpl(gpr1, 32768);
        masm.jcc(ConditionFlag.AboveEqual, bb2);

        masm.cmpl(gpr1, 16);
        masm.jcc(ConditionFlag.Below, bb3);

        masm.bind(bb4);
        masm.addsd(dest, dest);
        masm.jmp(bb8);

        masm.bind(bb5);
        masm.jcc(ConditionFlag.Above, bb4);

        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Above, bb4);

        masm.jmp(bb6);

        masm.bind(bb3);
        masm.xorpd(temp1, temp1);
        masm.addsd(temp1, dest);
        masm.movdl(gpr3, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr2, temp1);
        masm.orl(gpr3, gpr2);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Equal, bb7);

        masm.xorpd(temp1, temp1);
        masm.movl(gpr1, 18416);
        masm.pinsrw(temp1, gpr1, 3);
        masm.mulsd(dest, temp1);
        masm.movdqu(temp1, dest);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.psrlq(dest, 27);
        masm.movl(gpr2, 18416);
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp1, 12);
        masm.pshufd(temp6, temp5, 0xE4);
        masm.psrlq(temp1, 12);
        masm.jmp(bb1);

        masm.bind(bb2);
        masm.movdl(gpr3, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr2, temp1);
        masm.addl(gpr2, gpr2);
        masm.cmpl(gpr2, -2097152);
        masm.jcc(ConditionFlag.AboveEqual, bb5);

        masm.orl(gpr3, gpr2);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Equal, bb7);

        masm.bind(bb6);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 32752);
        masm.pinsrw(temp1, gpr1, 3);
        masm.mulsd(dest, temp1);
        masm.jmp(bb8);

        masm.bind(bb7);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 49136);
        masm.pinsrw(dest, gpr1, 3);
        masm.divsd(dest, temp1);

        masm.bind(bb8);
    }

    private static int[] highmaskLogTen = {
                    0xf8000000, 0xffffffff, 0x00000000, 0xffffe000
    };

    private static int[] logTenE = {
                    0x00000000, 0x3fdbc000, 0xbf2e4108, 0x3f5a7a6c
    };

    private static int[] logTenTable = {
                    0x509f7800, 0x3fd34413, 0x1f12b358, 0x3d1fef31, 0x80333400,
                    0x3fd32418, 0xc671d9d0, 0xbcf542bf, 0x51195000, 0x3fd30442,
                    0x78a4b0c3, 0x3d18216a, 0x6fc79400, 0x3fd2e490, 0x80fa389d,
                    0xbc902869, 0x89d04000, 0x3fd2c502, 0x75c2f564, 0x3d040754,
                    0x4ddd1c00, 0x3fd2a598, 0xd219b2c3, 0xbcfa1d84, 0x6baa7c00,
                    0x3fd28651, 0xfd9abec1, 0x3d1be6d3, 0x94028800, 0x3fd2672d,
                    0xe289a455, 0xbd1ede5e, 0x78b86400, 0x3fd2482c, 0x6734d179,
                    0x3d1fe79b, 0xcca3c800, 0x3fd2294d, 0x981a40b8, 0xbced34ea,
                    0x439c5000, 0x3fd20a91, 0xcc392737, 0xbd1a9cc3, 0x92752c00,
                    0x3fd1ebf6, 0x03c9afe7, 0x3d1e98f8, 0x6ef8dc00, 0x3fd1cd7d,
                    0x71dae7f4, 0x3d08a86c, 0x8fe4dc00, 0x3fd1af25, 0xee9185a1,
                    0xbcff3412, 0xace59400, 0x3fd190ee, 0xc2cab353, 0x3cf17ed9,
                    0x7e925000, 0x3fd172d8, 0x6952c1b2, 0x3cf1521c, 0xbe694400,
                    0x3fd154e2, 0xcacb79ca, 0xbd0bdc78, 0x26cbac00, 0x3fd1370d,
                    0xf71f4de1, 0xbd01f8be, 0x72fa0800, 0x3fd11957, 0x55bf910b,
                    0x3c946e2b, 0x5f106000, 0x3fd0fbc1, 0x39e639c1, 0x3d14a84b,
                    0xa802a800, 0x3fd0de4a, 0xd3f31d5d, 0xbd178385, 0x0b992000,
                    0x3fd0c0f3, 0x3843106f, 0xbd1f602f, 0x486ce800, 0x3fd0a3ba,
                    0x8819497c, 0x3cef987a, 0x1de49400, 0x3fd086a0, 0x1caa0467,
                    0x3d0faec7, 0x4c30cc00, 0x3fd069a4, 0xa4424372, 0xbd1618fc,
                    0x94490000, 0x3fd04cc6, 0x946517d2, 0xbd18384b, 0xb7e84000,
                    0x3fd03006, 0xe0109c37, 0xbd19a6ac, 0x798a0c00, 0x3fd01364,
                    0x5121e864, 0xbd164cf7, 0x38ce8000, 0x3fcfedbf, 0x46214d1a,
                    0xbcbbc402, 0xc8e62000, 0x3fcfb4ef, 0xdab93203, 0x3d1e0176,
                    0x2cb02800, 0x3fcf7c5a, 0x2a2ea8e4, 0xbcfec86a, 0xeeeaa000,
                    0x3fcf43fd, 0xc18e49a4, 0x3cf110a8, 0x9bb6e800, 0x3fcf0bda,
                    0x923cc9c0, 0xbd15ce99, 0xc093f000, 0x3fced3ef, 0x4d4b51e9,
                    0x3d1a04c7, 0xec58f800, 0x3fce9c3c, 0x163cad59, 0x3cac8260,
                    0x9a907000, 0x3fce2d7d, 0x3fa93646, 0x3ce4a1c0, 0x37311000,
                    0x3fcdbf99, 0x32abd1fd, 0x3d07ea9d, 0x6744b800, 0x3fcd528c,
                    0x4dcbdfd4, 0xbd1b08e2, 0xe36de800, 0x3fcce653, 0x0b7b7f7f,
                    0xbd1b8f03, 0x77506800, 0x3fcc7aec, 0xa821c9fb, 0x3d13c163,
                    0x00ff8800, 0x3fcc1053, 0x536bca76, 0xbd074ee5, 0x70719800,
                    0x3fcba684, 0xd7da9b6b, 0xbd1fbf16, 0xc6f8d800, 0x3fcb3d7d,
                    0xe2220bb3, 0x3d1a295d, 0x16c15800, 0x3fcad53c, 0xe724911e,
                    0xbcf55822, 0x82533800, 0x3fca6dbc, 0x6d982371, 0x3cac567c,
                    0x3c19e800, 0x3fca06fc, 0x84d17d80, 0x3d1da204, 0x85ef8000,
                    0x3fc9a0f8, 0x54466a6a, 0xbd002204, 0xb0ac2000, 0x3fc93bae,
                    0xd601fd65, 0x3d18840c, 0x1bb9b000, 0x3fc8d71c, 0x7bf58766,
                    0xbd14f897, 0x34aae800, 0x3fc8733e, 0x3af6ac24, 0xbd0f5c45,
                    0x76d68000, 0x3fc81012, 0x4303e1a1, 0xbd1f9a80, 0x6af57800,
                    0x3fc7ad96, 0x43fbcb46, 0x3cf4c33e, 0xa6c51000, 0x3fc74bc7,
                    0x70f0eac5, 0xbd192e3b, 0xccab9800, 0x3fc6eaa3, 0xc0093dfe,
                    0xbd0faf15, 0x8b60b800, 0x3fc68a28, 0xde78d5fd, 0xbc9ea4ee,
                    0x9d987000, 0x3fc62a53, 0x962bea6e, 0xbd194084, 0xc9b0e800,
                    0x3fc5cb22, 0x888dd999, 0x3d1fe201, 0xe1634800, 0x3fc56c93,
                    0x16ada7ad, 0x3d1b1188, 0xc176c000, 0x3fc50ea4, 0x4159b5b5,
                    0xbcf09c08, 0x51766000, 0x3fc4b153, 0x84393d23, 0xbcf6a89c,
                    0x83695000, 0x3fc4549d, 0x9f0b8bbb, 0x3d1c4b8c, 0x538d5800,
                    0x3fc3f881, 0xf49df747, 0x3cf89b99, 0xc8138000, 0x3fc39cfc,
                    0xd503b834, 0xbd13b99f, 0xf0df0800, 0x3fc3420d, 0xf011b386,
                    0xbd05d8be, 0xe7466800, 0x3fc2e7b2, 0xf39c7bc2, 0xbd1bb94e,
                    0xcdd62800, 0x3fc28de9, 0x05e6d69b, 0xbd10ed05, 0xd015d800,
                    0x3fc234b0, 0xe29b6c9d, 0xbd1ff967, 0x224ea800, 0x3fc1dc06,
                    0x727711fc, 0xbcffb30d, 0x01540000, 0x3fc183e8, 0x39786c5a,
                    0x3cc23f57, 0xb24d9800, 0x3fc12c54, 0xc905a342, 0x3d003a1d,
                    0x82835800, 0x3fc0d54a, 0x9b9920c0, 0x3d03b25a, 0xc72ac000,
                    0x3fc07ec7, 0x46f26a24, 0x3cf0fa41, 0xdd35d800, 0x3fc028ca,
                    0x41d9d6dc, 0x3d034a65, 0x52474000, 0x3fbfa6a4, 0x44f66449,
                    0x3d19cad3, 0x2da3d000, 0x3fbefcb8, 0x67832999, 0x3d18400f,
                    0x32a10000, 0x3fbe53ce, 0x9c0e3b1a, 0xbcff62fd, 0x556b7000,
                    0x3fbdabe3, 0x02976913, 0xbcf8243b, 0x97e88000, 0x3fbd04f4,
                    0xec793797, 0x3d1c0578, 0x09647000, 0x3fbc5eff, 0x05fc0565,
                    0xbd1d799e, 0xc6426000, 0x3fbbb9ff, 0x4625f5ed, 0x3d1f5723,
                    0xf7afd000, 0x3fbb15f3, 0xdd5aae61, 0xbd1a7e1e, 0xd358b000,
                    0x3fba72d8, 0x3314e4d3, 0x3d17bc91, 0x9b1f5000, 0x3fb9d0ab,
                    0x9a4d514b, 0x3cf18c9b, 0x9cd4e000, 0x3fb92f69, 0x7e4496ab,
                    0x3cf1f96d, 0x31f4f000, 0x3fb88f10, 0xf56479e7, 0x3d165818,
                    0xbf628000, 0x3fb7ef9c, 0x26bf486d, 0xbd1113a6, 0xb526b000,
                    0x3fb7510c, 0x1a1c3384, 0x3ca9898d, 0x8e31e000, 0x3fb6b35d,
                    0xb3875361, 0xbd0661ac, 0xd01de000, 0x3fb6168c, 0x2a7cacfa,
                    0xbd1bdf10, 0x0af23000, 0x3fb57a98, 0xff868816, 0x3cf046d0,
                    0xd8ea0000, 0x3fb4df7c, 0x1515fbe7, 0xbd1fd529, 0xde3b2000,
                    0x3fb44538, 0x6e59a132, 0x3d1faeee, 0xc8df9000, 0x3fb3abc9,
                    0xf1322361, 0xbd198807, 0x505f1000, 0x3fb3132d, 0x0888e6ab,
                    0x3d1e5380, 0x359bd000, 0x3fb27b61, 0xdfbcbb22, 0xbcfe2724,
                    0x429ee000, 0x3fb1e463, 0x6eb4c58c, 0xbcfe4dd6, 0x4a673000,
                    0x3fb14e31, 0x4ce1ac9b, 0x3d1ba691, 0x28b96000, 0x3fb0b8c9,
                    0x8c7813b8, 0xbd0b3872, 0xc1f08000, 0x3fb02428, 0xc2bc8c2c,
                    0x3cb5ea6b, 0x05a1a000, 0x3faf209c, 0x72e8f18e, 0xbce8df84,
                    0xc0b5e000, 0x3fadfa6d, 0x9fdef436, 0x3d087364, 0xaf416000,
                    0x3facd5c2, 0x1068c3a9, 0x3d0827e7, 0xdb356000, 0x3fabb296,
                    0x120a34d3, 0x3d101a9f, 0x5dfea000, 0x3faa90e6, 0xdaded264,
                    0xbd14c392, 0x6034c000, 0x3fa970ad, 0x1c9d06a9, 0xbd1b705e,
                    0x194c6000, 0x3fa851e8, 0x83996ad9, 0xbd0117bc, 0xcf4ac000,
                    0x3fa73492, 0xb1a94a62, 0xbca5ea42, 0xd67b4000, 0x3fa618a9,
                    0x75aed8ca, 0xbd07119b, 0x9126c000, 0x3fa4fe29, 0x5291d533,
                    0x3d12658f, 0x6f4d4000, 0x3fa3e50e, 0xcd2c5cd9, 0x3d1d5c70,
                    0xee608000, 0x3fa2cd54, 0xd1008489, 0x3d1a4802, 0x9900e000,
                    0x3fa1b6f9, 0x54fb5598, 0xbd16593f, 0x06bb6000, 0x3fa0a1f9,
                    0x64ef57b4, 0xbd17636b, 0xb7940000, 0x3f9f1c9f, 0xee6a4737,
                    0x3cb5d479, 0x91aa0000, 0x3f9cf7f5, 0x3a16373c, 0x3d087114,
                    0x156b8000, 0x3f9ad5ed, 0x836c554a, 0x3c6900b0, 0xd4764000,
                    0x3f98b67f, 0xed12f17b, 0xbcffc974, 0x77dec000, 0x3f9699a7,
                    0x232ce7ea, 0x3d1e35bb, 0xbfbf4000, 0x3f947f5d, 0xd84ffa6e,
                    0x3d0e0a49, 0x82c7c000, 0x3f92679c, 0x8d170e90, 0xbd14d9f2,
                    0xadd20000, 0x3f90525d, 0x86d9f88e, 0x3cdeb986, 0x86f10000,
                    0x3f8c7f36, 0xb9e0a517, 0x3ce29faa, 0xb75c8000, 0x3f885e9e,
                    0x542568cb, 0xbd1f7bdb, 0x46b30000, 0x3f8442e8, 0xb954e7d9,
                    0x3d1e5287, 0xb7e60000, 0x3f802c07, 0x22da0b17, 0xbd19fb27,
                    0x6c8b0000, 0x3f7833e3, 0x821271ef, 0xbd190f96, 0x29910000,
                    0x3f701936, 0xbc3491a5, 0xbd1bcf45, 0x354a0000, 0x3f600fe3,
                    0xc0ff520a, 0xbd19d71c, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000
    };

    private static int[] logTwoLogTenData = {
                    0x509f7800, 0x3f934413, 0x1f12b358, 0x3cdfef31
    };

    private static int[] coeffLogTenData = {
                    0xc1a5f12e, 0x40358874, 0x64d4ef0d, 0xc0089309, 0x385593b1,
                    0xc025c917, 0xdc963467, 0x3ffc6a02, 0x7f9d3aa1, 0x4016ab9f,
                    0xdc77b115, 0xbff27af2
    };

    /*
     * Copyright (c) 2014, 2016, Intel Corporation. All rights reserved. Intel Math Library (LIBM)
     * Source Code
     *
     * ALGORITHM DESCRIPTION - LOG10() ---------------------
     *
     * Let x=2^k * mx, mx in [1,2)
     *
     * Get B~1/mx based on the output of rcpss instruction (B0) B = int((B0*LH*2^7+0.5))/2^7 LH is a
     * short approximation for log10(e)
     *
     * Reduced argument: r=B*mx-LH (computed accurately in high and low parts)
     *
     * Result: k*log10(2) - log(B) + p(r) p(r) is a degree 7 polynomial -log(B) read from data table
     * (high, low parts) Result is formed from high and low parts
     *
     * Special cases: log10(0) = -INF with divide-by-zero exception raised log10(1) = +0 log10(x) =
     * NaN with invalid exception raised if x < -0, including -INF log10(+INF) = +INF
     *
     */

    public void log10Intrinsic(Register dest, Register value, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        ArrayDataPointerConstant highmaskLogTenPtr = new ArrayDataPointerConstant(highmaskLogTen, INT_ARRAY, crb, 16);
        ArrayDataPointerConstant logTenEPtr = new ArrayDataPointerConstant(logTenE, INT_ARRAY, crb, 16);
        ArrayDataPointerConstant logTenTablePtr = new ArrayDataPointerConstant(logTenTable, INT_ARRAY, crb, 16);
        ArrayDataPointerConstant logTwoLogTenDataPtr = new ArrayDataPointerConstant(logTwoLogTenData, INT_ARRAY, crb, 16);
        ArrayDataPointerConstant coeffLogTenDataPtr = new ArrayDataPointerConstant(coeffLogTenData, INT_ARRAY, crb, 16);

        Label bb0 = new Label();
        Label bb1 = new Label();
        Label bb2 = new Label();
        Label bb3 = new Label();
        Label bb4 = new Label();
        Label bb5 = new Label();
        Label bb6 = new Label();
        Label bb7 = new Label();
        Label bb8 = new Label();

        Register gpr1 = asRegister(gpr1Temp, AMD64Kind.QWORD);
        Register gpr2 = asRegister(gpr2Temp, AMD64Kind.QWORD);
        Register gpr3 = asRegister(gpr3Temp, AMD64Kind.QWORD);
        Register gpr4 = asRegister(gpr4Temp, AMD64Kind.QWORD);

        Register temp1 = asRegister(xmm1Temp, AMD64Kind.DOUBLE);
        Register temp2 = asRegister(xmm2Temp, AMD64Kind.DOUBLE);
        Register temp3 = asRegister(xmm3Temp, AMD64Kind.DOUBLE);
        Register temp4 = asRegister(xmm4Temp, AMD64Kind.DOUBLE);
        Register temp5 = asRegister(xmm5Temp, AMD64Kind.DOUBLE);
        Register temp6 = asRegister(xmm6Temp, AMD64Kind.DOUBLE);
        Register temp7 = asRegister(xmm7Temp, AMD64Kind.DOUBLE);

        AMD64Address stackSlot = (AMD64Address) crb.asAddress(stackTemp);

        masm.movdq(stackSlot, value);
        if (dest.encoding != value.encoding) {
            masm.movdqu(dest, value);
        }
        masm.movdqu(temp5, (AMD64Address) crb.recordDataReferenceInCode(highmaskLogTenPtr));  // 0xf8000000,
                                                                                              // 0xffffffff,
                                                                                              // 0x00000000,
                                                                                              // 0xffffe000
        masm.xorpd(temp2, temp2);
        masm.movl(gpr1, 16368);
        masm.pinsrw(temp2, gpr1, 3);
        masm.movl(gpr2, 1054736384);
        masm.movdl(temp7, gpr2);
        masm.xorpd(temp3, temp3);
        masm.movl(gpr3, 30704);
        masm.pinsrw(temp3, gpr3, 3);
        masm.movl(gpr3, 32768);
        masm.movdl(temp4, gpr3);
        masm.movdqu(temp1, value);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.movl(gpr2, 16352);
        masm.psrlq(dest, 27);
        masm.movdqu(temp2, (AMD64Address) crb.recordDataReferenceInCode(logTenEPtr));         // 0x00000000,
                                                                                              // 0x3fdbc000,
                                                                                              // 0xbf2e4108,
                                                                                              // 0x3f5a7a6c
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp1, 12);
        masm.pshufd(temp6, temp5, 0x4E);
        masm.psrlq(temp1, 12);
        masm.subl(gpr1, 16);
        masm.cmpl(gpr1, 32736);
        masm.jcc(ConditionFlag.AboveEqual, bb0);

        masm.bind(bb1);
        masm.mulss(dest, temp7);
        masm.por(temp1, temp3);
        masm.andpd(temp5, temp1);
        masm.paddd(dest, temp4);
        masm.movdqu(temp3, (AMD64Address) crb.recordDataReferenceInCode(coeffLogTenDataPtr)); // 0xc1a5f12e,
                                                                                              // 0x40358874,
                                                                                              // 0x64d4ef0d,
                                                                                              // 0xc0089309
        masm.leaq(gpr4, (AMD64Address) crb.recordDataReferenceInCode(coeffLogTenDataPtr));
        masm.movdqu(temp4, new AMD64Address(gpr4, 16));                                       // 0x385593b1,
                                                                                              // 0xc025c917,
                                                                                              // 0xdc963467,
                                                                                              // 0x3ffc6a02
        masm.subsd(temp1, temp5);
        masm.movdl(gpr3, dest);
        masm.psllq(dest, 29);
        masm.andpd(dest, temp6);
        masm.movdq(temp6, (AMD64Address) crb.recordDataReferenceInCode(logTwoLogTenDataPtr)); // 0x509f7800,
                                                                                              // 0x3f934413
        masm.andl(gpr1, 32752);
        masm.subl(gpr1, gpr2);
        masm.cvtsi2sdl(temp7, gpr1);
        masm.mulpd(temp5, dest);
        masm.mulsd(temp1, dest);
        masm.subsd(temp5, temp2);
        masm.movdqu(temp2, new AMD64Address(gpr4, 32));                                       // 0x7f9d3aa1,
                                                                                              // 0x4016ab9f,
                                                                                              // 0xdc77b115,
                                                                                              // 0xbff27af2
        masm.leaq(gpr4, (AMD64Address) crb.recordDataReferenceInCode(logTenTablePtr));
        masm.andl(gpr3, 16711680);
        masm.shrl(gpr3, 12);
        masm.movdqu(dest, new AMD64Address(gpr4, gpr3, Scale.Times1, -1504));
        masm.addsd(temp1, temp5);
        masm.mulsd(temp6, temp7);
        masm.pshufd(temp5, temp1, 0x44);
        masm.leaq(gpr4, (AMD64Address) crb.recordDataReferenceInCode(logTwoLogTenDataPtr));
        masm.mulsd(temp7, new AMD64Address(gpr4, 8));                                         // 0x1f12b358,
                                                                                              // 0x3cdfef31
        masm.mulsd(temp3, temp1);
        masm.addsd(dest, temp6);
        masm.mulpd(temp4, temp5);
        masm.leaq(gpr4, (AMD64Address) crb.recordDataReferenceInCode(logTenEPtr));
        masm.movdq(temp6, new AMD64Address(gpr4, 8));                                         // 0xbf2e4108,
                                                                                              // 0x3f5a7a6c
        masm.mulpd(temp5, temp5);
        masm.addpd(temp4, temp2);
        masm.mulpd(temp3, temp5);
        masm.pshufd(temp2, dest, 0xE4);
        masm.addsd(dest, temp1);
        masm.mulsd(temp4, temp1);
        masm.subsd(temp2, dest);
        masm.mulsd(temp6, temp1);
        masm.addsd(temp1, temp2);
        masm.pshufd(temp2, dest, 0xEE);
        masm.mulsd(temp5, temp5);
        masm.addsd(temp7, temp2);
        masm.addsd(temp1, temp6);
        masm.addpd(temp4, temp3);
        masm.addsd(temp1, temp7);
        masm.mulpd(temp4, temp5);
        masm.addsd(temp1, temp4);
        masm.pshufd(temp5, temp4, 0xEE);
        masm.addsd(temp1, temp5);
        masm.addsd(dest, temp1);
        masm.jmp(bb8);

        masm.bind(bb0);
        masm.movdq(dest, stackSlot);
        masm.movdq(temp1, stackSlot);
        masm.addl(gpr1, 16);
        masm.cmpl(gpr1, 32768);
        masm.jcc(ConditionFlag.AboveEqual, bb2);

        masm.cmpl(gpr1, 16);
        masm.jcc(ConditionFlag.Below, bb3);

        masm.bind(bb4);
        masm.addsd(dest, dest);
        masm.jmp(bb8);

        masm.bind(bb5);
        masm.jcc(ConditionFlag.Above, bb4);

        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Above, bb4);

        masm.jmp(bb6);

        masm.bind(bb3);
        masm.xorpd(temp1, temp1);
        masm.addsd(temp1, dest);
        masm.movdl(gpr3, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr2, temp1);
        masm.orl(gpr3, gpr2);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Equal, bb7);

        masm.xorpd(temp1, temp1);
        masm.xorpd(temp2, temp2);
        masm.movl(gpr1, 18416);
        masm.pinsrw(temp1, gpr1, 3);
        masm.mulsd(dest, temp1);
        masm.movl(gpr1, 16368);
        masm.pinsrw(temp2, gpr1, 3);
        masm.movdqu(temp1, dest);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.movl(gpr2, 18416);
        masm.psrlq(dest, 27);
        masm.movdqu(temp2, (AMD64Address) crb.recordDataReferenceInCode(logTenEPtr));         // 0x00000000,
                                                                                              // 0x3fdbc000,
                                                                                              // 0xbf2e4108,
                                                                                              // 0x3f5a7a6c
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp1, 12);
        masm.pshufd(temp6, temp5, 0x4E);
        masm.psrlq(temp1, 12);
        masm.jmp(bb1);

        masm.bind(bb2);
        masm.movdl(gpr3, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr2, temp1);
        masm.addl(gpr2, gpr2);
        masm.cmpl(gpr2, -2097152);
        masm.jcc(ConditionFlag.AboveEqual, bb5);

        masm.orl(gpr3, gpr2);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Equal, bb7);

        masm.bind(bb6);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 32752);
        masm.pinsrw(temp1, gpr1, 3);
        masm.mulsd(dest, temp1);
        masm.jmp(bb8);

        masm.bind(bb7);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 49136);
        masm.pinsrw(dest, gpr1, 3);
        masm.divsd(dest, temp1);

        masm.bind(bb8);
    }
}
