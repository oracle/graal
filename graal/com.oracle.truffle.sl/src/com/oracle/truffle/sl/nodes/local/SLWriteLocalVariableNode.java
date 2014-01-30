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
package com.oracle.truffle.sl.nodes.local;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.sl.nodes.*;

@NodeChild(value = "valueNode")
@NodeField(name = "slot", type = FrameSlot.class)
public abstract class SLWriteLocalVariableNode extends SLExpressionNode {

    protected abstract FrameSlot getSlot();

    @Specialization(guards = "isLongKind")
    protected long write(VirtualFrame frame, long value) {
        frame.setLong(getSlot(), value);
        return value;
    }

    @Specialization(guards = "isBooleanKind")
    protected boolean write(VirtualFrame frame, boolean value) {
        frame.setBoolean(getSlot(), value);
        return value;
    }

    @Specialization
    protected Object write(VirtualFrame frame, Object value) {
        if (getSlot().getKind() != FrameSlotKind.Object) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getSlot().setKind(FrameSlotKind.Object);
        }
        frame.setObject(getSlot(), value);
        return value;
    }

    protected boolean isLongKind() {
        return isKind(FrameSlotKind.Long);
    }

    protected boolean isBooleanKind() {
        return isKind(FrameSlotKind.Boolean);
    }

    private boolean isKind(FrameSlotKind kind) {
        if (getSlot().getKind() == kind) {
            return true;
        } else if (getSlot().getKind() == FrameSlotKind.Illegal) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getSlot().setKind(kind);
            return true;
        } else {
            return false;
        }
    }
}
