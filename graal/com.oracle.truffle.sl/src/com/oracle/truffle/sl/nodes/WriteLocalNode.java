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

import java.math.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.api.frame.*;

public abstract class WriteLocalNode extends FrameSlotNode {

    @Child protected TypedNode rightNode;

    public WriteLocalNode(FrameSlot slot, TypedNode right) {
        super(slot);
        this.rightNode = adoptChild(right);
    }

    public WriteLocalNode(WriteLocalNode node) {
        this(node.slot, node.rightNode);
    }

    @Specialization
    public int doInteger(VirtualFrame frame, int right) {
        frame.setInt(slot, right);
        return right;
    }

    @Specialization
    public BigInteger doBigInteger(VirtualFrame frame, BigInteger right) {
        frame.setObject(slot, right);
        return right;
    }

    @Specialization
    public boolean doBoolean(VirtualFrame frame, boolean right) {
        frame.setBoolean(slot, right);
        return right;
    }

    @Specialization
    public String doString(VirtualFrame frame, String right) {
        frame.setObject(slot, right);
        return right;
    }

    @Generic
    public Object doGeneric(VirtualFrame frame, Object right) {
        frame.setObject(slot, right);
        return right;
    }

    @SpecializationListener
    protected void onSpecialize(VirtualFrame frame, Object value) {
        slot.setType(value.getClass());
        frame.updateToLatestVersion();
    }

    @Override
    protected FrameSlotNode specialize(Class<?> clazz) {
        return WriteLocalNodeFactory.createSpecialized(this, clazz);
    }

}
