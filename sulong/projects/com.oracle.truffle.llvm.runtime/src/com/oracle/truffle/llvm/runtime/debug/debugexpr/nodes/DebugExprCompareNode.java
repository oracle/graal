/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.CompareOperator;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.Parser;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import static com.oracle.truffle.llvm.runtime.CompareOperator.*;

public class DebugExprCompareNode extends LLVMExpressionNode {

    @Child protected LLVMExpressionNode left, right;
    protected Op op;
    protected NodeFactory nodeFactory;

    public DebugExprCompareNode(NodeFactory nodeFactory, LLVMExpressionNode left, Op op, LLVMExpressionNode right) {
        this.nodeFactory = nodeFactory;
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        // try to do an integer comparison
        CompareOperator cop = getSignedIntOp();
        if (cop == null)
            return Parser.errorObjNode.executeGeneric(frame);
        /* null is passed as type, since a type check is done by the arithmetic node anyway */
        LLVMExpressionNode ret = nodeFactory.createComparison(cop, null, left, right);
        try {
            Object executeObj = ret.executeGeneric(frame);
            return executeObj;
        } catch (UnsupportedSpecializationException e) {

        }
        cop = getFloatingPointOp();
        if (cop == null)
            return Parser.errorObjNode.executeGeneric(frame);
        /* null is passed as type, since a type check is done by the arithmetic node anyway */
        ret = nodeFactory.createComparison(cop, null, left, right);
        try {
            Object executeObj = ret.executeGeneric(frame);
            return executeObj;
        } catch (UnsupportedSpecializationException e) {

        }
        // try to do a floating-point comparison
        cop = getFloatingPointOp();
        if (cop == null)
            return Parser.errorObjNode.executeGeneric(frame);
        /* null is passed as type, since a type check is done by the arithmetic node anyway */
        ret = nodeFactory.createComparison(cop, null, left, right);
        try {
            Object executeObj = ret.executeGeneric(frame);
            return executeObj;
        } catch (UnsupportedSpecializationException e) {

        }

        return Parser.errorObjNode.executeGeneric(frame);
    }

    private CompareOperator getSignedIntOp() {
        switch (op) {
            case EQ:
                return INT_EQUAL;
            case NE:
                return INT_NOT_EQUAL;
            case LT:
                return INT_SIGNED_LESS_THAN;
            case LE:
                return INT_SIGNED_LESS_OR_EQUAL;
            case GT:
                return INT_SIGNED_GREATER_THAN;
            case GE:
                return INT_SIGNED_GREATER_OR_EQUAL;
            default:
                return null;
        }
    }

    private CompareOperator getUnsignedIntOp() {
        switch (op) {
            case EQ:
                return INT_EQUAL;
            case NE:
                return INT_NOT_EQUAL;
            case LT:
                return INT_UNSIGNED_LESS_THAN;
            case LE:
                return INT_UNSIGNED_LESS_OR_EQUAL;
            case GT:
                return INT_UNSIGNED_GREATER_THAN;
            case GE:
                return INT_UNSIGNED_GREATER_OR_EQUAL;
            default:
                return null;
        }
    }

    private CompareOperator getFloatingPointOp() {
        switch (op) {
            case EQ:
                return FP_ORDERED_EQUAL;
            case NE:
                return FP_ORDERED_NOT_EQUAL;
            case LT:
                return FP_ORDERED_LESS_THAN;
            case LE:
                return FP_ORDERED_LESS_OR_EQUAL;
            case GT:
                return FP_ORDERED_GREATER_THAN;
            case GE:
                return FP_ORDERED_GREATER_OR_EQUAL;
            default:
                return null;
        }
    }

    public enum Op {
        EQ,
        NE,
        LT,
        LE,
        GT,
        GE
    }

}
