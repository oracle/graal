/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import java.util.Collection;
import java.util.List;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.CompareOperator;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

public final class DebugExprNodeFactory {

    private Collection<Scope> scopes;
    private Collection<Scope> globalScopes;

    private DebugExprNodeFactory(Collection<Scope> scopes, Collection<Scope> globalScopes) {
        // this.nodeFactory = nodeFactory;
        this.scopes = scopes;
        this.globalScopes = globalScopes;
    }

    public static DebugExprNodeFactory create(Collection<Scope> scopes, Collection<Scope> globalScopes) {
        return new DebugExprNodeFactory(scopes, globalScopes);
    }

    private static void checkError(DebugExpressionPair p, String operationDescription) {
        if (p == null) {
            throw DebugExprException.nullObject(operationDescription);
        }
    }

    public static DebugExpressionPair createArithmeticOp(ArithmeticOperation op, DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, op.name());
        checkError(right, op.name());
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        DebugExpressionPair leftPair = createCastIfNecessary(left, commonType);
        DebugExpressionPair rightPair = createCastIfNecessary(right, commonType);
        LLVMExpressionNode node = CommonNodeFactory.createArithmeticOp(op, commonType.getLLVMRuntimeType(), leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public static DebugExpressionPair createDivNode(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, "/");
        checkError(right, "/");
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        DebugExpressionPair leftPair = createCastIfNecessary(left, commonType);
        DebugExpressionPair rightPair = createCastIfNecessary(right, commonType);
        ArithmeticOperation op = commonType.isUnsigned() ? ArithmeticOperation.UDIV : ArithmeticOperation.DIV;
        LLVMExpressionNode node = CommonNodeFactory.createArithmeticOp(op, commonType.getLLVMRuntimeType(), leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public static DebugExpressionPair createRemNode(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, "%");
        checkError(right, "%");
        DebugExprType commonType = DebugExprType.commonType(left.getType(), right.getType());
        ArithmeticOperation op = commonType.isUnsigned() ? ArithmeticOperation.UREM : ArithmeticOperation.REM;
        LLVMExpressionNode node = CommonNodeFactory.createArithmeticOp(op, commonType.getLLVMRuntimeType(), left.getNode(), right.getNode());
        return DebugExpressionPair.create(node, commonType);
    }

    public static DebugExpressionPair createShiftLeft(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, "<<");
        checkError(right, "<<");
        LLVMExpressionNode node = CommonNodeFactory.createArithmeticOp(ArithmeticOperation.SHL, left.getType().getLLVMRuntimeType(), left.getNode(), right.getNode());

        if (!right.getType().isIntegerType() || !left.getType().isIntegerType()) {
            throw DebugExprException.typeError(node, left.getNode(), right.getNode());
        } else {
            return DebugExpressionPair.create(node, left.getType());
        }
    }

    public static DebugExpressionPair createShiftRight(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, ">>");
        checkError(right, ">>");

