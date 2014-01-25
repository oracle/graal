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
package com.oracle.truffle.sl.nodes.local;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.sl.nodes.*;

@NodeChild(value = "rightNode", type = SLExpressionNode.class)
public abstract class WriteLocalNode extends SLExpressionNode {

    private final FrameSlot slot;

    public WriteLocalNode(FrameSlot slot) {
        this.slot = slot;
    }

    public WriteLocalNode(WriteLocalNode node) {
        this(node.slot);
    }

    @Specialization(guards = "isLongKind")
    protected final long write(VirtualFrame frame, long right) {
        frame.setLong(slot, right);
        return right;
    }

    @Specialization(guards = "isBooleanKind")
    protected final boolean write(VirtualFrame frame, boolean right) {
        frame.setBoolean(slot, right);
        return right;
    }

    @Specialization(guards = "isObjectKind")
    protected final Object writeGeneric(VirtualFrame frame, Object right) {
        frame.setObject(slot, right);
        return right;
    }

    protected final boolean isLongKind() {
        return isKind(FrameSlotKind.Long);
    }

    protected final boolean isBooleanKind() {
        return isKind(FrameSlotKind.Boolean);
    }

    protected final boolean isObjectKind() {
        if (slot.getKind() != FrameSlotKind.Object) {
            CompilerDirectives.transferToInterpreter();
            slot.setKind(FrameSlotKind.Object);
        }
        return true;
    }

    private boolean isKind(FrameSlotKind kind) {
        return slot.getKind() == kind || initialSetKind(kind);
    }

    private boolean initialSetKind(FrameSlotKind kind) {
        if (slot.getKind() == FrameSlotKind.Illegal) {
            CompilerDirectives.transferToInterpreter();
            slot.setKind(kind);
            return true;
        }
        return false;
    }
}
