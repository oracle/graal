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
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * The body of a user-defined SL function. This is the node referenced by a {@link SLRootNode} for
 * user-defined functions. It handles the return value of a function: the {@link SLReturnNode return
 * statement} throws an {@link SLReturnException exception} with the return value. This node catches
 * the exception. If the method ends without an explicit {@code return}, return the
 * {@link SLNull#SINGLETON default null value}.
 */
@NodeInfo(shortName = "body")
public final class SLFunctionBodyNode extends SLExpressionNode {

    /** The body of the function. */
    @Child private SLStatementNode bodyNode;

    /**
     * Profiling information, collected by the interpreter, capturing whether the function had an
     * {@link SLReturnNode explicit return statement}. This allows the compiler to generate better
     * code.
     */
    private final BranchProfile exceptionTaken = new BranchProfile();
    private final BranchProfile nullTaken = new BranchProfile();

    public SLFunctionBodyNode(SourceSection src, SLStatementNode bodyNode) {
        super(src);
        this.bodyNode = bodyNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        try {
            /* Execute the function body. */
            bodyNode.executeVoid(frame);

        } catch (SLReturnException ex) {
            /*
             * In the interpreter, record profiling information that the function has an explicit
             * return.
             */
            exceptionTaken.enter();
            /* The exception transports the actual return value. */
            return ex.getResult();
        }

        /*
         * In the interpreter, record profiling information that the function ends without an
         * explicit return.
         */
        nullTaken.enter();
        /* Return the default null value. */
        return SLNull.SINGLETON;
    }
}