        ArithmeticOperation op = left.getType().isUnsigned() ? ArithmeticOperation.LSHR : ArithmeticOperation.ASHR;
        LLVMExpressionNode node = CommonNodeFactory.createArithmeticOp(op, left.getType().getLLVMRuntimeType(), left.getNode(), right.getNode());

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
            case '*':
                return createDereferenceNode(pair);
            case '+':
                return pair;
            case '-':
                return createArithmeticOp(ArithmeticOperation.SUB, createIntegerConstant(0), pair);
            case '~':
                return DebugExpressionPair.create(DebugExprBitFlipNodeGen.create(pair.getNode()), pair.getType());
            case '!':
                return DebugExpressionPair.create(DebugExprNotNodeGen.create(pair.getNode()), pair.getType());
            default:
                throw DebugExprException.create(pair.getNode(), "unknown symbol: %c", unaryOp);
        }
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createVarNode(String name) {
        DebugExprVarNode node = DebugExprVarNodeGen.create(name, scopes);
        return DebugExpressionPair.create(node, node.getType());
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createSizeofNode(DebugExprType type) {
        LLVMExpressionNode node;
        try {
            node = DebugExprSizeofNode.create(type);
        } catch (TypeOverflowException e) {
            node = Type.handleOverflowExpression(e);
        }
        return DebugExpressionPair.create(node, DebugExprType.getIntType(32, true));
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createLogicalAndNode(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, "&&");
        checkError(right, "&&");
        LLVMExpressionNode node = DebugExprShortCircuitEvaluationNodeGen.create(left.getNode(), right.getNode(), new DebugExprLogicalAndNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createLogicalOrNode(DebugExpressionPair left, DebugExpressionPair right) {
        checkError(left, "||");
        checkError(right, "||");
        LLVMExpressionNode node = DebugExprShortCircuitEvaluationNodeGen.create(left.getNode(), right.getNode(), new DebugExprLogicalOrNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
    }

    public static DebugExpressionPair createCompareNode(DebugExpressionPair left, CompareKind op, DebugExpressionPair right) {
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
        LLVMExpressionNode node = CommonNodeFactory.createComparison(cop, commonType.getLLVMRuntimeType(), leftPair.getNode(), rightPair.getNode());
        return DebugExpressionPair.create(node, DebugExprType.getBoolType());
    }

    public static DebugExpressionPair createIntegerConstant(int value) {
        return createIntegerConstant(value, true);
    }

    public static DebugExpressionPair createIntegerConstant(int value, boolean signed) {
        LLVMExpressionNode node = CommonNodeFactory.createSimpleConstantNoArray(value, PrimitiveType.I32);
        return DebugExpressionPair.create(node, DebugExprType.getIntType(32, signed));
    }

    public static DebugExpressionPair createFloatConstant(float value) {
        LLVMExpressionNode node = CommonNodeFactory.createSimpleConstantNoArray(value, PrimitiveType.FLOAT);
        return DebugExpressionPair.create(node, DebugExprType.getFloatType(32));
    }

    public static DebugExpressionPair createCharacterConstant(String charString) {
        boolean valid = true;
        char value = charString.charAt(1);
        if (value == '\\') {
            switch (charString.charAt(2)) {
                case 'n':
                    value = '\n';
                    break;
                case 'r':
                    value = '\r';
                    break;
                case '\'':
                    value = '\'';
                    break;
                case '\\':
                    value = '\\';
                    break;
                case '\"':
                    value = '\"';
                    break;
                default:
                    valid = false;
                    break;
            }
        }
        LLVMExpressionNode node = CommonNodeFactory.createSimpleConstantNoArray((byte) value, PrimitiveType.I8);
        if (!valid) {
            throw DebugExprException.create(node, "character %s not found", charString);
        }
        return DebugExpressionPair.create(node, DebugExprType.getIntType(8, false));
    }

    public static DebugExpressionPair createCastIfNecessary(DebugExpressionPair pair, DebugExprType type) {
        checkError(pair, "cast");
        if (pair.getType().equalsType(type)) {
            return pair;
        }
        if (!pair.getType().canBeCastTo(type)) {
            throw DebugExprException.create(pair.getNode(), "Cast from %s to %s not possible!", pair.getType(), type);
        }
        LLVMExpressionNode node;
        if (type.isFloatingType() || type.isIntegerType()) {
            if (type.isUnsigned()) {
                node = CommonNodeFactory.createUnsignedCast(pair.getNode(), type.getLLVMRuntimeType());
            } else {
                node = CommonNodeFactory.createSignedCast(pair.getNode(), type.getLLVMRuntimeType());
            }
        } else {
            node = CommonNodeFactory.createBitcast(pair.getNode(), type.getLLVMRuntimeType(), pair.getType().getLLVMRuntimeType());
        }
        return DebugExpressionPair.create(node, type);
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createObjectMember(DebugExpressionPair receiver, String fieldName) {
        LLVMExpressionNode baseNode = receiver.getNode();
        DebugExprObjectMemberNode node = DebugExprObjectMemberNodeGen.create(baseNode, fieldName);
        DebugExprType type = node.getType();
        return DebugExpressionPair.create(node, type);
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createDereferenceNode(DebugExpressionPair pointerPair) {
        checkError(pointerPair, "*");
        DebugExprDereferenceNode node = DebugExprDereferenceNodeGen.create(pointerPair.getNode());
        DebugExprType type = pointerPair.getType().getInnerType();
        return DebugExpressionPair.create(node, type);
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createObjectPointerMember(DebugExpressionPair receiver, String fieldName) {
        DebugExpressionPair dereferenced = createDereferenceNode(receiver);
        return createObjectMember(dereferenced, fieldName);
    }

    public DebugExpressionPair createFunctionCall(DebugExpressionPair functionPair, List<DebugExpressionPair> arguments) {
        checkError(functionPair, "call(...)");
        if (functionPair.getNode() instanceof DebugExprVarNode) {
            DebugExprVarNode varNode = (DebugExprVarNode) functionPair.getNode();
            DebugExprFunctionCallNode node = varNode.createFunctionCall(arguments, globalScopes);
            DebugExprType type = node.getType();
            return DebugExpressionPair.create(node, type);
        }
        throw DebugExprException.typeError(functionPair.getNode(), functionPair.getNode().toString());
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createArrayElement(DebugExpressionPair array, DebugExpressionPair index) {
        DebugExprArrayElementNode node = DebugExprArrayElementNode.create(array, index.getNode());
        if (array.getType() == null) {
            throw DebugExprException.typeError(node, node);
        }
        return DebugExpressionPair.create(node, array.getType().getInnerType());
    }

    public DebugExprTypeofNode createTypeofNode(String ident) {
        return DebugExprTypeofNodeGen.create(ident, scopes);
    }

    @SuppressWarnings("static-method")
    public DebugExpressionPair createPointerCastNode(DebugExpressionPair pair, DebugExprTypeofNode typeNode) {
        checkError(pair, "pointer cast");
        DebugExprPointerCastNode node = DebugExprPointerCastNodeGen.create(pair.getNode(), typeNode);
        return DebugExpressionPair.create(node, node.getType());
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
