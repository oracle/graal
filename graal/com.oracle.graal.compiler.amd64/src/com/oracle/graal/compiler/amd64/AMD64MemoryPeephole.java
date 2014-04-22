/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.lir.amd64.AMD64Arithmetic.*;
import static com.oracle.graal.nodes.ConstantNode.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public class AMD64MemoryPeephole implements MemoryArithmeticLIRLowerer {
    protected final AMD64NodeLIRBuilder gen;
    protected List<ValueNode> deferredNodes;

    protected AMD64MemoryPeephole(AMD64NodeLIRBuilder gen) {
        this.gen = gen;
    }

    public Value setResult(ValueNode x, Value operand) {
        return gen.setResult(x, operand);
    }

    @Override
    public boolean memoryPeephole(Access access, MemoryArithmeticLIRLowerable operation, List<ValueNode> deferred) {
        this.deferredNodes = deferred;
        boolean result = operation.generate(this, access);
        if (result) {
            Debug.log("merge %s %s with %1s %s %s", access, access.asNode().stamp(), operation, result, access.asNode().graph().method());
        } else {
            Debug.log("can't merge %s %s with %1s", access, access.asNode().stamp(), operation);
        }
        this.deferredNodes = null;
        return result;
    }

    protected LIRFrameState getState(Access access) {
        if (access instanceof DeoptimizingNode) {
            return gen.getLIRGenerator().state((DeoptimizingNode) access);
        }
        return null;
    }

    protected Kind getMemoryKind(Access access) {
        return (Kind) gen.getLIRGenerator().getPlatformKind(access.asNode().stamp());
    }

    protected AMD64AddressValue makeAddress(Access access) {
        return (AMD64AddressValue) access.accessLocation().generateAddress(gen, gen.getLIRGeneratorTool(), gen.operand(access.object()));
    }

    protected Value emitBinaryMemory(AMD64Arithmetic op, boolean commutative, ValueNode x, ValueNode y, Access access) {
        ValueNode other = x;
        if (uncast(other) == access) {
            if (commutative) {
                other = y;
            } else {
                return null;
            }
        }
        ensureEvaluated(other);
        return gen.getLIRGenerator().emitBinaryMemory(op, getMemoryKind(access), gen.getLIRGeneratorTool().asAllocatable(gen.operand(other)), makeAddress(access), getState(access));
    }

    /**
     * Constants with multiple users need to be evaluated in the right location so that later users
     * can pick up the operand. Make sure that happens when it needs to.
     */
    protected void ensureEvaluated(ValueNode node) {
        evaluateDeferred(node);
        evaluateDeferred();
    }

    protected void evaluateDeferred(ValueNode node) {
        // Ensure the other input value has a generated value.
        if (ConstantNodeRecordsUsages) {
            if (!gen.hasOperand(node)) {
                assert node instanceof ConstantNode : node;
                ((ConstantNode) node).generate(gen);
            }
        }
    }

    protected void evaluateDeferred() {
        if (deferredNodes != null) {
            for (ValueNode node : deferredNodes) {
                evaluateDeferred(node);
            }
        }
    }

    protected Value emitConvert2MemoryOp(PlatformKind kind, AMD64Arithmetic op, Access access) {
        AMD64AddressValue address = makeAddress(access);
        LIRFrameState state = getState(access);
        evaluateDeferred();
        return gen.getLIRGenerator().emitConvert2MemoryOp(kind, op, address, state);
    }

    @Override
    public Value emitAddMemory(ValueNode x, ValueNode y, Access access) {
        switch (getMemoryKind(access)) {
            case Int:
                return emitBinaryMemory(IADD, true, x, y, access);
            case Long:
                return emitBinaryMemory(LADD, true, x, y, access);
            case Float:
                return emitBinaryMemory(FADD, true, x, y, access);
            case Double:
                return emitBinaryMemory(DADD, true, x, y, access);
            default:
                return null;
        }
    }

    @Override
    public Value emitSubMemory(ValueNode x, ValueNode y, Access access) {
        switch (getMemoryKind(access)) {
            case Int:
                return emitBinaryMemory(ISUB, false, x, y, access);
            case Long:
                return emitBinaryMemory(LSUB, false, x, y, access);
            case Float:
                return emitBinaryMemory(FSUB, false, x, y, access);
            case Double:
                return emitBinaryMemory(DSUB, false, x, y, access);
            default:
                return null;
        }
    }

    @Override
    public Value emitMulMemory(ValueNode x, ValueNode y, Access access) {
        switch (getMemoryKind(access)) {
            case Int:
                return emitBinaryMemory(IMUL, true, x, y, access);
            case Long:
                return emitBinaryMemory(LMUL, true, x, y, access);
            case Float:
                return emitBinaryMemory(FMUL, true, x, y, access);
            case Double:
                return emitBinaryMemory(DMUL, true, x, y, access);
            default:
                return null;
        }
    }

    @Override
    public Value emitDivMemory(ValueNode x, ValueNode y, Access access) {
        return null;
    }

    @Override
    public Value emitRemMemory(ValueNode x, ValueNode y, Access access) {
        return null;
    }

    @Override
    public Value emitAndMemory(ValueNode x, ValueNode y, Access access) {
        Kind kind = getMemoryKind(access);
        switch (kind) {
            case Int:
                return emitBinaryMemory(IAND, true, x, y, access);
            case Long:
                return emitBinaryMemory(LAND, true, x, y, access);
            case Short: {
                ValueNode other = selectOtherInput(x, y, access);
                Constant constant = other instanceof ConstantNode ? ((ConstantNode) other).asConstant() : null;
                if (constant != null && constant.asInt() == IntegerStamp.defaultMask(kind.getBitCount())) {
                    // Convert to unsigned load
                    ensureEvaluated(other);
                    return emitZeroExtendMemory(16, 32, access);
                }
                return null;
            }
            case Byte: {
                if (OptFoldMemory.getValue()) {
                    return null;
                }
                ValueNode other = selectOtherInput(x, y, access);
                Constant constant = other instanceof ConstantNode ? ((ConstantNode) other).asConstant() : null;
                if (constant != null && constant.asInt() == IntegerStamp.defaultMask(kind.getBitCount())) {
                    // Convert to unsigned load
                    ensureEvaluated(other);
                    return emitConvert2MemoryOp(Kind.Int, MOV_B2UI, access);
                }
                return null;
            }

            default:
                return null;
        }
    }

    @Override
    public Value emitOrMemory(ValueNode x, ValueNode y, Access access) {
        switch (getMemoryKind(access)) {
            case Int:
                return emitBinaryMemory(IOR, true, x, y, access);
            case Long:
                return emitBinaryMemory(LOR, true, x, y, access);
            default:
                return null;
        }
    }

    @Override
    public Value emitXorMemory(ValueNode x, ValueNode y, Access access) {
        switch (getMemoryKind(access)) {
            case Int:
                return emitBinaryMemory(IXOR, true, x, y, access);
            case Long:
                return emitBinaryMemory(LXOR, true, x, y, access);
            default:
                return null;
        }
    }

    @Override
    public Value emitReinterpretMemory(Stamp stamp, Access access) {
        PlatformKind to = gen.getLIRGenerator().getPlatformKind(stamp);
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

    @Override
    public Value emitFloatConvertMemory(FloatConvert op, Access access) {
        switch (op) {
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

    @Override
    public Value emitSignExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return null;
        } else if (toBits > 32) {
            // sign extend to 64 bits
            switch (fromBits) {
                case 8:
                    return emitConvert2MemoryOp(Kind.Long, B2L, access);
                case 16:
                    return emitConvert2MemoryOp(Kind.Long, S2L, access);
                case 32:
                    return emitConvert2MemoryOp(Kind.Long, I2L, access);
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {

            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    return emitConvert2MemoryOp(Kind.Int, B2I, access);
                case 16:
                    return emitConvert2MemoryOp(Kind.Int, S2I, access);
                case 32:
                    return null;
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        }
    }

    @Override
    public Value emitNarrowMemory(int resultBits, Access access) {
        // TODO
        return null;
    }

    @Override
    public Value emitZeroExtendMemory(int fromBits, int toBits, Access access) {
        assert fromBits != toBits;
        Kind memoryKind = getMemoryKind(access);
        if (memoryKind.getBitCount() != fromBits && !memoryKind.isUnsigned()) {
            // The memory being read from is signed and smaller than the result size so
            // this is a sign extension to inputBits followed by a zero extension to resultBits
            // which can't be expressed in a memory operation.
            return null;
        }
        if (memoryKind == Kind.Short) {
            memoryKind = Kind.Char;
        }
        evaluateDeferred();
        return gen.getLIRGenerator().emitZeroExtendMemory(memoryKind, toBits, makeAddress(access), getState(access));
    }

    public boolean emitIfMemory(IfNode x, Access access) {
        return emitBranchMemory(x.condition(), access, gen.getLIRBlock(x.trueSuccessor()), gen.getLIRBlock(x.falseSuccessor()), x.probability(x.trueSuccessor()));
    }

    private boolean emitBranchMemory(LogicNode node, Access access, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        if (node instanceof IsNullNode) {
            // can't do anything interesting.
            return false;
        } else if (node instanceof CompareNode) {
            CompareNode compare = (CompareNode) node;
            return emitCompareBranchMemory(compare, access, trueSuccessor, falseSuccessor, trueSuccessorProbability);
        } else if (node instanceof LogicConstantNode) {
            return false;
        } else if (node instanceof IntegerTestNode) {
            return emitIntegerTestBranchMemory((IntegerTestNode) node, access, trueSuccessor, falseSuccessor, trueSuccessorProbability);
        } else {
            throw GraalInternalError.unimplemented(node.toString());
        }
    }

    public boolean emitCompareBranchMemory(CompareNode compare, Access access, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        return emitCompareBranchMemory(compare.x(), compare.y(), access, compare.condition(), compare.unorderedIsTrue(), trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    public boolean emitIntegerTestBranchMemory(IntegerTestNode test, Access access, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability) {
        return emitIntegerTestBranchMemory(test.x(), test.y(), access, trueSuccessor, falseSuccessor, trueSuccessorProbability);
    }

    private boolean emitIntegerTestBranchMemory(ValueNode left, ValueNode right, Access access, LabelRef trueLabel, LabelRef falseLabel, double trueLabelProbability) {
        ValueNode other = selectOtherInput(left, right, access);
        Kind kind = getMemoryKind(access);
        if (other.isConstant()) {
            if (kind != kind.getStackKind()) {
                return false;
            }
            Constant constant = other.asConstant();
            if (kind == Kind.Long && !NumUtil.isInt(constant.asLong())) {
                // Only imm32 as long
                return false;
            }
            ensureEvaluated(other);
            gen.append(new AMD64TestMemoryOp(kind, makeAddress(access), constant, getState(access)));
        } else {
            evaluateDeferred();
            gen.append(new AMD64TestMemoryOp(kind, makeAddress(access), gen.operand(other), getState(access)));
        }

        gen.append(new BranchOp(Condition.EQ, trueLabel, falseLabel, trueLabelProbability));
        return true;
    }

    /**
     * @return the input which is not equal to access, accounting for possible UnsafeCastNodes.
     */
    protected ValueNode selectOtherInput(ValueNode left, ValueNode right, Access access) {
        assert uncast(left) == access || uncast(right) == access;
        return uncast(left) == access ? right : left;
    }

    protected ValueNode uncast(ValueNode value) {
        if (value instanceof UnsafeCastNode) {
            UnsafeCastNode cast = (UnsafeCastNode) value;
            return cast.getOriginalNode();
        }
        return value;
    }

    protected boolean emitCompareBranchMemory(ValueNode left, ValueNode right, Access access, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        ValueNode other = selectOtherInput(left, right, access);
        Kind kind = getMemoryKind(access);
        boolean mirrored = false;

        if (other.isConstant()) {
            Constant constant = other.asConstant();
            if (kind == Kind.Long && !NumUtil.isInt(constant.asLong())) {
                // Only imm32 as long
                return false;
            }
            if (kind.isNumericFloat()) {
                Debug.log("Skipping constant compares for float kinds");
                return false;
            }
            if (kind == Kind.Object) {
                if (!constant.isNull()) {
                    Debug.log("Skipping constant compares for Object kinds");
                    return false;
                }
            }
            ensureEvaluated(other);
            gen.getLIRGenerator().emitCompareMemoryConOp(kind, makeAddress(access), constant, getState(access));
            mirrored = uncast(right) == access;
        } else {
            if (kind == Kind.Object) {
                // Can't compare against objects since they require encode/decode
                Debug.log("Skipping compares for Object kinds");
                return false;
            }

            evaluateDeferred();
            gen.getLIRGenerator().emitCompareRegMemoryOp(kind, gen.operand(other), makeAddress(access), getState(access));
            mirrored = uncast(left) == access;
        }

        Condition finalCondition = mirrored ? cond.mirror() : cond;
        switch (kind.getStackKind()) {
            case Long:
            case Int:
            case Object:
                gen.append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
                return true;
            case Float:
            case Double:
                gen.append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
                return true;
            default:
                throw GraalInternalError.shouldNotReachHere("" + kind.getStackKind());
        }
    }
}
