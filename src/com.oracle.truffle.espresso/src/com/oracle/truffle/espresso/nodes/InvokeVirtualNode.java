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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class InvokeVirtualNode extends QuickNode {

    final Method resolutionSeed;
    final int vtableIndex;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract Object executeVirtual(StaticObject receiver, Object[] args);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "receiver.getKlass() == cachedKlass")
    Object callVirtualDirect(StaticObject receiver, Object[] args,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("methodLookup(receiver, vtableIndex)") Method resolvedMethod,
                    @Cached("create(resolvedMethod.getCallTarget())") DirectCallNode directCallNode) {
        return directCallNode.call(args);
    }

    @Specialization(replaces = "callVirtualDirect")
    Object callVirtualIndirect(StaticObject receiver, Object[] arguments,
                    @Cached("create()") IndirectCallNode indirectCallNode) {
        // vtable lookup.
        Method target = methodLookup(receiver, vtableIndex);
        if (!target.hasCode()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw receiver.getKlass().getMeta().throwEx(AbstractMethodError.class);
        }
        return indirectCallNode.call(target.getCallTarget(), arguments);
    }

    InvokeVirtualNode(Method resolutionSeed) {
        assert !resolutionSeed.isStatic();
        this.resolutionSeed = resolutionSeed;
        this.vtableIndex = resolutionSeed.getVTableIndex();
    }

    static Method methodLookup(StaticObject receiver, int vtableIndex) {
        // Suprisingly, invokeVirtuals can try to invoke interface methods, even non-default
        // ones.
        // Good thing is, miranda methods are taken care of at vtable creation !
        Klass receiverKlass = receiver.getKlass();
        if (receiverKlass.isArray()) {
            return receiverKlass.getSuperKlass().vtableLookup(vtableIndex);
        }
        return ((ObjectKlass) receiverKlass).vtableLookup(vtableIndex);
    }

    @Override
    public final int invoke(final VirtualFrame frame, int top) {
        // Method signature does not change across methods.
        // Can safely use the constant signature from `resolutionSeed` instead of the non-constant
        // signature from the lookup.
        // TODO(peterssen): Maybe refrain from exposing the whole root node?.
        BytecodeNode root = (BytecodeNode) getParent();
        // TODO(peterssen): IsNull Node?.
        StaticObject receiver = root.peekReceiver(frame, top, resolutionSeed);
        nullCheck(receiver);
        Object[] args = root.peekArguments(frame, top, true, resolutionSeed.getParsedSignature());
        assert receiver != null;
        assert receiver == args[0] : "receiver must be the first argument";
        Object result = executeVirtual(receiver, args);
        int resultAt = top - Signatures.slotsForParameters(resolutionSeed.getParsedSignature()) - 1; // -receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, resolutionSeed.getReturnKind());
    }
}
