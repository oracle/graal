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
import com.oracle.truffle.api.nodes.*;

public class FunctionDefinitionNode extends RootNode {

    @Child private StatementNode body;

    @Child private TypedNode returnValue;

    private final FrameDescriptor frameDescriptor;
    private final String name;

    public FunctionDefinitionNode(StatementNode body, FrameDescriptor frameDescriptor, String name, TypedNode returnValue) {
        this.body = adoptChild(body);
        this.frameDescriptor = frameDescriptor;
        this.name = name;
        this.returnValue = adoptChild(returnValue);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            body.executeVoid(frame);
        } catch (ReturnException ex) {
            // Nothing to do, we just need to return.
        }
        if (returnValue != null) {
            return returnValue.executeGeneric(frame);
        } else {
            return null;
        }
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    @Override
    public String toString() {
        return "Function " + name;
    }
}
