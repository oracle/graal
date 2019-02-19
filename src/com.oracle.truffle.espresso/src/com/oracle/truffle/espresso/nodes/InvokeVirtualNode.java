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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public abstract class InvokeVirtualNode extends QuickNode {

    final Method resolutionSeed;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract Object executeVirtual(StaticObject receiver, Object[] args);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "receiver.getKlass() == cachedKlass")
    Object callVirtualDirect(StaticObjectImpl receiver, Object[] args,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("methodLookup(resolutionSeed, receiver)") Method resolvedMethod,
                    @Cached("create(resolvedMethod.getCallTarget())") DirectCallNode directCallNode) {
        return directCallNode.call(args);
    }

    @Specialization(replaces = "callVirtualDirect")
    Object callVirtualIndirect(StaticObject receiver, Object[] arguments,
                    @Cached("create()") IndirectCallNode indirectCallNode) {
        // Brute virtual method resolution, walk the whole klass hierarchy.
        // TODO(peterssen): Implement itable-based lookup.
        Method targetMethod = methodLookup(resolutionSeed, receiver);
        return indirectCallNode.call(targetMethod.getCallTarget(), arguments);
    }

    InvokeVirtualNode(Method resolutionSeed) {
        assert !resolutionSeed.isStatic();
        this.resolutionSeed = resolutionSeed;
    }

    @TruffleBoundary
    static Method methodLookup(Method resolutionSeed, StaticObject receiver) {
        // TODO(peterssen): Method lookup is uber-slow and non-spec-compliant.
        Klass clazz = receiver.getKlass();
        Method m = clazz.lookupMethod(resolutionSeed.getName(), resolutionSeed.getRawSignature());

        while (m == null && clazz != null) {
            // FIXME(peterssen): Out-of-spec lookup for default (interface) method.
            m = lookupDefaultInterfaceMethod((ObjectKlass) clazz, resolutionSeed.getName(), resolutionSeed.getRawSignature());
            clazz = clazz.getSuperKlass();
        }

        return m;
    }

    @Override
    public final int invoke(final VirtualFrame frame, int top) {
        // Method signature does not change across methods.
        // Can safely use the constant signature from `resolutionSeed` instead of the non-constant
        // signature from the lookup.
        // TODO(peterssen): Maybe refrain from exposing the whole root node?.
        BytecodeNode root = (BytecodeNode) getParent();
        // TODO(peterssen): IsNull Node?.
        StaticObject receiver = nullCheck(root.peekReceiver(frame, top, resolutionSeed));
        Object[] args = root.peekArguments(frame, top, true, resolutionSeed.getParsedSignature());
        assert receiver != null;
        assert receiver == args[0] : "receiver must be the first argument";
        Object result = executeVirtual(receiver, args);
        int resultAt = top - Signatures.slotsForParameters(resolutionSeed.getParsedSignature()) - 1; // -receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, resolutionSeed.getReturnKind());
    }
}
