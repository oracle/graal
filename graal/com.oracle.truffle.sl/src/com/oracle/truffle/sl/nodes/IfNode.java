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

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.api.frame.*;

@ExecuteChildren("conditionNode")
public abstract class IfNode extends StatementNode {

    @Child protected ConditionNode conditionNode;

    @Child private StatementNode thenPartNode;

    @Child private StatementNode elsePartNode;

    public IfNode(ConditionNode condition, StatementNode thenPart, StatementNode elsePart) {
        this.conditionNode = adoptChild(condition);
        this.thenPartNode = adoptChild(thenPart);
        this.elsePartNode = adoptChild(elsePart);
    }

    protected IfNode(IfNode node) {
        this(node.conditionNode, node.thenPartNode, node.elsePartNode);
    }

    @Specialization
    public void doVoid(VirtualFrame frame, boolean condition) {
        if (condition) {
            thenPartNode.executeVoid(frame);
        } else {
            elsePartNode.executeVoid(frame);
        }
    }

}
