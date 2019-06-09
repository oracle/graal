package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprBitFlipNodeFactory.BitFlipNodeGen;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprCompareNode.Op;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNotNodeFactory.NotNodeGen;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprShortCircuitEvaluationNodeFactory.DebugExprLogicalAndNodeGen;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprShortCircuitEvaluationNodeFactory.DebugExprLogicalOrNodeGen;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

public final class DebugExprNodeFactory {
    private ContextReference<LLVMContext> contextReference;
    public final static DebugExprErrorNode noObjNode = DebugExprErrorNode.create("<cannot find expression>");
    public final static DebugExprErrorNode errorObjNode = DebugExprErrorNode.create("<cannot evaluate expression>");
    public final static DebugExpressionPair noObjPair = DebugExpressionPair.create(noObjNode, DebugExprType.getVoidType());
    public final static DebugExpressionPair errorObjPair = DebugExpressionPair.create(errorObjNode, DebugExprType.getVoidType());
    private Iterable<Scope> scopes;

    private DebugExprNodeFactory(ContextReference<LLVMContext> contextReference, Iterable<Scope> scopes) {
        this.contextReference = contextReference;
        this.scopes = scopes;
    }

    private static DebugExprNodeFactory INSTANCE = null;

    public static DebugExprNodeFactory getInstance(ContextReference<LLVMContext> contextReference, Iterable<Scope> scopes) {
        if (INSTANCE == null || contextReference != INSTANCE.contextReference) {
            INSTANCE = new DebugExprNodeFactory(contextReference, scopes);
        }
        return INSTANCE;
    }

    public DebugExpressionPair createArithmeticOp(ArithmeticOperation op, DebugExpressionPair left, DebugExpressionPair right) {
        /* null is passed as type, since a type check is done by the arithmetic node anyway */
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        DebugExpressionPair leftPair = createCastIfNecessary(left, commonType);
        DebugExpressionPair rightPair = createCastIfNecessary(right, commonType);
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public DebugExpressionPair createDivNode(DebugExpressionPair left, DebugExpressionPair right) {
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        DebugExpressionPair leftPair = createCastIfNecessary(left, commonType);
        DebugExpressionPair rightPair = createCastIfNecessary(right, commonType);
        ArithmeticOperation op = commonType.isUnsigned() ? ArithmeticOperation.UDIV : ArithmeticOperation.DIV;
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public DebugExpressionPair createRemNode(DebugExpressionPair left, DebugExpressionPair right) {
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        ArithmeticOperation op = commonType.isUnsigned() ? ArithmeticOperation.UREM : ArithmeticOperation.REM;
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public DebugExpressionPair createShiftLeft(DebugExpressionPair left, DebugExpressionPair right) {
        if (!right.getType().isIntegerType()) {
            return errorObjPair;
        } else if (!left.getType().isIntegerType()) {
            return errorObjPair;
        } else {
            LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(ArithmeticOperation.SHL, null, left.getNode(), right.getNode());
            return DebugExpressionPair.create(node, left.getType());
        }
    }

    public DebugExpressionPair createShiftRight(DebugExpressionPair left, DebugExpressionPair right) {
        if (!right.getType().isIntegerType()) {
            return errorObjPair;
        } else if (!left.getType().isIntegerType()) {
            return errorObjPair;
        } else {
            ArithmeticOperation op = left.getType().isUnsigned() ? ArithmeticOperation.LSHR : ArithmeticOperation.ASHR;
            LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, left.getNode(), right.getNode());
            return DebugExpressionPair.create(node, left.getType());
        }
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createTernaryNode(DebugExpressionPair condition, DebugExpressionPair thenNode, DebugExpressionPair elseNode) {
        if (condition.getType() != DebugExprType.getBoolType())
            return errorObjPair;
        LLVMExpressionNode node = new DebugExprTernaryNode(condition.getNode(), thenNode.getNode(), elseNode.getNode());
        return DebugExpressionPair.create(node, DebugExprType.commonType(thenNode.getType(), elseNode.getType()));
    }

    public DebugExpressionPair createUnaryOpNode(DebugExpressionPair pair, char unaryOp) {
        switch (unaryOp) {
            case '&':
            case '*':
                return errorObjPair;
            case '+':
                return pair;
            case '-':
                return createArithmeticOp(ArithmeticOperation.SUB, createIntegerConstant(0), pair);
            case '~':
                return DebugExpressionPair.create(BitFlipNodeGen.create(pair.getNode()), pair.getType());
            case '!':
                return DebugExpressionPair.create(NotNodeGen.create(pair.getNode()), pair.getType());
            default:
                return errorObjPair;
        }
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createVarNode(String name) {
        DebugExprVarNode node = new DebugExprVarNode(name, scopes);
        return DebugExpressionPair.create(node, node.getType());
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createSizeofNode(DebugExprType type) {
        LLVMExpressionNode node = new DebugExprSizeofNode(type);
        return DebugExpressionPair.create(node, DebugExprType.getIntType(32, true));
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createLogicalAndNode(DebugExpressionPair left, DebugExpressionPair right) {
        LLVMExpressionNode node = DebugExprLogicalAndNodeGen.create(left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createLogicalOrNode(DebugExpressionPair left, DebugExpressionPair right) {
        LLVMExpressionNode node = DebugExprLogicalOrNodeGen.create(left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
    }

    @SuppressWarnings("static-method")
    public LLVMExpressionNode createErrorNode(Object errorObj) {
        return DebugExprErrorNode.create(errorObj);
    }

    public DebugExpressionPair createCompareNode(DebugExpressionPair left, Op op, DebugExpressionPair right) {
        LLVMExpressionNode node = new DebugExprCompareNode(contextReference.get().getNodeFactory(), left.getNode(), op, right.getNode());
        return DebugExpressionPair.create(node, DebugExprType.getIntType(1, false));
    }

    public DebugExpressionPair createIntegerConstant(int value) {
        return createIntegerConstant(value, true);
    }

    public DebugExpressionPair createIntegerConstant(int value, boolean signed) {
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createSimpleConstantNoArray(value, PrimitiveType.I32);
        return DebugExpressionPair.create(node, DebugExprType.getIntType(32, signed));
    }

    public DebugExpressionPair createFloatConstant(float value) {
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createSimpleConstantNoArray(value, PrimitiveType.FLOAT);
        return DebugExpressionPair.create(node, DebugExprType.getFloatType(32));
    }

    public DebugExpressionPair createCastIfNecessary(DebugExpressionPair pair, DebugExprType type) {
        if (pair.getType() == type) {
            return pair;
        }
        LLVMExpressionNode node;
        if (type.isUnsigned())
            node = contextReference.get().getNodeFactory().createUnsignedCast(pair.getNode(), type.getLLVMRuntimeType());
        else
            node = contextReference.get().getNodeFactory().createSignedCast(pair.getNode(), type.getLLVMRuntimeType());
        return DebugExpressionPair.create(node, type);
    }

}
