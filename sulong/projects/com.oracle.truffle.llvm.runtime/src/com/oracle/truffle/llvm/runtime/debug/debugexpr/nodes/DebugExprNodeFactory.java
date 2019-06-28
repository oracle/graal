package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import java.util.List;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.CompareOperator;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprBitFlipNodeFactory.BitFlipNodeGen;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNotNode.NotNode;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNotNodeFactory.NotNodeGen;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprShortCircuitEvaluationNodeFactory.DebugExprLogicalAndNodeGen;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprShortCircuitEvaluationNodeFactory.DebugExprLogicalOrNodeGen;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprTernaryNodeFactory.DebugExprConditionalNodeGen;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.Parser;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

public final class DebugExprNodeFactory {
    private ContextReference<LLVMContext> contextReference;
    public final static DebugExprErrorNode noObjNode = DebugExprErrorNode.create("<cannot find expression>");
    public final static DebugExprErrorNode errorObjNode = DebugExprErrorNode.create("<cannot evaluate expression>");
    public final static DebugExpressionPair noObjPair = DebugExpressionPair.create(noObjNode, DebugExprType.getVoidType());
    public final static DebugExpressionPair errorObjPair = DebugExpressionPair.create(errorObjNode, DebugExprType.getVoidType());
    private Iterable<Scope> scopes, globalScopes;
    private Parser parser;

    private DebugExprNodeFactory(ContextReference<LLVMContext> contextReference, Iterable<Scope> scopes, Parser parser, Iterable<Scope> globalScopes) {
        this.contextReference = contextReference;
        this.scopes = scopes;
        this.parser = parser;
        this.globalScopes = globalScopes;
    }

    public static DebugExprNodeFactory create(ContextReference<LLVMContext> contextReference, Iterable<Scope> scopes, Parser parser, Iterable<Scope> globalScopes) {
        return new DebugExprNodeFactory(contextReference, scopes, parser, globalScopes);
    }

    private boolean isErrorPair(DebugExpressionPair p) {
        if (p == null || p == errorObjPair) {
            parser.SemErr("cannot evaluate expression");
            return true;
        } else if (p == noObjPair) {
            parser.SemErr("cannot find expression");
            return true;
        }

        return false;
    }

