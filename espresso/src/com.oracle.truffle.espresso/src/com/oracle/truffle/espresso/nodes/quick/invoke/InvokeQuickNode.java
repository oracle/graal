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

package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class InvokeQuickNode extends QuickNode {
    protected final Method.MethodVersion method;

    // Helper information for easier arguments handling.
    protected final int resultAt;
    protected final int stackEffect;

    // Helps check for no foreign objects
    private final boolean returnsPrimitive;

    public InvokeQuickNode(Method m, int top, int callerBCI) {
        super(top, callerBCI);
        this.method = m.getMethodVersion();
        this.resultAt = top - (Signatures.slotsForParameters(m.getParsedSignature()) + (m.hasReceiver() ? 1 : 0));
        this.stackEffect = (resultAt - top) + m.getReturnKind().getSlotCount();
        this.returnsPrimitive = m.getReturnKind().isPrimitive();
    }

    protected final Object[] getArguments(VirtualFrame frame) {
        return EspressoFrame.popArguments(frame, top, !method.isStatic(), method.getMethod().getParsedSignature());
    }

    protected final int pushResult(VirtualFrame frame, Object result) {
        if (!returnsPrimitive) {
            getBytecodeNode().checkNoForeignObjectAssumption((StaticObject) result);
        }
        EspressoFrame.putKind(frame, resultAt, result, method.getMethod().getReturnKind());
        return stackEffect;
    }

    @Override
    public final boolean removedByRedefintion() {
        if (method.getRedefineAssumption().isValid()) {
            return false;
        } else {
            return method.getMethod().isRemovedByRedefition();
        }
    }
}
