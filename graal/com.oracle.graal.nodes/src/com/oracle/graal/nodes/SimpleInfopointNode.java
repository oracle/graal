/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo
public class SimpleInfopointNode extends InfopointNode implements LIRLowerable, IterableNodeType, Simplifiable {
    protected BytecodePosition position;

    public static SimpleInfopointNode create(InfopointReason reason, BytecodePosition position) {
        return USE_GENERATED_NODES ? new SimpleInfopointNodeGen(reason, position) : new SimpleInfopointNode(reason, position);
    }

    protected SimpleInfopointNode(InfopointReason reason, BytecodePosition position) {
        super(reason);
        this.position = position;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.visitSimpleInfopointNode(this);
    }

    public BytecodePosition getPosition() {
        return position;
    }

    public void addCaller(BytecodePosition caller) {
        this.position = relink(this.position, caller);
    }

    private static BytecodePosition relink(BytecodePosition position, BytecodePosition link) {
        if (position.getCaller() == null) {
            return new BytecodePosition(link, position.getMethod(), position.getBCI());
        } else {
            return new BytecodePosition(relink(position.getCaller(), link), position.getMethod(), position.getBCI());
        }
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (next() instanceof SimpleInfopointNode) {
            graph().removeFixed(this);
        }
    }
}