    public DebugExpressionPair createArithmeticOp(ArithmeticOperation op, DebugExpressionPair left, DebugExpressionPair right) {
        if (isErrorPair(left) || isErrorPair(right))
            return errorObjPair;
        /* null is passed as type, since a type check is done by the arithmetic node anyway */
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        DebugExpressionPair leftPair = createCastIfNecessary(left, commonType);
        DebugExpressionPair rightPair = createCastIfNecessary(right, commonType);
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public DebugExpressionPair createDivNode(DebugExpressionPair left, DebugExpressionPair right) {
        if (isErrorPair(left) || isErrorPair(right))
            return errorObjPair;
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        DebugExpressionPair leftPair = createCastIfNecessary(left, commonType);
        DebugExpressionPair rightPair = createCastIfNecessary(right, commonType);
        ArithmeticOperation op = commonType.isUnsigned() ? ArithmeticOperation.UDIV : ArithmeticOperation.DIV;
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public DebugExpressionPair createRemNode(DebugExpressionPair left, DebugExpressionPair right) {
        if (isErrorPair(left) || isErrorPair(right))
            return errorObjPair;
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        ArithmeticOperation op = commonType.isUnsigned() ? ArithmeticOperation.UREM : ArithmeticOperation.REM;
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public DebugExpressionPair createShiftLeft(DebugExpressionPair left, DebugExpressionPair right) {
        if (isErrorPair(left) || isErrorPair(right))
            return errorObjPair;
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
        if (isErrorPair(left) || isErrorPair(right))
            return errorObjPair;
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
        if (isErrorPair(condition) || isErrorPair(thenNode) || isErrorPair(elseNode))
            return errorObjPair;
        LLVMExpressionNode node = DebugExprConditionalNodeGen.create(condition.getNode(), thenNode.getNode(), elseNode.getNode());
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
        LLVMExpressionNode node = DebugExprSizeofNodeGen.create(type);
        return DebugExpressionPair.create(node, DebugExprType.getIntType(32, true));
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createLogicalAndNode(DebugExpressionPair left, DebugExpressionPair right) {
        if (isErrorPair(left) || isErrorPair(right))
            return errorObjPair;
        LLVMExpressionNode node = DebugExprLogicalAndNodeGen.create(left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createLogicalOrNode(DebugExpressionPair left, DebugExpressionPair right) {
        if (isErrorPair(left) || isErrorPair(right))
            return errorObjPair;
        LLVMExpressionNode node = DebugExprLogicalOrNodeGen.create(left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
    }

    @SuppressWarnings("static-method")
    public LLVMExpressionNode createErrorNode(Object errorObj) {
        return DebugExprErrorNode.create(errorObj);
    }

    public DebugExpressionPair createCompareNode(DebugExpressionPair left, CompareKind op, DebugExpressionPair right) {
        if (isErrorPair(left) || isErrorPair(right))
            return errorObjPair;
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        DebugExpressionPair leftPair = createCastIfNecessary(left, commonType);
        DebugExpressionPair rightPair = createCastIfNecessary(right, commonType);
        CompareOperator cop;
        if (commonType.isFloatingType()) {
            cop = getFloatingCompareOperator(op);
        } else if (commonType.isUnsigned()) {
            cop = getUnsignedCompareOperator(op);
        } else {
            cop = getSignedCompareOperator(op);
        }
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createComparison(cop, null, leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
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
        if (pair.getType() == type || pair == errorObjPair || pair == noObjPair) {
            return pair;
        }
        LLVMExpressionNode node;
        if (type.isUnsigned())
            node = contextReference.get().getNodeFactory().createUnsignedCast(pair.getNode(), type.getLLVMRuntimeType());
        else
            node = contextReference.get().getNodeFactory().createSignedCast(pair.getNode(), type.getLLVMRuntimeType());
        return DebugExpressionPair.create(node, type);
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createObjectMember(DebugExpressionPair receiver, String fieldName) {
        Object baseMember = null;
        if (receiver.getNode() instanceof DebugExprVarNode) {
            baseMember = ((DebugExprVarNode) (receiver.getNode())).getMember();
        } else if (receiver.getNode() instanceof DebugExprObjectMemberNode) {
            baseMember = ((DebugExprObjectMemberNode) (receiver.getNode())).getMember();
        }
        if (baseMember != null) {
            DebugExprObjectMemberNode node = new DebugExprObjectMemberNode(fieldName, baseMember);
            return DebugExpressionPair.create(node, node.getType());
        }
        return errorObjPair;
    }

    public DebugExpressionPair createFunctionCall(DebugExpressionPair functionPair, List<DebugExpressionPair> arguments) {
        if (isErrorPair(functionPair))
            return errorObjPair;
        if (functionPair.getNode() instanceof DebugExprVarNode) {
            DebugExprVarNode varNode = (DebugExprVarNode) functionPair.getNode();
            LLVMExpressionNode node = varNode.createFunctionCall(arguments, globalScopes);
            return DebugExpressionPair.create(node, varNode.getType());
        }
        return errorObjPair;
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createArrayElement(DebugExpressionPair array, DebugExpressionPair index) {
        Object baseMember = null;
        DebugExprType baseType = null;
        if (array.getNode() instanceof DebugExprVarNode) {
            baseMember = ((DebugExprVarNode) (array.getNode())).getMember();
            baseType = ((DebugExprVarNode) (array.getNode())).getType();
        } else if (array.getNode() instanceof DebugExprObjectMemberNode) {
            baseMember = ((DebugExprObjectMemberNode) (array.getNode())).getMember();
            baseType = ((DebugExprObjectMemberNode) (array.getNode())).getType();
        }
        if (baseMember != null) {
            DebugExprArrayElementNode node = new DebugExprArrayElementNode(baseMember, index.getNode(), baseType.getInnerType());
            return DebugExpressionPair.create(node, baseType.getInnerType());
        }
        return errorObjPair;
    }

    public enum CompareKind {
        EQ,
        NE,
        LT,
        LE,
        GT,
        GE
    }

    private static CompareOperator getSignedCompareOperator(CompareKind kind) {
        switch (kind) {
            case EQ:
                return CompareOperator.INT_EQUAL;
            case GE:
                return CompareOperator.INT_SIGNED_GREATER_OR_EQUAL;
            case GT:
                return CompareOperator.INT_SIGNED_GREATER_THAN;
            case LE:
                return CompareOperator.INT_SIGNED_LESS_OR_EQUAL;
            case LT:
                return CompareOperator.INT_SIGNED_LESS_THAN;
            case NE:
                return CompareOperator.INT_NOT_EQUAL;
            default:
                return CompareOperator.INT_EQUAL;
        }
    }

    private static CompareOperator getUnsignedCompareOperator(CompareKind kind) {
        switch (kind) {
            case EQ:
                return CompareOperator.INT_EQUAL;
            case GE:
                return CompareOperator.INT_UNSIGNED_GREATER_OR_EQUAL;
            case GT:
                return CompareOperator.INT_UNSIGNED_GREATER_THAN;
            case LE:
                return CompareOperator.INT_UNSIGNED_LESS_OR_EQUAL;
            case LT:
                return CompareOperator.INT_UNSIGNED_LESS_THAN;
            case NE:
                return CompareOperator.INT_NOT_EQUAL;
            default:
                return CompareOperator.INT_EQUAL;
        }
    }

    private static CompareOperator getFloatingCompareOperator(CompareKind kind) {
        switch (kind) {
            case EQ:
                return CompareOperator.FP_ORDERED_EQUAL;
            case GE:
                return CompareOperator.FP_ORDERED_GREATER_OR_EQUAL;
            case GT:
                return CompareOperator.FP_ORDERED_GREATER_THAN;
            case LE:
                return CompareOperator.FP_ORDERED_LESS_OR_EQUAL;
            case LT:
                return CompareOperator.FP_ORDERED_LESS_THAN;
            case NE:
                return CompareOperator.FP_ORDERED_NOT_EQUAL;
            default:
                return CompareOperator.FP_FALSE;
        }
    }

}
