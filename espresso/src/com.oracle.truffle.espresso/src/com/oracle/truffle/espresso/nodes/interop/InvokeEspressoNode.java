/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.InteropUtils;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@GenerateUncached
@ReportPolymorphism
public abstract class InvokeEspressoNode extends EspressoNode {
    static final int LIMIT = 4;

    public final Object execute(Method method, Object receiver, Object[] arguments, boolean argsConverted) throws ArityException, UnsupportedTypeException {
        Method.MethodVersion resolutionSeed = method.getMethodVersion();
        if (!resolutionSeed.getRedefineAssumption().isValid()) {
            // OK, we know it's a removed method then
            resolutionSeed = method.getContext().getClassRedefinition().handleRemovedMethod(
                            method,
                            method.isStatic() ? method.getDeclaringKlass() : ((StaticObject) receiver).getKlass()).getMethodVersion();
        }
        EspressoLanguage language = getLanguage();
        Meta meta = getMeta();
        EspressoThreadLocalState tls = language.getThreadLocalState();
        tls.blockContinuationSuspension();
        try {
            Object result = executeMethod(resolutionSeed, receiver, arguments, argsConverted);
            /*
             * Invariant: Foreign objects are always wrapped when coming into Espresso and unwrapped
             * when going out.
             */
            return InteropUtils.unwrap(language, result, meta);
        } catch (EspressoException e) {
            /*
             * Invariant: Foreign exceptions are always unwrapped when going out of Espresso.
             */
            throw InteropUtils.unwrapExceptionBoundary(language, e, meta);
        } finally {
            tls.unblockContinuationSuspension();
        }
    }

    public final Object execute(Method method, Object receiver, Object[] arguments) throws ArityException, UnsupportedTypeException {
        return execute(method, receiver, arguments, false);
    }

    static DirectCallNode createDirectCallNode(CallTarget callTarget) {
        return DirectCallNode.create(callTarget);
    }

    abstract Object executeMethod(Method.MethodVersion method, Object receiver, Object[] arguments, boolean argsConverted) throws ArityException, UnsupportedTypeException;

    public static ToEspressoNode[] createToEspresso(Method.MethodVersion methodVersion) {
        Klass[] parameterKlasses = methodVersion.getMethod().resolveParameterKlasses();
        ToEspressoNode[] toEspresso = new ToEspressoNode[parameterKlasses.length];
        for (int i = 0; i < parameterKlasses.length; i++) {
            toEspresso[i] = ToEspressoNode.createToEspresso(parameterKlasses[i], parameterKlasses[i].getMeta());
        }
        return toEspresso;
    }

    @ExplodeLoop
    @Specialization(guards = "method == cachedMethod", limit = "LIMIT", assumptions = "cachedMethod.getRedefineAssumption()")
    Object doCached(Method.MethodVersion method, Object receiver, Object[] arguments, boolean argsConverted,
                    @Cached("method") Method.MethodVersion cachedMethod,
                    @Cached("createToEspresso(cachedMethod)") ToEspressoNode[] toEspressoNodes,
                    @Cached(value = "createDirectCallNode(method.getMethod().getCallTarget())") DirectCallNode directCallNode,
                    @Cached InitCheck initCheck,
                    @Cached BranchProfile badArityProfile)
                    throws ArityException, UnsupportedTypeException {

        checkValidInvoke(method.getMethod(), receiver);

        int expectedArity = cachedMethod.getMethod().getParameterCount();
        if (arguments.length != expectedArity) {
            badArityProfile.enter();
            throw ArityException.create(expectedArity, expectedArity, arguments.length);
        }

        Object[] convertedArguments = argsConverted ? arguments : new Object[expectedArity];
        if (!argsConverted) {
            for (int i = 0; i < expectedArity; i++) {
                convertedArguments[i] = toEspressoNodes[i].execute(arguments[i]);
            }
        }

        initCheck.execute(cachedMethod.getDeclaringKlass());
        if (!cachedMethod.getMethod().isStatic()) {
            Object[] argumentsWithReceiver = new Object[convertedArguments.length + 1];
            argumentsWithReceiver[0] = receiver;
            System.arraycopy(convertedArguments, 0, argumentsWithReceiver, 1, convertedArguments.length);
            return directCallNode.call(argumentsWithReceiver);
        }
        return directCallNode.call(/* static => no receiver */ convertedArguments);
    }

    @Specialization(replaces = "doCached")
    @ReportPolymorphism.Megamorphic
    Object doGeneric(Method.MethodVersion method, Object receiver, Object[] arguments, boolean argsConverted,
                    @Cached ToEspressoNode.DynamicToEspresso toEspressoNode,
                    @Cached IndirectCallNode indirectCallNode)
                    throws ArityException, UnsupportedTypeException {

        checkValidInvoke(method.getMethod(), receiver);

        int expectedArity = method.getMethod().getParameterCount();
        if (arguments.length != expectedArity) {
            throw ArityException.create(expectedArity, expectedArity, arguments.length);
        }

        Object[] convertedArguments = argsConverted ? arguments : new Object[expectedArity];

        if (!argsConverted) {
            Klass[] parameterKlasses = getParameterKlasses(method.getMethod());
            for (int i = 0; i < expectedArity; i++) {
                convertedArguments[i] = toEspressoNode.execute(arguments[i], parameterKlasses[i]);
            }
        }

        if (!method.getMethod().isStatic()) {
            Object[] argumentsWithReceiver = new Object[convertedArguments.length + 1];
            argumentsWithReceiver[0] = receiver;
            System.arraycopy(convertedArguments, 0, argumentsWithReceiver, 1, convertedArguments.length);
            return indirectCallNode.call(method.getCallTarget(), argumentsWithReceiver);
        }

        return indirectCallNode.call(method.getMethod().getCallTargetForceInit(), /*- static => no receiver */ convertedArguments);
    }

    @TruffleBoundary
    private static Klass[] getParameterKlasses(Method method) {
        return method.resolveParameterKlasses();
    }

    private static void checkValidInvoke(Method method, Object receiver) {
        EspressoError.guarantee(!method.isSignaturePolymorphicDeclared(), "Espresso interop does not support signature polymorphic methods.");
        EspressoError.guarantee(((method.isStatic() && receiver == null) ||
                        (!method.isStatic() && method.isPublic() && receiver != null)),
                        "Espresso interop only supports static methods and public instance method");
    }
}
