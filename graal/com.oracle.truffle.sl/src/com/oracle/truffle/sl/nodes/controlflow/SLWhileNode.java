/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.controlflow;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.sl.nodes.*;

@NodeInfo(shortName = "while")
public final class SLWhileNode extends SLStatementNode {

    /**
     * The condition of the loop. This in a {@link SLExpressionNode} because we require a result
     * value. We do not have a node type that can only return a {@code boolean} value, so
     * {@link #evaluateCondition executing the condition} can lead to a type error.
     */
    @Child private SLExpressionNode conditionNode;

    /** Statement (or {@SLBlockNode block}) executed as long as the condition is true. */
    @Child private SLStatementNode bodyNode;

    /**
     * Profiling information, collected by the interpreter, capturing whether a {@code continue}
     * statement was used in this loop. This allows the compiler to generate better code for loops
     * without a {@code continue}.
     */
    private final BranchProfile continueTaken = new BranchProfile();
    private final BranchProfile breakTaken = new BranchProfile();

    public SLWhileNode(SLExpressionNode conditionNode, SLStatementNode bodyNode) {
        /*
         * It is a Truffle requirement to call adoptChild(), which performs all the necessary steps
         * to add the new child to the node tree.
         */
        this.conditionNode = adoptChild(conditionNode);
        this.bodyNode = adoptChild(bodyNode);
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        int count = 0;
        try {
            while (evaluateCondition(frame)) {
                try {
                    /* Execute the loop body. */
                    bodyNode.executeVoid(frame);

                    if (CompilerDirectives.inInterpreter()) {
                        /* In the interpreter, profile the the number of loop iteration. */
                        count++;
                    }
                } catch (SLContinueException ex) {
                    /* In the interpreter, record profiling information that the loop uses continue. */
                    continueTaken.enter();
                    /* Fall through to next loop iteration. */
                }
            }
        } catch (SLBreakException ex) {
            /* In the interpreter, record profiling information that the loop uses break. */
            breakTaken.enter();
            /* Done executing this loop, exit method to execute statement following the loop. */

        } finally {
            if (CompilerDirectives.inInterpreter()) {
                /*
                 * In the interpreter, report the loop count to the Truffle system. It is used for
                 * compilation and inlining decisions.
                 */
                getRootNode().reportLoopCount(count);
            }
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
}
