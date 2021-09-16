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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * INVOKESPECIAL bytecode.
 *
 * <p>
 * The receiver must the first element of the arguments passed to {@link #execute(Object[])}.
 * </p>
 *
 * <p>
 * Method resolution does not perform any access checks, the caller is responsible to pass a
 * compatible receiver.
 * </p>
 */
@NodeInfo(shortName = "INVOKEINTERFACE")
public abstract class InvokeInterface extends Node {

    final Method resolutionSeed;

    InvokeInterface(Method resolutionSeed) {
        this.resolutionSeed = resolutionSeed;
    }

    public abstract Object execute(Object[] args);

    @Specialization
    Object executeWithNullCheck(Object[] args,
                    @Cached NullCheck nullCheck,
                    @Cached("create(resolutionSeed)") WithoutNullCheck invokeInterface) {
        StaticObject receiver = (StaticObject) args[0];
        nullCheck.execute(receiver);
        return invokeInterface.execute(args);
    }

    static StaticObject getReceiver(Object[] args) {
        return (StaticObject) args[0];
    }

    @ReportPolymorphism
    @ImportStatic(InvokeInterface.class)
    @NodeInfo(shortName = "INVOKEINTERFACE !nullcheck")
    public abstract static class WithoutNullCheck extends Node {

        protected static final int LIMIT = 8;

        final Method resolutionSeed;

        WithoutNullCheck(Method resolutionSeed) {
            this.resolutionSeed = resolutionSeed;
        }

        public abstract Object execute(Object[] args);

        @SuppressWarnings("unused")
        @Specialization(limit = "LIMIT", //
                        guards = "receiver.getKlass() == cachedKlass", //
                        assumptions = "resolvedMethod.getAssumption()")
        Object callDirect(Object[] args,
                        @Bind("getReceiver(args)") StaticObject receiver,
                        @Cached("receiver.getKlass()") Klass cachedKlass,
                        @Cached("methodLookup(resolutionSeed, receiver)") Method.MethodVersion resolvedMethod,
                        @Cached("create(resolvedMethod.getMethod().getCallTargetNoInit())") DirectCallNode directCallNode) {
            assert !StaticObject.isNull(receiver);
            assert resolvedMethod.getMethod().getDeclaringKlass().isInitializedOrInitializing() : resolvedMethod.getMethod().getDeclaringKlass();
            return directCallNode.call(args);
        }

        @Specialization
        @ReportPolymorphism.Megamorphic
        Object callIndirect(Object[] args,
                        @Cached IndirectCallNode indirectCallNode) {
            StaticObject receiver = (StaticObject) args[0];
            assert !StaticObject.isNull(receiver);
            // itable lookup.
            Method.MethodVersion target = methodLookup(resolutionSeed, receiver);
            assert target.getMethod().getDeclaringKlass().isInitializedOrInitializing() : target.getMethod().getDeclaringKlass();
            return indirectCallNode.call(target.getCallTarget(), args);
        }
    }

    static Method.MethodVersion methodLookup(Method resolutionSeed, StaticObject receiver) {
        assert !receiver.getKlass().isArray();
        if (resolutionSeed.isRemovedByRedefition()) {
            /*
             * Accept a slow path once the method has been removed put method behind a boundary to
             * avoid a deopt loop
             */
            return ClassRedefinition.handleRemovedMethod(resolutionSeed, receiver.getKlass(), receiver).getMethodVersion();
        }

        int iTableIndex = resolutionSeed.getITableIndex();
        Method method = ((ObjectKlass) receiver.getKlass()).itableLookup(resolutionSeed.getDeclaringKlass(), iTableIndex);
        if (!method.isPublic()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Meta meta = receiver.getKlass().getMeta();
            throw meta.throwException(meta.java_lang_IllegalAccessError);
        }
        return method.getMethodVersion();
    }

    @GenerateUncached
    @NodeInfo(shortName = "INVOKEINTERFACE dynamic")
    public abstract static class Dynamic extends Node {

        protected static final int LIMIT = 4;

        public abstract Object execute(Method resolutionSeed, Object[] args);

        @Specialization
        Object executeWithNullCheck(Method resolutionSeed, Object[] args,
                        @Cached NullCheck nullCheck,
                        @Cached WithoutNullCheck invokeInterface) {
            StaticObject receiver = (StaticObject) args[0];
            nullCheck.execute(receiver);
            return invokeInterface.execute(resolutionSeed, args);
        }

        @GenerateUncached
        @ReportPolymorphism
        @NodeInfo(shortName = "INVOKEINTERFACE dynamic !nullcheck")
        public abstract static class WithoutNullCheck extends Node {

            protected static final int LIMIT = 4;

            public abstract Object execute(Method resolutionSeed, Object[] args);

            @Specialization(limit = "LIMIT", //
                            guards = "resolutionSeed == cachedResolutionSeed")
            Object doCached(@SuppressWarnings("unused") Method resolutionSeed, Object[] args,
                            @SuppressWarnings("unused") @Cached("resolutionSeed") Method cachedResolutionSeed,
                            @Cached("create(cachedResolutionSeed)") InvokeInterface.WithoutNullCheck invokeInterface) {
                return invokeInterface.execute(args);
            }

            @ReportPolymorphism.Megamorphic
            @Specialization(replaces = "doCached")
            Object doGeneric(Method resolutionSeed, Object[] args,
                            @Cached IndirectCallNode indirectCallNode) {
                StaticObject receiver = (StaticObject) args[0];
                assert !StaticObject.isNull(receiver);
                Method.MethodVersion target = methodLookup(resolutionSeed, receiver);
                assert target.getMethod().getDeclaringKlass().isInitializedOrInitializing() : target.getMethod().getDeclaringKlass();
                return indirectCallNode.call(target.getCallTarget(), args);
            }
        }
    }
}
