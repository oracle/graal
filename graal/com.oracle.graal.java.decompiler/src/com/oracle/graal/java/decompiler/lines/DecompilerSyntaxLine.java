/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java.decompiler.lines;

import com.oracle.graal.graph.*;
import com.oracle.graal.java.decompiler.block.*;
import com.oracle.graal.nodes.*;

public abstract class DecompilerSyntaxLine {

    protected final Node node;
    protected final DecompilerBlock block;

    protected DecompilerSyntaxLine(DecompilerBlock block, Node node) {
        this.node = node;
        this.block = block;
    }

    public Node getNode() {
        return node;
    }

    public abstract String getAsString();

    public DecompilerBlock getBlock() {
        return block;
    }

    protected static String getStringRepresentation(Node node) {
        if (node instanceof ConstantNode) {
            return String.valueOf(((ConstantNode) node).asConstant().asBoxedValue());
        } else if (node instanceof ParameterNode) {
            return "param_" + ((ParameterNode) node).index();
        } else {
            return getVariable(node);
        }
    }

    @SuppressWarnings("deprecation")
    protected static String getVariable(Node node) {
        if (node != null) {
            return "var_" + node.getId();
        } else {
            return "null";
        }
    }

}
