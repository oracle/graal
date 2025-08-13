/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
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
 * sinh(x)=(exp(x)-exp(-x))/2
 *
 * Let |x|=xH+xL (upper 26 bits, lower 27 bits)
 * log2(e) rounded to 26 bits (high part) plus a double precision low part is
 *         L2EH+L2EL (upper 26, lower 53 bits)
 *
 * Let xH*L2EH=k+f+r`, where (k+f)*2^7=int(xH*L2EH*2^7),
 *                             f=0.b1 b2 ... b7, k integer
 * 2^f is approximated as Tp[f]+Dp[f], and 2^{-f} as Tn[f]+Dn[f]
 * Tp stores the high 53 bits, Dp stores (2^f-Tp[f]) rounded to double precision
 *
 * e^|x|=2^{k+f}*2^r, r=r`+xL*L2EH+|x|*L2EL, |r|<2^{-8}+2^{-14},
 *                      for |x| in [23/64,3*2^7)
 * e^{-|x|}=2^{-k-f}*2^{-r}
 *
 * e^|x| is approximated as 2^k*Tp+2^k*Tp*c1*r(1+c2*r+..+c5*r^4)+2^k*Dp=
 *                          =2^k*Tp+2^k*Tp*P15+2^k*Dp
 * e^{-|x|} approximated as 2^{-k}*Tn-2^{-k}*Tn*c1*r(1-c2*r+..+c5*r^4)+2^{-k}*Dn
 *
 * For |x| in [1/8, 3*2^7), sinh(x) is formed as
 *     RN(2^k*Tp-2^{-k}*Tn)+2^k*Tp*P15-2^{-k}*Tn*P`15-2^{-k}*TnL-2^{-k}*Dn+2^k*Dp
 *
 * For x in (3*2^7, 3*2^8), sign(x)*(e^|x|)/2 is returned, and
 * the result is checked for overflow.
 *
 * For |x|<23/64, a Taylor polynomial expansion is used (degree 13)
 * To reduce rounding errors, the p3*x^3 term is computed as
 *    (p3*xh^3)_high+[(p3*xl*(3*x*xh+xl^2))+(p3*xh^3)_low],
 * where x=xh+xl, (xh are the leading 17 bits of x), and
 *    (p3*xh^3)_high=RN(x+p3*xh^3)-x
 *
 * Error bound:
 *  0.51 ulp
 *
 * Special cases:
 *  sinh(NaN) = quiet NaN, and raise invalid exception
 *  sinh(+/-INF) = +/-INF
 *  sinh(+/-0) = +/-0
 * </pre>
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/001aaa1e49f2692061cad44d68c9e81a27ea3b98/src/hotspot/cpu/x86/stubGenerator_x86_64_sinh.cpp#L30-L523",
          sha1 = "ec41339ad6dcdce94d6e288a56c0502fa59bf5ec")
// @formatter:on
public final class AMD64MathSinhOp extends AMD64MathIntrinsicUnaryOp {

    public static final LIRInstructionClass<AMD64MathSinhOp> TYPE = LIRInstructionClass.create(AMD64MathSinhOp.class);

    public AMD64MathSinhOp() {
        super(TYPE, /* GPR */ rax, rcx, rdx, r8, r9, r10, r11,
                        /* XMM */ xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);
    }

    private static ArrayDataPointerConstant halfMask = pointerConstant(16, new int[]{
            // @formatter:off
            0xF8000000, 0x7FFFFFFF
            // @formatter:on
    });

    private static ArrayDataPointerConstant mask3 = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0xFFFFFFF0, 0x00000000, 0xFFFFFFF0
            // @formatter:on
    });

    private static ArrayDataPointerConstant l2E = pointerConstant(16, new int[]{
            // @formatter:off
            0x60000000, 0x40671547
            // @formatter:on
    });

    private static ArrayDataPointerConstant l2E8 = pointerConstant(16, new int[]{
            // @formatter:off
            0xF85DDF44, 0x3EC4AE0B
            // @formatter:on
    });

    private static ArrayDataPointerConstant shifter = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x43380000, 0x00000000, 0xC3380000
            // @formatter:on
    });

    private static ArrayDataPointerConstant cv = pointerConstant(16, new int[]{
            // @formatter:off
            0xD704A0C0, 0x3E3C6B08, 0xD704A0C0, 0xBE3C6B08, 
            // @formatter:on
    });

    private static ArrayDataPointerConstant cv16 = pointerConstant(16, new int[]{
            // @formatter:off
            0xFEFA39EF, 0x3F662E42, 0xFEFA39EF, 0xBF662E42,
            // @formatter:on
    });

    private static ArrayDataPointerConstant cv32 = pointerConstant(16, new int[]{
            // @formatter:off
            0x7F907D8B, 0x3D9F8445, 0x7F907D8B, 0x3D9F8445,
            // @formatter:on
    });

    private static ArrayDataPointerConstant cv48 = pointerConstant(16, new int[]{
            // @formatter:off
            0xFFAC83B4, 0x3ED47FD3, 0xFFAC83B4, 0x3ED47FD3,
            // @formatter:on
    });

    private static ArrayDataPointerConstant cv64 = pointerConstant(16, new int[]{
            // @formatter:off
            0xFEFA39EF, 0x3F762E42, 0xFEFA39EF, 0x3F762E42
            // @formatter:on
    });

    private static ArrayDataPointerConstant pv = pointerConstant(16, new int[]{
            // @formatter:off
            0x13A86D08, 0x3DE61246, 0xA556C732, 0x3EC71DE3,
            // @formatter:on
    });

    private static ArrayDataPointerConstant pv16 = pointerConstant(16, new int[]{
            // @formatter:off
            0x11111111, 0x3F811111, 0x55555555, 0x3FC55555,
            // @formatter:on
    });

    private static ArrayDataPointerConstant pv32 = pointerConstant(16, new int[]{
            // @formatter:off
            0x67F544E1, 0x3E5AE645, 0x1A01A019, 0x3F2A01A0
            // @formatter:on
    });

    private static ArrayDataPointerConstant t2F = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x3FF00000, 0x00000000, 0x00000000, 0xA9FB3335, 0x3FF0163D,
            0x9AB8CDB7, 0x3C9B6129, 0x3E778061, 0x3FF02C9A, 0x535B085D, 0xBC719083,
            0xE86E7F85, 0x3FF04315, 0x1977C96E, 0xBC90A31C, 0xD3158574, 0x3FF059B0,
            0xA475B465, 0x3C8D73E2, 0x29DDF6DE, 0x3FF0706B, 0xE2B13C27, 0xBC8C91DF,
            0x18759BC8, 0x3FF08745, 0x4BB284FF, 0x3C6186BE, 0xCAC6F383, 0x3FF09E3E,
            0x18316136, 0x3C914878, 0x6CF9890F, 0x3FF0B558, 0x4ADC610B, 0x3C98A62E,
            0x2B7247F7, 0x3FF0CC92, 0x16E24F71, 0x3C901EDC, 0x32D3D1A2, 0x3FF0E3EC,
            0x27C57B52, 0x3C403A17, 0xAFFED31B, 0x3FF0FB66, 0xC44EBD7B, 0xBC6B9BED,
            0xD0125B51, 0x3FF11301, 0x39449B3A, 0xBC96C510, 0xC06C31CC, 0x3FF12ABD,
            0xB36CA5C7, 0xBC51B514, 0xAEA92DE0, 0x3FF1429A, 0x9AF1369E, 0xBC932FBF,
            0xC8A58E51, 0x3FF15A98, 0xB9EEAB0A, 0x3C82406A, 0x3C7D517B, 0x3FF172B8,
            0xB9D78A76, 0xBC819041, 0x388C8DEA, 0x3FF18AF9, 0xD1970F6C, 0xBC911023,
            0xEB6FCB75, 0x3FF1A35B, 0x7B4968E4, 0x3C8E5B4C, 0x84045CD4, 0x3FF1BBE0,
            0x352EF607, 0xBC995386, 0x3168B9AA, 0x3FF1D487, 0x00A2643C, 0x3C9E016E,
            0x22FCD91D, 0x3FF1ED50, 0x027BB78C, 0xBC91DF98, 0x88628CD6, 0x3FF2063B,
            0x814A8495, 0x3C8DC775, 0x917DDC96, 0x3FF21F49, 0x9494A5EE, 0x3C82A97E,
            0x6E756238, 0x3FF2387A, 0xB6C70573, 0x3C99B07E, 0x4FB2A63F, 0x3FF251CE,
            0xBEF4F4A4, 0x3C8AC155, 0x65E27CDD, 0x3FF26B45, 0x9940E9D9, 0x3C82BD33,
            0xE1F56381, 0x3FF284DF, 0x8C3F0D7E, 0xBC9A4C3A, 0xF51FDEE1, 0x3FF29E9D,
            0xAFAD1255, 0x3C8612E8, 0xD0DAD990, 0x3FF2B87F, 0xD6381AA4, 0xBC410ADC,
            0xA6E4030B, 0x3FF2D285, 0x54DB41D5, 0x3C900247, 0xA93E2F56, 0x3FF2ECAF,
            0x45D52383, 0x3C71CA0F, 0x0A31B715, 0x3FF306FE, 0xD23182E4, 0x3C86F46A,
            0xFC4CD831, 0x3FF32170, 0x8E18047C, 0x3C8A9CE7, 0xB26416FF, 0x3FF33C08,
            0x843659A6, 0x3C932721, 0x5F929FF1, 0x3FF356C5, 0x5C4E4628, 0xBC8B5CEE,
            0x373AA9CB, 0x3FF371A7, 0xBF42EAE2, 0xBC963AEA, 0x6D05D866, 0x3FF38CAE,
            0x3C9904BD, 0xBC9E958D, 0x34E59FF7, 0x3FF3A7DB, 0xD661F5E3, 0xBC75E436,
            0xC313A8E5, 0x3FF3C32D, 0x375D29C3, 0xBC9EFFF8, 0x4C123422, 0x3FF3DEA6,
            0x11F09EBC, 0x3C8ADA09, 0x04AC801C, 0x3FF3FA45, 0xF956F9F3, 0xBC97D023,
            0x21F72E2A, 0x3FF4160A, 0x1C309278, 0xBC5EF369, 0xD950A897, 0x3FF431F5,
            0xE35F7999, 0xBC81C7DD, 0x6061892D, 0x3FF44E08, 0x04EF80D0, 0x3C489B7A,
            0xED1D0057, 0x3FF46A41, 0xD1648A76, 0x3C9C944B, 0xB5C13CD0, 0x3FF486A2,
            0xB69062F0, 0x3C73C1A3, 0xF0D7D3DE, 0x3FF4A32A, 0xF3D1BE56, 0x3C99CB62,
            0xD5362A27, 0x3FF4BFDA, 0xAFEC42E2, 0x3C7D4397, 0x99FDDD0D, 0x3FF4DCB2,
            0xBC6A7833, 0x3C98ECDB, 0x769D2CA7, 0x3FF4F9B2, 0xD25957E3, 0xBC94B309,
            0xA2CF6642, 0x3FF516DA, 0x69BD93EF, 0xBC8F7685, 0x569D4F82, 0x3FF5342B,
            0x1DB13CAD, 0xBC807ABE, 0xCA5D920F, 0x3FF551A4, 0xEFEDE59B, 0xBC8D689C,
            0x36B527DA, 0x3FF56F47, 0x011D93AD, 0x3C99BB2C, 0xD497C7FD, 0x3FF58D12,
            0x5B9A1DE8, 0x3C8295E1, 0xDD485429, 0x3FF5AB07, 0x054647AD, 0x3C96324C,
            0x8A5946B7, 0x3FF5C926, 0x816986A2, 0x3C3C4B1B, 0x15AD2148, 0x3FF5E76F,
            0x3080E65E, 0x3C9BA6F9, 0xB976DC09, 0x3FF605E1, 0x9B56DE47, 0xBC93E242,
            0xB03A5585, 0x3FF6247E, 0x7E40B497, 0xBC9383C1, 0x34CCC320, 0x3FF64346,
            0x759D8933, 0xBC8C483C, 0x82552225, 0x3FF66238, 0x87591C34, 0xBC9BB609,
            0xD44CA973, 0x3FF68155, 0x44F73E65, 0x3C6038AE, 0x667F3BCD, 0x3FF6A09E,
            0x13B26456, 0xBC9BDD34, 0x750BDABF, 0x3FF6C012, 0x67FF0B0D, 0xBC728956,
            0x3C651A2F, 0x3FF6DFB2, 0x683C88AB, 0xBC6BBE3A, 0xF9519484, 0x3FF6FF7D,
            0x25860EF6, 0xBC883C0F, 0xE8EC5F74, 0x3FF71F75, 0x86887A99, 0xBC816E47,
            0x48A58174, 0x3FF73F9A, 0x6C65D53C, 0xBC90A8D9, 0x564267C9, 0x3FF75FEB,
            0x57316DD3, 0xBC902459, 0x4FDE5D3F, 0x3FF78069, 0x0A02162D, 0x3C9866B8,
            0x73EB0187, 0x3FF7A114, 0xEE04992F, 0xBC841577, 0x0130C132, 0x3FF7C1ED,
            0xD1164DD6, 0x3C9F124C, 0x36CF4E62, 0x3FF7E2F3, 0xBA15797E, 0x3C705D02,
            0x543E1A12, 0x3FF80427, 0x626D972B, 0xBC927C86, 0x994CCE13, 0x3FF82589,
            0xD41532D8, 0xBC9D4C1D, 0x4623C7AD, 0x3FF8471A, 0xA341CDFB, 0xBC88D684,
            0x9B4492ED, 0x3FF868D9, 0x9BD4F6BA, 0xBC9FC6F8, 0xD98A6699, 0x3FF88AC7,
            0xF37CB53A, 0x3C9994C2, 0x422AA0DB, 0x3FF8ACE5, 0x56864B27, 0x3C96E9F1,
            0x16B5448C, 0x3FF8CF32, 0x32E9E3AA, 0xBC70D55E, 0x99157736, 0x3FF8F1AE,
            0xA2E3976C, 0x3C85CC13, 0x0B91FFC6, 0x3FF9145B, 0x2E582524, 0xBC9DD679,
            0xB0CDC5E5, 0x3FF93737, 0x81B57EBC, 0xBC675FC7, 0xCBC8520F, 0x3FF95A44,
            0x96A5F039, 0xBC764B7C, 0x9FDE4E50, 0x3FF97D82, 0x7C1B85D1, 0xBC9D185B,
            0x70CA07BA, 0x3FF9A0F1, 0x91CEE632, 0xBC9173BD, 0x82A3F090, 0x3FF9C491,
            0xB071F2BE, 0x3C7C7C46, 0x19E32323, 0x3FF9E863, 0x78E64C6E, 0x3C7824CA,
            0x7B5DE565, 0x3FFA0C66, 0x5D1CD533, 0xBC935949, 0xEC4A2D33, 0x3FFA309B,
            0x7DDC36AB, 0x3C96305C, 0xB23E255D, 0x3FFA5503, 0xDB8D41E1, 0xBC9D2F6E,
            0x1330B358, 0x3FFA799E, 0xCAC563C7, 0x3C9BCB7E, 0x5579FDBF, 0x3FFA9E6B,
            0x0EF7FD31, 0x3C90FAC9, 0xBFD3F37A, 0x3FFAC36B, 0xCAE76CD0, 0xBC8F9234,
            0x995AD3AD, 0x3FFAE89F, 0x345DCC81, 0x3C97A1CD, 0x298DB666, 0x3FFB0E07,
            0x4C80E425, 0xBC9BDEF5, 0xB84F15FB, 0x3FFB33A2, 0x3084D708, 0xBC62805E,
            0x8DE5593A, 0x3FFB5972, 0xBBBA6DE3, 0xBC9C71DF, 0xF2FB5E47, 0x3FFB7F76,
            0x7E54AC3B, 0xBC75584F, 0x30A1064A, 0x3FFBA5B0, 0x0E54292E, 0xBC9EFCD3,
            0x904BC1D2, 0x3FFBCC1E, 0x7A2D9E84, 0x3C823DD0, 0x5BD71E09, 0x3FFBF2C2,
            0x3F6B9C73, 0xBC9EFDCA, 0xDD85529C, 0x3FFC199B, 0x895048DD, 0x3C811065,
            0x5FFFD07A, 0x3FFC40AB, 0xE083C60A, 0x3C9B4537, 0x2E57D14B, 0x3FFC67F1,
            0xFF483CAD, 0x3C92884D, 0x9406E7B5, 0x3FFC8F6D, 0x48805C44, 0x3C71ACBC,
            0xDCEF9069, 0x3FFCB720, 0xD1E949DC, 0x3C7503CB, 0x555DC3FA, 0x3FFCDF0B,
            0x53829D72, 0xBC8DD83B, 0x4A07897C, 0x3FFD072D, 0x43797A9C, 0xBC9CBC37,
            0x080D89F2, 0x3FFD2F87, 0x719D8578, 0xBC9D487B, 0xDCFBA487, 0x3FFD5818,
            0xD75B3707, 0x3C82ED02, 0x16C98398, 0x3FFD80E3, 0x8BEDDFE8, 0xBC911EC1,
            0x03DB3285, 0x3FFDA9E6, 0x696DB532, 0x3C9C2300, 0xF301B460, 0x3FFDD321,
            0x78F018C3, 0x3C92DA57, 0x337B9B5F, 0x3FFDFC97, 0x4F184B5C, 0xBC91A5CD,
            0x14F5A129, 0x3FFE2646, 0x817A1496, 0xBC97B627, 0xE78B3FF6, 0x3FFE502E,
            0x80A9CC8F, 0x3C839E89, 0xFBC74C83, 0x3FFE7A51, 0xCA0C8DE2, 0x3C92D522,
            0xA2A490DA, 0x3FFEA4AF, 0x179C2893, 0xBC9E9C23, 0x2D8E67F1, 0x3FFECF48,
            0xB411AD8C, 0xBC9C93F3, 0xEE615A27, 0x3FFEFA1B, 0x86A4B6B0, 0x3C9DC7F4,
            0x376BBA97, 0x3FFF252B, 0xBF0D8E43, 0x3C93A1A5, 0x5B6E4540, 0x3FFF5076,
            0x2DD8A18B, 0x3C99D3E1, 0xAD9CBE14, 0x3FFF7BFD, 0xD006350A, 0xBC9DBB12,
            0x819E90D8, 0x3FFFA7C1, 0xF3A5931E, 0x3C874853, 0x2B8F71F1, 0x3FFFD3C2,
            0x966579E7, 0x3C62EB74
            // @formatter:on
    });

    private static ArrayDataPointerConstant t2NegF = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x3FF00000, 0x00000000, 0x00000000, 0x2B8F71F1, 0x3FEFD3C2,
            0x966579E7, 0x3C52EB74, 0x819E90D8, 0x3FEFA7C1, 0xF3A5931E, 0x3C774853,
            0xAD9CBE14, 0x3FEF7BFD, 0xD006350A, 0xBC8DBB12, 0x5B6E4540, 0x3FEF5076,
            0x2DD8A18B, 0x3C89D3E1, 0x376BBA97, 0x3FEF252B, 0xBF0D8E43, 0x3C83A1A5,
            0xEE615A27, 0x3FEEFA1B, 0x86A4B6B0, 0x3C8DC7F4, 0x2D8E67F1, 0x3FEECF48,
            0xB411AD8C, 0xBC8C93F3, 0xA2A490DA, 0x3FEEA4AF, 0x179C2893, 0xBC8E9C23,
            0xFBC74C83, 0x3FEE7A51, 0xCA0C8DE2, 0x3C82D522, 0xE78B3FF6, 0x3FEE502E,
            0x80A9CC8F, 0x3C739E89, 0x14F5A129, 0x3FEE2646, 0x817A1496, 0xBC87B627,
            0x337B9B5F, 0x3FEDFC97, 0x4F184B5C, 0xBC81A5CD, 0xF301B460, 0x3FEDD321,
            0x78F018C3, 0x3C82DA57, 0x03DB3285, 0x3FEDA9E6, 0x696DB532, 0x3C8C2300,
            0x16C98398, 0x3FED80E3, 0x8BEDDFE8, 0xBC811EC1, 0xDCFBA487, 0x3FED5818,
            0xD75B3707, 0x3C72ED02, 0x080D89F2, 0x3FED2F87, 0x719D8578, 0xBC8D487B,
            0x4A07897C, 0x3FED072D, 0x43797A9C, 0xBC8CBC37, 0x555DC3FA, 0x3FECDF0B,
            0x53829D72, 0xBC7DD83B, 0xDCEF9069, 0x3FECB720, 0xD1E949DC, 0x3C6503CB,
            0x9406E7B5, 0x3FEC8F6D, 0x48805C44, 0x3C61ACBC, 0x2E57D14B, 0x3FEC67F1,
            0xFF483CAD, 0x3C82884D, 0x5FFFD07A, 0x3FEC40AB, 0xE083C60A, 0x3C8B4537,
            0xDD85529C, 0x3FEC199B, 0x895048DD, 0x3C711065, 0x5BD71E09, 0x3FEBF2C2,
            0x3F6B9C73, 0xBC8EFDCA, 0x904BC1D2, 0x3FEBCC1E, 0x7A2D9E84, 0x3C723DD0,
            0x30A1064A, 0x3FEBA5B0, 0x0E54292E, 0xBC8EFCD3, 0xF2FB5E47, 0x3FEB7F76,
            0x7E54AC3B, 0xBC65584F, 0x8DE5593A, 0x3FEB5972, 0xBBBA6DE3, 0xBC8C71DF,
            0xB84F15FB, 0x3FEB33A2, 0x3084D708, 0xBC52805E, 0x298DB666, 0x3FEB0E07,
            0x4C80E425, 0xBC8BDEF5, 0x995AD3AD, 0x3FEAE89F, 0x345DCC81, 0x3C87A1CD,
            0xBFD3F37A, 0x3FEAC36B, 0xCAE76CD0, 0xBC7F9234, 0x5579FDBF, 0x3FEA9E6B,
            0x0EF7FD31, 0x3C80FAC9, 0x1330B358, 0x3FEA799E, 0xCAC563C7, 0x3C8BCB7E,
            0xB23E255D, 0x3FEA5503, 0xDB8D41E1, 0xBC8D2F6E, 0xEC4A2D33, 0x3FEA309B,
            0x7DDC36AB, 0x3C86305C, 0x7B5DE565, 0x3FEA0C66, 0x5D1CD533, 0xBC835949,
            0x19E32323, 0x3FE9E863, 0x78E64C6E, 0x3C6824CA, 0x82A3F090, 0x3FE9C491,
            0xB071F2BE, 0x3C6C7C46, 0x70CA07BA, 0x3FE9A0F1, 0x91CEE632, 0xBC8173BD,
            0x9FDE4E50, 0x3FE97D82, 0x7C1B85D1, 0xBC8D185B, 0xCBC8520F, 0x3FE95A44,
            0x96A5F039, 0xBC664B7C, 0xB0CDC5E5, 0x3FE93737, 0x81B57EBC, 0xBC575FC7,
            0x0B91FFC6, 0x3FE9145B, 0x2E582524, 0xBC8DD679, 0x99157736, 0x3FE8F1AE,
            0xA2E3976C, 0x3C75CC13, 0x16B5448C, 0x3FE8CF32, 0x32E9E3AA, 0xBC60D55E,
            0x422AA0DB, 0x3FE8ACE5, 0x56864B27, 0x3C86E9F1, 0xD98A6699, 0x3FE88AC7,
            0xF37CB53A, 0x3C8994C2, 0x9B4492ED, 0x3FE868D9, 0x9BD4F6BA, 0xBC8FC6F8,
            0x4623C7AD, 0x3FE8471A, 0xA341CDFB, 0xBC78D684, 0x994CCE13, 0x3FE82589,
            0xD41532D8, 0xBC8D4C1D, 0x543E1A12, 0x3FE80427, 0x626D972B, 0xBC827C86,
            0x36CF4E62, 0x3FE7E2F3, 0xBA15797E, 0x3C605D02, 0x0130C132, 0x3FE7C1ED,
            0xD1164DD6, 0x3C8F124C, 0x73EB0187, 0x3FE7A114, 0xEE04992F, 0xBC741577,
            0x4FDE5D3F, 0x3FE78069, 0x0A02162D, 0x3C8866B8, 0x564267C9, 0x3FE75FEB,
            0x57316DD3, 0xBC802459, 0x48A58174, 0x3FE73F9A, 0x6C65D53C, 0xBC80A8D9,
            0xE8EC5F74, 0x3FE71F75, 0x86887A99, 0xBC716E47, 0xF9519484, 0x3FE6FF7D,
            0x25860EF6, 0xBC783C0F, 0x3C651A2F, 0x3FE6DFB2, 0x683C88AB, 0xBC5BBE3A,
            0x750BDABF, 0x3FE6C012, 0x67FF0B0D, 0xBC628956, 0x667F3BCD, 0x3FE6A09E,
            0x13B26456, 0xBC8BDD34, 0xD44CA973, 0x3FE68155, 0x44F73E65, 0x3C5038AE,
            0x82552225, 0x3FE66238, 0x87591C34, 0xBC8BB609, 0x34CCC320, 0x3FE64346,
            0x759D8933, 0xBC7C483C, 0xB03A5585, 0x3FE6247E, 0x7E40B497, 0xBC8383C1,
            0xB976DC09, 0x3FE605E1, 0x9B56DE47, 0xBC83E242, 0x15AD2148, 0x3FE5E76F,
            0x3080E65E, 0x3C8BA6F9, 0x8A5946B7, 0x3FE5C926, 0x816986A2, 0x3C2C4B1B,
            0xDD485429, 0x3FE5AB07, 0x054647AD, 0x3C86324C, 0xD497C7FD, 0x3FE58D12,
            0x5B9A1DE8, 0x3C7295E1, 0x36B527DA, 0x3FE56F47, 0x011D93AD, 0x3C89BB2C,
            0xCA5D920F, 0x3FE551A4, 0xEFEDE59B, 0xBC7D689C, 0x569D4F82, 0x3FE5342B,
            0x1DB13CAD, 0xBC707ABE, 0xA2CF6642, 0x3FE516DA, 0x69BD93EF, 0xBC7F7685,
            0x769D2CA7, 0x3FE4F9B2, 0xD25957E3, 0xBC84B309, 0x99FDDD0D, 0x3FE4DCB2,
            0xBC6A7833, 0x3C88ECDB, 0xD5362A27, 0x3FE4BFDA, 0xAFEC42E2, 0x3C6D4397,
            0xF0D7D3DE, 0x3FE4A32A, 0xF3D1BE56, 0x3C89CB62, 0xB5C13CD0, 0x3FE486A2,
            0xB69062F0, 0x3C63C1A3, 0xED1D0057, 0x3FE46A41, 0xD1648A76, 0x3C8C944B,
            0x6061892D, 0x3FE44E08, 0x04EF80D0, 0x3C389B7A, 0xD950A897, 0x3FE431F5,
            0xE35F7999, 0xBC71C7DD, 0x21F72E2A, 0x3FE4160A, 0x1C309278, 0xBC4EF369,
            0x04AC801C, 0x3FE3FA45, 0xF956F9F3, 0xBC87D023, 0x4C123422, 0x3FE3DEA6,
            0x11F09EBC, 0x3C7ADA09, 0xC313A8E5, 0x3FE3C32D, 0x375D29C3, 0xBC8EFFF8,
            0x34E59FF7, 0x3FE3A7DB, 0xD661F5E3, 0xBC65E436, 0x6D05D866, 0x3FE38CAE,
            0x3C9904BD, 0xBC8E958D, 0x373AA9CB, 0x3FE371A7, 0xBF42EAE2, 0xBC863AEA,
            0x5F929FF1, 0x3FE356C5, 0x5C4E4628, 0xBC7B5CEE, 0xB26416FF, 0x3FE33C08,
            0x843659A6, 0x3C832721, 0xFC4CD831, 0x3FE32170, 0x8E18047C, 0x3C7A9CE7,
            0x0A31B715, 0x3FE306FE, 0xD23182E4, 0x3C76F46A, 0xA93E2F56, 0x3FE2ECAF,
            0x45D52383, 0x3C61CA0F, 0xA6E4030B, 0x3FE2D285, 0x54DB41D5, 0x3C800247,
            0xD0DAD990, 0x3FE2B87F, 0xD6381AA4, 0xBC310ADC, 0xF51FDEE1, 0x3FE29E9D,
            0xAFAD1255, 0x3C7612E8, 0xE1F56381, 0x3FE284DF, 0x8C3F0D7E, 0xBC8A4C3A,
            0x65E27CDD, 0x3FE26B45, 0x9940E9D9, 0x3C72BD33, 0x4FB2A63F, 0x3FE251CE,
            0xBEF4F4A4, 0x3C7AC155, 0x6E756238, 0x3FE2387A, 0xB6C70573, 0x3C89B07E,
            0x917DDC96, 0x3FE21F49, 0x9494A5EE, 0x3C72A97E, 0x88628CD6, 0x3FE2063B,
            0x814A8495, 0x3C7DC775, 0x22FCD91D, 0x3FE1ED50, 0x027BB78C, 0xBC81DF98,
            0x3168B9AA, 0x3FE1D487, 0x00A2643C, 0x3C8E016E, 0x84045CD4, 0x3FE1BBE0,
            0x352EF607, 0xBC895386, 0xEB6FCB75, 0x3FE1A35B, 0x7B4968E4, 0x3C7E5B4C,
            0x388C8DEA, 0x3FE18AF9, 0xD1970F6C, 0xBC811023, 0x3C7D517B, 0x3FE172B8,
            0xB9D78A76, 0xBC719041, 0xC8A58E51, 0x3FE15A98, 0xB9EEAB0A, 0x3C72406A,
            0xAEA92DE0, 0x3FE1429A, 0x9AF1369E, 0xBC832FBF, 0xC06C31CC, 0x3FE12ABD,
            0xB36CA5C7, 0xBC41B514, 0xD0125B51, 0x3FE11301, 0x39449B3A, 0xBC86C510,
            0xAFFED31B, 0x3FE0FB66, 0xC44EBD7B, 0xBC5B9BED, 0x32D3D1A2, 0x3FE0E3EC,
            0x27C57B52, 0x3C303A17, 0x2B7247F7, 0x3FE0CC92, 0x16E24F71, 0x3C801EDC,
            0x6CF9890F, 0x3FE0B558, 0x4ADC610B, 0x3C88A62E, 0xCAC6F383, 0x3FE09E3E,
            0x18316136, 0x3C814878, 0x18759BC8, 0x3FE08745, 0x4BB284FF, 0x3C5186BE,
            0x29DDF6DE, 0x3FE0706B, 0xE2B13C27, 0xBC7C91DF, 0xD3158574, 0x3FE059B0,
            0xA475B465, 0x3C7D73E2, 0xE86E7F85, 0x3FE04315, 0x1977C96E, 0xBC80A31C,
            0x3E778061, 0x3FE02C9A, 0x535B085D, 0xBC619083, 0xA9FB3335, 0x3FE0163D,
            0x9AB8CDB7, 0x3C8B6129
            // @formatter:on
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label l2TAGPACKET002 = new Label();
        Label l2TAGPACKET102 = new Label();
        Label l2TAGPACKET302 = new Label();
        Label l2TAGPACKET402 = new Label();
        Label l2TAGPACKET502 = new Label();
        Label l2TAGPACKET602 = new Label();
        Label b12 = new Label();
        Label b15 = new Label();

        masm.bind(b12);
        masm.xorpd(xmm4, xmm4);
        masm.movsd(xmm1, recordExternalAddress(crb, l2E));
        masm.movl(rax, 32768);
        masm.pinsrw(xmm4, rax, 3);
        masm.pextrw(rcx, xmm0, 3);
        masm.andnpd(xmm4, xmm0);
        masm.pshufd(xmm5, xmm4, 68);
        masm.movl(rdx, 32768);
        masm.andl(rdx, rcx);
        masm.andl(rcx, 32767);
        masm.subl(rcx, 16343);
        // Branch only if |x| is not in [23/64, 3*2^8)
        masm.cmplAndJcc(rcx, 177, ConditionFlag.AboveEqual, l2TAGPACKET002, false);
        masm.movsd(xmm3, recordExternalAddress(crb, halfMask));
        masm.movsd(xmm2, recordExternalAddress(crb, l2E8));
        masm.movsd(xmm6, recordExternalAddress(crb, shifter));
        masm.andpd(xmm3, xmm0);
        masm.subsd(xmm4, xmm3);
        masm.mulsd(xmm3, xmm1);
        masm.mulsd(xmm2, xmm5);
        masm.cvtsd2siq(rax, xmm3);
        masm.shll(rdx, 3);
        masm.orl(rax, rdx);
        masm.movq(xmm7, xmm3);
        masm.addsd(xmm3, xmm6);
        masm.mulsd(xmm1, xmm4);
        masm.xorpd(xmm5, xmm5);
        masm.subsd(xmm3, xmm6);
        masm.movapd(xmm4, recordExternalAddress(crb, cv));
        masm.addsd(xmm2, xmm1);
        masm.movapd(xmm6, recordExternalAddress(crb, cv16));
        masm.subsd(xmm7, xmm3);
        masm.movl(rdx, 32704);
        masm.pinsrw(xmm5, rdx, 3);
        masm.movapd(xmm1, recordExternalAddress(crb, cv32));
        masm.addsd(xmm2, xmm7);
        masm.movl(rdx, 127);
        masm.andl(rdx, rax);
        masm.addl(rdx, rdx);
        masm.shrl(rax, 3);
        masm.andl(rax, 65520);
        masm.addl(rax, 16352);
        masm.xorpd(xmm0, xmm0);
        // Branch only if |x| is not in [23/64, 3*2^7)
        masm.cmplAndJcc(rcx, 161, ConditionFlag.AboveEqual, l2TAGPACKET102, false);
        masm.pshufd(xmm5, xmm5, 68);
        masm.pinsrw(xmm0, rax, 3);
        masm.pshufd(xmm0, xmm0, 68);
        masm.psubw(xmm5, xmm0);
        masm.leaq(r8, recordExternalAddress(crb, t2F));
        masm.mulpd(xmm0, new AMD64Address(r8, rdx, Stride.S8));
        masm.leaq(r8, recordExternalAddress(crb, t2NegF));
        masm.mulpd(xmm5, new AMD64Address(r8, rdx, Stride.S8));
        masm.pshufd(xmm3, xmm2, 68);
        masm.movapd(xmm7, recordExternalAddress(crb, cv48));
        masm.pshufd(xmm2, xmm2, 68);
        masm.mulpd(xmm3, xmm3);
        masm.mulpd(xmm4, xmm2);
        masm.mulpd(xmm6, xmm2);
        masm.mulpd(xmm2, recordExternalAddress(crb, cv64));
        masm.mulpd(xmm1, xmm3);
        masm.mulpd(xmm7, xmm3);
        masm.mulpd(xmm4, xmm3);
        masm.mulpd(xmm1, xmm3);
        masm.addpd(xmm6, xmm7);
        masm.movq(xmm7, xmm0);
        masm.addpd(xmm4, xmm1);
        masm.shufpd(xmm7, xmm5, 0);
        masm.subpd(xmm0, xmm5);
        masm.mulpd(xmm2, xmm7);
        masm.addpd(xmm4, xmm6);
        masm.subsd(xmm7, xmm0);
        masm.mulpd(xmm4, xmm2);
        masm.pshufd(xmm6, xmm0, 238);
        masm.subsd(xmm7, xmm5);
        masm.addpd(xmm4, xmm2);
        masm.addsd(xmm7, xmm6);
        masm.pshufd(xmm2, xmm4, 238);
        masm.addsd(xmm2, xmm7);
        masm.addsd(xmm2, xmm4);
        masm.addsd(xmm0, xmm2);
        masm.jmp(b15);

        masm.bind(l2TAGPACKET102);
        masm.subl(rax, 16352);
        masm.movl(rcx, rax);
        masm.andl(rax, 32752);
        masm.shrl(rax, 1);
        masm.andl(rax, 65520);
        masm.subl(rcx, rax);
        masm.addl(rax, 16352);
        masm.pinsrw(xmm0, rax, 3);
        masm.pshufd(xmm0, xmm0, 68);
        masm.leaq(r8, recordExternalAddress(crb, t2F));
        masm.mulpd(xmm0, new AMD64Address(r8, rdx, Stride.S8));
        masm.pshufd(xmm3, xmm2, 68);
        masm.movsd(xmm7, recordExternalAddress(crb, cv48));
        masm.mulsd(xmm3, xmm3);
        masm.mulsd(xmm4, xmm2);
        masm.mulsd(xmm6, xmm2);
        masm.mulsd(xmm2, recordExternalAddress(crb, cv64));
        masm.mulsd(xmm1, xmm3);
        masm.mulsd(xmm7, xmm3);
        masm.mulsd(xmm4, xmm3);
        masm.addl(rcx, 16368);
        masm.pinsrw(xmm5, rcx, 3);
        masm.mulsd(xmm1, xmm3);
        masm.addsd(xmm6, xmm7);
        masm.addsd(xmm4, xmm1);
        masm.mulsd(xmm2, xmm0);
        masm.addsd(xmm4, xmm6);
        masm.mulsd(xmm4, xmm2);
        masm.pshufd(xmm6, xmm0, 238);
        masm.addsd(xmm4, xmm6);
        masm.addsd(xmm2, xmm4);
        masm.addsd(xmm0, xmm2);
        masm.mulsd(xmm0, xmm5);
        masm.jmp(b15);

        masm.bind(l2TAGPACKET002);
        masm.addl(rcx, 16343);
        // Branch only if |x| > 23/64
        masm.cmplAndJcc(rcx, 16343, ConditionFlag.Above, l2TAGPACKET302, false);
        // Branch only if |x| < 2^-32
        masm.cmplAndJcc(rcx, 15856, ConditionFlag.Below, l2TAGPACKET402, false);
        masm.movapd(xmm1, recordExternalAddress(crb, pv));
        masm.pshufd(xmm6, xmm0, 68);
        masm.mulpd(xmm5, xmm5);
        masm.movapd(xmm2, recordExternalAddress(crb, pv16));
        masm.pshufd(xmm7, xmm0, 68);
        masm.movapd(xmm3, recordExternalAddress(crb, pv32));
        masm.pshufd(xmm4, xmm0, 68);
        masm.andpd(xmm6, recordExternalAddress(crb, mask3));
        masm.mulpd(xmm1, xmm5);
        masm.mulsd(xmm2, xmm5);
        masm.subpd(xmm4, xmm6);
        masm.mulpd(xmm7, xmm5);
        masm.addpd(xmm1, xmm3);
        masm.pshufd(xmm3, xmm6, 68);
        masm.mulpd(xmm5, xmm5);
        masm.mulsd(xmm2, xmm7);
        masm.mulpd(xmm1, xmm7);
        masm.pshufd(xmm7, xmm0, 68);
        masm.mulsd(xmm6, xmm6);
        masm.addsd(xmm7, xmm7);
        masm.mulsd(xmm4, xmm4);
        masm.mulpd(xmm1, xmm5);
        masm.addsd(xmm7, xmm0);
        masm.mulsd(xmm6, xmm3);
        masm.mulsd(xmm7, xmm3);
        masm.pshufd(xmm3, xmm1, 238);
        masm.mulsd(xmm1, xmm5);
        masm.pshufd(xmm5, xmm4, 238);
        masm.addsd(xmm3, xmm2);
        masm.pshufd(xmm2, xmm2, 238);
        masm.addsd(xmm7, xmm4);
        masm.movq(xmm4, xmm0);
        masm.mulsd(xmm6, xmm2);
        masm.mulsd(xmm7, xmm5);
        masm.addsd(xmm0, xmm6);
        masm.mulsd(xmm7, xmm2);
        masm.subsd(xmm4, xmm0);
        masm.addsd(xmm1, xmm7);
        masm.addsd(xmm6, xmm4);
        masm.addsd(xmm1, xmm3);
        masm.addsd(xmm1, xmm6);
        masm.addsd(xmm0, xmm1);
        masm.jmp(b15);

        masm.bind(l2TAGPACKET402);
        // Branch only if |x| is not denormalized
        masm.cmplAndJcc(rcx, 16, ConditionFlag.AboveEqual, l2TAGPACKET502, false);
        masm.movq(xmm1, xmm0);
        masm.mulsd(xmm1, xmm1);
        masm.jmp(b15);

        masm.bind(l2TAGPACKET502);
        masm.xorpd(xmm2, xmm2);
        masm.movl(rcx, 17392);
        masm.pinsrw(xmm2, rcx, 3);
        masm.xorpd(xmm3, xmm3);
        masm.movl(rdx, 15344);
        masm.pinsrw(xmm3, rdx, 3);
        masm.mulsd(xmm2, xmm0);
        masm.addsd(xmm0, xmm2);
        masm.mulsd(xmm0, xmm3);
        masm.jmp(b15);

        masm.bind(l2TAGPACKET302);
        // Branch only if |x| is INF or NaN
        masm.cmplAndJcc(rcx, 32752, ConditionFlag.AboveEqual, l2TAGPACKET602, false);
        masm.xorpd(xmm0, xmm0);
        masm.movl(rax, 32736);
        masm.pinsrw(xmm0, rax, 3);
        masm.orl(rax, rdx);
        masm.pinsrw(xmm1, rax, 3);
        masm.mulsd(xmm0, xmm1);
        masm.jmp(b15);

        masm.bind(l2TAGPACKET602);
        masm.xorpd(xmm1, xmm1);
        masm.movl(rax, 32768);
        masm.pinsrw(xmm1, rax, 3);
        masm.andnpd(xmm1, xmm0);
        masm.mulsd(xmm0, xmm1);

        masm.bind(b15);
    }
}
