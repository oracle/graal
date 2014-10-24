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
package com.oracle.truffle.sl.nodes;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.builtins.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * The root of all SL execution trees. It is a Truffle requirement that the tree root extends the
 * class {@link RootNode}. This class is used for both builtin and user-defined functions. For
 * builtin functions, the {@link #bodyNode} is a subclass of {@link SLBuiltinNode}. For user-defined
 * functions, the {@link #bodyNode} is a {@link SLFunctionBodyNode}.
 */
@NodeInfo(language = "Simple Language", description = "The root of all Simple Language execution trees")
public final class SLRootNode extends RootNode {

    /** The function body that is executed, and specialized during execution. */
    @Child private SLExpressionNode bodyNode;

    /** The name of the function, for printing purposes only. */
    private final String name;

    /** The Simple execution context for this tree. **/
    private final SLContext context;

    @CompilationFinal private boolean isCloningAllowed;

    public SLRootNode(SLContext context, FrameDescriptor frameDescriptor, SLExpressionNode bodyNode, String name) {
        super(null, frameDescriptor);
        this.bodyNode = bodyNode;
        this.name = name;
        this.context = context;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return bodyNode.executeGeneric(frame);
    }

    public String getName() {
        return name;
    }

    public void setCloningAllowed(boolean isCloningAllowed) {
        this.isCloningAllowed = isCloningAllowed;
    }

    public SLExpressionNode getBodyNode() {
        return bodyNode;
    }

    @Override
    public boolean isCloningAllowed() {
        return isCloningAllowed;
    }

    @Override
    public String toString() {
        return "root " + name;
    }

    public SLContext getSLContext() {
        return this.context;
    }
}
