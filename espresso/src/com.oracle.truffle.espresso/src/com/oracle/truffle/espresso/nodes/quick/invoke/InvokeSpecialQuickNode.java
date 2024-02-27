/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeSpecial;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeSpecialNodeGen;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class InvokeSpecialQuickNode extends InvokeQuickNode {

    @Child InvokeSpecial.WithoutNullCheck invokeSpecial;

    public InvokeSpecialQuickNode(Method method, int top, int callerBCI) {
        super(method, top, callerBCI);
        assert !method.isStatic();
        this.invokeSpecial = insert(InvokeSpecialNodeGen.WithoutNullCheckNodeGen.create(method));
    }

    @Override
    public int execute(VirtualFrame frame) {
        Object[] args = getArguments(frame);
        nullCheck((StaticObject) args[0]);
        return pushResult(frame, invokeSpecial.execute(args));
    }
}
