/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.EspressoInterop;

public abstract class LookupAndInvokeKnownMethodNode extends Node {
    static final int LIMIT = 3;

    protected final Klass declaringKlass;
    protected final Method method;

    public LookupAndInvokeKnownMethodNode(Klass declaringKlass, Method method) {
        this.declaringKlass = declaringKlass;
        this.method = method;
    }

    public abstract Object execute(StaticObject receiver, Object[] arguments);

    @Specialization(guards = {"method.getParameterCount() == 0", "declaringKlass.isInterface()", "receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
    Object doInterfaceCachedNoArg(StaticObject receiver, Object[] arguments,
                    @SuppressWarnings("unused") @Cached("receiver.getKlass()") Klass cachedKlass,
                    @SuppressWarnings("unused") @Cached("interfaceLookup(receiver)") Method m,
                    @Cached("create(m.getCallTarget())") DirectCallNode callNode) {
        assert 0 == arguments.length;
        return callNode.call(receiver);
    }

    @Specialization(guards = {"method.getParameterCount() == 0", "!declaringKlass.isInterface()", "receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
    Object doVirtualCachedNoArg(StaticObject receiver, Object[] arguments,
                    @SuppressWarnings("unused") @Cached("receiver.getKlass()") Klass cachedKlass,
                    @SuppressWarnings("unused") @Cached("virtualLookup(receiver)") Method m,
                    @Cached("create(m.getCallTarget())") DirectCallNode callNode) {
        assert 0 == arguments.length;
        return callNode.call(receiver);
    }

    @Specialization(guards = {"declaringKlass.isInterface()", "receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
    Object doInterfaceCached(StaticObject receiver, Object[] arguments,
                    @SuppressWarnings("unused") @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("interfaceLookup(receiver)") Method m,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        assert m.getParameterCount() == arguments.length;
        return invoke(invoke, m, receiver, arguments);
    }

    @Specialization(guards = {"!declaringKlass.isInterface()", "receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
    Object doVirtualCached(StaticObject receiver, Object[] arguments,
                    @SuppressWarnings("unused") @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("virtualLookup(receiver)") Method m,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        assert m.getParameterCount() == arguments.length;
        return invoke(invoke, m, receiver, arguments);
    }

    @Specialization(guards = {"declaringKlass.isInterface()"}, replaces = {"doInterfaceCachedNoArg", "doInterfaceCached"})
    Object doInterfaceUncached(StaticObject receiver, Object[] arguments,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return invoke(invoke, interfaceLookup(receiver), receiver, arguments);
    }

    @Specialization(guards = {"!declaringKlass.isInterface()"}, replaces = {"doVirtualCachedNoArg", "doVirtualCached"})
    Object doVirtualUncached(StaticObject receiver, Object[] arguments,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return invoke(invoke, virtualLookup(receiver), receiver, arguments);
    }

    Method interfaceLookup(StaticObject receiver) {
        assert declaringKlass.isAssignableFrom(receiver.getKlass());
        return EspressoInterop.getInteropKlass(receiver).itableLookup(declaringKlass, method.getITableIndex());
    }

    Method virtualLookup(StaticObject receiver) {
        assert declaringKlass.isAssignableFrom(receiver.getKlass());
        return EspressoInterop.getInteropKlass(receiver).vtableLookup(method.getVTableIndex());
    }

    private static Object invoke(InvokeEspressoNode invoke, Method m, StaticObject receiver, Object[] arguments) {
        try {
            return invoke.execute(m, receiver, arguments);
        } catch (ArityException | UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }
}
