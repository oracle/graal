/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.EspressoNode;

public abstract class AbstractLookupNode extends EspressoNode {
    public static final char METHOD_SELECTION_SEPARATOR = '/';

    abstract Method.MethodVersion[] getMethodArray(Klass k);

    @TruffleBoundary
    Method[] doLookup(Klass klass, String key, boolean publicOnly, boolean isStatic, int arity) throws ArityException {
        ArrayList<Method> result = new ArrayList<>();
        String methodName;
        String signature = null;
        int separatorIndex = key.indexOf(METHOD_SELECTION_SEPARATOR);
        if (separatorIndex >= 0) {
            methodName = key.substring(0, separatorIndex);
            signature = key.substring(separatorIndex + 1);
        } else {
            methodName = key;
        }

        int minOverallArity = Integer.MAX_VALUE;
        int maxOverallArity = -1;
        boolean skipArityCheck = arity == -1;
        for (Method.MethodVersion m : getMethodArray(klass)) {
            if (matchMethod(m.getMethod(), methodName, signature, isStatic, publicOnly)) {
                int matchArity = m.getMethod().getParameterCount();
                minOverallArity = min(minOverallArity, matchArity);
                maxOverallArity = max(maxOverallArity, matchArity);
                if (matchArity == arity || skipArityCheck) {
                    result.add(m.getMethod());
                }
            }
        }
        if (!skipArityCheck && result.isEmpty() && maxOverallArity >= 0) {
            throw ArityException.create(minOverallArity, maxOverallArity, arity);
        }
        return result.isEmpty() ? null : result.toArray(new Method[0]);
    }

    private static boolean matchMethod(Method m, String methodName, String signature, boolean isStatic, boolean publicOnly) {
        return (!publicOnly || m.isPublic()) &&
                        m.isStatic() == isStatic &&
                        !m.isSignaturePolymorphicDeclared() &&
                        m.getName().toString().equals(methodName) &&
                        // If signature is specified, do the check.
                        (signature == null || m.getSignatureAsString().equals(signature));
    }

    @TruffleBoundary
    protected boolean isInvocable(Klass klass, String key, boolean publicOnly, boolean isStatic) {
        String methodName;
        String signature = null;
        int separatorIndex = key.indexOf(METHOD_SELECTION_SEPARATOR);
        if (separatorIndex >= 0) {
            methodName = key.substring(0, separatorIndex);
            signature = key.substring(separatorIndex + 1);
        } else {
            methodName = key;
        }
        Map<Integer, List<Method>> methodsByArity = new HashMap<>();
        // we will first disambiguate overloads with arity
        // then proceed to check if the parameter types are
        // incompatible, if not we don't support invoking
        // ambiguous members
        for (Method.MethodVersion m : getMethodArray(klass)) {
            if (matchMethod(m.getMethod(), methodName, signature, isStatic, publicOnly)) {
                Integer arity = m.getMethod().getParameterCount();
                List<Method> methods = methodsByArity.get(arity);
                if (methods == null) {
                    methods = new ArrayList<>();
                    methodsByArity.put(arity, methods);
                }
                methods.add(m.getMethod());
            }
        }
        if (methodsByArity.isEmpty()) {
            return false;
        }

        for (List<Method> overloads : methodsByArity.values()) {
            if (overloads.size() > 1) {
                // Type checking required to disambiguate!
                // If below sorting throws, it means that parameter types
                // for at least two overloaded methods are compatible
                // meaning we can't disambiguate.
                Map<Method, Klass[]> parameterTypeCache = new HashMap<>(overloads.size());
                // cache parameter types to avoid resolving multiple times
                // in comparator
                for (Method overload : overloads) {
                    Klass[] parameterTypes = overload.resolveParameterKlasses();
                    parameterTypeCache.put(overload, parameterTypes);
                }

                try {
                    Collections.sort(overloads, new Comparator<Method>() {
                        @Override
                        public int compare(Method m1, Method m2) throws IllegalArgumentException {
                            Klass[] m1Types = parameterTypeCache.get(m1);
                            Klass[] m2Types = parameterTypeCache.get(m2);
                            for (int i = 0; i < m1Types.length; i++) {
                                if (!canConvert(m1Types[i], m2Types[i])) {
                                    return 0;
                                }
                            }
                            // all types are compatible meaning
                            // we can't disambiguate
                            throw new IllegalArgumentException();
                        }
                    });
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canConvert(Klass m1Type, Klass m2Type) {
        if (m1Type.isPrimitive()) {
            if (m2Type.isPrimitive()) {
                // char and boolean are the only types
                // that are not numbers, all others can
                // be converted in one direction
                if (m1Type == m2Type) {
                    return true;
                } else if (m1Type == getMeta()._boolean || m1Type == getMeta()._char) {
                    return false;
                } else {
                    return true;
                }
            } else {
                if (m1Type == getMeta()._char && m2Type == getMeta().java_lang_String) {
                    return true;
                }
                return false;
            }
        } else if (m2Type == getMeta()._char && m1Type == getMeta().java_lang_String) {
            return true;
        } else {
            // isAssignableFrom in either direction qualifies
            return m1Type.isAssignable(m2Type) || m2Type.isAssignable(m1Type);
        }
    }
}
