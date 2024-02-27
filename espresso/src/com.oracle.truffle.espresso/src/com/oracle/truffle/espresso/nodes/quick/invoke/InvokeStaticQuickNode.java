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
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeStatic;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeStaticNodeGen;
import com.oracle.truffle.espresso.vm.VM;

public final class InvokeStaticQuickNode extends InvokeQuickNode {

    @Child InvokeStatic invokeStatic;
    final boolean isDoPrivilegedCall;

    public InvokeStaticQuickNode(Method method, int top, int curBCI) {
        super(method, top, curBCI);
        assert method.isStatic();
        this.isDoPrivilegedCall = method.getMeta().java_security_AccessController.equals(method.getDeclaringKlass()) &&
                        Name.doPrivileged.equals(method.getName());
        this.invokeStatic = insert(InvokeStaticNodeGen.create(method));
    }

    @Override
    public int execute(VirtualFrame frame) {
        // Support for AccessController.doPrivileged.
        if (isDoPrivilegedCall) {
            EspressoRootNode rootNode = (EspressoRootNode) getRootNode();
            if (rootNode != null) {
                // Put cookie in the caller frame.
                rootNode.setFrameId(frame, VM.GlobalFrameIDs.getID());
            }
        }
        Object[] args = getArguments(frame);
        return pushResult(frame, invokeStatic.execute(args));
    }

    @Override
    public void initializeResolvedKlass() {
        invokeStatic.getStaticMethod().getDeclaringKlass().safeInitialize();
    }
}
