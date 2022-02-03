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
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MethodHandleIntrinsicNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class InvokeHandleNode extends QuickNode {

    private final Method method;
    final int resultAt;

    @CompilationFinal(dimensions = 1) //
    private final Symbol<Type>[] parsedSignature;

    @Child private MethodHandleIntrinsicNode intrinsic;
    private final boolean hasReceiver;
    private final int argCount;
    private final int parameterCount;
    private final JavaKind rKind;
    private final boolean returnsPrimitiveType;

    public InvokeHandleNode(Method method, Klass accessingKlass, int top, int curBCI) {
        super(top, curBCI);
        this.method = method;
        this.parsedSignature = method.getParsedSignature();
        this.hasReceiver = !method.isStatic();
        this.intrinsic = method.spawnIntrinsicNode(accessingKlass, method.getName(), method.getRawSignature());
        this.argCount = method.getParameterCount() + (method.isStatic() ? 0 : 1) + (method.isInvokeIntrinsic() ? 1 : 0);
        this.parameterCount = method.getParameterCount();
        this.rKind = method.getReturnKind();
        this.resultAt = top - Signatures.slotsForParameters(method.getParsedSignature()) - (hasReceiver ? 1 : 0); // -receiver
        this.returnsPrimitiveType = Types.isPrimitive(Signatures.returnType(method.getParsedSignature()));
    }

    @Override
    public int execute(VirtualFrame frame) {
        Object[] args = new Object[argCount];
        if (hasReceiver) {
            args[0] = nullCheck(BytecodeNode.peekReceiver(frame, top, method));
        }
        BytecodeNode.popBasicArgumentsWithArray(frame, top, parsedSignature, args, parameterCount, hasReceiver ? 1 : 0);
        Object result = intrinsic.processReturnValue(intrinsic.call(args), rKind);
        if (!returnsPrimitiveType) {
            getBytecodeNode().checkNoForeignObjectAssumption((StaticObject) result);
        }
        return (getResultAt() - top) + BytecodeNode.putKind(frame, getResultAt(), result, method.getReturnKind());
    }

    private int getResultAt() {
        return resultAt;
    }
}
