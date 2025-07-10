/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Intel Corporation. All rights reserved.
 * Intel Math Library (LIBM) Source Code
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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

/**
 * <pre>
 *                     ALGORITHM DESCRIPTION
 *                     ---------------------
 *
 * x=2^{3*k+j} * 1.b1 b2 ... b5 b6 ... b52
 * Let r=(x*2^{-3k-j} - 1.b1 b2 ... b5 1)* rcp[b1 b2 ..b5],
 * where rcp[b1 b2 .. b5]=1/(1.b1 b2 b3 b4 b5 1) in double precision
 * cbrt(2^j * 1. b1 b2 .. b5 1) is approximated as T[j][b1..b5]+D[j][b1..b5]
 * (T stores the high 53 bits, D stores the low order bits)
 * Result=2^k*T+(2^k*T*r)*P+2^k*D
 * where P=p1+p2*r+..+p8*r^7
 *
 * Special cases:
 *  cbrt(NaN) = quiet NaN
 *  cbrt(+/-INF) = +/-INF
 *  cbrt(+/-0) = +/-0
 * </pre>
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/b1fa1ecc988fb07f191892a459625c2c8f2de3b5/src/hotspot/cpu/x86/stubGenerator_x86_64_cbrt.cpp#L30-L339",
          sha1 = "f852059d5e60328eeefb5bb9c9b336c325a3c5d6")
// @formatter:on
public final class AMD64MathCbrtOp extends AMD64MathIntrinsicUnaryOp {

    public static final LIRInstructionClass<AMD64MathCbrtOp> TYPE = LIRInstructionClass.create(AMD64MathCbrtOp.class);

    public AMD64MathCbrtOp() {
        super(TYPE, /* GPR */ rax, rcx, rdx, r8, r9,
                        /* XMM */ xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);
    }

    private static ArrayDataPointerConstant absMask = pointerConstant(16, new int[]{
                    0xFFFFFFFF, 0x7FFFFFFF, 0x00000000, 0x00000000
    });

    private static ArrayDataPointerConstant sigMask = pointerConstant(16, new int[]{
                    0x00000000, 0x000fc000
    });

    private static ArrayDataPointerConstant expMask = pointerConstant(16, new int[]{
                    0x00000000, 0xbff00000
    });

    private static ArrayDataPointerConstant expMsk2 = pointerConstant(16, new int[]{
                    0x00000000, 0xbff04000
    });

    private static ArrayDataPointerConstant expMsk3 = pointerConstant(16, new int[]{
                    0xffffffff, 0x000fffff
    });

    private static ArrayDataPointerConstant scale63 = pointerConstant(16, new int[]{
                    0x00000000, 0x43e00000
    });

    private static ArrayDataPointerConstant zeron = pointerConstant(16, new int[]{
                    0x00000000, 0x80000000
    });

    private static ArrayDataPointerConstant inf = pointerConstant(16, new int[]{
                    0x00000000, 0x7ff00000
    });

    private static ArrayDataPointerConstant coeffTable = pointerConstant(16, new int[]{
                    0x5c9cc8e7, 0xbf9036de, 0xd2b3183b, 0xbfa511e8,
    });

    private static ArrayDataPointerConstant coeffTable16 = pointerConstant(16, new int[]{
                    0x6221a247, 0xbf98090d, 0x1c71c71c, 0xbfbc71c7,
    });

    private static ArrayDataPointerConstant coeffTable32 = pointerConstant(16, new int[]{
                    0xd588f115, 0x3f93750a, 0x3c0ca458, 0x3faf9add
    });

    private static ArrayDataPointerConstant coeffTable48 = pointerConstant(16, new int[]{
                    0x3506ac12, 0x3f9ee711, 0x55555555, 0x3fd55555
    });

    private static ArrayDataPointerConstant rcpTable = pointerConstant(16, new int[]{
                    0x1f81f820, 0xbfef81f8, 0xabf0b767, 0xbfee9131, 0x76b981db, 0xbfedae60,
                    0x89039b0b, 0xbfecd856, 0x0381c0e0, 0xbfec0e07, 0xb4e81b4f, 0xbfeb4e81,
                    0x606a63be, 0xbfea98ef, 0x951033d9, 0xbfe9ec8e, 0xfcd6e9e0, 0xbfe948b0,
                    0x0f6bf3aa, 0xbfe8acb9, 0x18181818, 0xbfe81818, 0x8178a4c8, 0xbfe78a4c,
                    0x5c0b8170, 0xbfe702e0, 0x16816817, 0xbfe68168, 0x60581606, 0xbfe60581,
                    0x308158ed, 0xbfe58ed2, 0xeae2f815, 0xbfe51d07, 0xa052bf5b, 0xbfe4afd6,
                    0x6562d9fb, 0xbfe446f8, 0xbce4a902, 0xbfe3e22c, 0x13813814, 0xbfe38138,
                    0x4a2b10bf, 0xbfe323e3, 0x4d812ca0, 0xbfe2c9fb, 0xb8812735, 0xbfe27350,
                    0x8121fb78, 0xbfe21fb7, 0xada2811d, 0xbfe1cf06, 0x11811812, 0xbfe18118,
                    0x1135c811, 0xbfe135c8, 0x6be69c90, 0xbfe0ecf5, 0x0a6810a7, 0xbfe0a681,
                    0xd2f1a9fc, 0xbfe0624d, 0x81020408, 0xbfe02040
    });

    private static ArrayDataPointerConstant cbrtTable = pointerConstant(16, new int[]{
                    0x221d4c97, 0x3ff01539, 0x771a2e33, 0x3ff03f06, 0xe629d671, 0x3ff06800,
                    0x8731deb2, 0x3ff09032, 0xb1bd64ac, 0x3ff0b7a4, 0x1024fb87, 0x3ff0de60,
                    0xb0597000, 0x3ff1046c, 0x12a9ba9b, 0x3ff129d2, 0x36cdaf38, 0x3ff14e97,
                    0xa772f507, 0x3ff172c2, 0x848001d3, 0x3ff1965a, 0x8c38c55d, 0x3ff1b964,
                    0x236a0c45, 0x3ff1dbe6, 0x5cbb1f9f, 0x3ff1fde4, 0xff409042, 0x3ff21f63,
                    0x8c6746e5, 0x3ff24069, 0x454bb99b, 0x3ff260f9, 0x2f8e7073, 0x3ff28117,
                    0x19b4b6d0, 0x3ff2a0c7, 0x9f2263ec, 0x3ff2c00c, 0x2bb7fb78, 0x3ff2deeb,
                    0xff1efbbc, 0x3ff2fd65, 0x2fccf6a2, 0x3ff31b80, 0xadc50708, 0x3ff3393c,
                    0x451e4c2a, 0x3ff3569e, 0xa0554cde, 0x3ff373a7, 0x4a6d76ce, 0x3ff3905b,
                    0xb0e756b6, 0x3ff3acbb, 0x258fa340, 0x3ff3c8cb, 0xe02ac0ce, 0x3ff3e48b,
                    0x00000000, 0x3ff40000, 0x8d47800e, 0x3ff41b29, 0x4b34d9b2, 0x3ff44360,
                    0x20906571, 0x3ff4780b, 0x3ee06706, 0x3ff4abac, 0x5da66b8d, 0x3ff4de50,
                    0x420a5c07, 0x3ff51003, 0xd6fd11c1, 0x3ff540cf, 0x4260716b, 0x3ff570c0,
                    0xf7a45f38, 0x3ff59fdd, 0xc83539df, 0x3ff5ce31, 0xf20966a4, 0x3ff5fbc3,
                    0x2c8f1b70, 0x3ff6289c, 0xb4316dcf, 0x3ff654c1, 0x54a34e44, 0x3ff6803b,
                    0x72182659, 0x3ff6ab0f, 0x118c08bc, 0x3ff6d544, 0xe0388d4a, 0x3ff6fede,
                    0x3a4f645e, 0x3ff727e5, 0x31104114, 0x3ff7505c, 0x904cd549, 0x3ff77848,
                    0xe36b2534, 0x3ff79fae, 0x79f4605b, 0x3ff7c693, 0x6bbca391, 0x3ff7ecfa,
                    0x9cae7eb9, 0x3ff812e7, 0xc043c71d, 0x3ff8385e, 0x5cb41b9d, 0x3ff85d63,
                    0xcde083db, 0x3ff881f8, 0x4802b8a8, 0x3ff8a622, 0xda25e5e4, 0x3ff8c9e2,
                    0x706e1010, 0x3ff8ed3d, 0xd632b6df, 0x3ff91034, 0xb7f0cf2d, 0x3ff932cb,
                    0xa517bf3a, 0x3ff95504, 0x34f8bb19, 0x3ff987af, 0x8337b317, 0x3ff9ca0a,
                    0x09cc13d5, 0x3ffa0b17, 0xce6419ed, 0x3ffa4ae4, 0xa5567031, 0x3ffa8982,
                    0x500ab570, 0x3ffac6fe, 0x97a15a17, 0x3ffb0364, 0x64671755, 0x3ffb3ec1,
                    0xd288c46f, 0x3ffb791f, 0x44693be4, 0x3ffbb28a, 0x72eb6e31, 0x3ffbeb0a,
                    0x7bf5f697, 0x3ffc22a9, 0xef6af983, 0x3ffc596f, 0xdac655a3, 0x3ffc8f65,
                    0xd38ce8d9, 0x3ffcc492, 0x00b19367, 0x3ffcf8fe, 0x230f8709, 0x3ffd2cae,
                    0x9d15208f, 0x3ffd5fa9, 0x79b6e505, 0x3ffd91f6, 0x72bf2302, 0x3ffdc39a,
                    0xf68c1570, 0x3ffdf49a, 0x2d4c23b8, 0x3ffe24fd, 0xfdc5ec73, 0x3ffe54c5,
                    0x11b81dbb, 0x3ffe83fa, 0xd9dbaf25, 0x3ffeb29d, 0x9191d374, 0x3ffee0b5,
                    0x4245e4bf, 0x3fff0e45, 0xc68a9dd3, 0x3fff3b50, 0xccf922dc, 0x3fff67db,
                    0xdad7a4a6, 0x3fff93e9, 0x4e8cc9cb, 0x3fffbf7e, 0x61e47cd3, 0x3fffea9c
    });

    private static ArrayDataPointerConstant dTable = pointerConstant(16, new int[]{
                    0xf173d5fa, 0x3c76ee36, 0x45055704, 0x3c95b62d, 0x51ee3f07, 0x3ca2545b,
                    0xa7706e18, 0x3c9c65f4, 0xdf1025a1, 0x3c63b83f, 0xb8dec2c5, 0x3ca67428,
                    0x03e33ea6, 0x3ca1823d, 0xa06e6c52, 0x3ca241d3, 0xefa7e815, 0x3ca8b4e1,
                    0x4e754fd0, 0x3cadeac4, 0x3d7c04c0, 0x3c71cc82, 0xc264f127, 0x3c953dc9,
                    0x34d5c5a7, 0x3c93b5f7, 0xb9a7b902, 0x3c7366a3, 0x6433dd6c, 0x3caac888,
                    0x4f401711, 0x3c987a4c, 0x1bbe943f, 0x3c9fab9f, 0xfd6ac93c, 0x3ca0c4b5,
                    0x766f1035, 0x3ca90835, 0x2ce13c95, 0x3ca09fd9, 0x8418c8d8, 0x3cadc143,
                    0xff474261, 0x3c8dc87d, 0x5cd783d3, 0x3c8f8872, 0xe7d0c8aa, 0x3caec35d,
                    0xdba49538, 0x3ca3943b, 0x2b203947, 0x3ca92195, 0xafe6f86c, 0x3c59f556,
                    0x3195a5f9, 0x3caadc99, 0x3d770e65, 0x3ca41943, 0xa36b97ea, 0x3ca76b6e,
                    0xaaaaaaab, 0x3bd46aaa, 0xfee9d063, 0x3c637d40, 0xf514c24e, 0x3c89f356,
                    0x670030e9, 0x3c953f22, 0xa173c1cf, 0x3caea671, 0x3fbcc1dd, 0x3c841d58,
                    0x29b9b818, 0x3c9648f0, 0xad202ab4, 0x3ca1a66d, 0xf2d6b269, 0x3ca7b07b,
                    0x017dc4ad, 0x3c836a36, 0xd6b16f60, 0x3c8b726b, 0xc2bc701d, 0x3cabfe18,
                    0x1dfe451f, 0x3c7e799d, 0x7e7b5452, 0x3caddf5a, 0xea15c5e5, 0x3c734d90,
                    0xa6558f7b, 0x3c8f4cbb, 0x4f4d361a, 0x3c9d473a, 0xf06b5ecf, 0x3c87e2d6,
                    0xdc49b5f3, 0x3ca5f6f5, 0x0f5a41f1, 0x3ca16024, 0xc062c2bc, 0x3ca3586c,
                    0x0df45d94, 0x3ca0c6a9, 0xeef4e10b, 0x3ca2703c, 0x74215c62, 0x3ca99f3e,
                    0x286f88d2, 0x3cafa5ef, 0xb7d00b1f, 0x3c99239e, 0x8ff8e50c, 0x3cabc642,
                    0x0a756b50, 0x3ca33971, 0xbe93d5dc, 0x3c389058, 0x7b752d97, 0x3c8e08ee,
                    0x0fff0a3f, 0x3c9a2fed, 0x37eac5df, 0x3ca42034, 0x6c4969df, 0x3ca35668,
                    0xf5860fa5, 0x3ca082ae, 0x99b322b6, 0x3c62cf11, 0x933e42d8, 0x3c7ac44e,
                    0x0761e377, 0x3c975f68, 0x4b704cc9, 0x3c7c5adf, 0xcb8394dc, 0x3ca0f9ae,
                    0x3e08f0f9, 0x3c9158c1, 0xcfa3f556, 0x3c9c7516, 0xf6cb01cd, 0x3c91d9c1,
                    0xe811c1da, 0x3c9da58f, 0xea9036db, 0x3c2dcd9d, 0xb18fab04, 0x3c8015a8,
                    0x92316223, 0x3cad4c55, 0xbe291e10, 0x3c8c6a0d, 0xfc9476ab, 0x3c8c615d,
                    0x9b9bca75, 0x3cace0d7, 0x7ecc4726, 0x3ca4614a, 0x312152ee, 0x3cacd427,
                    0x811960ca, 0x3cac1ba1, 0xa557fd24, 0x3c6514ca, 0xf5cdf826, 0x3ca712cc,
                    0x75cdbea6, 0x3c9d93a5, 0xf3f3450c, 0x3ca90aaf, 0x071ba369, 0x3c85382f,
                    0xcf26ae90, 0x3ca87e97, 0x75933097, 0x3c86da5c, 0x309c2b19, 0x3ca61791,
                    0x90d5990b, 0x3ca44210, 0xf22ac222, 0x3c9a5f49, 0x0411eef9, 0x3cac502e,
                    0x839809ae, 0x3c93d12a, 0x468a4418, 0x3ca46c91, 0x088afcdb, 0x3c9f1c33
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        // Registers:
        // input: xmm0
        // scratch: xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
        // rax, rdx, rcx
        Label l2TAGPACKET001 = new Label();
        Label l2TAGPACKET101 = new Label();
        Label l2TAGPACKET201 = new Label();
        Label lB11 = new Label();
        Label lB12 = new Label();
        Label lB14 = new Label();

        masm.bind(lB11);
        masm.ucomisd(xmm0, recordExternalAddress(crb, zeron));
        masm.jcc(ConditionFlag.Equal, l2TAGPACKET101);
        masm.movq(xmm1, xmm0);
        masm.andpd(xmm1, recordExternalAddress(crb, absMask));
        masm.ucomisd(xmm1, recordExternalAddress(crb, inf));
        masm.jcc(ConditionFlag.Equal, lB14);

        masm.bind(lB12);
        masm.movq(xmm7, xmm0);
        masm.movl(rdx, 0x7ff00);
        masm.movsd(xmm5, recordExternalAddress(crb, expMsk3));
        masm.movsd(xmm3, recordExternalAddress(crb, expMsk2));
        masm.psrlq(xmm7, 44);
        masm.pextrw(rcx, xmm7, 0);
        masm.movdl(rax, xmm7);
        masm.movsd(xmm1, recordExternalAddress(crb, expMask));
        masm.movsd(xmm2, recordExternalAddress(crb, sigMask));
        masm.andl(rcx, 0xf8);
        masm.leaq(r8, recordExternalAddress(crb, rcpTable));
        masm.movsd(xmm4, new AMD64Address(rcx, r8, Stride.S1));
        masm.movq(r9, rax);
        masm.andl(rdx, rax);
        // Branch only if |x| is denormalized
        masm.cmplAndJcc(rdx, 0, ConditionFlag.Equal, l2TAGPACKET001, false);
        masm.shrl(rdx, 8);
        masm.shrq(r9, 8);
        masm.andpd(xmm2, xmm0);
        masm.andpd(xmm0, xmm5);
        masm.orpd(xmm3, xmm2);
        masm.orpd(xmm1, xmm0);
        masm.movapd(xmm5, recordExternalAddress(crb, coeffTable));
        masm.movl(rax, 0x1556);
        masm.movapd(xmm6, recordExternalAddress(crb, coeffTable16));
        masm.mull(rdx);
        masm.movq(rdx, r9);
        masm.andq(r9, 0x7ff);
        masm.shrl(rax, 14);
        masm.andl(rdx, 0x800);
        masm.subq(r9, rax);
        masm.subq(r9, rax);
        masm.subq(r9, rax);
        masm.shlq(r9, 8);
        masm.addl(rax, 0x2aa);
        masm.orl(rax, rdx);
        masm.movdl(xmm7, rax);
        masm.addq(rcx, r9);
        masm.psllq(xmm7, 52);

        masm.bind(l2TAGPACKET201);
        masm.movapd(xmm2, recordExternalAddress(crb, coeffTable32));
        masm.movapd(xmm0, recordExternalAddress(crb, coeffTable48));
        masm.subsd(xmm1, xmm3);
        masm.movq(xmm3, xmm7);
        masm.leaq(r8, recordExternalAddress(crb, cbrtTable));
        masm.mulsd(xmm7, new AMD64Address(rcx, r8, Stride.S1));
        masm.mulsd(xmm1, xmm4);
        masm.leaq(r8, recordExternalAddress(crb, dTable));
        masm.mulsd(xmm3, new AMD64Address(rcx, r8, Stride.S1));
        masm.movapd(xmm4, xmm1);
        masm.unpcklpd(xmm1, xmm1);
        masm.mulpd(xmm5, xmm1);
        masm.mulpd(xmm6, xmm1);
        masm.mulpd(xmm1, xmm1);
        masm.addpd(xmm2, xmm5);
        masm.addpd(xmm0, xmm6);
        masm.mulpd(xmm2, xmm1);
        masm.mulpd(xmm1, xmm1);
        masm.mulsd(xmm4, xmm7);
        masm.addpd(xmm0, xmm2);
        masm.mulsd(xmm1, xmm0);
        masm.unpckhpd(xmm0, xmm0);
        masm.addsd(xmm0, xmm1);
        masm.mulsd(xmm0, xmm4);
        masm.addsd(xmm0, xmm3);
        masm.addsd(xmm0, xmm7);
        masm.jmp(lB14);

        masm.bind(l2TAGPACKET001);
        masm.mulsd(xmm0, recordExternalAddress(crb, scale63));
        masm.movq(xmm7, xmm0);
        masm.movl(rdx, 0x7ff00);
        masm.psrlq(xmm7, 44);
        masm.pextrw(rcx, xmm7, 0);
        masm.movdl(rax, xmm7);
        masm.andl(rcx, 0xf8);
        masm.leaq(r8, recordExternalAddress(crb, rcpTable));
        masm.movsd(xmm4, new AMD64Address(rcx, r8, Stride.S1));
        masm.movq(r9, rax);
        masm.andl(rdx, rax);
        masm.shrl(rdx, 8);
        masm.shrq(r9, 8);
        masm.andpd(xmm2, xmm0);
        masm.andpd(xmm0, xmm5);
        masm.orpd(xmm3, xmm2);
        masm.orpd(xmm1, xmm0);
        masm.movapd(xmm5, recordExternalAddress(crb, coeffTable));
        masm.movl(rax, 0x1556);
        masm.movapd(xmm6, recordExternalAddress(crb, coeffTable16));
        masm.mull(rdx);
        masm.movq(rdx, r9);
        masm.andq(r9, 0x7ff);
        masm.shrl(rax, 14);
        masm.andl(rdx, 0x800);
        masm.subq(r9, rax);
        masm.subq(r9, rax);
        masm.subq(r9, rax);
        masm.shlq(r9, 8);
        masm.addl(rax, 0x295);
        masm.orl(rax, rdx);
        masm.movdl(xmm7, rax);
        masm.addq(rcx, r9);
        masm.psllq(xmm7, 52);
        masm.jmp(l2TAGPACKET201);

        masm.bind(l2TAGPACKET101);
        masm.addsd(xmm0, xmm0);

        masm.bind(lB14);
    }
}
