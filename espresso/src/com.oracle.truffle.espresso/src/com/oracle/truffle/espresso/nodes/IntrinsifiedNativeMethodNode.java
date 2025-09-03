/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.substitutions.CallableFromNative;
import com.oracle.truffle.espresso.vm.VM;

@ExportLibrary(NodeLibrary.class)
final class IntrinsifiedNativeMethodNode extends EspressoInstrumentableRootNodeImpl {
    @Child private CallableFromNative nativeMethod;
    private final Object env;

    IntrinsifiedNativeMethodNode(Method.MethodVersion methodVersion, CallableFromNative.Factory factory, Object env) {
        super(methodVersion);
        assert CallableFromNative.validParameterCount(factory, methodVersion);
        this.nativeMethod = insert(factory.create());
        this.env = env;
    }

    @Override
    Object execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        Method method = getMethodVersion().getMethod();
        if (method.isStatic()) {
            int parameterCount = method.getParameterCount();
            Object[] newArgs = new Object[parameterCount + 1];
            newArgs[0] = method.getDeclaringKlass().mirror();
            System.arraycopy(args, 0, newArgs, 1, parameterCount);
            args = newArgs;
        }
        return nativeMethod.invokeDirect(env, args);
    }

    @Override
    public int getBci(Frame frame) {
        return VM.EspressoStackElement.NATIVE_BCI;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
        return new SubstitutionScope(frame.getArguments(), getMethodVersion());
    }
}
