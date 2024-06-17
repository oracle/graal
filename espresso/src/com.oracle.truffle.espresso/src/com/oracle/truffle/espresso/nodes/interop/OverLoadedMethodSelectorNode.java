/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;

@GenerateUncached
public abstract class OverLoadedMethodSelectorNode extends EspressoNode {

    static final int LIMIT = 2;

    public abstract CandidateMethodWithArgs execute(Method[] candidates, Object[] arguments);

    @Specialization(guards = {"same(candidates, cachedCandidates)"}, limit = "LIMIT")
    CandidateMethodWithArgs doCached(@SuppressWarnings("unused") Method[] candidates,
                    Object[] arguments,
                    @Cached(value = "candidates", dimensions = 1) Method[] cachedCandidates,
                    @Cached(value = "resolveParameterKlasses(candidates)", dimensions = 2) Klass[][] parameterKlasses,
                    @Cached ToEspressoNode.DynamicToEspresso toEspressoNode) {
        return selectMatchingOverloads(cachedCandidates, arguments, parameterKlasses, toEspressoNode);
    }

    @Specialization(replaces = "doCached")
    CandidateMethodWithArgs doGeneric(Method[] candidates,
                    Object[] arguments,
                    @Cached ToEspressoNode.DynamicToEspresso toEspressoNode) {
        return selectMatchingOverloads(candidates, arguments, resolveParameterKlasses(candidates), toEspressoNode);
    }

    @TruffleBoundary
    private static CandidateMethodWithArgs selectMatchingOverloads(Method[] candidates, Object[] arguments, Klass[][] parameterKlasses, ToEspressoNode.DynamicToEspresso toEspressoNode) {
        ArrayList<CandidateMethodWithArgs> fitByType = new ArrayList<>(candidates.length);
        for (int i = 0; i < candidates.length; i++) {
            CandidateMethodWithArgs matched = MethodArgsUtils.matchCandidate(candidates[i], arguments, parameterKlasses[i], toEspressoNode);
            if (matched != null) {
                fitByType.add(matched);
            }
        }
        if (fitByType.isEmpty()) {
            return null;
        }
        if (fitByType.size() == 1) {
            CandidateMethodWithArgs matched = fitByType.get(0);
            if (matched.getMethod().isVarargs()) {
                return MethodArgsUtils.ensureVarArgsArrayCreated(matched);
            }
            return matched;
        }
        // still multiple candidates, so try to select the best one
        CandidateMethodWithArgs mostSpecificOverload = findMostSpecificOverload(fitByType, arguments);
        if (mostSpecificOverload != null && mostSpecificOverload.getMethod().isVarargs()) {
            return MethodArgsUtils.ensureVarArgsArrayCreated(mostSpecificOverload);
        }
        return mostSpecificOverload;
    }

    private static CandidateMethodWithArgs findMostSpecificOverload(List<CandidateMethodWithArgs> candidates, Object[] args) {
        assert candidates.size() >= 2;
        if (candidates.size() == 2) {
            int res = compareOverloads(candidates.get(0), candidates.get(1), args);
            return res == 0 ? null : (res < 0 ? candidates.get(0) : candidates.get(1));
        }

        Iterator<CandidateMethodWithArgs> candIt = candidates.iterator();
        List<CandidateMethodWithArgs> best = new LinkedList<>();
        best.add(candIt.next());

        while (candIt.hasNext()) {
            CandidateMethodWithArgs cand = candIt.next();
            boolean add = false;
            for (Iterator<CandidateMethodWithArgs> bestIt = best.iterator(); bestIt.hasNext();) {
                int res = compareOverloads(cand, bestIt.next(), args);
                if (res == 0) {
                    add = true;
                } else if (res < 0) {
                    bestIt.remove();
                    add = true;
                } else {
                    assert res > 0;
                }
            }
            if (add) {
                best.add(cand);
            }
        }

        assert !best.isEmpty();
        if (best.size() == 1) {
            return best.get(0);
        }
        return null; // ambiguous
    }

    private static int compareOverloads(CandidateMethodWithArgs m1, CandidateMethodWithArgs m2, Object[] arguments) {
        int exact = 0;
        int res = 0;

        for (int i = 0; i < arguments.length; i++) {
            Klass t1 = getParameterType(i, m1);
            Klass t2 = getParameterType(i, m2);
            if (t1 == t2) {
                continue;
            }
            int r;
            r = compareKnownTypesExact(t1, t2, arguments[i]);
            if (r != 0) {
                if (exact == 0) {
                    exact = r;
                } else if (exact != r) {
                    // cannot determine definite ranking between these two overloads
                    return 0;
                }
                r = 0;
            }

            if (exact == 0) {
                r = compareAssignable(t1, t2);
            }
            if (r == 0) {
                continue;
            }
            if (res == 0) {
                res = r;
            } else if (res != r) {
                // cannot determine definite ranking between these two overloads
                return 0;
            }
        }
        return exact != 0 ? exact : res;
    }

    private static Klass getParameterType(int i, CandidateMethodWithArgs m1) {
        int length = m1.getParameterTypes().length;
        if (m1.getMethod().isVarargs() && i >= length - 1) {
            return ((ArrayKlass) m1.getParameterTypes()[length - 1]).getComponentType();
        } else {
            return m1.getParameterTypes()[i];
        }
    }

