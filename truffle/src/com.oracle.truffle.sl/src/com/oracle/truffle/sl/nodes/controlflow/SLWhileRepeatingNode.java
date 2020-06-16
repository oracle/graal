/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.nodes.controlflow;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.SLStatementNode;
import com.oracle.truffle.sl.nodes.util.SLUnboxNodeGen;

/**
 * The loop body of a {@link SLWhileNode while loop}. A Truffle framework {@link LoopNode} between
 * the {@link SLWhileNode} and {@link SLWhileRepeatingNode} allows Truffle to perform loop
 * optimizations, for example, compile just the loop body for long running loops.
 */
public final class SLWhileRepeatingNode extends Node implements RepeatingNode {

    /**
     * The condition of the loop. This in a {@link SLExpressionNode} because we require a result
     * value. We do not have a node type that can only return a {@code boolean} value, so
     * {@link #evaluateCondition executing the condition} can lead to a type error.
     */
    @Child private SLExpressionNode conditionNode;

    /** Statement (or {@link SLBlockNode block}) executed as long as the condition is true. */
    @Child private SLStatementNode bodyNode;

    /**
     * Profiling information, collected by the interpreter, capturing whether a {@code continue}
     * statement was used in this loop. This allows the compiler to generate better code for loops
     * without a {@code continue}.
     */
    private final BranchProfile continueTaken = BranchProfile.create();
    private final BranchProfile breakTaken = BranchProfile.create();

    public SLWhileRepeatingNode(SLExpressionNode conditionNode, SLStatementNode bodyNode) {
        this.conditionNode = SLUnboxNodeGen.create(conditionNode);
        this.bodyNode = bodyNode;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        if (!evaluateCondition(frame)) {
            /* Normal exit of the loop when loop condition is false. */
            return false;
        }

        try {
            /* Execute the loop body. */
            bodyNode.executeVoid(frame);
            /* Continue with next loop iteration. */
            return true;

        } catch (SLContinueException ex) {
            /* In the interpreter, record profiling information that the loop uses continue. */
            continueTaken.enter();
            /* Continue with next loop iteration. */
            return true;

        } catch (SLBreakException ex) {
            /* In the interpreter, record profiling information that the loop uses break. */
            breakTaken.enter();
            /* Break out of the loop. */
            return false;
        }
    }

    private boolean evaluateCondition(VirtualFrame frame) {
        try {
            /*
             * The condition must evaluate to a boolean value, so we call the boolean-specialized
             * execute method.
             */
            return conditionNode.executeBoolean(frame);
        } catch (UnexpectedResultException ex) {
            /*
             * The condition evaluated to a non-boolean result. This is a type error in the SL
             * program. We report it with the same exception that Truffle DSL generated nodes use to
             * report type errors.
             */
            throw new UnsupportedSpecializationException(this, new Node[]{conditionNode}, ex.getResult());
        }
    }

    @Override
    public String toString() {
        return SLStatementNode.formatSourceSection(this);
    }

}
