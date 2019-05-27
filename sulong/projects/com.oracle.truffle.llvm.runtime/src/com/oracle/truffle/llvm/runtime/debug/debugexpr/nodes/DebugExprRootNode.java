/* Copyright (c) 2019, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.Parser;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

/**
 * this @Node has to be the root node of 'debugexpr' expressions. Exceptions, that occur during
 * execution of these AST nodes, are caught and overwritten by a message string
 */
public class DebugExprRootNode extends LLVMExpressionNode {
    private final LLVMExpressionNode root;

    public DebugExprRootNode(LLVMExpressionNode root) {
        this.root = root;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        try {
            // for boolean results: convert to a string
            boolean result = root.executeI1(frame);
            return result ? "true" : "false";
        } catch (UnsupportedSpecializationException | UnexpectedResultException e) {
            // perform normal execution
        }
        try {
            // try to execute node
            return root.executeGeneric(frame);
        } catch (UnsupportedSpecializationException use) {
            // return error string node
            return Parser.errorObjNode.executeGeneric(frame);
        } catch (DebugExprException dee) {
            // use existing exception if available
            if (dee.exceptionNode == null)
                return Parser.errorObjNode.executeGeneric(frame);
            else
                return dee.exceptionNode.executeGeneric(frame);
        }
    }

}
