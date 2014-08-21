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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.nodes.instrument.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * The base class of all Truffle nodes for SL. All nodes (even expressions) can be used as
 * statements, i.e., without returning a value. The {@link VirtualFrame} provides access to the
 * local variables.
 */
@NodeInfo(language = "Simple Language", description = "The abstract base node for all statements")
public abstract class SLStatementNode extends Node implements Instrumentable {

    public SLStatementNode(SourceSection src) {
        super(src);
    }

    /**
     * Execute this node as as statement, where no return value is necessary.
     */
    public abstract void executeVoid(VirtualFrame frame);

    public SLStatementNode getNonWrapperNode() {
        return this;
    }

    @Override
    public Probe probe(ExecutionContext context) {
        Node parent = getParent();

        if (parent == null)
            throw new IllegalStateException("Cannot probe a node without a parent");

        if (parent instanceof SLStatementWrapper)
            return ((SLStatementWrapper) parent).getProbe();

        SLStatementWrapper wrapper = new SLStatementWrapper((SLContext) context, this);
        this.replace(wrapper);
        wrapper.insertChild();
        return wrapper.getProbe();
    }
}
