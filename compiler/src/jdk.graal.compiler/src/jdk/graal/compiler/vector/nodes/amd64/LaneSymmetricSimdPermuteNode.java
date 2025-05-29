/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.vector.nodes.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.EVPSHUFD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.EVPSHUFHW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.EVPSHUFLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPSHUFD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPSHUFHW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPSHUFLW;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;

import java.util.EnumSet;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorShuffle;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.vector.lir.amd64.AMD64AVX512ArithmeticLIRGenerator;
import jdk.graal.compiler.vector.lir.amd64.AMD64VectorArithmeticLIRGenerator;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 * Node that reorders the elements of a vector - potentially duplicating or dropping elements.
 *
 * This AMD64 specific node maps to PSHUFLW / PSHUFHW / PSHUFD / QPERM and requires specific forms
 * of lane symmetry on the permutation indices to be functionally correct. See
 * {@code GeneralSimdPermuteNode} for the general case node that can handle arbitrary permutations.
 */
@NodeInfo(cycles = CYCLES_1, size = NodeSize.SIZE_1)
public class LaneSymmetricSimdPermuteNode extends UnaryNode implements VectorLIRLowerable {

    public static final NodeClass<LaneSymmetricSimdPermuteNode> TYPE = NodeClass.create(LaneSymmetricSimdPermuteNode.class);

    protected int[] destinationMapping;

    /**
     * Creates a new LaneSymmetricSimdPermuteNode.
     *
     * @param stamp the result type of this instruction
     * @param value the input vector
     * @param destinationMapping the mapping of destination indices back to source indices
     */
    public LaneSymmetricSimdPermuteNode(Stamp stamp, ValueNode value, int[] destinationMapping) {
        super(TYPE, stamp, value);
        this.destinationMapping = destinationMapping;
        Stamp elementStamp = ((SimdStamp) value.stamp(NodeView.DEFAULT)).getComponent(0);
        assert (elementStamp instanceof PrimitiveStamp) || elementStamp instanceof AbstractObjectStamp : "Unsupported element stamp " + elementStamp;
    }

