/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSD2USI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSD2USQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSS2USI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSS2USQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTSD2SI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTSD2SQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTSS2SI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTSS2SQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTUSQ2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTUSQ2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSD2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSI2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSI2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSQ2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSQ2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSS2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VADDSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VADDSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMULSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMULSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBSS;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.amd64.AMD64NodeMatchRules;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.FloatConvertCategory;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.match.ComplexMatchResult;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorBinary.AVXBinaryMemoryOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary.AVXConvertMemoryOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary.AVXUnaryMemoryOp;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AMD64VectorNodeMatchRules extends AMD64NodeMatchRules {

    private final AMD64SIMDInstructionEncoding simdEncoding;

    public AMD64VectorNodeMatchRules(LIRGeneratorTool gen) {
        super(gen);
        assert gen.getArithmetic() instanceof VectorLIRGeneratorTool : gen.getArithmetic();
        simdEncoding = ((AMD64VectorArithmeticLIRGenerator) gen.getArithmetic()).getSimdEncoding();
    }

    public static AVXSize getRegisterSize(Value a) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.isXMM()) {
            return AVXKind.getRegisterSize(kind);
        } else {
            return AVXSize.XMM;
        }
    }

    private AVXSize fusedVectorOpSize(SimdStamp stamp, LIRLowerableAccess access, boolean isConversion) {
        AMD64Kind accessKind = getMemoryKind(access);
        int inputBytes = accessKind.getScalar().getSizeInBytes();
        AMD64Kind outputKind = (AMD64Kind) getLIRGeneratorTool().getLIRKind(stamp).getPlatformKind();
        int outputBytes = outputKind.getScalar().getSizeInBytes();

        GraalError.guarantee(accessKind.getVectorLength() == outputKind.getVectorLength(), "must have the same length");
        GraalError.guarantee(isConversion || accessKind == outputKind, "non-conversion operations must have the same input and output");
        int size = accessKind.getVectorLength() * Math.max(inputBytes, outputBytes);
        if (size == AVXSize.ZMM.getBytes()) {
            return AVXSize.ZMM;
        } else if (size == AVXSize.YMM.getBytes()) {
            return AVXSize.YMM;
        } else if (size == AVXSize.XMM.getBytes()) {
            return AVXSize.XMM;
        } else {
            GraalError.guarantee(size < AVXSize.XMM.getBytes(), "must not be larger than XMM");
            return null;
        }
    }

    private AMD64Kind scalarKind(SimdStamp stamp) {
        AMD64Kind vKind = (AMD64Kind) getLIRGeneratorTool().getLIRKind(stamp).getPlatformKind();
        GraalError.guarantee(vKind.getVectorLength() > 1, "must be vector");
        return vKind.getScalar();
    }

    private ComplexMatchResult binaryRead(VexRVMOp op, AVXSize size, ValueNode value, AddressableMemoryAccess access) {
        return builder -> {
            LIRGeneratorTool g = getLIRGeneratorTool();
            AllocatableValue v = g.asAllocatable(operand(value));
            Variable result = g.newVariable(LIRKind.combine(v));
            AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
            append(new AVXBinaryMemoryOp(op.encoding(simdEncoding), size, result, v, address, getState(access)));
            return result;
        };
    }

    @Override
    public ComplexMatchResult logicalAndNot(ValueNode a, ValueNode b) {
        if (SimdStamp.isOpmask(a.stamp(NodeView.DEFAULT)) && SimdStamp.isOpmask(b.stamp(NodeView.DEFAULT))) {
            if (AMD64BaseAssembler.supportsFullAVX512(((AMD64) getLIRGeneratorTool().target().arch).getFeatures())) {
                return builder -> getArithmeticLIRGenerator().emitLogicalAndNot(operand(a), operand(b));
            } else {
                return null;
            }
        } else {
            return super.logicalAndNot(a, b);
        }
    }

    @Override
    public ComplexMatchResult addMemory(ValueNode value, LIRLowerableAccess access) {
        Stamp stamp = access.getAccessStamp(NodeView.DEFAULT);
        if (stamp instanceof SimdStamp simdStamp) {
            AVXSize size = fusedVectorOpSize(simdStamp, access, false);
            if (size == null) {
                return null;
            }
            VexRVMOp op = switch (scalarKind(simdStamp)) {
                case BYTE -> VexRVMOp.VPADDB;
                case WORD -> VexRVMOp.VPADDW;
                case DWORD -> VexRVMOp.VPADDD;
                case QWORD -> VexRVMOp.VPADDQ;
                case SINGLE -> VexRVMOp.VADDPS;
                case DOUBLE -> VexRVMOp.VADDPD;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(scalarKind(simdStamp));
            };
            return binaryRead(op, size, value, access);
        } else if (stamp instanceof FloatStamp) {
            VexRVMOp op = ((FloatStamp) stamp).getBits() == 32 ? VADDSS : VADDSD;
            return binaryRead(op, AVXSize.XMM, value, access);
        } else {
            return super.addMemory(value, access);
        }
    }

    @Override
    public ComplexMatchResult subMemory(ValueNode value, LIRLowerableAccess access) {
        Stamp stamp = access.getAccessStamp(NodeView.DEFAULT);
        if (stamp instanceof SimdStamp simdStamp) {
            AVXSize size = fusedVectorOpSize(simdStamp, access, false);
            if (size == null) {
                return null;
            }
            VexRVMOp op = switch (scalarKind(simdStamp)) {
                case BYTE -> VexRVMOp.VPSUBB;
                case WORD -> VexRVMOp.VPSUBW;
                case DWORD -> VexRVMOp.VPSUBD;
                case QWORD -> VexRVMOp.VPSUBQ;
                case SINGLE -> VexRVMOp.VSUBPS;
                case DOUBLE -> VexRVMOp.VSUBPD;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(scalarKind(simdStamp));
            };
            return binaryRead(op, size, value, access);
        } else if (stamp instanceof FloatStamp) {
            VexRVMOp op = ((FloatStamp) stamp).getBits() == 32 ? VSUBSS : VSUBSD;
            return binaryRead(op, AVXSize.XMM, value, access);
        } else {
            return super.subMemory(value, access);
        }
    }

    @Override
    public ComplexMatchResult mulMemory(ValueNode value, LIRLowerableAccess access) {
        Stamp stamp = access.getAccessStamp(NodeView.DEFAULT);
        if (stamp instanceof SimdStamp simdStamp) {
            AVXSize size = fusedVectorOpSize(simdStamp, access, false);
            if (size == null) {
                return null;
            }
            VexRVMOp op = switch (scalarKind(simdStamp)) {
                case WORD -> VexRVMOp.VPMULLW;
                case DWORD -> VexRVMOp.VPMULLD;
                case QWORD -> VexRVMOp.EVPMULLQ;
                case SINGLE -> VexRVMOp.VMULPS;
                case DOUBLE -> VexRVMOp.VMULPD;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(scalarKind(simdStamp));
            };
            return binaryRead(op, size, value, access);
        } else if (stamp instanceof FloatStamp) {
            VexRVMOp op = ((FloatStamp) stamp).getBits() == 32 ? VMULSS : VMULSD;
            return binaryRead(op, AVXSize.XMM, value, access);
        } else {
            return super.mulMemory(value, access);
        }
    }

    @Override
    public ComplexMatchResult andMemory(ValueNode value, LIRLowerableAccess access) {
        Stamp stamp = access.getAccessStamp(NodeView.DEFAULT);
        if (stamp instanceof SimdStamp simdStamp) {
            AVXSize size = fusedVectorOpSize(simdStamp, access, false);
            if (size == null) {
                return null;
            }
            VexRVMOp op = switch (scalarKind(simdStamp)) {
                case BYTE, WORD, DWORD, QWORD -> VexRVMOp.VPAND;
                case SINGLE, DOUBLE -> VexRVMOp.VANDPS;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(scalarKind(simdStamp));
            };
            return binaryRead(op, size, value, access);
        } else {
            return super.andMemory(value, access);
        }
    }

    @Override
    public ComplexMatchResult orMemory(ValueNode value, LIRLowerableAccess access) {
        Stamp stamp = access.getAccessStamp(NodeView.DEFAULT);
        if (stamp instanceof SimdStamp simdStamp) {
            AVXSize size = fusedVectorOpSize(simdStamp, access, false);
            if (size == null) {
                return null;
            }
            VexRVMOp op = switch (scalarKind(simdStamp)) {
                case BYTE, WORD, DWORD, QWORD -> VexRVMOp.VPOR;
                case SINGLE, DOUBLE -> VexRVMOp.VORPS;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(scalarKind(simdStamp));
            };
            return binaryRead(op, size, value, access);
        } else {
            return super.orMemory(value, access);
        }
    }

    @Override
    public ComplexMatchResult xorMemory(ValueNode value, LIRLowerableAccess access) {
        Stamp stamp = access.getAccessStamp(NodeView.DEFAULT);
        if (stamp instanceof SimdStamp simdStamp) {
            AVXSize size = fusedVectorOpSize(simdStamp, access, false);
            if (size == null) {
                return null;
            }
            VexRVMOp op = switch (scalarKind(simdStamp)) {
                case BYTE, WORD, DWORD, QWORD -> VexRVMOp.VPXOR;
                case SINGLE, DOUBLE -> VexRVMOp.VXORPS;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(scalarKind(simdStamp));
            };
            return binaryRead(op, size, value, access);
        } else {
            return super.xorMemory(value, access);
        }
    }

    @Override
    public ComplexMatchResult writeNarrow(WriteNode root, NarrowNode narrow) {
        Stamp stamp = narrow.stamp(NodeView.DEFAULT);
        if (stamp instanceof SimdStamp) {
            return null;
        } else {
            return super.writeNarrow(root, narrow);
        }
    }

    private ComplexMatchResult emitConvertMemory(PlatformKind kind, VexRMOp op, AVXSize size, LIRLowerableAccess access) {
        return builder -> {
            Variable result = getLIRGeneratorTool().newVariable(LIRKind.value(kind));
            AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
            append(new AVXUnaryMemoryOp(op.encoding(simdEncoding), size, result, address, getState(access)));
            return result;
        };
    }

    private ComplexMatchResult emitConvertMemory(PlatformKind kind, VexRVMOp op, LIRLowerableAccess access) {
        return builder -> {
            Variable result = getLIRGeneratorTool().newVariable(LIRKind.value(kind));
            AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
            append(new AVXConvertMemoryOp(op.encoding(simdEncoding), AVXSize.XMM, result, address, getState(access)));
            return result;
        };
    }

    @Override
    public ComplexMatchResult floatConvert(FloatConvertNode root, LIRLowerableAccess access) {
        if (root.stamp(NodeView.DEFAULT) instanceof SimdStamp simdStamp) {
            if (root.getFloatConvert().getCategory().equals(FloatConvertCategory.FloatingPointToInteger)) {
                return null;
            }
            AVXSize size = fusedVectorOpSize(simdStamp, access, true);
            if (size == null) {
                return null;
            }
            VexRMOp op = switch (root.getFloatConvert()) {
                case I2F -> VexRMOp.VCVTDQ2PS;
                case I2D -> VexRMOp.VCVTDQ2PD;
                case L2F -> VexRMOp.EVCVTQQ2PS;
                case L2D -> VexRMOp.EVCVTQQ2PD;
                case F2D -> VexRMOp.VCVTPS2PD;
                case D2F -> VexRMOp.VCVTPD2PS;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(root.getFloatConvert());
            };
            return emitConvertMemory(getLIRGeneratorTool().getLIRKind(simdStamp).getPlatformKind(), op, size, access);
        } else if (root.getFloatConvert().getCategory().equals(FloatConvertCategory.FloatingPointToInteger) && (root.inputCanBeNaN() || root.canOverflow())) {
            /*
             * We need to fix up the result of the conversion, the input should be in a register.
             */
            return null;
        } else {
            boolean avx512 = simdEncoding == AMD64SIMDInstructionEncoding.EVEX;
            return switch (root.getFloatConvert()) {
                case D2F -> emitConvertMemory(AMD64Kind.SINGLE, VCVTSD2SS, access);
                case D2I -> emitConvertMemory(AMD64Kind.DWORD, VCVTTSD2SI, AVXSize.XMM, access);
                case D2UI -> avx512 ? emitConvertMemory(AMD64Kind.DWORD, EVCVTTSD2USI, AVXSize.XMM, access) : null;
                case D2L -> emitConvertMemory(AMD64Kind.QWORD, VCVTTSD2SQ, AVXSize.XMM, access);
                case D2UL -> avx512 ? emitConvertMemory(AMD64Kind.QWORD, EVCVTTSD2USQ, AVXSize.XMM, access) : null;
                case F2D -> emitConvertMemory(AMD64Kind.DOUBLE, VCVTSS2SD, access);
                case F2I -> emitConvertMemory(AMD64Kind.DWORD, VCVTTSS2SI, AVXSize.XMM, access);
                case F2UI -> avx512 ? emitConvertMemory(AMD64Kind.DWORD, EVCVTTSS2USI, AVXSize.XMM, access) : null;
                case F2L -> emitConvertMemory(AMD64Kind.QWORD, VCVTTSS2SQ, AVXSize.XMM, access);
                case F2UL -> avx512 ? emitConvertMemory(AMD64Kind.QWORD, EVCVTTSS2USQ, AVXSize.XMM, access) : null;
                case I2D -> emitConvertMemory(AMD64Kind.DOUBLE, VCVTSI2SD, access);
                case I2F -> emitConvertMemory(AMD64Kind.SINGLE, VCVTSI2SS, access);
                case L2D -> emitConvertMemory(AMD64Kind.DOUBLE, VCVTSQ2SD, access);
                case UL2D -> avx512 ? emitConvertMemory(AMD64Kind.DOUBLE, EVCVTUSQ2SD, access) : null;
                case L2F -> emitConvertMemory(AMD64Kind.SINGLE, VCVTSQ2SS, access);
                case UL2F -> avx512 ? emitConvertMemory(AMD64Kind.SINGLE, EVCVTUSQ2SS, access) : null;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(root.getFloatConvert()); // ExcludeFromJacocoGeneratedReport
            };
        }
    }

    @Override
    public ComplexMatchResult zeroExtend(ZeroExtendNode root, LIRLowerableAccess access) {
        if (root.stamp(NodeView.DEFAULT) instanceof SimdStamp simdStamp) {
            AVXSize size = fusedVectorOpSize(simdStamp, access, true);
            if (size == null) {
                return null;
            }
            int inputBytes = getMemoryKind(access).getScalar().getSizeInBytes();
            AMD64Kind outputKind = (AMD64Kind) getLIRGeneratorTool().getLIRKind(simdStamp).getPlatformKind();
            int outputBytes = outputKind.getScalar().getSizeInBytes();
            VexRMOp op;
            if (inputBytes == Byte.BYTES && outputBytes == Short.BYTES) {
                op = VexRMOp.VPMOVZXBW;
            } else if (inputBytes == Byte.BYTES && outputBytes == Integer.BYTES) {
                op = VexRMOp.VPMOVZXBD;
            } else if (inputBytes == Byte.BYTES && outputBytes == Long.BYTES) {
                op = VexRMOp.VPMOVZXBQ;
            } else if (inputBytes == Short.BYTES && outputBytes == Integer.BYTES) {
                op = VexRMOp.VPMOVZXWD;
            } else if (inputBytes == Short.BYTES && outputBytes == Long.BYTES) {
                op = VexRMOp.VPMOVZXWQ;
            } else if (inputBytes == Integer.BYTES && outputBytes == Long.BYTES) {
                op = VexRMOp.VPMOVZXDQ;
            } else {
                throw GraalError.shouldNotReachHere(simdStamp + " - " + access);
            }
            return emitConvertMemory(outputKind, op, size, access);
        } else {
            return super.zeroExtend(root, access);
        }
    }

    @Override
    public ComplexMatchResult signExtend(SignExtendNode root, LIRLowerableAccess access) {
        if (root.stamp(NodeView.DEFAULT) instanceof SimdStamp simdStamp) {
            AVXSize size = fusedVectorOpSize(simdStamp, access, true);
            if (size == null) {
                return null;
            }
            int inputBytes = getMemoryKind(access).getScalar().getSizeInBytes();
            AMD64Kind outputKind = (AMD64Kind) getLIRGeneratorTool().getLIRKind(simdStamp).getPlatformKind();
            int outputBytes = outputKind.getScalar().getSizeInBytes();
            VexRMOp op;
            if (inputBytes == Byte.BYTES && outputBytes == Short.BYTES) {
                op = VexRMOp.VPMOVSXBW;
            } else if (inputBytes == Byte.BYTES && outputBytes == Integer.BYTES) {
                op = VexRMOp.VPMOVSXBD;
            } else if (inputBytes == Byte.BYTES && outputBytes == Long.BYTES) {
                op = VexRMOp.VPMOVSXBQ;
            } else if (inputBytes == Short.BYTES && outputBytes == Integer.BYTES) {
                op = VexRMOp.VPMOVSXWD;
            } else if (inputBytes == Short.BYTES && outputBytes == Long.BYTES) {
                op = VexRMOp.VPMOVSXWQ;
            } else if (inputBytes == Integer.BYTES && outputBytes == Long.BYTES) {
                op = VexRMOp.VPMOVSXDQ;
            } else {
                throw GraalError.shouldNotReachHere(simdStamp + " - " + access);
            }
            return emitConvertMemory(outputKind, op, size, access);
        } else {
            return super.signExtend(root, access);
        }
    }

    @Override
    public ComplexMatchResult narrowRead(NarrowNode root, LIRLowerableAccess access) {
        if (access.getAccessStamp(NodeView.DEFAULT) instanceof SimdStamp) {
            // Vector narrows are MR ops
            return null;
        } else {
            return super.narrowRead(root, access);
        }
    }

    @Override
    public ComplexMatchResult signExtendNarrowRead(SignExtendNode root, NarrowNode narrow, LIRLowerableAccess access) {
        if (access.getAccessStamp(NodeView.DEFAULT) instanceof SimdStamp) {
            return null;
        } else {
            return super.signExtendNarrowRead(root, narrow, access);
        }
    }

    @Override
    public ComplexMatchResult rotateLeftConstant(LeftShiftNode lshift, UnsignedRightShiftNode rshift) {
        if (lshift.stamp(NodeView.DEFAULT) instanceof SimdStamp) {
            return null;
        } else {
            return super.rotateLeftConstant(lshift, rshift);
        }
    }

    @Override
    public ComplexMatchResult rotateLeftVariable(ValueNode value, ValueNode shiftAmount, ConstantNode delta) {
        if (value.stamp(NodeView.DEFAULT) instanceof SimdStamp) {
            return null;
        } else {
            return super.rotateLeftVariable(value, shiftAmount, delta);
        }
    }

    @Override
    public ComplexMatchResult rotateRightVariable(ValueNode value, ConstantNode delta, ValueNode shiftAmount) {
        if (value.stamp(NodeView.DEFAULT) instanceof SimdStamp) {
            return null;
        } else {
            return super.rotateRightVariable(value, delta, shiftAmount);
        }
    }

    @Override
    public ComplexMatchResult testBitAndBranch(IfNode root, ValueNode value, ConstantNode a) {
        if (value.stamp(NodeView.DEFAULT) instanceof SimdStamp) {
            return null;
        } else {
            return super.testBitAndBranch(root, value, a);
        }
    }

}
