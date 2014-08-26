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

import java.io.*;

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
    public String toString() {
        return formatSourceSection(this);
    }

    /**
     * Formats a source section of a node in human readable form. If no source section could be
     * found it looks up the parent hierarchy until it finds a source section. Nodes where this was
     * required append a <code>'~'</code> at the end.
     *
     * @param node the node to format.
     * @return a formatted source section string
     */
    private static String formatSourceSection(Node node) {
        if (node == null) {
            return "<unknown>";
        }
        SourceSection section = node.getSourceSection();
        boolean estimated = false;
        if (section == null) {
            section = node.getEncapsulatingSourceSection();
            estimated = true;
        }

        if (section == null || section.getSource() == null) {
            return "<unknown source>";
        } else {
            String sourceName = new File(section.getSource().getName()).getName();
            int startLine = section.getStartLine();
            return String.format("%s:%d%s", sourceName, startLine, estimated ? "~" : "");
        }
    }

    @Override
    public Probe probe(ExecutionContext context) {
        Node parent = getParent();

        if (parent == null)
            throw new IllegalStateException("Cannot probe a node without a parent");

        if (parent instanceof SLStatementWrapper) {
            return ((SLStatementWrapper) parent).getProbe();
        }

        // Create a new wrapper/probe with this node as its child.
        SLStatementWrapper wrapper = new SLStatementWrapper((SLContext) context, this);

        // Replace this node in the AST with the wrapper
        this.replace(wrapper);

        return wrapper.getProbe();
    }
}
