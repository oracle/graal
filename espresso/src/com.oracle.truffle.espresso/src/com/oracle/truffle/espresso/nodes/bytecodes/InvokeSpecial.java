/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * INVOKESPECIAL bytecode.
 *
 * <p>
 * The receiver must be the rist element of the arguments passed to {@link #execute(Object[])}. e.g.
 * </p>
 *
 * <p>
 * Method resolution does not perform any access checks, the caller is responsible to pass a
 * compatible receiver.
 * </p>
 */
@NodeInfo(shortName = "INVOKESPECIAL")
public abstract class InvokeSpecial extends EspressoNode {

    final Method method;

    InvokeSpecial(Method method) {
        this.method = method;
    }

    public abstract Object execute(Object[] args);

    @Specialization
    Object doWithNullCheck(Object[] args,
                    @Cached NullCheck nullCheck,
                    @Cached("create(method)") WithoutNullCheck invokeSpecial) {
        StaticObject receiver = (StaticObject) args[0];
        nullCheck.execute(receiver);
        return invokeSpecial.execute(args);
    }

    static Method.MethodVersion methodLookup(Method method, StaticObject receiver) {
        if (method.isRemovedByRedefinition()) {
            /*
             * Accept a slow path once the method has been removed put method behind a boundary to
             * avoid a deopt loop.
             */
            return method.getContext().getClassRedefinition().handleRemovedMethod(method, receiver.getKlass()).getMethodVersion();
        }
        return method.getMethodVersion();
    }

    @ImportStatic({InvokeSpecial.class, Utils.class})
    @NodeInfo(shortName = "INVOKESPECIAL !nullcheck")
    public abstract static class WithoutNullCheck extends EspressoNode {

        final Method method;

        WithoutNullCheck(Method method) {
            this.method = method;
        }

        public abstract Object execute(Object[] args);

        static StaticObject getReceiver(Object[] args) {
            return (StaticObject) args[0];
        }

        @SuppressWarnings("unused")
        @Specialization(assumptions = "resolvedMethod.getRedefineAssumption()")
        public Object callDirect(Object[] args,
                        @Bind("getReceiver(args)") StaticObject receiver,
                        // TODO(peterssen): Use the method's declaring class instead of the first
                        // receiver's class?
                        @Cached("methodLookup(method, receiver)") Method.MethodVersion resolvedMethod,
                        @Cached("createAndMaybeForceInline(resolvedMethod)") DirectCallNode directCallNode) {
            assert !StaticObject.isNull(receiver);
            return directCallNode.call(args);
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "callDirect")
        Object callIndirect(Object[] args,
                        @Cached IndirectCallNode indirectCallNode) {
            StaticObject receiver = (StaticObject) args[0];
            assert !StaticObject.isNull(receiver);
            Method.MethodVersion resolvedMethod = methodLookup(method, receiver);
            return indirectCallNode.call(resolvedMethod.getCallTarget(), receiver, args);
        }
    }

    @GenerateUncached
    @NodeInfo(shortName = "INVOKESPECIAL dynamic")
    public abstract static class Dynamic extends EspressoNode {

        public abstract Object execute(Method method, Object[] args);

        @Specialization
        Object doWithNullCheck(Method method, Object[] args,
                        @Cached NullCheck nullCheck,
                        @Cached WithoutNullCheck invokeSpecial) {
            StaticObject receiver = (StaticObject) args[0];
            nullCheck.execute(receiver);
            return invokeSpecial.execute(method, args);
        }

        @GenerateUncached
        @NodeInfo(shortName = "INVOKESPECIAL dynamic !nullcheck")
        public abstract static class WithoutNullCheck extends EspressoNode {

            protected static final int LIMIT = 4;

            public abstract Object execute(Method method, Object[] args);

            static StaticObject getReceiver(Object[] args) {
                return (StaticObject) args[0];
            }

            @SuppressWarnings("unused")
            @Specialization(limit = "LIMIT", //
                            guards = "method == cachedMethod")
            public Object callDirect(Method method, Object[] args,
                            @Bind("getReceiver(args)") StaticObject receiver,
                            @Cached("method") Method cachedMethod,
                            @Cached("create(method)") InvokeSpecial invokeSpecial) {
                return invokeSpecial.execute(args);
            }

            @ReportPolymorphism.Megamorphic
            @Specialization(replaces = "callDirect")
            Object callIndirect(Method method, Object[] args,
                            @Cached IndirectCallNode indirectCallNode) {
                StaticObject receiver = (StaticObject) args[0];
                assert !StaticObject.isNull(receiver);
                Method.MethodVersion resolvedMethod = methodLookup(method, receiver);
                return indirectCallNode.call(resolvedMethod.getCallTarget(), args);
            }
        }
    }
}
