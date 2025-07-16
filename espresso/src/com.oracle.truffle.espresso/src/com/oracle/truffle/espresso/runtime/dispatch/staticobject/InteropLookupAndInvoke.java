/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.dispatch.staticobject;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.interop.CandidateMethodWithArgs;
import com.oracle.truffle.espresso.nodes.interop.InteropUnwrapNode;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.LookupDeclaredMethod;
import com.oracle.truffle.espresso.nodes.interop.LookupVirtualMethodNode;
import com.oracle.truffle.espresso.nodes.interop.MethodArgsUtils;
import com.oracle.truffle.espresso.nodes.interop.OverLoadedMethodSelectorNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.InteropUtils;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Lookups and invokes an Espresso method with interop conventions.
 *
 * <p>
 * Espresso maintains some invariants at the boundary e.g. Foreign objects are always "wrapped" (and
 * a class assigned) when entering into Espresso and always unwrapped when returning. Foreign
 * exceptions (leaving this method) are always unwrapped. Besides these invariants, Espresso tries
 * to select the (most) specific method in case of ambiguity caused by method overloads or
 * vargars...
 *
 * <p>
 * This helper node should be used by all interop invocations into Espresso (instance/static methods
 * and constructors) so the behavior is consistent.
 *
 * <p>
 * throws UnsupportedTypeException if the arguments cannot be coerced to the target method
 * signature, or when a single (most) specific method cannot be disambiguated e.g. overloads or
 * varargs...
 */
public abstract class InteropLookupAndInvoke extends EspressoNode {
    public abstract Object execute(StaticObject receiver, Klass klass, Object[] arguments, String member)
                    throws ArityException, UnsupportedTypeException, UnknownIdentifierException;

    @GenerateUncached
    public abstract static class Virtual extends InteropLookupAndInvoke {
        @Specialization
        Object doVirtual(StaticObject receiver, Klass klass, Object[] arguments, String member,
                        @Bind Node node,
                        @Cached LookupVirtualMethodNode lookup,
                        @Cached SelectAndInvokeNode selectAndInvoke,
                        @Cached InlinedBranchProfile error,
                        @Cached InlinedBranchProfile exception)
                        throws ArityException, UnsupportedTypeException, UnknownIdentifierException {
            assert receiver != null;
            Method[] candidates = lookup.execute(klass, member, arguments.length);
            if (candidates != null) {
                return selectAndInvoke(selectAndInvoke, exception, node, receiver, arguments, candidates);
            }
            error.enter(node);
            throw ArityException.create(arguments.length + 1, -1, arguments.length);
        }
    }

    @GenerateUncached
    public abstract static class NonVirtual extends InteropLookupAndInvoke {
        @Specialization
        Object doNonVirtual(StaticObject receiver, Klass klass, Object[] arguments, String member,
                        @Bind Node node,
                        @Cached LookupDeclaredMethod lookup,
                        @Cached SelectAndInvokeNode selectAndInvoke,
                        @Cached InlinedBranchProfile error,
                        @Cached InlinedBranchProfile exception)
                        throws ArityException, UnsupportedTypeException, UnknownIdentifierException {
            boolean isStatic = receiver == null;
            Method[] candidates = lookup.execute(klass, member, true, isStatic, arguments.length);
            if (candidates != null) {
                return selectAndInvoke(selectAndInvoke, exception, node, receiver, arguments, candidates);
            }
            error.enter(node);
            throw ArityException.create(arguments.length + 1, -1, arguments.length);
        }
    }

    Object selectAndInvoke(SelectAndInvokeNode selectAndInvoke,
                    InlinedBranchProfile exception, Node node,
                    StaticObject receiver, Object[] args, Method[] candidates) throws UnsupportedTypeException, ArityException {
        try {
            return selectAndInvoke.execute(receiver, args, candidates);
        } catch (EspressoException e) {
            exception.enter(node);
            throw InteropUtils.unwrapExceptionBoundary(getLanguage(), e, getMeta());
        }
    }

    @GenerateUncached
    abstract static class SelectAndInvokeNode extends EspressoNode {
        public abstract Object execute(StaticObject receiver, Object[] args, Method[] candidates) throws ArityException, UnsupportedTypeException;

        @Specialization(guards = {"isSingleNonVarargs(candidates)"})
        static Object doSingleNonVarargs(StaticObject receiver, Object[] args, Method[] candidates,
                        @Cached @Exclusive InvokeEspressoNode invoke,
                        @Cached InteropUnwrapNode unwrapNode)
                        throws ArityException, UnsupportedTypeException {
            assert candidates.length == 1;
            Method m = candidates[0];
            assert m.getParameterCount() == args.length;
            assert m.isPublic();
            return invoke.execute(m, receiver, args, unwrapNode);
        }

        @Specialization(guards = {"isSingleVarargs(candidates)"})
        static Object doSingleVarargs(StaticObject receiver, Object[] args, Method[] candidates,
                        @Bind Node node,
                        @Cached @Exclusive InvokeEspressoNode invoke,
                        @Cached ToEspressoNode.DynamicToEspresso toEspresso,
                        @Cached InteropUnwrapNode unwrapNode,
                        @Cached InlinedBranchProfile error)
                        throws ArityException, UnsupportedTypeException {
            assert candidates.length == 1;
            Method m = candidates[0];
            assert m.isPublic();
            CandidateMethodWithArgs matched = MethodArgsUtils.matchCandidate(m, args, m.resolveParameterKlasses(), toEspresso);
            if (matched != null) {
                matched = MethodArgsUtils.ensureVarArgsArrayCreated(matched);
                assert matched != null;
                return invoke.execute(matched.getMethod(), receiver, matched.getConvertedArgs(), true, unwrapNode);
            }
            error.enter(node);
            throw UnsupportedTypeException.create(args);
        }

        @Specialization(guards = {"isMulti(candidates)"})
        static Object doMulti(StaticObject receiver, Object[] args, Method[] candidates,
                        @Bind Node node,
                        @Cached OverLoadedMethodSelectorNode selector,
                        @Cached @Exclusive InvokeEspressoNode invoke,
                        @Cached InteropUnwrapNode unwrapNode,
                        @Cached InlinedBranchProfile error)
                        throws ArityException, UnsupportedTypeException {
            CandidateMethodWithArgs typeMatched = selector.execute(candidates, args);
            if (typeMatched != null) {
                // single match found!
                return invoke.execute(typeMatched.getMethod(), receiver, typeMatched.getConvertedArgs(), true, unwrapNode);
            } else {
                // unable to select exactly one best candidate for the input args!
                error.enter(node);
                throw UnsupportedTypeException.create(args);
            }
        }

        static boolean isSingleNonVarargs(Method[] candidates) {
            return candidates.length == 1 && !candidates[0].isVarargs();
        }

        static boolean isSingleVarargs(Method[] candidates) {
            return candidates.length == 1 && candidates[0].isVarargs();
        }

        static boolean isMulti(Method[] candidates) {
            return candidates.length > 1;
        }
    }
}
