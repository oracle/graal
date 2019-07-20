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
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.Parser;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

public final class DebugExprNodeFactory {
    private ContextReference<LLVMContext> contextReference;
    private Iterable<Scope> scopes, globalScopes;

    private DebugExprNodeFactory(ContextReference<LLVMContext> contextReference, Iterable<Scope> scopes, Iterable<Scope> globalScopes) {
        this.contextReference = contextReference;
        this.scopes = scopes;
        this.globalScopes = globalScopes;
    }

    public static DebugExprNodeFactory create(ContextReference<LLVMContext> contextReference, Iterable<Scope> scopes, Iterable<Scope> globalScopes) {
        return new DebugExprNodeFactory(contextReference, scopes, globalScopes);
    }

    private static void checkError(DebugExpressionPair p, String operationDescription) {
        if (p == null) {
            throw DebugExprException.nullObject(p, operationDescription);
        }
    }

    public DebugExpressionPair createArithmeticOp(ArithmeticOperation op, DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, op.name());
        checkError(right, op.name());
        /* null is passed as type, since a type check is done by the arithmetic node anyway */
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        DebugExpressionPair leftPair = createCastIfNecessary(left, commonType);
        DebugExpressionPair rightPair = createCastIfNecessary(right, commonType);
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public DebugExpressionPair createDivNode(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, "/");
        checkError(right, "/");
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        DebugExpressionPair leftPair = createCastIfNecessary(left, commonType);
        DebugExpressionPair rightPair = createCastIfNecessary(right, commonType);
        ArithmeticOperation op = commonType.isUnsigned() ? ArithmeticOperation.UDIV : ArithmeticOperation.DIV;
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public DebugExpressionPair createRemNode(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, "%");
        checkError(right, "%");
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        ArithmeticOperation op = commonType.isUnsigned() ? ArithmeticOperation.UREM : ArithmeticOperation.REM;
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public DebugExpressionPair createShiftLeft(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, "<<");
        checkError(right, "<<");
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(ArithmeticOperation.SHL, null, left.getNode(), right.getNode());

        if (!right.getType().isIntegerType() || !left.getType().isIntegerType()) {
            throw DebugExprException.typeError(node, left.getNode(), right.getNode());
        } else {
            return DebugExpressionPair.create(node, left.getType());
        }
    }

    public DebugExpressionPair createShiftRight(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, ">>");
        checkError(right, ">>");

        ArithmeticOperation op = left.getType().isUnsigned() ? ArithmeticOperation.LSHR : ArithmeticOperation.ASHR;
        LLVMExpressionNode node = contextReference.get().getNodeFactory().createArithmeticOp(op, null, left.getNode(), right.getNode());

        if (!right.getType().isIntegerType() || !left.getType().isIntegerType()) {
            throw DebugExprException.typeError(node, left.getNode(), right.getNode());
        } else {
            return DebugExpressionPair.create(node, left.getType());
        }
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createTernaryNode(DebugExpressionPair condition, DebugExpressionPair thenNode, DebugExpressionPair elseNode) {
        checkError(condition, "? :");
        checkError(thenNode, "? :");
        checkError(elseNode, "? :");
        LLVMExpressionNode node = DebugExprTernaryNodeGen.create(thenNode.getNode(), elseNode.getNode(), condition.getNode());
        return DebugExpressionPair.create(node, DebugExprType.commonType(thenNode.getType(), elseNode.getType()));
    }

    public DebugExpressionPair createUnaryOpNode(DebugExpressionPair pair, char unaryOp) {
        checkError(pair, Character.toString(unaryOp));
        switch (unaryOp) {
            case '&':
            case '*':
                throw DebugExprException.create(pair.getNode(), "Pointer expressions have not been implemented yet");
            case '+':
                return pair;
            case '-':
                return createArithmeticOp(ArithmeticOperation.SUB, createIntegerConstant(0), pair);
            case '~':
                return DebugExpressionPair.create(BitFlipNodeGen.create(pair.getNode()), pair.getType());
            case '!':
                return DebugExpressionPair.create(NotNodeGen.create(pair.getNode()), pair.getType());
            default:
                throw DebugExprException.create(pair.getNode(), "unknown symbol: " + unaryOp);
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
        checkError(left, "&&");
        checkError(right, "&&");
        LLVMExpressionNode node = new DebugExprLogicalAndNode(left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createLogicalOrNode(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, "||");
        checkError(right, "||");
        LLVMExpressionNode node = new DebugExprLogicalOrNode(left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
    }

    public DebugExpressionPair createCompareNode(DebugExpressionPair left, CompareKind op, DebugExpressionPair right) {
        checkError(left, op.name());
        checkError(right, op.name());
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
        checkError(pair, "cast");
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

    @SuppressWarnings("static-method")
    public DebugExpressionPair createObjectMember(DebugExpressionPair receiver, String fieldName) {
        Object baseMember = null;
        String memberName = null;
        if (receiver.getNode() instanceof DebugExprVarNode) {
            baseMember = ((DebugExprVarNode) (receiver.getNode())).getMember();
            memberName = ((DebugExprVarNode) (receiver.getNode())).getName();
        } else if (receiver.getNode() instanceof DebugExprObjectMemberNode) {
            baseMember = ((DebugExprObjectMemberNode) (receiver.getNode())).getMember();
            memberName = ((DebugExprObjectMemberNode) (receiver.getNode())).getFieldName();
        }
        if (baseMember != null) {
            DebugExprObjectMemberNode node = new DebugExprObjectMemberNode(fieldName, baseMember);
            return DebugExpressionPair.create(node, node.getType());
        }
        throw DebugExprException.symbolNotFound(receiver.getNode(), memberName, null);
    }

    public DebugExpressionPair createFunctionCall(DebugExpressionPair functionPair, List<DebugExpressionPair> arguments) {
        checkError(functionPair, "call(...)");
        if (functionPair.getNode() instanceof DebugExprVarNode) {
            DebugExprVarNode varNode = (DebugExprVarNode) functionPair.getNode();
            LLVMExpressionNode node = varNode.createFunctionCall(arguments, globalScopes);
            return DebugExpressionPair.create(node, varNode.getType());
        }
        throw DebugExprException.typeError(functionPair.getNode(), functionPair.getNode().toString());
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
        throw DebugExprException.typeError(array.getNode(), baseMember);
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
