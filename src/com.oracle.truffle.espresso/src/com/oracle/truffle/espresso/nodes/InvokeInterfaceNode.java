/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.bytecode.OperandStack;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public abstract class InvokeInterfaceNode extends InvokeNode {

    final MethodInfo resolutionSeed;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract Object executeVirtual(StaticObject receiver, Object[] arguments);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "receiver.getKlass() == cachedKlass")
    Object callVirtualDirect(StaticObjectImpl receiver, Object[] arguments,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("methodLookup(resolutionSeed, receiver)") MethodInfo resolvedMethod,
                    @Cached("create(resolvedMethod.getCallTarget())") DirectCallNode directCallNode) {
        return directCallNode.call(arguments);
    }

    @Specialization(replaces = "callVirtualDirect")
    Object callVirtualIndirect(StaticObject receiver, Object[] arguments,
                    @Cached("create()") IndirectCallNode indirectCallNode) {
        // Brute virtual method resolution, walk the whole klass hierarchy.
        MethodInfo targetMethod = methodLookup(resolutionSeed, receiver);
        return indirectCallNode.call(targetMethod.getCallTarget(), arguments);
    }

    InvokeInterfaceNode(MethodInfo resolutionSeed) {
        assert !resolutionSeed.isStatic();
        this.resolutionSeed = resolutionSeed;
    }

    @TruffleBoundary
    static MethodInfo methodLookup(MethodInfo resolutionSeed, StaticObject receiver) {
        Klass clazz = ((StaticObjectClass) resolutionSeed.getContext().getJNI().GetObjectClass(receiver)).getMirror();
        return clazz.findConcreteMethod(resolutionSeed.getName(), resolutionSeed.getSignature());
    }

    @Override
    public final void invoke(OperandStack stack) {
        // Method signature does not change.
        StaticObject receiver = nullCheck(stack.peekReceiver(resolutionSeed));
        Object[] arguments = stack.popArguments(true, resolutionSeed.getSignature());
        Object result = executeVirtual(receiver, arguments);
        stack.pushKind(result, resolutionSeed.getSignature().getReturnTypeDescriptor().toKind());
    }
}
