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

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * Implementation of the SL return statement. We need to unwind an unknown number of interpreter
 * frames that are between this {@link SLReturnNode} and the {@link SLFunctionBodyNode} of the
 * method we are exiting. This is done by throwing an {@link SLReturnException exception} that is
 * caught by the {@link SLFunctionBodyNode#executeGeneric function body}. The exception transports
 * the return value.
 */
@NodeInfo(shortName = "return")
public final class SLReturnNode extends SLStatementNode {

    @Child private SLExpressionNode valueNode;

    public SLReturnNode(SLExpressionNode valueNode) {
        this.valueNode = adoptChild(valueNode);
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object result;
        if (valueNode != null) {
            result = valueNode.executeGeneric(frame);
        } else {
            /* Return statement that was not followed by an expression, so return the SL null value. */
            result = SLNull.SINGLETON;
        }
        throw new SLReturnException(result);
    }
}
