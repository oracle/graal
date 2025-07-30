/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

//                     ALGORITHM DESCRIPTION
//                     ---------------------
//
// tanh(x)=(exp(x)-exp(-x))/(exp(x)+exp(-x))=(1-exp(-2*x))/(1+exp(-2*x))
//
// Let |x|=xH+xL (upper 26 bits, lower 27 bits)
// log2(e) rounded to 26 bits (high part) plus a double precision low part is
//         L2EH+L2EL (upper 26, lower 53 bits)
//
// Let xH*L2EH=k+f+r`, where (k+f)*2^8*2=int(xH*L2EH*2^9),
//                             f=0.b1 b2 ... b8, k integer
// 2^{-f} is approximated as Tn[f]+Dn[f]
// Tn stores the high 53 bits, Dn stores (2^{-f}-Tn[f]) rounded to double precision
//
//  r=r`+xL*L2EH+|x|*L2EL, |r|<2^{-9}+2^{-14},
//                      for |x| in [23/64,3*2^7)
// e^{-2*|x|}=2^{-k-f}*2^{-r} ~ 2^{-k}*(Tn+Dn)*(1+p)=(T0+D0)*(1+p)
//
// For |x| in [2^{-4},22):
//         2^{-r}-1 ~ p=c1*r+c2*r^2+..+c5*r^5
//      Let R=1/(1+T0+p*T0), truncated to 35 significant bits
//  R=1/(1+T0+D0+p*(T0+D0))*(1+eps), |eps|<2^{-33}
//  1+T0+D0+p*(T0+D0)=KH+KL, where
//       KH=(1+T0+c1*r*T0)_high (leading 17 bits)
//       KL=T0_low+D0+(c1*r*T0)_low+c1*r*D0+(c2*r^2+..c5*r^5)*T0
//  eps ~ (R*KH-1)+R*KL
//  1/(1+T0+D0+p*(T0+D0)) ~ R-R*eps
//  The result is approximated as (1-T0-D0-(T0+D0)*p)*(R-R*eps)
//  1-T0-D0-(T0+D0)*p=-((KH-2)+KL)
//    The result is formed as
//    (KH-2)*R+(-(KH-2)*R*eps+(KL*R-KL*R*eps)), with the correct sign
//                                                  set at the end
//
// For |x| in [2^{-64},2^{-4}):
//  A Taylor series expansion is used  (x+p3*x^3+..+p13*x^{13})
//
// For |x|<2^{-64}:  x is returned
//
// For |x|>=22: return +/-1
//
// Special cases:
//  tanh(NaN) = quiet NaN, and raise invalid exception
//  tanh(+/-INF) = +/-1
//  tanh(+/-0) = +/-0
//
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/b1fa1ecc988fb07f191892a459625c2c8f2de3b5/src/hotspot/cpu/x86/stubGenerator_x86_64_tanh.cpp#L30-L499",
          sha1 = "5b14b14118c8a8399df0ed1a1ae1456f2979cd1e")
// @formatter:on
public final class AMD64MathTanhOp extends AMD64MathIntrinsicUnaryOp {

    public static final LIRInstructionClass<AMD64MathTanhOp> TYPE = LIRInstructionClass.create(AMD64MathTanhOp.class);

    public AMD64MathTanhOp() {
        super(TYPE, /* GPR */ r8, rax, rcx, rdx,
                        /* XMM */ xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);
    }

    private static ArrayDataPointerConstant halfMask = pointerConstant(16, new int[]{
            // @formatter:off
            0xF8000000, 0x7FFFFFFF
            // @formatter:on
    });