    private static AllocatableValue buildWordPermuteMask(LIRGeneratorTool tool, AMD64Kind vectorKind, int mask) {
        assert tool instanceof AMD64AVX512ArithmeticLIRGenerator : "expected AMD64AVX512ArithmeticLIRGenerator for mask construction, got " + tool.getClass().getName();
        AMD64AVX512ArithmeticLIRGenerator arithmeticLIRGen = (AMD64AVX512ArithmeticLIRGenerator) tool;

        JavaConstant constMask;
        AMD64Kind maskKind;
        if (vectorKind == AMD64Kind.V128_WORD) {
            constMask = JavaConstant.forByte((byte) (mask & 0xff));
            maskKind = AMD64Kind.MASK8;
        } else if (vectorKind == AMD64Kind.V256_WORD) {
            constMask = JavaConstant.forShort((short) (~mask & 0xffff));
            maskKind = AMD64Kind.MASK16;
        } else if (vectorKind == AMD64Kind.V512_WORD) {
            constMask = JavaConstant.forInt(~mask);
            maskKind = AMD64Kind.MASK32;
        } else {
            throw GraalError.shouldNotReachHere("Unsupported platform kind for masked WORD shuffle"); // ExcludeFromJacocoGeneratedReport
        }

        return tool.asAllocatable(arithmeticLIRGen.emitConstOpmask(maskKind, constMask));
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        AMD64SIMDInstructionEncoding enc = ((AMD64VectorArithmeticLIRGenerator) gen).getSimdEncoding();

        Value vector = builder.operand(value);
        LIRGeneratorTool tool = builder.getLIRGeneratorTool();
        AMD64Kind vectorKind = (AMD64Kind) vector.getPlatformKind();
        AMD64Kind elementKind = vectorKind.getScalar();

        Variable result = tool.newVariable(tool.getLIRKind(stamp(NodeView.DEFAULT)));
        AMD64Assembler.VexRMIOp op;
        int[] mapping = destinationMapping;
        switch (elementKind) {
            case WORD: {
                Variable partialResult = null;
                if (destinationMapping.length > 4) {
                    int mask = 0;
                    byte immHigh = 0;
                    for (int idx = 7; idx > 3; --idx) {
                        if (idx < destinationMapping.length) {
                            if (destinationMapping[idx] > -1) {
                                immHigh = (byte) (((byte) (immHigh << 2)) | ((byte) (destinationMapping[idx] - 4)));
                                mask <<= 1;
                            } else {
                                immHigh <<= 2;
                                mask = (mask << 1) | 0x1;
                            }
                        }
                    }
                    partialResult = tool.newVariable(vector.getValueKind());
                    if (mask == 0) {
                        tool.append(new AMD64VectorShuffle.ShuffleWordOp(VPSHUFHW.encoding(enc), partialResult, tool.asAllocatable(vector), immHigh));
                    } else {
                        assert ((AMD64) tool.target().arch).getFeatures().contains(AMD64.CPUFeature.AVX512F) : "zeroing in lane symmetric permutation requires AVX-512F support";
                        AllocatableValue kReg = buildWordPermuteMask(tool, vectorKind, mask);
                        tool.append(new AMD64VectorShuffle.ShuffleWordOpWithMask(EVPSHUFHW, partialResult, tool.asAllocatable(vector), immHigh, kReg));
                    }
                }
                int mask = 0;
                byte immLow = 0;
                for (int idx = 3; idx > -1; --idx) {
                    if (idx < destinationMapping.length) {
                        if (destinationMapping[idx] > -1) {
                            immLow = (byte) (((byte) (immLow << 2)) | ((byte) (destinationMapping[idx])));
                            mask <<= 1;
                        } else {
                            immLow <<= 2;
                            mask = (mask << 1) | 0x1;
                        }
                    }
                }

                if (mask == 0) {
                    tool.append(new AMD64VectorShuffle.ShuffleWordOp(VPSHUFLW.encoding(enc), result, partialResult != null ? partialResult : tool.asAllocatable(vector), immLow));
                } else {
                    assert ((AMD64) tool.target().arch).getFeatures().contains(AMD64.CPUFeature.AVX512F) : "zeroing in lane symmetric permutation requires AVX-512F support";
                    AllocatableValue kReg = buildWordPermuteMask(tool, vectorKind, mask);
                    tool.append(new AMD64VectorShuffle.ShuffleWordOpWithMask(EVPSHUFLW, result, partialResult != null ? partialResult : tool.asAllocatable(vector), immLow, kReg));
                }
                builder.setResult(this, result);
                return;
            }
            case QWORD:
            case DOUBLE:
                if (((AMD64) tool.target().arch).getFeatures().contains(AMD64.CPUFeature.AVX2) && (destinationMapping.length > 2 || vectorKind.getSizeInBytes() > 16)) {
                    AMD64Kind targetKind;
                    if (elementKind == AMD64Kind.QWORD) {
                        targetKind = destinationMapping.length > 4 ? AMD64Kind.V512_QWORD : AMD64Kind.V256_QWORD;
                    } else if (elementKind == AMD64Kind.DOUBLE) {
                        targetKind = destinationMapping.length > 4 ? AMD64Kind.V512_DOUBLE : AMD64Kind.V256_DOUBLE;
                    } else {
                        throw GraalError.shouldNotReachHere("Unhandled element kind"); // ExcludeFromJacocoGeneratedReport
                    }
                    if (vectorKind.getVectorLength() < destinationMapping.length) {
                        AllocatableValue widenedVector = tool.asAllocatable(tool.newVariable(vector.getValueKind().changeType(targetKind)));
                        AllocatableValue allocatableVector = tool.asAllocatable(vector);
                        tool.append(new AMD64VectorShuffle.Insert128Op(widenedVector, allocatableVector, allocatableVector, 0, enc));
                        vector = widenedVector;
                    }
                    op = VPERMQ.encoding(enc);
                } else {
                    assert vectorKind.getSizeInBytes() <= 16 : "VPSHUFD is not lane symmetric beyond 16 bytes";
                    mapping = new int[destinationMapping.length * 2];
                    for (int i = 0; i < destinationMapping.length; ++i) {
                        mapping[2 * i] = destinationMapping[i] * 2;
                        mapping[2 * i + 1] = destinationMapping[i] * 2 + 1;
                    }
                    op = VPSHUFD.encoding(enc);
                }
                break;
            case DWORD:
            case SINGLE:
                op = VPSHUFD.encoding(enc);
                break;
            default:
                throw GraalError.shouldNotReachHere("Unsupported elementKind" + elementKind); // ExcludeFromJacocoGeneratedReport
        }

        byte imm = 0;
        for (int idx = 3; idx >= 0; --idx) {
            if (idx < mapping.length) {
                if (mapping[idx] > -1) {
                    imm = (byte) (((byte) (imm << 2)) | ((byte) mapping[idx]));
                } else {
                    imm <<= 2;
                }
            }
        }

        int mask = 0;
        for (int idx = mapping.length - 1; idx >= 0; --idx) {
            if (mapping[idx] == -1) {
                mask = (mask << 1) | 0x1;
            } else {
                mask <<= 1;
            }
        }

        if (mask == 0) {
            tool.append(new AMD64VectorShuffle.ShuffleWordOp(op, result, tool.asAllocatable(vector), imm));
        } else {
            EnumSet<AMD64.CPUFeature> features = ((AMD64) tool.target().arch).getFeatures();
            GraalError.guarantee(features.contains(AMD64.CPUFeature.AVX512F), "zeroing in lane symmetric permutation requires AVX-512F support");
            JavaConstant constMask;
            AMD64Kind maskKind;
            if (op == EVPSHUFD && vectorKind.getSizeInBytes() == 512) {
                constMask = JavaConstant.forShort((short) (~mask & 0xffff));
                maskKind = AMD64Kind.MASK16;
            } else {
                constMask = JavaConstant.forByte((byte) (~mask & 0xff));
                /*
                 * If no byte-sized move is available, we can also move to a larger mask kind. This
                 * zero-extends, which is what we want.
                 */
                maskKind = features.contains(AMD64.CPUFeature.AVX512DQ) ? AMD64Kind.MASK8 : AMD64Kind.MASK16;
            }
            assert tool instanceof AMD64AVX512ArithmeticLIRGenerator : "expected AMD64AVX512ArithmeticLIRGenerator, got " + tool.getClass().getName();
            Value kReg = ((AMD64AVX512ArithmeticLIRGenerator) tool).emitConstOpmask(maskKind, constMask);
            tool.append(new AMD64VectorShuffle.ShuffleWordOpWithMask(op, result, tool.asAllocatable(vector), imm, tool.asAllocatable(kReg)));
        }
        builder.setResult(this, result);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant() && forValue.asConstant() instanceof SimdConstant) {
            SimdConstant simdConst = (SimdConstant) forValue.asConstant();
            Constant[] newComponents = new Constant[simdConst.getVectorLength()];
            for (int i = 0; i < newComponents.length; ++i) {
                newComponents[i] = simdConst.getValue(destinationMapping[i]);
            }
            return ConstantNode.forConstant(((SimdStamp) forValue.stamp(NodeView.DEFAULT)).permute(destinationMapping),
                            new SimdConstant(newComponents), null);
        }
        return this;
    }
}
