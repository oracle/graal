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
import java.util.Arrays;

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

    public abstract Method[] execute(Method[] candidates, Object[] arguments);

    static ToEspressoNode[] createToEspresso(int length) {
        return InvokeEspressoNode.createToEspresso(length);
    }

    static boolean same(Method[] methods, Method[] cachedMethods) {
        return Arrays.equals(methods, cachedMethods);
    }

    static Klass[][] resolveParameterKlasses(Method[] methods) {
        Klass[][] resolved = new Klass[methods.length][];
        for (int i = 0; i < methods.length; i++) {
            resolved[i] = methods[i].resolveParameterKlasses();;
        }
        return resolved;
    }

    @Specialization(guards = {"same(candidates, cachedCandidates)", "arguments.length == toEspressoNodes.length"}, limit = "LIMIT")
    Method[] doCached(Method[] candidates,
                    Object[] arguments,
                    @SuppressWarnings("unused") @Cached(value = "candidates", dimensions = 1) Method[] cachedCandidates,
                    @Cached(value = "resolveParameterKlasses(candidates)", dimensions = 2) Klass[][] parameterKlasses,
                    @Cached(value = "createToEspresso(arguments.length)") ToEspressoNode[] toEspressoNodes) {
        return doTypeCheck(candidates, arguments, parameterKlasses, toEspressoNodes);
    }

    @Specialization(replaces = "doCached")
    Method[] doGeneric(Method[] candidates, Object[] arguments) {
        return doTypeCheck(candidates, arguments, resolveParameterKlasses(candidates), createToEspresso(arguments.length));
    }

    private Method[] doTypeCheck(Method[] candidates, Object[] arguments, Klass[][] parameterKlasses, ToEspressoNode[] toEspressoNodes) {
        ArrayList<Method> fitByType = new ArrayList<>(candidates.length);

        for (int i = 0; i < candidates.length; i++) {
            Method candidate = candidates[i];
            Klass[] parameters = parameterKlasses[i];
            boolean canConvert = true;
            for (int j = 0; j < parameters.length; j++) {
                // try converting the parameters, if no exception
                // the candidate stands
                try {
                    toEspressoNodes[j].execute(arguments[j], parameters[j]);
                } catch (UnsupportedTypeException e) {
                    canConvert = false;
                    break;
                }
            }
            if (canConvert) {
                fitByType.add(candidate);
            }
        }
        return fitByType.toArray(new Method[fitByType.size()]);
    }
}
