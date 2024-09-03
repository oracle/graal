/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class InvokeDynamicCallSiteNode extends QuickNode {

    private final StaticObject appendix;
    private final boolean hasAppendix;
    private final Symbol<Type> returnType;
    private final JavaKind returnKind;
    @Child private DirectCallNode callNode;
    final int resultAt;
    final boolean returnsPrimitiveType;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private Symbol<Type>[] parsedSignature;

    public InvokeDynamicCallSiteNode(StaticObject memberName, StaticObject appendix, Symbol<Type>[] parsedSignature, Meta meta, int top, int curBCI) {
        super(top, curBCI);
        Method target = (Method) meta.HIDDEN_VMTARGET.getHiddenObject(memberName);
        this.appendix = appendix;
        this.parsedSignature = parsedSignature;
        this.returnType = Signatures.returnType(parsedSignature);
        this.returnKind = Signatures.returnKind(parsedSignature);
        this.hasAppendix = !StaticObject.isNull(appendix);
        this.callNode = DirectCallNode.create(target.getCallTarget());
        this.resultAt = top - Signatures.slotsForParameters(parsedSignature); // no receiver
        this.returnsPrimitiveType = Types.isPrimitive(returnType);
    }

    @Override
    public int execute(VirtualFrame frame, boolean isContinuationResume) {
        int argCount = Signatures.parameterCount(parsedSignature);
        Object[] args = EspressoFrame.popBasicArgumentsWithArray(frame, top, parsedSignature, false, new Object[argCount + (hasAppendix ? 1 : 0)]);
        if (hasAppendix) {
            args[args.length - 1] = appendix;
        }
        EspressoThreadLocalState tls = getLanguage().getThreadLocalState();
        tls.blockContinuationSuspension();
        Object result;
        try {
            result = callNode.call(args);
        } finally {
            tls.unblockContinuationSuspension();
        }
        if (!returnsPrimitiveType) {
            getBytecodeNode().checkNoForeignObjectAssumption((StaticObject) result);
        }
        return (getResultAt() - top) + EspressoFrame.putKind(frame, getResultAt(), unbasic(result, returnType), returnKind);
    }

    private int getResultAt() {
        return resultAt;
    }

    // Transforms ints to sub-words
    public static Object unbasic(Object arg, Symbol<Type> t) {
        if (t == Type._boolean) {
            return ((int) arg != 0);
        } else if (t == Type._short) { // Unbox to cast.
            int value = (int) arg;
            return (short) value;
        } else if (t == Type._byte) {
            int value = (int) arg;
            return (byte) value;
        } else if (t == Type._char) {
            int value = (int) arg;
            return (char) value;
        } else {
            return arg;
        }
    }
}
