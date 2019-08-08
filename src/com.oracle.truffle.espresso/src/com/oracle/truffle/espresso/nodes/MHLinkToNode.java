/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class MHLinkToNode extends EspressoBaseNode {
    final int argCount;
    final MethodHandleIntrinsics.PolySigIntrinsics id;
    @Child IndirectCallNode callNode;
    private final int hidden_vmtarget;
    private final boolean hasReceiver;

    MHLinkToNode(Method method, MethodHandleIntrinsics.PolySigIntrinsics id) {
        super(method);
        this.id = id;
        this.argCount = Signatures.parameterCount(getMethod().getParsedSignature(), false);
        this.callNode = IndirectCallNode.create();
        this.hidden_vmtarget = getMeta().HIDDEN_VMTARGET.getFieldIndex();
        this.hasReceiver = id != MethodHandleIntrinsics.PolySigIntrinsics.LinkToStatic;
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        assert (getMethod().isStatic());
        Method target = getTarget(frame.getArguments());
        Object[] args = unbasic(frame.getArguments(), target.getParsedSignature(), 0, argCount - 1, hasReceiver);
        return rebasic(linkTo(target, args), target.getReturnKind());
    }

    protected abstract Object linkTo(Method target, Object[] args);

    @ExplodeLoop
    static Object[] unbasic(Object[] args, Symbol<Type>[] targetSig, int from, int length, boolean inclReceiver) {
        Object[] res = new Object[length];
        int start = 0;
        if (inclReceiver) {
            res[start++] = args[from];
        }
        for (int i = start; i < length; i++) {
            Symbol<Type> t = Signatures.parameterType(targetSig, i - start);
            res[i] = InvokeDynamicCallSiteNode.unbasic(args[i + from], t);
        }
        return res;
    }

    // Tranform sub-words to int
    private static Object rebasic(Object obj, JavaKind rKind) {
        switch (rKind) {
            case Boolean:
                return ((boolean) obj) ? 1 : 0;
            case Byte:
                return (int) ((byte) obj);
            case Char:
                return (int) ((char) obj);
            case Short:
                return (int) ((short) obj);
            default:
                return obj;
        }
    }

    public static MHLinkToNode create(Method method, MethodHandleIntrinsics.PolySigIntrinsics id) {
        switch (id) {
            case LinkToVirtual:
                return new LinkToVirtualNode(method);
            case LinkToStatic:
                return new LinkToStaticNode(method);
            case LinkToSpecial:
                return new LinkToSpecialNode(method);
            case LinkToInterface:
                return new LinkToInterfaceNode(method);
            default:
                throw EspressoError.shouldNotReachHere("unrecognized linkTo intrinsic: " + id);
        }
    }

    private final Method getTarget(Object[] args) {
        assert args.length >= 1;
        StaticObject memberName = (StaticObject) args[args.length - 1];
        assert (memberName.getKlass().getType() == Symbol.Type.MemberName);
        Method target = (Method) memberName.getUnsafeField(hidden_vmtarget);
        return target;
    }
}