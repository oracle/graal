/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;

@GenerateUncached
public abstract class InvokeEspressoNode extends Node {
    static final int LIMIT = 4;

    public abstract Object execute(Method method, Object receiver, Object[] arguments) throws ArityException, UnsupportedMessageException, UnsupportedTypeException;

    static ToEspressoNode[] createToEspresso(long argsLength) {
        ToEspressoNode[] toEspresso = new ToEspressoNode[(int) argsLength];
        for (int i = 0; i < argsLength; i++) {
            toEspresso[i] = ToEspressoNodeGen.create();
        }
        return toEspresso;
    }

    static DirectCallNode createDirectCallNode(CallTarget callTarget) {
        return DirectCallNode.create(callTarget);
    }

    @ExplodeLoop
    @Specialization(guards = "method == cachedMethod", limit = "LIMIT")
    Object doCached(Method method, Object receiver, Object[] arguments,
                    @Cached("method") Method cachedMethod,
                    @Cached("createToEspresso(method.getParameterCount())") ToEspressoNode[] toEspressoNodes,
                    @Cached(value = "createDirectCallNode(method.getCallTarget())", allowUncached = true) DirectCallNode directCallNode)
                    throws ArityException, UnsupportedMessageException, UnsupportedTypeException {

        EspressoError.guarantee(method.isStatic() && receiver == null, "Espresso interop only supports static methods");

        int expectedArity = cachedMethod.getParameterCount();
        if (arguments.length != expectedArity) {
            throw ArityException.create(expectedArity, arguments.length);
        }

        Klass[] parameterKlasses = method.resolveParameterKlasses();

        int parameterCount = arguments.length;
        Object[] convertedArguments = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            convertedArguments[i] = toEspressoNodes[i].execute(arguments[i], parameterKlasses[i]);
        }

        return directCallNode.call(/* static => no receiver */ convertedArguments);
    }

    @ExplodeLoop
    @Specialization(replaces = "doCached")
    Object doGeneric(Method method, Object receiver, Object[] arguments,
                    @Cached ToEspressoNode toEspressoNode,
                    @Cached IndirectCallNode indirectCallNode)
                    throws ArityException, UnsupportedMessageException, UnsupportedTypeException {

        EspressoError.guarantee(method.isStatic() && receiver == null, "Espresso interop only supports static methods");

        int expectedArity = method.getParameterCount();
        if (arguments.length != expectedArity) {
            throw ArityException.create(expectedArity, arguments.length);
        }

        Klass[] parameterKlasses = method.resolveParameterKlasses();

        int parameterCount = arguments.length;
        Object[] convertedArguments = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            convertedArguments[i] = toEspressoNode.execute(arguments[i], parameterKlasses[i]);
        }

        return indirectCallNode.call(method.getCallTarget(), /* static => no receiver */ convertedArguments);
    }
}