    private static ArrayDataPointerConstant oneMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x3FF00000
            // @formatter:on
    });

    private static ArrayDataPointerConstant twoMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x40000000
            // @formatter:on
    });

    private static ArrayDataPointerConstant threeMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0xFFFFFFF0, 0x00000000, 0xFFFFFFF0
            // @formatter:on
    });

    private static ArrayDataPointerConstant rMask = pointerConstant(16, new int[]{
            // @formatter:off
            0xFFFC0000, 0xFFFFFFFF, 0xFFFC0000, 0xFFFFFFFF
            // @formatter:on
    });

    private static ArrayDataPointerConstant l2e = pointerConstant(16, new int[]{
            // @formatter:off
            0x60000000, 0x40871547
            // @formatter:on
    });

    private static ArrayDataPointerConstant l2eOff8 = pointerConstant(16, new int[]{
            // @formatter:off
            0xF85DDF44, 0x3EE4AE0B
            // @formatter:on
    });

    private static ArrayDataPointerConstant shifter = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x43380000, 0x00000000, 0xC3380000
            // @formatter:on
    });

    private static ArrayDataPointerConstant cv = pointerConstant(16, new int[]{
            // @formatter:off
            0xe78a6731, 0xbcd5d87f, 0xd704a0bf, 0xbe2c6b08
            // @formatter:on
    });

    private static ArrayDataPointerConstant cvOff16 = pointerConstant(16, new int[]{
            // @formatter:off
            0x6fba4e77, 0x3d83b2ab, 0xff82c58e, 0x3ecebfbd
            // @formatter:on
    });

    private static ArrayDataPointerConstant cvOff32 = pointerConstant(16, new int[]{
            // @formatter:off
            0xfefa39ef, 0xbf662e42, 0x00000000, 0x00000000,
            // @formatter:on
    });

    private static ArrayDataPointerConstant pv = pointerConstant(16, new int[]{
            // @formatter:off
            0x0e157ddf, 0x3f6d6d3d, 0x1ba1ba1c, 0xbfaba1ba
            // @formatter:on
    });

    private static ArrayDataPointerConstant pvOff16 = pointerConstant(16, new int[]{
            // @formatter:off
            0x55e6c23d, 0xbf8226e3, 0x11111111, 0x3fc11111
            // @formatter:on
    });

    private static ArrayDataPointerConstant pvOff32 = pointerConstant(16, new int[]{
            // @formatter:off
            0x882c10fa, 0x3f9664f4, 0x55555555, 0xbfd55555,
            // @formatter:on
    });

    private static ArrayDataPointerConstant t2NegF = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x3ff00000, 0x00000000, 0x00000000, 0x6b2a23d9, 0x3fefe9d9,
            0x7442fde3, 0x3c64a603, 0x2b8f71f1, 0x3fefd3c2, 0x966579e7, 0x3c52eb74,
            0x3692d514, 0x3fefbdba, 0x15098eb6, 0xbc696773, 0x819e90d8, 0x3fefa7c1,
            0xf3a5931e, 0x3c774853, 0x02243c89, 0x3fef91d8, 0xa779f689, 0xbc512ea8,
            0xad9cbe14, 0x3fef7bfd, 0xd006350a, 0xbc8dbb12, 0x798844f8, 0x3fef6632,
            0x3539343e, 0x3c8fa37b, 0x5b6e4540, 0x3fef5076, 0x2dd8a18b, 0x3c89d3e1,
            0x48dd7274, 0x3fef3ac9, 0x3ed837de, 0xbc695a5a, 0x376bba97, 0x3fef252b,
            0xbf0d8e43, 0x3c83a1a5, 0x1cb6412a, 0x3fef0f9c, 0x65181d45, 0xbc832200,
            0xee615a27, 0x3feefa1b, 0x86a4b6b0, 0x3c8dc7f4, 0xa2188510, 0x3feee4aa,
            0xa487568d, 0x3c81c68d, 0x2d8e67f1, 0x3feecf48, 0xb411ad8c, 0xbc8c93f3,
            0x867cca6e, 0x3feeb9f4, 0x2293e4f2, 0x3c84832f, 0xa2a490da, 0x3feea4af,
            0x179c2893, 0xbc8e9c23, 0x77cdb740, 0x3fee8f79, 0x80b054b1, 0xbc810894,
            0xfbc74c83, 0x3fee7a51, 0xca0c8de2, 0x3c82d522, 0x24676d76, 0x3fee6539,
            0x7522b735, 0xbc763ff8, 0xe78b3ff6, 0x3fee502e, 0x80a9cc8f, 0x3c739e89,
            0x3b16ee12, 0x3fee3b33, 0x31fdc68b, 0xbc89f4a4, 0x14f5a129, 0x3fee2646,
            0x817a1496, 0xbc87b627, 0x6b197d17, 0x3fee1167, 0xbd5c7f44, 0xbc62b529,
            0x337b9b5f, 0x3fedfc97, 0x4f184b5c, 0xbc81a5cd, 0x641c0658, 0x3fede7d5,
            0x8e79ba8f, 0xbc8ca552, 0xf301b460, 0x3fedd321, 0x78f018c3, 0x3c82da57,
            0xd63a8315, 0x3fedbe7c, 0x926b8be4, 0xbc8b76f1, 0x03db3285, 0x3feda9e6,
            0x696db532, 0x3c8c2300, 0x71ff6075, 0x3fed955d, 0xbb9af6be, 0x3c8a052d,
            0x16c98398, 0x3fed80e3, 0x8beddfe8, 0xbc811ec1, 0xe862e6d3, 0x3fed6c76,
            0x4a8165a0, 0x3c4fe87a, 0xdcfba487, 0x3fed5818, 0xd75b3707, 0x3c72ed02,
            0xeacaa1d6, 0x3fed43c8, 0xbf5a1614, 0x3c83db53, 0x080d89f2, 0x3fed2f87,
            0x719d8578, 0xbc8d487b, 0x2b08c968, 0x3fed1b53, 0x219a36ee, 0x3c855636,
            0x4a07897c, 0x3fed072d, 0x43797a9c, 0xbc8cbc37, 0x5b5bab74, 0x3fecf315,
            0xb86dff57, 0xbc8a08e9, 0x555dc3fa, 0x3fecdf0b, 0x53829d72, 0xbc7dd83b,
            0x2e6d1675, 0x3feccb0f, 0x86009093, 0xbc6d220f, 0xdcef9069, 0x3fecb720,
            0xd1e949dc, 0x3c6503cb, 0x5751c4db, 0x3feca340, 0xd10d08f5, 0xbc77f2be,
            0x9406e7b5, 0x3fec8f6d, 0x48805c44, 0x3c61acbc, 0x8988c933, 0x3fec7ba8,
            0xbe255559, 0xbc7e76bb, 0x2e57d14b, 0x3fec67f1, 0xff483cad, 0x3c82884d,
            0x78fafb22, 0x3fec5447, 0x2493b5af, 0x3c812f07, 0x5fffd07a, 0x3fec40ab,
            0xe083c60a, 0x3c8b4537, 0xd9fa652c, 0x3fec2d1c, 0x17c8a5d7, 0xbc86e516,
            0xdd85529c, 0x3fec199b, 0x895048dd, 0x3c711065, 0x6141b33d, 0x3fec0628,
            0xa1fbca34, 0xbc7d8a5a, 0x5bd71e09, 0x3febf2c2, 0x3f6b9c73, 0xbc8efdca,
            0xc3f3a207, 0x3febdf69, 0x60ea5b53, 0xbc2c2623, 0x904bc1d2, 0x3febcc1e,
            0x7a2d9e84, 0x3c723dd0, 0xb79a6f1f, 0x3febb8e0, 0xc9696204, 0xbc2f52d1,
            0x30a1064a, 0x3feba5b0, 0x0e54292e, 0xbc8efcd3, 0xf22749e4, 0x3feb928c,
            0x54cb65c6, 0xbc8b7216, 0xf2fb5e47, 0x3feb7f76, 0x7e54ac3b, 0xbc65584f,
            0x29f1c52a, 0x3feb6c6e, 0x52883f6e, 0x3c82a8f3, 0x8de5593a, 0x3feb5972,
            0xbbba6de3, 0xbc8c71df, 0x15b749b1, 0x3feb4684, 0xe9df7c90, 0xbc6f763d,
            0xb84f15fb, 0x3feb33a2, 0x3084d708, 0xbc52805e, 0x6c9a8952, 0x3feb20ce,
            0x4a0756cc, 0x3c84dd02, 0x298db666, 0x3feb0e07, 0x4c80e425, 0xbc8bdef5,
            0xe622f2ff, 0x3feafb4c, 0x0f315ecd, 0xbc84b2fc, 0x995ad3ad, 0x3feae89f,
            0x345dcc81, 0x3c87a1cd, 0x3a3c2774, 0x3fead5ff, 0xb6b1b8e5, 0x3c87ef3b,
            0xbfd3f37a, 0x3feac36b, 0xcae76cd0, 0xbc7f9234, 0x21356eba, 0x3feab0e5,
            0xdae94545, 0x3c789c31, 0x5579fdbf, 0x3fea9e6b, 0x0ef7fd31, 0x3c80fac9,
            0x53c12e59, 0x3fea8bfe, 0xb2ba15a9, 0xbc84f867, 0x1330b358, 0x3fea799e,
            0xcac563c7, 0x3c8bcb7e, 0x8af46052, 0x3fea674a, 0x30670366, 0x3c550f56,
            0xb23e255d, 0x3fea5503, 0xdb8d41e1, 0xbc8d2f6e, 0x80460ad8, 0x3fea42c9,
            0x589fb120, 0xbc8aa780, 0xec4a2d33, 0x3fea309b, 0x7ddc36ab, 0x3c86305c,
            0xed8eb8bb, 0x3fea1e7a, 0xee8be70e, 0x3c8c6618, 0x7b5de565, 0x3fea0c66,
            0x5d1cd533, 0xbc835949, 0x8d07f29e, 0x3fe9fa5e, 0xaaf1face, 0xbc74a9ce,
            0x19e32323, 0x3fe9e863, 0x78e64c6e, 0x3c6824ca, 0x194bb8d5, 0x3fe9d674,
            0xa3dd8233, 0xbc8516be, 0x82a3f090, 0x3fe9c491, 0xb071f2be, 0x3c6c7c46,
            0x4d53fe0d, 0x3fe9b2bb, 0x4df6d518, 0xbc8dd84e, 0x70ca07ba, 0x3fe9a0f1,
            0x91cee632, 0xbc8173bd, 0xe47a22a2, 0x3fe98f33, 0xa24c78ec, 0x3c6cabda,
            0x9fde4e50, 0x3fe97d82, 0x7c1b85d1, 0xbc8d185b, 0x9a7670b3, 0x3fe96bdd,
            0x7f19c896, 0xbc4ba596, 0xcbc8520f, 0x3fe95a44, 0x96a5f039, 0xbc664b7c,
            0x2b5f98e5, 0x3fe948b8, 0x797d2d99, 0xbc7dc3d6, 0xb0cdc5e5, 0x3fe93737,
            0x81b57ebc, 0xbc575fc7, 0x53aa2fe2, 0x3fe925c3, 0xa639db7f, 0xbc73455f,
            0x0b91ffc6, 0x3fe9145b, 0x2e582524, 0xbc8dd679, 0xd0282c8a, 0x3fe902fe,
            0x85fe3fd2, 0x3c8592ca, 0x99157736, 0x3fe8f1ae, 0xa2e3976c, 0x3c75cc13,
            0x5e0866d9, 0x3fe8e06a, 0x6fc9b2e6, 0xbc87114a, 0x16b5448c, 0x3fe8cf32,
            0x32e9e3aa, 0xbc60d55e, 0xbad61778, 0x3fe8be05, 0xfc43446e, 0x3c8ecb5e,
            0x422aa0db, 0x3fe8ace5, 0x56864b27, 0x3c86e9f1, 0xa478580f, 0x3fe89bd0,
            0x4475202a, 0x3c8d5395, 0xd98a6699, 0x3fe88ac7, 0xf37cb53a, 0x3c8994c2,
            0xd931a436, 0x3fe879ca, 0xd2db47bd, 0x3c75d2d7, 0x9b4492ed, 0x3fe868d9,
            0x9bd4f6ba, 0xbc8fc6f8, 0x179f5b21, 0x3fe857f4, 0xf8b216d0, 0xbc4ba748,
            0x4623c7ad, 0x3fe8471a, 0xa341cdfb, 0xbc78d684, 0x1eb941f7, 0x3fe8364c,
            0x31df2bd5, 0x3c899b9a, 0x994cce13, 0x3fe82589, 0xd41532d8, 0xbc8d4c1d,
            0xadd106d9, 0x3fe814d2, 0x0d151d4d, 0x3c846437, 0x543e1a12, 0x3fe80427,
            0x626d972b, 0xbc827c86, 0x8491c491, 0x3fe7f387, 0xcf9311ae, 0xbc707f11,
            0x36cf4e62, 0x3fe7e2f3, 0xba15797e, 0x3c605d02, 0x62ff86f0, 0x3fe7d26a,
            0xfb72b8b4, 0x3c81bddb, 0x0130c132, 0x3fe7c1ed, 0xd1164dd6, 0x3c8f124c,
            0x0976cfdb, 0x3fe7b17b, 0x8468dc88, 0xbc8bebb5, 0x73eb0187, 0x3fe7a114,
            0xee04992f, 0xbc741577, 0x38ac1cf6, 0x3fe790b9, 0x62aadd3e, 0x3c8349a8,
            0x4fde5d3f, 0x3fe78069, 0x0a02162d, 0x3c8866b8, 0xb1ab6e09, 0x3fe77024,
            0x169147f8, 0x3c8b7877, 0x564267c9, 0x3fe75feb, 0x57316dd3, 0xbc802459,
            0x35d7cbfd, 0x3fe74fbd, 0x618a6e1c, 0x3c8047fd, 0x48a58174, 0x3fe73f9a,
            0x6c65d53c, 0xbc80a8d9, 0x86ead08a, 0x3fe72f82, 0x2cd62c72, 0xbc820aa0,
            0xe8ec5f74, 0x3fe71f75, 0x86887a99, 0xbc716e47, 0x66f42e87, 0x3fe70f74,
            0xd45aa65f, 0x3c49d644, 0xf9519484, 0x3fe6ff7d, 0x25860ef6, 0xbc783c0f,
            0x98593ae5, 0x3fe6ef92, 0x9e1ac8b2, 0xbc80b974, 0x3c651a2f, 0x3fe6dfb2,
            0x683c88ab, 0xbc5bbe3a, 0xddd47645, 0x3fe6cfdc, 0xb6f17309, 0x3c8c7aa9,
            0x750bdabf, 0x3fe6c012, 0x67ff0b0d, 0xbc628956, 0xfa75173e, 0x3fe6b052,
            0x2c9a9d0e, 0x3c6a38f5, 0x667f3bcd, 0x3fe6a09e, 0x13b26456, 0xbc8bdd34,
            0xb19e9538, 0x3fe690f4, 0x9aeb445d, 0x3c7804bd, 0xd44ca973, 0x3fe68155,
            0x44f73e65, 0x3c5038ae, 0xc70833f6, 0x3fe671c1, 0x586c6134, 0xbc7e8732,
            0x82552225, 0x3fe66238, 0x87591c34, 0xbc8bb609, 0xfebc8fb7, 0x3fe652b9,
            0xc9a73e09, 0xbc8ae3d5, 0x34ccc320, 0x3fe64346, 0x759d8933, 0xbc7c483c,
            0x1d1929fd, 0x3fe633dd, 0xbeb964e5, 0x3c884710, 0xb03a5585, 0x3fe6247e,
            0x7e40b497, 0xbc8383c1, 0xe6cdf6f4, 0x3fe6152a, 0x4ab84c27, 0x3c8e4b3e,
            0xb976dc09, 0x3fe605e1, 0x9b56de47, 0xbc83e242, 0x20dceb71, 0x3fe5f6a3,
            0xe3cdcf92, 0xbc79eadd, 0x15ad2148, 0x3fe5e76f, 0x3080e65e, 0x3c8ba6f9,
            0x90998b93, 0x3fe5d845, 0xa8b45643, 0xbc8cd6a7, 0x8a5946b7, 0x3fe5c926,
            0x816986a2, 0x3c2c4b1b, 0xfba87a03, 0x3fe5ba11, 0x4c233e1a, 0xbc8b77a1,
            0xdd485429, 0x3fe5ab07, 0x054647ad, 0x3c86324c, 0x27ff07cc, 0x3fe59c08,
            0xe467e60f, 0xbc87e2ce, 0xd497c7fd, 0x3fe58d12, 0x5b9a1de8, 0x3c7295e1,
            0xdbe2c4cf, 0x3fe57e27, 0x8a57b9c4, 0xbc80b98c, 0x36b527da, 0x3fe56f47,
            0x011d93ad, 0x3c89bb2c, 0xdde910d2, 0x3fe56070, 0x168eebf0, 0xbc80fb6e,
            0xca5d920f, 0x3fe551a4, 0xefede59b, 0xbc7d689c, 0xf4f6ad27, 0x3fe542e2,
            0x192d5f7e, 0x3c77926d, 0x569d4f82, 0x3fe5342b, 0x1db13cad, 0xbc707abe,
            0xe83f4eef, 0x3fe5257d, 0x43efef71, 0xbc6c998d, 0xa2cf6642, 0x3fe516da,
            0x69bd93ef, 0xbc7f7685, 0x7f4531ee, 0x3fe50841, 0x49b7465f, 0x3c6a249b,
            0x769d2ca7, 0x3fe4f9b2, 0xd25957e3, 0xbc84b309, 0x81d8abff, 0x3fe4eb2d,
            0x2e5d7a52, 0xbc85257d, 0x99fddd0d, 0x3fe4dcb2, 0xbc6a7833, 0x3c88ecdb,
            0xb817c114, 0x3fe4ce41, 0x690abd5d, 0x3c805e29, 0xd5362a27, 0x3fe4bfda,
            0xafec42e2, 0x3c6d4397, 0xea6db7d7, 0x3fe4b17d, 0x7f2897f0, 0xbc7125b8,
            0xf0d7d3de, 0x3fe4a32a, 0xf3d1be56, 0x3c89cb62, 0xe192aed2, 0x3fe494e1,
            0x5e499ea0, 0xbc73b289, 0xb5c13cd0, 0x3fe486a2, 0xb69062f0, 0x3c63c1a3,
            0x668b3237, 0x3fe4786d, 0xed445733, 0xbc8c20f0, 0xed1d0057, 0x3fe46a41,
            0xd1648a76, 0x3c8c944b, 0x42a7d232, 0x3fe45c20, 0x82fb1f8e, 0xbc586419,
            0x6061892d, 0x3fe44e08, 0x04ef80d0, 0x3c389b7a, 0x3f84b9d4, 0x3fe43ffa,
            0x9704c003, 0x3c7880be, 0xd950a897, 0x3fe431f5, 0xe35f7999, 0xbc71c7dd,
            0x2709468a, 0x3fe423fb, 0xc0b314dd, 0xbc88462d, 0x21f72e2a, 0x3fe4160a,
            0x1c309278, 0xbc4ef369, 0xc367a024, 0x3fe40822, 0xb6f4d048, 0x3c7bddf8,
            0x04ac801c, 0x3fe3fa45, 0xf956f9f3, 0xbc87d023, 0xdf1c5175, 0x3fe3ec70,
            0x7b8c9bca, 0xbc7af663, 0x4c123422, 0x3fe3dea6, 0x11f09ebc, 0x3c7ada09,
            0x44ede173, 0x3fe3d0e5, 0x8c284c71, 0x3c6fe8d0, 0xc313a8e5, 0x3fe3c32d,
            0x375d29c3, 0xbc8efff8, 0xbfec6cf4, 0x3fe3b57f, 0xe26fff18, 0x3c854c66,
            0x34e59ff7, 0x3fe3a7db, 0xd661f5e3, 0xbc65e436, 0x1b7140ef, 0x3fe39a40,
            0xfc8e2934, 0xbc89a9a5, 0x6d05d866, 0x3fe38cae, 0x3c9904bd, 0xbc8e958d,
            0x231e754a, 0x3fe37f26, 0x9eceb23c, 0xbc89f5ca, 0x373aa9cb, 0x3fe371a7,
            0xbf42eae2, 0xbc863aea, 0xa2de883b, 0x3fe36431, 0xa06cb85e, 0xbc7c3144,
            0x5f929ff1, 0x3fe356c5, 0x5c4e4628, 0xbc7b5cee, 0x66e3fa2d, 0x3fe34962,
            0x930881a4, 0xbc735a75, 0xb26416ff, 0x3fe33c08, 0x843659a6, 0x3c832721,
            0x3ba8ea32, 0x3fe32eb8, 0x3cb4f318, 0xbc8c45e8, 0xfc4cd831, 0x3fe32170,
            0x8e18047c, 0x3c7a9ce7, 0xedeeb2fd, 0x3fe31432, 0xf3f3fcd1, 0x3c7959a3,
            0x0a31b715, 0x3fe306fe, 0xd23182e4, 0x3c76f46a, 0x4abd886b, 0x3fe2f9d2,
            0x532bda93, 0xbc553c55, 0xa93e2f56, 0x3fe2ecaf, 0x45d52383, 0x3c61ca0f,
            0x1f641589, 0x3fe2df96, 0xfbbce198, 0x3c8d16cf, 0xa6e4030b, 0x3fe2d285,
            0x54db41d5, 0x3c800247, 0x39771b2f, 0x3fe2c57e, 0xa6eb5124, 0xbc850145,
            0xd0dad990, 0x3fe2b87f, 0xd6381aa4, 0xbc310adc, 0x66d10f13, 0x3fe2ab8a,
            0x191690a7, 0xbc895743, 0xf51fdee1, 0x3fe29e9d, 0xafad1255, 0x3c7612e8,
            0x7591bb70, 0x3fe291ba, 0x28401cbd, 0xbc72cc72, 0xe1f56381, 0x3fe284df,
            0x8c3f0d7e, 0xbc8a4c3a, 0x341ddf29, 0x3fe2780e, 0x05f9e76c, 0x3c8e067c,
            0x65e27cdd, 0x3fe26b45, 0x9940e9d9, 0x3c72bd33, 0x711ece75, 0x3fe25e85,
            0x4ac31b2c, 0x3c83e1a2, 0x4fb2a63f, 0x3fe251ce, 0xbef4f4a4, 0x3c7ac155,
            0xfb82140a, 0x3fe2451f, 0x911ca996, 0x3c7acfcc, 0x6e756238, 0x3fe2387a,
            0xb6c70573, 0x3c89b07e, 0xa27912d1, 0x3fe22bdd, 0x5577d69f, 0x3c7d34fb,
            0x917ddc96, 0x3fe21f49, 0x9494a5ee, 0x3c72a97e, 0x3578a819, 0x3fe212be,
            0x2cfcaac9, 0x3c83592d, 0x88628cd6, 0x3fe2063b, 0x814a8495, 0x3c7dc775,
            0x8438ce4d, 0x3fe1f9c1, 0xa097af5c, 0xbc8bf524, 0x22fcd91d, 0x3fe1ed50,
            0x027bb78c, 0xbc81df98, 0x5eb44027, 0x3fe1e0e7, 0x088cb6de, 0xbc86fdd8,
            0x3168b9aa, 0x3fe1d487, 0x00a2643c, 0x3c8e016e, 0x95281c6b, 0x3fe1c82f,
            0x8010f8c9, 0x3c800977, 0x84045cd4, 0x3fe1bbe0, 0x352ef607, 0xbc895386,
            0xf8138a1c, 0x3fe1af99, 0xa4b69280, 0x3c87bf85, 0xeb6fcb75, 0x3fe1a35b,
            0x7b4968e4, 0x3c7e5b4c, 0x58375d2f, 0x3fe19726, 0x85f17e08, 0x3c84aadd,
            0x388c8dea, 0x3fe18af9, 0xd1970f6c, 0xbc811023, 0x8695bbc0, 0x3fe17ed4,
            0xe2ac5a64, 0x3c609e3f, 0x3c7d517b, 0x3fe172b8, 0xb9d78a76, 0xbc719041,
            0x5471c3c2, 0x3fe166a4, 0x82ea1a32, 0x3c48f23b, 0xc8a58e51, 0x3fe15a98,
            0xb9eeab0a, 0x3c72406a, 0x934f312e, 0x3fe14e95, 0x39bf44ab, 0xbc7b91e8,
            0xaea92de0, 0x3fe1429a, 0x9af1369e, 0xbc832fbf, 0x14f204ab, 0x3fe136a8,
            0xba48dcf0, 0xbc57108f, 0xc06c31cc, 0x3fe12abd, 0xb36ca5c7, 0xbc41b514,
            0xab5e2ab6, 0x3fe11edb, 0xf703fb72, 0xbc8ca454, 0xd0125b51, 0x3fe11301,
            0x39449b3a, 0xbc86c510, 0x28d7233e, 0x3fe10730, 0x1692fdd5, 0x3c7d46eb,
            0xaffed31b, 0x3fe0fb66, 0xc44ebd7b, 0xbc5b9bed, 0x5fdfa9c5, 0x3fe0efa5,
            0xbc54021b, 0xbc849db9, 0x32d3d1a2, 0x3fe0e3ec, 0x27c57b52, 0x3c303a17,
            0x23395dec, 0x3fe0d83b, 0xe43f316a, 0xbc8bc14d, 0x2b7247f7, 0x3fe0cc92,
            0x16e24f71, 0x3c801edc, 0x45e46c85, 0x3fe0c0f1, 0x06d21cef, 0x3c84f989,
            0x6cf9890f, 0x3fe0b558, 0x4adc610b, 0x3c88a62e, 0x9b1f3919, 0x3fe0a9c7,
            0x873d1d38, 0x3c75d16c, 0xcac6f383, 0x3fe09e3e, 0x18316136, 0x3c814878,
            0xf66607e0, 0x3fe092bd, 0x800a3fd1, 0xbc868063, 0x18759bc8, 0x3fe08745,
            0x4bb284ff, 0x3c5186be, 0x2b72a836, 0x3fe07bd4, 0x54458700, 0x3c732334,
            0x29ddf6de, 0x3fe0706b, 0xe2b13c27, 0xbc7c91df, 0x0e3c1f89, 0x3fe0650a,
            0x5799c397, 0xbc85cb7b, 0xd3158574, 0x3fe059b0, 0xa475b465, 0x3c7d73e2,
            0x72f654b1, 0x3fe04e5f, 0x3aa0d08c, 0x3c74c379, 0xe86e7f85, 0x3fe04315,
            0x1977c96e, 0xbc80a31c, 0x2e11bbcc, 0x3fe037d4, 0xeeade11a, 0x3c556811,
            0x3e778061, 0x3fe02c9a, 0x535b085d, 0xbc619083, 0x143b0281, 0x3fe02168,
            0x0fc54eb6, 0xbc72bf31, 0xa9fb3335, 0x3fe0163d, 0x9ab8cdb7, 0x3c8b6129,
            0xfa5abcbf, 0x3fe00b1a, 0xa7609f71, 0xbc74f6b2,
            // @formatter:on
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label l2TAGPACKET001 = new Label();
        Label l2TAGPACKET101 = new Label();
        Label l2TAGPACKET201 = new Label();
        Label l2TAGPACKET301 = new Label();
        Label l2TAGPACKET401 = new Label();
        Label l2TAGPACKET501 = new Label();
        Label lB12 = new Label();
        Label lB14 = new Label();

        masm.bind(lB12);
        masm.pextrw(rcx, xmm0, 3);
        masm.movl(rdx, 32768);
        masm.andl(rdx, rcx);
        masm.andl(rcx, 32767);
        // Branch only if |x| >= 22
        masm.cmplAndJcc(rcx, 16438, ConditionFlag.AboveEqual, l2TAGPACKET201, false);

        masm.movsd(xmm3, recordExternalAddress(crb, halfMask));
        masm.xorpd(xmm4, xmm4);
        masm.movsd(xmm1, recordExternalAddress(crb, l2e));
        /*
         * We do not support offset for the constants in data section because it will be rewritten
         * to offset 0 during data patching at CodeInstaller::pd_patch_DataSectionReference in
         * HotSpot. Thus we split the original constant into several to ensure every access starts
         * at offset 0. This assumes the accesses do not exceed the width of the constants because
         * we cannot guarantee that the split constants are placed sequentially.
         */
        masm.movsd(xmm2, recordExternalAddress(crb, l2eOff8));
        masm.movl(rax, 32768);
        masm.pinsrw(xmm4, rax, 3);
        masm.movsd(xmm6, recordExternalAddress(crb, shifter));
        masm.andpd(xmm3, xmm0);
        masm.andnpd(xmm4, xmm0);
        masm.pshufd(xmm5, xmm4, 68);
        masm.subl(rcx, 16304);
        // Branch only if |x| is not in [2^{-4},22)
        masm.cmplAndJcc(rcx, 134, ConditionFlag.AboveEqual, l2TAGPACKET001, false);
        masm.subsd(xmm4, xmm3);
        masm.mulsd(xmm3, xmm1);
        masm.mulsd(xmm2, xmm5);
        masm.cvtsd2siq(rax, xmm3);
        masm.movq(xmm7, xmm3);
        masm.addsd(xmm3, xmm6);
        masm.mulsd(xmm1, xmm4);
        masm.movsd(xmm4, recordExternalAddress(crb, oneMask));
        masm.subsd(xmm3, xmm6);
        masm.xorpd(xmm0, xmm0);
        masm.addsd(xmm2, xmm1);
        masm.subsd(xmm7, xmm3);
        masm.movdqu(xmm6, recordExternalAddress(crb, cv));
        masm.addsd(xmm2, xmm7);
        masm.movl(rcx, 255);
        masm.andl(rcx, rax);
        masm.addl(rcx, rcx);
        masm.leaq(r8, recordExternalAddress(crb, t2NegF));
        masm.movdqu(xmm5, new AMD64Address(r8, rcx, Stride.S8));
        masm.shrl(rax, 4);
        masm.andl(rax, 65520);
        masm.subl(rax, 16368);
        masm.negl(rax);
        masm.pinsrw(xmm0, rax, 3);
        masm.movdqu(xmm1, recordExternalAddress(crb, cvOff16));
        masm.pshufd(xmm0, xmm0, 68);
        masm.mulpd(xmm0, xmm5);
        masm.movsd(xmm7, recordExternalAddress(crb, cvOff32));
        masm.pshufd(xmm2, xmm2, 68);
        masm.movq(xmm5, xmm4);
        masm.addsd(xmm4, xmm0);
        masm.mulpd(xmm6, xmm2);
        masm.mulsd(xmm7, xmm2);
        masm.mulpd(xmm2, xmm2);
        masm.addpd(xmm1, xmm6);
        masm.mulsd(xmm2, xmm2);
        masm.movsd(xmm3, recordExternalAddress(crb, oneMask));
        masm.mulpd(xmm1, xmm2);
        masm.pshufd(xmm6, xmm1, 78);
        masm.addsd(xmm1, xmm6);
        masm.movq(xmm6, xmm1);
        masm.addsd(xmm1, xmm7);
        masm.mulsd(xmm1, xmm0);
        masm.addsd(xmm1, xmm4);
        masm.andpd(xmm4, recordExternalAddress(crb, threeMask));
        masm.divsd(xmm5, xmm1);
        masm.subsd(xmm3, xmm4);
        masm.pshufd(xmm1, xmm0, 238);
        masm.addsd(xmm3, xmm0);
        masm.movq(xmm2, xmm4);
        masm.addsd(xmm3, xmm1);
        masm.mulsd(xmm1, xmm7);
        masm.mulsd(xmm7, xmm0);
        masm.addsd(xmm3, xmm1);
        masm.addsd(xmm4, xmm7);
        masm.movsd(xmm1, recordExternalAddress(crb, rMask));
        masm.mulsd(xmm6, xmm0);
        masm.andpd(xmm4, recordExternalAddress(crb, threeMask));
        masm.addsd(xmm3, xmm6);
        masm.movq(xmm6, xmm4);
        masm.subsd(xmm2, xmm4);
        masm.addsd(xmm2, xmm7);
        masm.movsd(xmm7, recordExternalAddress(crb, oneMask));
        masm.andpd(xmm5, xmm1);
        masm.addsd(xmm3, xmm2);
        masm.mulsd(xmm4, xmm5);
        masm.xorpd(xmm2, xmm2);
        masm.mulsd(xmm3, xmm5);
        masm.subsd(xmm6, recordExternalAddress(crb, twoMask));
        masm.subsd(xmm4, xmm7);
        masm.xorl(rdx, 32768);
        masm.pinsrw(xmm2, rdx, 3);
        masm.addsd(xmm4, xmm3);
        masm.mulsd(xmm6, xmm5);
        masm.movq(xmm1, xmm3);
        masm.mulsd(xmm3, xmm4);
        masm.movq(xmm0, xmm6);
        masm.mulsd(xmm6, xmm4);
        masm.subsd(xmm1, xmm3);
        masm.subsd(xmm1, xmm6);
        masm.addsd(xmm0, xmm1);
        masm.xorpd(xmm0, xmm2);
        masm.jmp(lB14);

        masm.bind(l2TAGPACKET001);
        masm.addl(rcx, 960);
        // Branch only if |x| not in [2^{-64}, 2^{-4})
        masm.cmplAndJcc(rcx, 1094, ConditionFlag.AboveEqual, l2TAGPACKET101, false);
        masm.movdqu(xmm2, recordExternalAddress(crb, pv));
        masm.pshufd(xmm1, xmm0, 68);
        masm.movdqu(xmm3, recordExternalAddress(crb, pvOff16));
        masm.mulpd(xmm1, xmm1);
        masm.movdqu(xmm4, recordExternalAddress(crb, pvOff32));
        masm.mulpd(xmm2, xmm1);
        masm.pshufd(xmm5, xmm1, 68);
        masm.addpd(xmm2, xmm3);
        masm.mulsd(xmm5, xmm5);
        masm.mulpd(xmm2, xmm1);
        masm.mulsd(xmm5, xmm5);
        masm.addpd(xmm2, xmm4);
        masm.mulpd(xmm2, xmm5);
        masm.pshufd(xmm5, xmm2, 238);
        masm.addsd(xmm2, xmm5);
        masm.mulsd(xmm2, xmm0);
        masm.addsd(xmm0, xmm2);
        masm.jmp(lB14);

        masm.bind(l2TAGPACKET101);
        // Branch only if |x| is denormalized
        masm.cmplAndJcc(rcx, 16, ConditionFlag.Below, l2TAGPACKET301, false);
        masm.xorpd(xmm2, xmm2);
        masm.movl(rax, 17392);
        masm.pinsrw(xmm2, rax, 3);
        masm.mulsd(xmm2, xmm0);
        masm.addsd(xmm2, xmm0);
        masm.jmp(lB14);

        masm.bind(l2TAGPACKET301);
        masm.movq(xmm2, xmm0);
        masm.mulsd(xmm2, xmm2);
        masm.jmp(lB14);

        masm.bind(l2TAGPACKET201);
        // Branch only if |x| is INF or NaN
        masm.cmplAndJcc(rcx, 32752, ConditionFlag.AboveEqual, l2TAGPACKET401, false);
        masm.xorpd(xmm2, xmm2);
        masm.movl(rcx, 15344);
        masm.pinsrw(xmm2, rcx, 3);
        masm.movq(xmm3, xmm2);
        masm.mulsd(xmm2, xmm2);
        masm.addsd(xmm2, xmm3);

        masm.bind(l2TAGPACKET501);
        masm.xorpd(xmm0, xmm0);
        masm.orl(rdx, 16368);
        masm.pinsrw(xmm0, rdx, 3);
        masm.jmp(lB14);

        masm.bind(l2TAGPACKET401);
        masm.movq(xmm2, xmm0);
        masm.movdl(rax, xmm0);
        masm.psrlq(xmm2, 20);
        masm.movdl(rcx, xmm2);
        masm.orl(rcx, rax);
        // Branch only if |x| is not NaN
        masm.cmplAndJcc(rcx, 0, ConditionFlag.Equal, l2TAGPACKET501, false);
        masm.addsd(xmm0, xmm0);

        masm.bind(lB14);
    }
}
