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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.nodes.*;

/**
 * A statement node that just executes a list of other statements.
 */
@NodeInfo(shortName = "block", description = "The node implementing a source code block")
public final class SLBlockNode extends SLStatementNode {

    /**
     * The array of child nodes. The annotation {@link com.oracle.truffle.api.nodes.Node.Children
     * Children} informs Truffle that the field contains multiple children. It is a Truffle
     * requirement that the field is {@code final} and an array of nodes.
     */
    @Children private final SLStatementNode[] bodyNodes;

    public SLBlockNode(SourceSection src, SLStatementNode... bodyNodes) {
        super(src);
        this.bodyNodes = bodyNodes;
    }

    /**
     * Execute all child statements. The annotation {@link ExplodeLoop} triggers full unrolling of
     * the loop during compilation. This allows the {@link SLStatementNode#executeVoid} method of
     * all children to be inlined.
     */
    @Override
    @ExplodeLoop
    public void executeVoid(VirtualFrame frame) {
        /*
         * This assertion illustrates that the arryay length is really a constant during
         * compilation.
         */
        CompilerAsserts.compilationConstant(bodyNodes.length);

        for (SLStatementNode statement : bodyNodes) {
            statement.executeVoid(frame);
        }
    }
}
