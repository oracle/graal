/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;

public class WhileNode extends StatementNode {

    @Child private ConditionNode condition;

    @Child private StatementNode body;

    private final BreakException breakTarget;
    private final ContinueException continueTarget;

    private final BranchProfile continueMismatch = new BranchProfile();
    private final BranchProfile continueMatch = new BranchProfile();
    private final BranchProfile breakMismatch = new BranchProfile();
    private final BranchProfile breakMatch = new BranchProfile();

    public WhileNode(ConditionNode condition, StatementNode body) {
        this.condition = adoptChild(condition);
        this.body = adoptChild(body);

        this.breakTarget = new BreakException();
        this.continueTarget = new ContinueException();
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        try {
            while (condition.executeCondition(frame)) {
                try {
                    body.executeVoid(frame);
                } catch (ContinueException ex) {
                    if (ex != continueTarget) {
                        continueMismatch.enter();
                        throw ex;
                    }
                    continueMatch.enter();
                    // Fall through to next loop iteration.
                }
            }
        } catch (BreakException ex) {
            if (ex != breakTarget) {
                breakMismatch.enter();
                throw ex;
            }
            breakMatch.enter();
            // Done executing this loop, exit method to execute statement following the loop.
        }
    }
}
