/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoNode;

/**
 * INVOKESTATIC bytecode.
 *
 * <h3>Note</h3> The declaring class must be initialized before executing the method. Linking native
 * methods before, so class initialization must happen before the call target is computed.
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link AbstractMethodError} if the resolved method is abstract.</li>
 * </ul>
 */
@NodeInfo(shortName = "INVOKESTATIC")
public abstract class InvokeStatic extends EspressoNode {

    final Method staticMethod;

    InvokeStatic(Method staticMethod) {
        assert staticMethod.isStatic();
        this.staticMethod = staticMethod;
    }

    public Method getStaticMethod() {
        return staticMethod;
    }

    public abstract Object execute(Object[] args);

    @Specialization
    Object callWithClassInitCheck(Object[] args,
                    @Cached InitCheck initCheck,
                    @Cached("create(staticMethod)") WithoutClassInitCheck invokeStatic) {
        initCheck.execute(staticMethod.getDeclaringKlass());
        return invokeStatic.execute(args);
    }

    @ImportStatic({InvokeStatic.class, Utils.class})
    @NodeInfo(shortName = "INVOKESTATIC !initcheck")
    public abstract static class WithoutClassInitCheck extends EspressoNode {

        protected static final int LIMIT = 2;

        final Method staticMethod;

        WithoutClassInitCheck(Method staticMethod) {
            assert staticMethod.isStatic();
            this.staticMethod = staticMethod;
        }

        public abstract Object execute(Object[] args);

        @SuppressWarnings("unused")
        @Specialization(assumptions = "resolvedMethod.getRedefineAssumption()")
        Object callDirect(Object[] args,
                        @Cached("methodLookup(staticMethod)") Method.MethodVersion resolvedMethod,
                        @Cached("createAndMaybeForceInline(resolvedMethod)") DirectCallNode directCallNode) {
            return directCallNode.call(args);
        }

        @Specialization(replaces = "callDirect")
        Object callIndirect(Object[] args,
                        @Cached IndirectCallNode indirectCallNode) {
            Method.MethodVersion target = methodLookup(staticMethod);
            return indirectCallNode.call(target.getCallTarget(), args);
        }
    }

    static Method.MethodVersion methodLookup(Method staticMethod) {
        assert staticMethod.isStatic();
        if (staticMethod.isRemovedByRedefinition()) {
            /*
             * Accept a slow path once the method has been removed put method behind a boundary to
             * avoid a deopt loop.
             */
            return staticMethod.getContext().getClassRedefinition().handleRemovedMethod(staticMethod, staticMethod.getDeclaringKlass()).getMethodVersion();
        }
        return staticMethod.getMethodVersion();
    }

    @GenerateUncached
    @NodeInfo(shortName = "INVOKESTATIC dynamic")
    public abstract static class Dynamic extends EspressoNode {

        public abstract Object execute(Method staticMethod, Object[] args);

        @Specialization
        Object callWithClassInitCheck(Method staticMethod, Object[] args,
                        @Cached InitCheck initCheck,
                        @Cached WithoutClassInitCheck invokeStatic) {
            initCheck.execute(staticMethod.getDeclaringKlass());
            return invokeStatic.execute(staticMethod, args);
        }

        @GenerateUncached
        @NodeInfo(shortName = "INVOKESTATIC dynamic !initcheck")
        public abstract static class WithoutClassInitCheck extends EspressoNode {

            protected static final int LIMIT = 2;

            public abstract Object execute(Method staticMethod, Object[] args);

            @SuppressWarnings("unused")
            @Specialization(limit = "LIMIT", //
                            guards = "staticMethod == cachedStaticMethod")
            Object callDirect(Method staticMethod, Object[] args,
                            @Cached("staticMethod") Method cachedStaticMethod,
                            @Cached("create(cachedStaticMethod)") InvokeStatic.WithoutClassInitCheck invokeStatic) {
                return invokeStatic.execute(args);
            }

            @Specialization(replaces = "callDirect")
            Object callIndirect(Method staticMethod, Object[] args,
                            @Cached IndirectCallNode indirectCallNode) {
                Method.MethodVersion target = methodLookup(staticMethod);
                return indirectCallNode.call(target.getCallTarget(), args);
            }
        }
    }
}
