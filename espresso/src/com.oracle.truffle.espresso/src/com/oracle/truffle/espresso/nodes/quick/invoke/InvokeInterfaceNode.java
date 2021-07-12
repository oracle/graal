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
package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;

import com.oracle.truffle.espresso.redefinition.ClassRedefinition;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

@ReportPolymorphism
public abstract class InvokeInterfaceNode extends QuickNode {

    final Method resolutionSeed;
    final Klass declaringKlass;
    final int resultAt;
    final boolean returnsPrimitiveType;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract Object executeInterface(StaticObject receiver, Object[] args);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "receiver.getKlass() == cachedKlass", assumptions = "resolvedMethod.getAssumption()")
    Object callVirtualDirect(StaticObject receiver, Object[] args,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("methodLookup(receiver, resolutionSeed, declaringKlass)") MethodVersion resolvedMethod,
                    @Cached("create(resolvedMethod.getMethod().getCallTargetNoInit())") DirectCallNode directCallNode) {
        // getCallTarget doesn't ensure declaring class is initialized
        // so we need the below check prior to executing the method
        if (!resolvedMethod.getMethod().getDeclaringKlass().isInitialized()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            resolvedMethod.getMethod().getDeclaringKlass().safeInitialize();
        }
        return directCallNode.call(args);
    }

    @ReportPolymorphism.Megamorphic
    @Specialization(replaces = "callVirtualDirect")
    Object callVirtualIndirect(StaticObject receiver, Object[] arguments,
                    @Cached("create()") IndirectCallNode indirectCallNode) {
        // itable Lookup
        return indirectCallNode.call(methodLookup(receiver, resolutionSeed, declaringKlass).getMethod().getCallTarget(), arguments);
    }

    InvokeInterfaceNode(Method resolutionSeed, int top, int curBCI) {
        super(top, curBCI);
        assert !resolutionSeed.isStatic();
        this.resolutionSeed = resolutionSeed;
        this.declaringKlass = resolutionSeed.getDeclaringKlass();
        this.resultAt = top - Signatures.slotsForParameters(resolutionSeed.getParsedSignature()) - 1; // -receiver
        this.returnsPrimitiveType = Types.isPrimitive(Signatures.returnType(resolutionSeed.getParsedSignature()));
    }

    protected static MethodVersion methodLookup(StaticObject receiver, Method resolutionSeed, Klass declaringKlass) {
        assert !receiver.getKlass().isArray();
        if (resolutionSeed.isRemovedByRedefition()) {
            // accept a slow path once the method has been removed
            // put method behind a boundary to avoid a deopt loop
            return ClassRedefinition.handleRemovedMethod(resolutionSeed, receiver.getKlass(), receiver).getMethodVersion();
        }

        int iTableIndex = resolutionSeed.getITableIndex();
        Method method = ((ObjectKlass) receiver.getKlass()).itableLookup(declaringKlass, iTableIndex);
        if (!method.isPublic()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Meta meta = receiver.getKlass().getMeta();
            throw meta.throwException(meta.java_lang_IllegalAccessError);
        }
        return method.getMethodVersion();
    }

    @Override
    public final int execute(VirtualFrame frame, long[] primitives, Object[] refs) {
        /**
         * Method signature does not change across methods. Can safely use the constant signature
         * from `resolutionSeed` instead of the non-constant signature from the lookup.
         */
        final Object[] args = BytecodeNode.popArguments(primitives, refs, top, true, resolutionSeed.getParsedSignature());
        final StaticObject receiver = nullCheck((StaticObject) args[0]);
        Object result = executeInterface(receiver, args);
        if (!returnsPrimitiveType) {
            getBytecodeNode().checkNoForeignObjectAssumption((StaticObject) result);
        }
        return (getResultAt() - top) + BytecodeNode.putKind(primitives, refs, getResultAt(), result, Signatures.returnKind(resolutionSeed.getParsedSignature()));
    }

    private int getResultAt() {
        return resultAt;
    }
}
