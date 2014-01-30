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
public class SLWhileNode extends SLStatementNode {

    @Child private SLExpressionNode conditionNode;
    @Child private SLStatementNode bodyNode;

    private final BranchProfile continueTaken = new BranchProfile();
    private final BranchProfile breakTaken = new BranchProfile();

    public SLWhileNode(SLExpressionNode conditionNode, SLStatementNode bodyNode) {
        this.conditionNode = adoptChild(conditionNode);
        this.bodyNode = adoptChild(bodyNode);
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        int count = 0;
        try {
            while (evaluateCondition(frame)) {
                try {
                    bodyNode.executeVoid(frame);
                    if (CompilerDirectives.inInterpreter()) {
                        count++;
                    }
                } catch (SLContinueException ex) {
                    continueTaken.enter();
                    /* Fall through to next loop iteration. */
                }
            }
        } catch (SLBreakException ex) {
            breakTaken.enter();
            /* Done executing this loop, exit method to execute statement following the loop. */
        } finally {
            if (CompilerDirectives.inInterpreter()) {
                /*
                 * Report the loop count to the Truffle system. It is used for compilation and
                 * inlining decisions.
                 */
                RootNode root = getRootNode();
                if (root.getCallTarget() instanceof LoopCountReceiver) {
                    ((LoopCountReceiver) root.getCallTarget()).reportLoopCount(count);
                }
            }
        }
    }

    private boolean evaluateCondition(VirtualFrame frame) {
        try {
            /*
             * The condition must evaluate to a boolean value, so we call boolean-specialized
             * method.
             */
            return conditionNode.executeBoolean(frame);
        } catch (UnexpectedResultException ex) {
            /*
             * The condition evaluated to a non-boolean result. This is a type error in the SL
             * program. We report it with the same exception that Truffle DSL generated nodes use to
             * report type errors.
             */
            throw new UnsupportedSpecializationException(this, ex.getResult());
        }
    }
}
