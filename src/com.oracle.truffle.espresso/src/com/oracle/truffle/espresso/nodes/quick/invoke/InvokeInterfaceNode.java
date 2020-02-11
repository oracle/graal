/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class InvokeInterfaceNode extends QuickNode {

    final Method resolutionSeed;
    final int itableIndex;
    final Klass declaringKlass;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract Object executeInterface(StaticObject receiver, Object[] args);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "receiver.getKlass() == cachedKlass")
    Object callVirtualDirect(StaticObject receiver, Object[] args,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("methodLookup(receiver, itableIndex, declaringKlass)") Method resolvedMethod,
                    @Cached("create(resolvedMethod.getCallTarget())") DirectCallNode directCallNode) {
        return directCallNode.call(args);
    }

    @Specialization(replaces = "callVirtualDirect")
    Object callVirtualIndirect(StaticObject receiver, Object[] arguments,
                    @Cached("create()") IndirectCallNode indirectCallNode) {
        // itable Lookup
        return indirectCallNode.call(methodLookup(receiver, itableIndex, declaringKlass).getCallTarget(), arguments);
    }

    InvokeInterfaceNode(Method resolutionSeed, int top, int curBCI) {
        super(top, curBCI);
        assert !resolutionSeed.isStatic();
        this.resolutionSeed = resolutionSeed;
        this.itableIndex = resolutionSeed.getITableIndex();
        this.declaringKlass = resolutionSeed.getDeclaringKlass();
    }

    static Method methodLookup(StaticObject receiver, int itableIndex, Klass declaringKlass) {
        assert !receiver.getKlass().isArray();
        Method method = ((ObjectKlass) receiver.getKlass()).itableLookup(declaringKlass, itableIndex);
        if (!method.isPublic()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Meta meta = receiver.getKlass().getMeta();
            throw Meta.throwException(meta.java_lang_IllegalAccessError);
        }
        return method;
    }

    @Override
    public final int execute(final VirtualFrame frame) {
        // Method signature does not change across methods.
        // Can safely use the constant signature from `resolutionSeed` instead of the non-constant
        // signature from the lookup.
        // TODO(peterssen): Maybe refrain from exposing the whole root node?.
        BytecodeNode root = getBytecodesNode();
        // TODO(peterssen): IsNull Node?.
        final StaticObject receiver = nullCheck(root.peekReceiver(frame, top, resolutionSeed));
        assert receiver != null;
        final Object[] args = root.peekAndReleaseArguments(frame, top, true, resolutionSeed.getParsedSignature());
        assert receiver == args[0] : "receiver must be the first argument";
        Object result = executeInterface(receiver, args);
        int resultAt = top - Signatures.slotsForParameters(resolutionSeed.getParsedSignature()) - 1; // -receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, Signatures.returnKind(resolutionSeed.getParsedSignature()));
    }
}
