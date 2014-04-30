/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.amd64;

import static com.oracle.graal.lir.amd64.AMD64Arithmetic.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.match.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;

public abstract class AMD64NodeLIRBuilder extends NodeLIRBuilder {

    public AMD64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen) {
        super(graph, gen);
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        AllocatableValue targetAddress = AMD64.rax.asValue();
        gen.emitMove(targetAddress, operand(callTarget.computedAddress()));
        append(new AMD64Call.IndirectCallOp(callTarget.target(), result, parameters, temps, targetAddress, callState));
    }

    @Override
    public void emitNullCheck(ValueNode v, DeoptimizingNode deopt) {
        assert v.getKind() == Kind.Object : v + " - " + v.stamp() + " @ " + deopt;
        append(new AMD64Move.NullCheckOp(gen.load(operand(v)), state(deopt)));
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        if ((valueNode instanceof IntegerDivNode) || (valueNode instanceof IntegerRemNode)) {
            FixedBinaryNode divRem = (FixedBinaryNode) valueNode;
            FixedNode node = divRem.next();
            while (node instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) node;
                if (((fixedWithNextNode instanceof IntegerDivNode) || (fixedWithNextNode instanceof IntegerRemNode)) && fixedWithNextNode.getClass() != divRem.getClass()) {
                    FixedBinaryNode otherDivRem = (FixedBinaryNode) fixedWithNextNode;
                    if (otherDivRem.x() == divRem.x() && otherDivRem.y() == divRem.y() && !hasOperand(otherDivRem)) {
                        Value[] results = ((AMD64LIRGenerator) gen).emitIntegerDivRem(operand(divRem.x()), operand(divRem.y()), state((DeoptimizingNode) valueNode));
                        if (divRem instanceof IntegerDivNode) {
                            setResult(divRem, results[0]);
                            setResult(otherDivRem, results[1]);
                        } else {
                            setResult(divRem, results[1]);
                            setResult(otherDivRem, results[0]);
                        }
                        return true;
                    }
                }
                node = fixedWithNextNode.next();
            }
        }
        return false;
    }

    protected LIRFrameState getState(Access access) {
        if (access instanceof DeoptimizingNode) {
            return state((DeoptimizingNode) access);
        }
        return null;
    }

    protected Kind getMemoryKind(Access access) {
        return (Kind) gen.getPlatformKind(access.asNode().stamp());
    }

    protected AMD64AddressValue makeAddress(Access access) {
        return (AMD64AddressValue) access.accessLocation().generateAddress(this, gen, operand(access.object()));
    }

    protected ValueNode uncast(ValueNode value) {
        if (value instanceof UnsafeCastNode) {
            UnsafeCastNode cast = (UnsafeCastNode) value;
            return cast.getOriginalNode();
        }
        return value;
    }

    protected ComplexMatchResult emitCompareBranchMemory(IfNode ifNode, CompareNode compare, ValueNode value, Access access) {
        Condition cond = compare.condition();
        Kind kind = getMemoryKind(access);

        if (value.isConstant()) {
            Constant constant = value.asConstant();
            if (kind == Kind.Long && !NumUtil.isInt(constant.asLong())) {
                // Only imm32 as long
                return null;
            }
            if (kind.isNumericFloat()) {
                Debug.log("Skipping constant compares for float kinds");
                return null;
            }
            if (kind == Kind.Object) {
                if (!constant.isNull()) {
                    Debug.log("Skipping constant compares for Object kinds");
                    return null;
                }
            }
        } else {
            if (kind == Kind.Object) {
                // Can't compare against objects since they require encode/decode
                Debug.log("Skipping compares for Object kinds");
                return null;
            }
        }

        PlatformKind cmpKind = gen.getPlatformKind(compare.x().stamp());
        if (cmpKind instanceof Kind) {
            // emitCompareBranchMemory expects the memory on the right, so mirror the condition if
            // that's not true. It might be mirrored again the actual compare is emitted but that's
            // ok.
            Condition finalCondition = uncast(compare.x()) == access ? cond.mirror() : cond;
            return new ComplexMatchResult() {
                public Value evaluate(NodeLIRBuilder builder) {
                    LabelRef trueLabel = getLIRBlock(ifNode.trueSuccessor());
                    LabelRef falseLabel = getLIRBlock(ifNode.falseSuccessor());
                    boolean unorderedIsTrue = compare.unorderedIsTrue();
                    double trueLabelProbability = ifNode.probability(ifNode.trueSuccessor());
                    Value other;
                    if (value.isConstant()) {
                        other = value.asConstant();
                    } else {
                        other = operand(value);
                    }

                    getLIRGeneratorTool().emitCompareBranchMemory((Kind) cmpKind, other, makeAddress(access), getState(access), finalCondition, unorderedIsTrue, trueLabel, falseLabel,
                                    trueLabelProbability);
                    return null;
                }
            };
        }
        return null;

    }

    private ComplexMatchResult emitIntegerTestBranchMemory(IfNode x, ValueNode value, Access access) {
        LabelRef trueLabel = getLIRBlock(x.trueSuccessor());
        LabelRef falseLabel = getLIRBlock(x.falseSuccessor());
        double trueLabelProbability = x.probability(x.trueSuccessor());
        Kind kind = getMemoryKind(access);
        if (value.isConstant()) {
            if (kind != kind.getStackKind()) {
                return null;
            }
            Constant constant = value.asConstant();
            if (kind == Kind.Long && !NumUtil.isInt(constant.asLong())) {
                // Only imm32 as long
                return null;
            }
            return new ComplexMatchResult() {
                public Value evaluate(NodeLIRBuilder builder) {
                    gen.append(new AMD64TestMemoryOp(kind, makeAddress(access), constant, getState(access)));
                    gen.append(new BranchOp(Condition.EQ, trueLabel, falseLabel, trueLabelProbability));
                    return null;
                }
            };
        } else {
            return new ComplexMatchResult() {
                public Value evaluate(NodeLIRBuilder builder) {
                    gen.append(new AMD64TestMemoryOp(kind, makeAddress(access), operand(value), getState(access)));
                    gen.append(new BranchOp(Condition.EQ, trueLabel, falseLabel, trueLabelProbability));
                    return null;
                }
            };
        }
    }

    protected Value emitConvert2MemoryOp(PlatformKind kind, AMD64Arithmetic op, Access access) {
        AMD64AddressValue address = makeAddress(access);
        LIRFrameState state = getState(access);
        return getLIRGeneratorTool().emitConvert2MemoryOp(kind, op, address, state);
    }

    private Value emitFloatConvertMemory(FloatConvertNode op, Access access) {
        switch (op.getOp()) {
            case D2F:
                return emitConvert2MemoryOp(Kind.Float, D2F, access);
            case D2I:
                return emitConvert2MemoryOp(Kind.Int, D2I, access);
            case D2L:
                return emitConvert2MemoryOp(Kind.Long, D2L, access);
            case F2D:
                return emitConvert2MemoryOp(Kind.Double, F2D, access);
            case F2I:
                return emitConvert2MemoryOp(Kind.Int, F2I, access);
            case F2L:
                return emitConvert2MemoryOp(Kind.Long, F2L, access);
            case I2D:
                return emitConvert2MemoryOp(Kind.Double, I2D, access);
            case I2F:
                return emitConvert2MemoryOp(Kind.Float, I2F, access);
            case L2D:
                return emitConvert2MemoryOp(Kind.Double, L2D, access);
            case L2F:
                return emitConvert2MemoryOp(Kind.Float, L2F, access);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private ComplexMatchResult emitSignExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        Kind kind = null;
        AMD64Arithmetic op = null;
        if (fromBits == toBits) {
            return null;
        } else if (toBits > 32) {
            kind = Kind.Long;
            // sign extend to 64 bits
            switch (fromBits) {
                case 8:
                    op = B2L;
                    break;
                case 16:
                    op = S2L;
                    break;
                case 32:
                    op = I2L;
                    break;
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {
            kind = Kind.Int;
            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    op = B2I;
                    break;
                case 16:
                    op = S2I;
                    break;
                case 32:
                    return null;
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        }
        if (kind != null && op != null) {
            Kind localKind = kind;
            AMD64Arithmetic localOp = op;
            return new ComplexMatchResult() {
                public Value evaluate(NodeLIRBuilder builder) {
                    return emitConvert2MemoryOp(localKind, localOp, access);
                }
            };
        }
        return null;
    }

    private Value emitReinterpretMemory(PlatformKind to, Access access) {
        Kind from = getMemoryKind(access);
        assert to != from : "should have been eliminated";

        /*
         * Conversions between integer to floating point types require moves between CPU and FPU
         * registers.
         */
        switch ((Kind) to) {
            case Int:
                switch (from) {
                    case Float:
                        return emitConvert2MemoryOp(to, MOV_F2I, access);
                }
                break;
            case Long:
                switch (from) {
                    case Double:
                        return emitConvert2MemoryOp(to, MOV_D2L, access);
                }
                break;
            case Float:
                switch (from) {
                    case Int:
                        return emitConvert2MemoryOp(to, MOV_I2F, access);
                }
                break;
            case Double:
                switch (from) {
                    case Long:
                        return emitConvert2MemoryOp(to, MOV_L2D, access);
                }
                break;
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    static Object lock = new Object();

    private AMD64Arithmetic getOp(ValueNode operation, Access access) {
        Kind memoryKind = getMemoryKind(access);
        if (operation.getClass() == IntegerAddNode.class) {
            switch (memoryKind) {
                case Int:
                    return IADD;
                case Long:
                    return LADD;
            }
        } else if (operation.getClass() == FloatAddNode.class) {
            switch (memoryKind) {
                case Float:
                    return FADD;
                case Double:
                    return DADD;
            }
        } else if (operation.getClass() == AndNode.class) {
            switch (memoryKind) {
                case Int:
                    return IAND;
                case Long:
                    return LAND;
            }
        } else if (operation.getClass() == OrNode.class) {
            switch (memoryKind) {
                case Int:
                    return IOR;
                case Long:
                    return LOR;
            }
        } else if (operation.getClass() == XorNode.class) {
            switch (memoryKind) {
                case Int:
                    return IXOR;
                case Long:
                    return LXOR;
            }
        } else if (operation.getClass() == IntegerSubNode.class) {
            switch (memoryKind) {
                case Int:
                    return ISUB;
                case Long:
                    return LSUB;
            }
        } else if (operation.getClass() == FloatSubNode.class) {
            switch (memoryKind) {
                case Float:
                    return FSUB;
                case Double:
                    return DSUB;
            }
        } else if (operation.getClass() == IntegerMulNode.class) {
            switch (memoryKind) {
                case Int:
                    return IMUL;
                case Long:
                    return LMUL;
            }
        } else if (operation.getClass() == FloatMulNode.class) {
            switch (memoryKind) {
                case Float:
                    return FMUL;
                case Double:
                    return DMUL;
            }
        }
        return null;
    }

    @MatchRule("(If (IntegerTest=compare Read=access value))")
    @MatchRule("(If (IntegerTest=compare FloatingRead=access value))")
    public static class IfIntegerTest extends AMD64MatchGenerator {
        IfNode root;
        Access access;
        ValueNode value;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            return gen.emitIntegerTestBranchMemory(root, value, access);
        }
    }

    @MatchRule("(If (IntegerEquals=compare value Read=access))")
    @MatchRule("(If (IntegerLessThan=compare value Read=access))")
    @MatchRule("(If (IntegerBelowThan=compare value Read=access))")
    @MatchRule("(If (IntegerEquals=compare value FloatingRead=access))")
    @MatchRule("(If (IntegerLessThan=compare value FloatingRead=access))")
    @MatchRule("(If (IntegerBelowThan=compare value FloatingRead=access))")
    @MatchRule("(If (FloatEquals=compare value Read=access))")
    @MatchRule("(If (FloatEquals=compare value FloatingRead=access))")
    @MatchRule("(If (FloatLessThan=compare value Read=access))")
    @MatchRule("(If (FloatLessThan=compare value FloatingRead=access))")
    public static class IfCompareMemory extends AMD64MatchGenerator {
        IfNode root;
        Access access;
        ValueNode value;
        CompareNode compare;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            return gen.emitCompareBranchMemory(root, compare, value, access);
        }
    }

    @MatchRule("(Or (LeftShift=lshift value Constant) (UnsignedRightShift=rshift value Constant))")
    public static class RotateLeftConstant extends AMD64MatchGenerator {
        LeftShiftNode lshift;
        UnsignedRightShiftNode rshift;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            if ((lshift.getShiftAmountMask() & (lshift.y().asConstant().asInt() + rshift.y().asConstant().asInt())) == 0) {
                return builder -> gen.getLIRGeneratorTool().emitRol(gen.operand(lshift.x()), gen.operand(lshift.y()));
            }
            return null;
        }

    }

    @MatchRule("(Or (LeftShift=lshift value (IntegerSub Constant=delta shiftAmount)) (UnsignedRightShift value shiftAmount))")
    public static class RotateRightVariable extends AMD64MatchGenerator {
        ValueNode value;
        ValueNode shiftAmount;
        ConstantNode delta;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            if (delta.asConstant().asLong() == 0 || delta.asConstant().asLong() == 32) {
                return builder -> gen.getLIRGeneratorTool().emitRor(gen.operand(value), gen.operand(shiftAmount));
            }
            return null;
        }

    }

    @MatchRule("(Or (LeftShift value shiftAmount) (UnsignedRightShift value (IntegerSub Constant=delta shiftAmount)))")
    public static class RotateLeftVariable extends AMD64MatchGenerator {
        ValueNode value;
        ValueNode shiftAmount;
        ConstantNode delta;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            if (delta.asConstant().asLong() == 0 || delta.asConstant().asLong() == 32) {
                return builder -> gen.getLIRGeneratorTool().emitRol(gen.operand(value), gen.operand(shiftAmount));
            }
            return null;
        }
    }

    @MatchRule("(IntegerAdd value Read=access)")
    @MatchRule("(IntegerSub value Read=access)")
    @MatchRule("(IntegerMul value Read=access)")
    @MatchRule("(FloatAdd value Read=access)")
    @MatchRule("(FloatSub value Read=access)")
    @MatchRule("(FloatMul value Read=access)")
    @MatchRule("(Or value Read=access)")
    @MatchRule("(Xor value Read=access)")
    @MatchRule("(And value Read=access)")
    @MatchRule("(IntegerAdd value FloatingRead=access)")
    @MatchRule("(IntegerSub value FloatingRead=access)")
    @MatchRule("(IntegerMul value FloatingRead=access)")
    @MatchRule("(FloatAdd value FloatingRead=access)")
    @MatchRule("(FloatSub value FloatingRead=access)")
    @MatchRule("(FloatMul value FloatingRead=access)")
    @MatchRule("(Or value FloatingRead=access)")
    @MatchRule("(Xor value FloatingRead=access)")
    @MatchRule("(And value FloatingRead=access)")
    public static class BinaryRead extends AMD64MatchGenerator {
        BinaryNode root;
        Access access;
        ValueNode value;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            AMD64Arithmetic op = gen.getOp(root, access);
            if (op != null) {
                return builder -> gen.getLIRGeneratorTool().emitBinaryMemory(op, gen.getMemoryKind(access), gen.getLIRGeneratorTool().asAllocatable(gen.operand(value)), gen.makeAddress(access),
                                gen.getState(access));
            }
            return null;
        }
    }

    @MatchRule("(Write Narrow=narrow value)")
    public static class WriteNarrow extends AMD64MatchGenerator {
        WriteNode root;
        NarrowNode narrow;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            return new ComplexMatchResult() {
                @Override
                public Value evaluate(NodeLIRBuilder builder) {
                    PlatformKind writeKind = gen.getLIRGeneratorTool().getPlatformKind(root.value().stamp());
                    Value address = root.location().generateAddress(builder, gen.getLIRGeneratorTool(), gen.operand(root.object()));
                    Value v = gen.operand(narrow.getInput());
                    gen.getLIRGeneratorTool().emitStore(writeKind, address, v, gen.state(root));
                    return null;
                }
            };
        }
    }

    @MatchRule("(SignExtend Read=access)")
    @MatchRule("(SignExtend FloatingRead=access)")
    public static class SignExtend extends AMD64MatchGenerator {
        Access access;
        SignExtendNode root;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            return gen.emitSignExtendMemory(access, root.getInputBits(), root.getResultBits());
        }
    }

    static abstract class AMD64MatchGenerator implements MatchGenerator {
        public AMD64MatchGenerator() {
        }

        public ComplexMatchResult match(NodeLIRBuilder gen) {
            return match((AMD64NodeLIRBuilder) gen);
        }

        abstract public ComplexMatchResult match(AMD64NodeLIRBuilder gen);
    }

    @MatchRule("(ZeroExtend Read=access)")
    @MatchRule("(ZeroExtend FloatingRead=access)")
    public static class ZeroExtend extends AMD64MatchGenerator {
        Access access;
        ZeroExtendNode root;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            Kind memoryKind = gen.getMemoryKind(access);
            if (memoryKind.getBitCount() != root.getInputBits() && !memoryKind.isUnsigned()) {
                /*
                 * The memory being read from is signed and smaller than the result size so this is
                 * a sign extension to inputBits followed by a zero extension to resultBits which
                 * can't be expressed in a memory operation.
                 */
                return null;
            }
            return new ComplexMatchResult() {
                public Value evaluate(NodeLIRBuilder unused) {
                    return gen.getLIRGeneratorTool().emitZeroExtendMemory(memoryKind == Kind.Short ? Kind.Char : memoryKind, root.getResultBits(), gen.makeAddress(access), gen.getState(access));
                }
            };
        }
    }

    @MatchRule("(FloatConvert Read=access)")
    @MatchRule("(FloatConvert FloatingRead=access)")
    public static class FloatConvert extends AMD64MatchGenerator {
        Access access;
        FloatConvertNode root;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            return new ComplexMatchResult() {
                public Value evaluate(NodeLIRBuilder builder) {
                    return gen.emitFloatConvertMemory(root, access);
                }
            };
        }
    }

    @MatchRule("(Reinterpret Read=access)")
    @MatchRule("(Reinterpret FloatingRead=access)")
    public static class Reinterpret extends AMD64MatchGenerator {
        Access access;
        ReinterpretNode root;

        @Override
        public ComplexMatchResult match(AMD64NodeLIRBuilder gen) {
            return new ComplexMatchResult() {
                public Value evaluate(NodeLIRBuilder builder) {
                    PlatformKind kind = gen.getLIRGeneratorTool().getPlatformKind(root.stamp());
                    return gen.emitReinterpretMemory(kind, access);
                }
            };
        }
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        JavaType[] sig = new JavaType[node.arguments().size()];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = node.arguments().get(i).stamp().javaType(gen.getMetaAccess());
        }

        Value[] parameters = visitInvokeArguments(gen.getResult().getFrameMap().registerConfig.getCallingConvention(CallingConvention.Type.JavaCall, null, sig, gen.target(), false), node.arguments());
        append(new AMD64BreakpointOp(parameters));
    }

    @Override
    public void visitInfopointNode(InfopointNode i) {
        append(new InfopointOp(stateFor(i.getState()), i.reason));
    }

    @Override
    public AMD64LIRGenerator getLIRGeneratorTool() {
        return (AMD64LIRGenerator) gen;
    }
}
