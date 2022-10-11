/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoNode;

@GenerateUncached
public abstract class OverLoadedMethodSelectorNode extends EspressoNode {

    static final int LIMIT = 2;

    public abstract OverloadedMethodWithArgs[] execute(Method[] candidates, Object[] arguments);

    @Specialization(guards = {"same(candidates, cachedCandidates)"}, limit = "LIMIT")
    OverloadedMethodWithArgs[] doCached(Method[] candidates,
                    Object[] arguments,
                    @SuppressWarnings("unused") @Cached(value = "candidates", dimensions = 1) Method[] cachedCandidates,
                    @Cached(value = "resolveParameterKlasses(candidates)", dimensions = 2) Klass[][] parameterKlasses,
                    @Cached ToEspressoNode toEspressoNode) {
        return selectMatchingOverloads(candidates, arguments, parameterKlasses, toEspressoNode);
    }

    @Specialization(replaces = "doCached")
    OverloadedMethodWithArgs[] doGeneric(Method[] candidates, Object[] arguments, @Cached ToEspressoNode toEspressoNode) {
        return selectMatchingOverloads(candidates, arguments, resolveParameterKlasses(candidates), toEspressoNode);
    }

    private OverloadedMethodWithArgs[] selectMatchingOverloads(Method[] candidates, Object[] arguments, Klass[][] parameterKlasses, ToEspressoNode toEspressoNode) {
        ArrayList<OverloadedMethodWithArgs> fitByType = new ArrayList<>(candidates.length);

        for (int i = 0; i < candidates.length; i++) {
            Method candidate = candidates[i];
            Klass[] parameters = parameterKlasses[i];
            boolean canConvert = true;
            Object[] convertedArgs = new Object[parameters.length];
            for (int j = 0; j < parameters.length; j++) {
                // try converting the parameters, if no exception
                // the candidate stands
                try {
                    convertedArgs[j] = toEspressoNode.execute(arguments[j], parameters[j]);
                } catch (UnsupportedTypeException e) {
                    canConvert = false;
                    break;
                }
            }
            if (canConvert) {
                fitByType.add(new OverloadedMethodWithArgs(candidate, convertedArgs));
            }
        }
        return fitByType.toArray(new OverloadedMethodWithArgs[fitByType.size()]);
    }

    static boolean same(Method[] methods, Method[] cachedMethods) {
        assert methods != null;
        assert cachedMethods != null;

        if (methods.length != cachedMethods.length) {
            return false;
        }
        for (int i = 0; i < cachedMethods.length; i++) {
            if (methods[i] != cachedMethods[i]) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    static Klass[][] resolveParameterKlasses(Method[] methods) {
        Klass[][] resolved = new Klass[methods.length][];
        for (int i = 0; i < methods.length; i++) {
            resolved[i] = methods[i].resolveParameterKlasses();
        }
        return resolved;
    }

    public final class OverloadedMethodWithArgs {
        private final Method method;
        private final Object[] convertedArgs;

        private OverloadedMethodWithArgs(Method method, Object[] convertedArgs) {
            this.method = method;
            this.convertedArgs = convertedArgs;
        }

        public Method getMethod() {
            return method;
        }

        public Object[] getConvertedArgs() {
            return convertedArgs;
        }
    }
}
