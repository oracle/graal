/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MethodHandleIntrinsicNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;

public final class InvokeHandleNode extends QuickNode {

    private final Method method;

    @CompilationFinal(dimensions = 1) //
    private final Symbol<Type>[] parsedSignature;

    @Child private MethodHandleIntrinsicNode intrinsic;
    private final boolean hasReceiver;
    private final int argCount;
    private final int parameterCount;
    private final JavaKind rKind;

    public InvokeHandleNode(Method method, Klass accessingKlass, int top, int curBCI) {
        super(top, curBCI);
        this.method = method;
        this.parsedSignature = method.getParsedSignature();
        this.hasReceiver = !method.isStatic();
        this.intrinsic = method.spawnIntrinsicNode(accessingKlass, method.getName(), method.getRawSignature());
        this.argCount = method.getParameterCount() + (method.isStatic() ? 0 : 1) + (method.isMethodHandleInvokeIntrinsic() ? 1 : 0);
        this.parameterCount = method.getParameterCount();
        this.rKind = method.getReturnKind();
    }

    @Override
    public int execute(VirtualFrame frame) {
        BytecodeNode root = getBytecodesNode();
        Object[] args = new Object[argCount];
        if (hasReceiver) {
            args[0] = nullCheck(root.peekReceiver(frame, top, method));
        }
        root.peekAndReleaseBasicArgumentsWithArray(frame, top, parsedSignature, args, parameterCount, hasReceiver ? 1 : 0);
        Object result = unbasic(intrinsic.call(args), rKind);
        int resultAt = top - Signatures.slotsForParameters(method.getParsedSignature()) - (hasReceiver ? 1 : 0); // -receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, method.getReturnKind());
    }

    private static Object unbasic(Object obj, JavaKind kind) {
        switch (kind) {
            case Boolean:
                return ((int) obj != 0);
            case Byte:
                return ((byte) (int) obj);
            case Char:
                return ((char) (int) obj);
            case Short:
                return ((short) (int) obj);
            default:
                return obj;
        }
    }
}
