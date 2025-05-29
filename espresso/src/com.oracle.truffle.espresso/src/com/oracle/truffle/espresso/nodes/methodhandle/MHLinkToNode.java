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
package com.oracle.truffle.espresso.nodes.methodhandle;

import static com.oracle.truffle.espresso.nodes.methodhandle.LinkToInterfaceNode.interfaceLinker;
import static com.oracle.truffle.espresso.nodes.methodhandle.LinkToSpecialNode.specialLinker;
import static com.oracle.truffle.espresso.nodes.methodhandle.LinkToStaticNode.staticLinker;
import static com.oracle.truffle.espresso.nodes.methodhandle.LinkToVirtualNode.virtualLinker;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeDynamicCallSiteNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Performs the actual job of an invocation:
 * <li>Obtain the trailing MemberName from the arguments, and extract its payload.
 * <li>Perform method lookup if needed on the given receiver.
 * <li>Execute the payload on the given arguments, stripped from the given MemberName
 *
 * Note that there is a small overhead, as the method invoked is usually the actual payload (in
 * opposition to the other MH nodes, who usually either calls the type checkers, or performs type
 * checks on erased primitives), whose signature is not sub-workd erased. Unfortunately, the
 * under-the-carpet machinery created by guest java code returns sub-words erased to ints. We thus
 * need to restore the kind of each argument before executing the payload, and since this method is
 * called from the black box, we need to erase the kind of the result to int if needed.
 */
public abstract class MHLinkToNode extends MethodHandleIntrinsicNode {
    private final int argCount;
    private final Linker linker;
    private final Field hiddenVmtarget;
    private final boolean hasReceiver;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    MHLinkToNode(Method method, MethodHandleIntrinsics.PolySigIntrinsics id) {
        super(method);
        this.argCount = SignatureSymbols.parameterCount(method.getParsedSignature());
        this.hiddenVmtarget = method.getMeta().HIDDEN_VMTARGET;
        this.hasReceiver = id != MethodHandleIntrinsics.PolySigIntrinsics.LinkToStatic;
        this.linker = findLinker(id);
        assert method.isStatic();
    }

    @Override
    public Object call(Object[] args) {
        assert (getMethod().isStatic());
        Method.MethodVersion resolutionSeed = getTarget(args);
        Object[] basicArgs = unbasic(args, resolutionSeed.getMethod().getParsedSignature(), 0, argCount - 1, hasReceiver);
        // method might have been redefined or removed by redefinition
        if (!resolutionSeed.getRedefineAssumption().isValid()) {
            if (resolutionSeed.getMethod().isRemovedByRedefinition()) {
                Klass receiverKlass = hasReceiver ? ((StaticObject) basicArgs[0]).getKlass() : resolutionSeed.getMethod().getDeclaringKlass();
                resolutionSeed = EspressoContext.get(this).getClassRedefinition().handleRemovedMethod(resolutionSeed.getMethod(), receiverKlass).getMethodVersion();
            }
        }

        Method target = linker.linkTo(resolutionSeed.getMethod(), args);
        Object result = executeCall(basicArgs, target.getMethodVersion());
        return rebasic(result, target.getReturnKind());
    }

    protected abstract Object executeCall(Object[] args, Method.MethodVersion target);

    public static boolean canInline(Method.MethodVersion target, Method.MethodVersion cachedTarget) {
        return target.getMethod().identity() == cachedTarget.getMethod().identity();
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = {"inliningEnabled()", "canInline(target, cachedTarget)"})
    Object doCallDirect(Object[] args, Method.MethodVersion target,
                    @Cached("target") Method.MethodVersion cachedTarget,
                    @Cached("create(target.getCallTarget())") DirectCallNode directCallNode) {
        hits.inc();
        return directCallNode.call(args);
    }

    @Specialization(replaces = "doCallDirect")
    Object doCallIndirect(Object[] args, Method.MethodVersion target,
                    @Cached("create()") IndirectCallNode callNode) {
        miss.inc();
        return callNode.call(target.getCallTarget(), args);
    }

    @ExplodeLoop
    static Object[] unbasic(Object[] args, Symbol<Type>[] targetSig, int from, int length, boolean inclReceiver) {
        Object[] res = new Object[length];
        int start = 0;
        if (inclReceiver) {
            res[start++] = args[from];
        }
        for (int i = start; i < length; i++) {
            Symbol<Type> t = SignatureSymbols.parameterType(targetSig, i - start);
            res[i] = InvokeDynamicCallSiteNode.unbasic(args[i + from], t);
        }
        return res;
    }

    // Tranform sub-words to int
    public static Object rebasic(Object obj, JavaKind rKind) {
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

    private static Linker findLinker(MethodHandleIntrinsics.PolySigIntrinsics id) {
        switch (id) {
            case LinkToVirtual:
                return virtualLinker;
            case LinkToStatic:
                return staticLinker;
            case LinkToSpecial:
                return specialLinker;
            case LinkToInterface:
                return interfaceLinker;
            default:
                throw EspressoError.shouldNotReachHere("unrecognized linkTo intrinsic: " + id);
        }
    }

    private Method.MethodVersion getTarget(Object[] args) {
        assert args.length >= 1;
        StaticObject memberName = (StaticObject) args[args.length - 1];
        assert (memberName.getKlass().getType() == Types.java_lang_invoke_MemberName);
        return ((Method) hiddenVmtarget.getHiddenObject(memberName)).getMethodVersion();
    }
}
