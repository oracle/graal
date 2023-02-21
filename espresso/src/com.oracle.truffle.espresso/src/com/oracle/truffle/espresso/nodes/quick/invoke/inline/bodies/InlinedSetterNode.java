/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick.invoke.inline.bodies;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.helper.AbstractSetFieldNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedFrameAccess;
import com.oracle.truffle.espresso.runtime.StaticObject;

class InlinedSetterNode extends InlinedFieldAccessNode {

    @Child AbstractSetFieldNode setFieldNode;
    private final int slotCount;

    InlinedSetterNode(Method.MethodVersion method, char fieldCpi) {
        super(method, fieldCpi);
        this.setFieldNode = insert(AbstractSetFieldNode.create(field));
        this.slotCount = field.getKind().getSlotCount();
    }

    @Override
    public void execute(VirtualFrame frame, InlinedFrameAccess frameAccess) {
        assert !field.needsReResolution(); // Should have been guaranteed before calling.
        StaticObject receiver = getReceiver(frame, field, frameAccess.top(), slotCount);
        setFieldNode.setField(frame, frameAccess.getBytecodeNode(), receiver, frameAccess.top(), frameAccess.statementIndex());
    }

    private static StaticObject getReceiver(VirtualFrame frame, Field field, int top, int slotCount) {
        return field.isStatic()
                        ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                        : EspressoFrame.popObject(frame, top - 1 - slotCount);
    }
}