    // if an interop primitive is used, represented by a boxed host primitive
    // we can take the hint and do exact primitive type mapping
    private static int compareKnownTypesExact(Klass t1, Klass t2, Object arg) {
        Meta meta = t1.getMeta();
        Class<?> hostClass = arg.getClass();

        Klass compareType1;
        Klass compareType2;

        if (t1.isArray() && t2.isArray()) {
            // compare element types
            compareType1 = t1.getElementalType();
            compareType2 = t2.getElementalType();
            while (hostClass.isArray()) {
                hostClass = hostClass.getComponentType();
            }
        } else {
            compareType1 = t1;
            compareType2 = t2;
        }

        // primitives
        boolean t1IsPrimitive = compareType1.isPrimitive();
        Klass t1AsPrimitive = t1IsPrimitive ? compareType1 : MethodArgsUtils.boxedTypeToPrimitiveType(compareType1);

        if (t1AsPrimitive != null) {
            boolean t2Primitive = compareType2.isPrimitive();
            Klass t2AsPrimitive = t2Primitive ? compareType2 : MethodArgsUtils.boxedTypeToPrimitiveType(compareType2);

            if (hostClass == Boolean.class) {
                if (t1AsPrimitive == meta._boolean) {
                    return -1;
                }
                if (t2AsPrimitive == meta._boolean) {
                    return 1;
                }
            }
            if (hostClass == Character.class) {
                if (t1AsPrimitive == meta._char) {
                    return -1;
                }
                if (t2AsPrimitive == meta._char) {
                    return 1;
                }
            }
            if (hostClass == Byte.class) {
                if (t1AsPrimitive == meta._byte) {
                    return -1;
                }
                if (t2AsPrimitive == meta._byte) {
                    return 1;
                }
            }
            if (hostClass == Short.class) {
                if (t1AsPrimitive == meta._short) {
                    return -1;
                }
                if (t2AsPrimitive == meta._short) {
                    return 1;
                }
            }
            if (hostClass == Integer.class) {
                if (t1AsPrimitive == meta._int) {
                    return -1;
                }
                if (t2AsPrimitive == meta._int) {
                    return 1;
                }
            }
            if (hostClass == Long.class) {
                if (t1AsPrimitive == meta._long) {
                    return -1;
                }
                if (t2AsPrimitive == meta._long) {
                    return 1;
                }
            }
            if (hostClass == Float.class) {
                if (t1AsPrimitive == meta._float) {
                    return -1;
                }
                if (t2AsPrimitive == meta._float) {
                    return 1;
                }
            }
            if (hostClass == Double.class) {
                if (t1AsPrimitive == meta._double) {
                    return -1;
                }
                if (t2AsPrimitive == meta._double) {
                    return 1;
                }
            }
        }
        // String
        if (hostClass == String.class) {
            if (compareType1 == meta.java_lang_String || compareType1 == meta.java_lang_CharSequence) {
                return -1;
            }
            if (compareType2 == meta.java_lang_String || compareType2 == meta.java_lang_CharSequence) {
                return 1;
            }
        }
        return 0;
    }

    private static int compareAssignable(Klass t1, Klass t2) {
        if (isAssignableFrom(t1, t2)) {
            // t1 > t2 (t2 more specific)
            return 1;
        } else if (isAssignableFrom(t2, t1)) {
            // t1 < t2 (t1 more specific)
            return -1;
        } else {
            return 0;
        }
    }

    private static boolean isAssignableFrom(Klass toType, Klass fromType) {
        if (toType.isAssignableFrom(fromType)) {
            return true;
        }
        Meta meta = toType.getMeta();
        boolean fromIsPrimitive = fromType.isPrimitive();
        boolean toIsPrimitive = toType.isPrimitive();
        PrimitiveKlass fromAsPrimitive = fromIsPrimitive ? (PrimitiveKlass) fromType : MethodArgsUtils.boxedTypeToPrimitiveType(fromType);
        PrimitiveKlass toAsPrimitive = toIsPrimitive ? (PrimitiveKlass) toType : MethodArgsUtils.boxedTypeToPrimitiveType(toType);
        if (toAsPrimitive != null && fromAsPrimitive != null) {
            if (toAsPrimitive == fromAsPrimitive) {
                assert fromIsPrimitive != toIsPrimitive;
                // primitive <: boxed
                return fromIsPrimitive;
            } else if (MethodArgsUtils.isWideningPrimitiveConversion(toAsPrimitive, fromAsPrimitive)) {
                // primitive|boxed <: wider primitive|boxed
                return true;
            }
        } else if (fromAsPrimitive == meta._char && (toType == meta.java_lang_String || toType == meta.java_lang_CharSequence)) {
            // char|Character <: String|CharSequence
            return true;
        } else if (toAsPrimitive == null && fromAsPrimitive != null && toType.isAssignableFrom(MethodArgsUtils.primitiveTypeToBoxedType(fromAsPrimitive))) {
            // primitive|boxed <: Number et al
            return true;
        }
        return false;
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

}
