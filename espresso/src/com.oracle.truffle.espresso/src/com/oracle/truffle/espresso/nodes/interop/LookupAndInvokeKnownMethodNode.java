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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.staticobject.EspressoInterop;

/**
 * This node is a shortcut for implementing behaviors that require doing a virtual/interface lookup
 * then an invocation of a method.
 * <p>
 * Use cases include implementation of interop library messages that would look like
 * 
 * <pre>
 * &#64;ExportMessage
 * static class TheMessage {
 *     
 *     &#64;Specialization(guards = "receiver.getKlass() == cachedKlass")
 *     static Object doCached(StaticObject receiver, Object[] arguments,
 *                     &#64;Cached("receiver.getKlass()") Klass cachedKlass
 *                     &#64;Cached("doLookup(receiver)") Method cachedMethod
 *                     &#64;Cached InvokeEspressoNode invoke) {
 *         invoke.execute(cachedMethod, receiver, arguments);
 *     }
 *     
 *     &#64;Specialization
 *     static Object doUncached(StaticObject receiver, Object[] arguments
 *                     &#64;Cached InvokeEspressoNode invoke) {
 *         Method method = doLookup(receiver);
 *         invoke.execute(method, receiver, arguments);
 *     }
 *     
 *     static Method doLookup(StaticObject receiver) {
 *         getInteropKlass(receiver).itableLookup(getMeta().a_class, getMeta().a_method);
 *     }
 * }
 * </pre>
 * 
 * which can be replaced with:
 * 
 * <pre>
 * 
 * static Object theMessage(StaticObject receiver, Object[] arguments,
 *                 &#64;Cached("getMeta().a_method") Method method,
 *                 &#64;Cached LookupAndInvokeKnownMethodNode invoke) {
 *     return invoke.execute(receiver, arguments, method);
 * }
 * </pre>
 */
@GenerateUncached
@SuppressWarnings("unused")
public abstract class LookupAndInvokeKnownMethodNode extends EspressoNode {
    static final int LIMIT = 3;
    static final Object[] EMPTY_ARGS = new Object[0];

    public final Object execute(StaticObject receiver, Method resolutionSeed) {
        return execute(receiver, resolutionSeed, EMPTY_ARGS);
    }

    public abstract Object execute(StaticObject receiver, Method resolutionSeed, Object[] arguments);

    @Specialization(guards = {"resolutionSeed == cachedSeed", "cachedSeed.getParameterCount() == 0", "cachedSeed.getDeclaringKlass().isInterface()",
                    "receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
    Object doInterfaceCachedNoArg(StaticObject receiver, Method resolutionSeed, Object[] arguments,
                    @Cached("resolutionSeed") Method cachedSeed,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("interfaceLookup(receiver, resolutionSeed)") Method m,
                    @Cached("create(m.getCallTarget())") DirectCallNode callNode) {
        assert 0 == arguments.length;
        return callNode.call(receiver);
    }

    @Specialization(guards = {"resolutionSeed == cachedSeed", "cachedSeed.getParameterCount() == 0", "!cachedSeed.getDeclaringKlass().isInterface()",
                    "receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
    Object doVirtualCachedNoArg(StaticObject receiver, Method resolutionSeed, Object[] arguments,
                    @Cached("resolutionSeed") Method cachedSeed,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("virtualLookup(receiver, resolutionSeed)") Method m,
                    @Cached("create(m.getCallTarget())") DirectCallNode callNode) {
        assert 0 == arguments.length;
        return callNode.call(receiver);
    }

    @Specialization(guards = {"resolutionSeed == cachedSeed", "cachedSeed.getDeclaringKlass().isInterface()", "receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
    Object doInterfaceCached(StaticObject receiver, Method resolutionSeed, Object[] arguments,
                    @Cached("resolutionSeed") Method cachedSeed,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("interfaceLookup(receiver, resolutionSeed)") Method m,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        assert m.getParameterCount() == arguments.length;
        return invoke(invoke, m, receiver, arguments);
    }

    @Specialization(guards = {"resolutionSeed == cachedSeed", "!cachedSeed.getDeclaringKlass().isInterface()", "receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
    Object doVirtualCached(StaticObject receiver, Method resolutionSeed, Object[] arguments,
                    @Cached("resolutionSeed") Method cachedSeed,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("virtualLookup(receiver, resolutionSeed)") Method m,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        assert m.getParameterCount() == arguments.length;
        return invoke(invoke, m, receiver, arguments);
    }

    @Specialization(guards = {"resolutionSeed == cachedSeed", "cachedSeed.getDeclaringKlass().isInterface()"}, replaces = {"doInterfaceCachedNoArg", "doInterfaceCached"}, limit = "1")
    Object doInterfaceUncached(StaticObject receiver, Method resolutionSeed, Object[] arguments,
                    @Cached("resolutionSeed") Method cachedSeed,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return invoke(invoke, interfaceLookup(receiver, cachedSeed), receiver, arguments);
    }

    @Specialization(guards = {"resolutionSeed == cachedSeed", "!cachedSeed.getDeclaringKlass().isInterface()"}, replaces = {"doVirtualCachedNoArg", "doVirtualCached"}, limit = "1")
    Object doVirtualUncached(StaticObject receiver, Method resolutionSeed, Object[] arguments,
                    @Cached("resolutionSeed") Method cachedSeed,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return invoke(invoke, virtualLookup(receiver, cachedSeed), receiver, arguments);
    }

    @Specialization(guards = {"resolutionSeed.getDeclaringKlass().isInterface()"}, replaces = {"doInterfaceUncached"})
    Object doInterfaceUnknown(StaticObject receiver, Method resolutionSeed, Object[] arguments,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return invoke(invoke, interfaceLookup(receiver, resolutionSeed), receiver, arguments);
    }

    @Specialization(guards = {"!resolutionSeed.getDeclaringKlass().isInterface()"}, replaces = {"doVirtualUncached"})
    Object doVirtualUnknown(StaticObject receiver, Method resolutionSeed, Object[] arguments,
                    @Cached.Exclusive @Cached InvokeEspressoNode invoke) {
        return invoke(invoke, virtualLookup(receiver, resolutionSeed), receiver, arguments);
    }

    Method interfaceLookup(StaticObject receiver, Method resolutionSeed) {
        assert resolutionSeed.getDeclaringKlass().isAssignableFrom(receiver.getKlass());
        return EspressoInterop.getInteropKlass(receiver).itableLookup(resolutionSeed.getDeclaringKlass(), resolutionSeed.getITableIndex());
    }

    Method virtualLookup(StaticObject receiver, Method resolutionSeed) {
        assert resolutionSeed.getDeclaringKlass().isAssignableFrom(receiver.getKlass());
        return EspressoInterop.getInteropKlass(receiver).vtableLookup(resolutionSeed.getVTableIndex());
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
