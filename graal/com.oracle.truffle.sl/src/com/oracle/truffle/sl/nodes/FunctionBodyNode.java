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

public class FunctionBodyNode extends TypedNode {

    @Child private StatementNode body;
    @Child private TypedNode returnValue;
    @Child private StatementNode writeArguments;

    private FrameDescriptor frameDescriptor;

    public FunctionBodyNode(FrameDescriptor frameDescriptor, StatementNode body, TypedNode returnValue, String[] parameterNames) {
        this.frameDescriptor = frameDescriptor;
        this.body = adoptChild(body);
        this.returnValue = adoptChild(returnValue);
        this.writeArguments = adoptChild(new BlockNode(createWriteArguments(parameterNames)));
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        writeArguments.executeVoid(frame);
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

    @Override
    public Node copy() {
        FunctionBodyNode copy = (FunctionBodyNode) super.copy();
        copy.frameDescriptor = frameDescriptor.shallowCopy();
        return copy;
    }

    private StatementNode[] createWriteArguments(String[] parameterNames) {
        StatementNode[] writeNodes = new StatementNode[parameterNames.length];
        for (int i = 0; i < parameterNames.length; i++) {
            FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(parameterNames[i]);
            writeNodes[i] = WriteLocalNodeFactory.create(frameSlot, new ReadArgumentNode(i));
        }
        return writeNodes;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

}
