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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;

@GenerateUncached
public abstract class OverLoadedMethodSelectorNode extends EspressoNode {

    static final int LIMIT = 2;

    public abstract OverloadedMethodWithArgs execute(Method[] candidates, Object[] arguments);

    @Specialization(guards = {"same(candidates, cachedCandidates)"}, limit = "LIMIT")
    OverloadedMethodWithArgs doCached(Method[] candidates,
                    Object[] arguments,
                    @SuppressWarnings("unused") @Cached(value = "candidates", dimensions = 1) Method[] cachedCandidates,
                    @Cached(value = "resolveParameterKlasses(candidates)", dimensions = 2) Klass[][] parameterKlasses,
                    @Cached ToEspressoNode toEspressoNode) {
        return selectMatchingOverloads(candidates, arguments, parameterKlasses, toEspressoNode);
    }

    @Specialization(replaces = "doCached")
    OverloadedMethodWithArgs doGeneric(Method[] candidates, Object[] arguments, @Cached ToEspressoNode toEspressoNode) {
        return selectMatchingOverloads(candidates, arguments, resolveParameterKlasses(candidates), toEspressoNode);
    }

    private OverloadedMethodWithArgs selectMatchingOverloads(Method[] candidates, Object[] arguments, Klass[][] parameterKlasses, ToEspressoNode toEspressoNode) {
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
                fitByType.add(new OverloadedMethodWithArgs(candidate, convertedArgs, parameters));
            }
        }
        if (fitByType.size() == 1) {
            return fitByType.get(0);
        }
        // still multiple candidates, so try to select the best one
        return findMostSpecificOverload(fitByType, arguments, false);
    }

    private static OverloadedMethodWithArgs findMostSpecificOverload(List<OverloadedMethodWithArgs> candidates, Object[] args, boolean varArgs) {
        assert candidates.size() >= 2;
        if (candidates.size() == 2) {
            int res = compareOverloads(candidates.get(0), candidates.get(1), args, varArgs);
            return res == 0 ? null : (res < 0 ? candidates.get(0) : candidates.get(1));
        }

        Iterator<OverloadedMethodWithArgs> candIt = candidates.iterator();
        List<OverloadedMethodWithArgs> best = new LinkedList<>();
        best.add(candIt.next());

        while (candIt.hasNext()) {
            OverloadedMethodWithArgs cand = candIt.next();
            boolean add = false;
            for (Iterator<OverloadedMethodWithArgs> bestIt = best.iterator(); bestIt.hasNext();) {
                int res = compareOverloads(cand, bestIt.next(), args, varArgs);
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

    private static int compareOverloads(OverloadedMethodWithArgs m1, OverloadedMethodWithArgs m2, Object[] args, boolean varArgs) {
        int res = 0;
        assert !varArgs || m1.getMethod().isVarargs() && m2.getMethod().isVarargs();
        assert m1.getParameterTypes().length == m1.getParameterTypes().length;
        for (int i = 0; i < m1.getParameterTypes().length; i++) {
            Klass t1 = m1.getParameterTypes()[i];
            Klass t2 = m2.getParameterTypes()[i];
            if (t1 == t2) {
                continue;
            }
            int r = compareKnownTypesExact(t1, t2, args[i]);
            if (r == 0) {
                r = compareAssignable(t1, t2);
            }
            if (r == 0) {
                continue;
            }
            if (res == 0) {
                res = r;
            } else if (res != r) {
                // cannot determine definite ranking between these two overloads
                res = 0;
                break;
            }
        }
        return res;
    }

    // if an interop primitive is used, represented by a boxed host primitive
    // we can take the hint and do exact primitive type mapping
    private static int compareKnownTypesExact(Klass t1, Klass t2, Object arg) {
        Meta meta = t1.getMeta();
        Class<?> hostClass = arg.getClass();

        // primitives
        boolean t1IsPrimitive = t1.isPrimitive();
        Klass t1AsPrimitive = t1IsPrimitive ? t1 : boxedTypeToPrimitiveType(t1);

        if (t1AsPrimitive != null) {
            boolean t2Primitive = t2.isPrimitive();
            Klass t2AsPrimitive = t2Primitive ? t2 : boxedTypeToPrimitiveType(t2);
            assert t2AsPrimitive != null;

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
            if (t1 == meta.java_lang_String) {
                return -1;
            }
            if (t2 == meta.java_lang_String) {
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
        Klass fromAsPrimitive = fromIsPrimitive ? fromType : boxedTypeToPrimitiveType(fromType);
        Klass toAsPrimitive = toIsPrimitive ? toType : boxedTypeToPrimitiveType(toType);
        if (toAsPrimitive != null && fromAsPrimitive != null) {
            if (toAsPrimitive == fromAsPrimitive) {
                assert fromIsPrimitive != toIsPrimitive;
                // primitive <: boxed
                return fromIsPrimitive;
            } else if (isWideningPrimitiveConversion(toAsPrimitive, fromAsPrimitive)) {
                // primitive|boxed <: wider primitive|boxed
                return true;
            }
        } else if (fromAsPrimitive == meta._char && (toType == meta.java_lang_String || toType == meta.java_lang_CharSequence)) {
            // char|Character <: String|CharSequence
            return true;
        } else if (toAsPrimitive == null && fromAsPrimitive != null && toType.isAssignableFrom(primitiveTypeToBoxedType(fromAsPrimitive))) {
            // primitive|boxed <: Number et al
            return true;
        }
        return false;
    }

    static Klass boxedTypeToPrimitiveType(Klass primitiveType) {
        Meta meta = primitiveType.getMeta();
        if (primitiveType == meta.java_lang_Boolean) {
            return meta._boolean;
        } else if (primitiveType == meta.java_lang_Byte) {
            return meta._byte;
        } else if (primitiveType == meta.java_lang_Short) {
            return meta._short;
        } else if (primitiveType == meta.java_lang_Character) {
            return meta._char;
        } else if (primitiveType == meta.java_lang_Integer) {
            return meta._int;
        } else if (primitiveType == meta.java_lang_Long) {
            return meta._long;
        } else if (primitiveType == meta.java_lang_Float) {
            return meta._float;
        } else if (primitiveType == meta.java_lang_Double) {
            return meta._double;
        } else {
            return null;
        }
    }

    static Klass primitiveTypeToBoxedType(Klass primitiveType) {
        Meta meta = primitiveType.getMeta();
        if (primitiveType == meta._boolean) {
            return meta.java_lang_Boolean;
        } else if (primitiveType == meta._byte) {
            return meta.java_lang_Byte;
        } else if (primitiveType == meta._short) {
            return meta.java_lang_Short;
        } else if (primitiveType == meta._char) {
            return meta.java_lang_Character;
        } else if (primitiveType == meta._int) {
            return meta.java_lang_Integer;
        } else if (primitiveType == meta._long) {
            return meta.java_lang_Long;
        } else if (primitiveType == meta._float) {
            return meta.java_lang_Float;
        } else if (primitiveType == meta._double) {
            return meta.java_lang_Double;
        } else {
            return null;
        }
    }

    private static boolean isWideningPrimitiveConversion(Klass toType, Klass fromType) {
        assert toType.isPrimitive();
        Meta meta = toType.getMeta();
        if (fromType == meta._byte) {
            return toType == meta._short || toType == meta._int || toType == meta._long || toType == meta._float || toType == meta._double;
        } else if (fromType == meta._short) {
            return toType == meta._int || toType == meta._long || toType == meta._float || toType == meta._double;
        } else if (fromType == meta._char) {
            return toType == meta._int || toType == meta._long || toType == meta._float || toType == meta._double;
        } else if (fromType == meta._int) {
            return toType == meta._long || toType == meta._float || toType == meta._double;
        } else if (fromType == meta._long) {
            return toType == meta._float || toType == meta._double;
        } else if (fromType == meta._float) {
            return toType == meta._double;
        } else {
            return false;
        }
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
        private final Klass[] parameterTypes;

        private OverloadedMethodWithArgs(Method method, Object[] convertedArgs, Klass[] paramaterTypes) {
            this.method = method;
            this.convertedArgs = convertedArgs;
            this.parameterTypes = paramaterTypes;
        }

        public Method getMethod() {
            return method;
        }

        public Object[] getConvertedArgs() {
            return convertedArgs;
        }

        public Klass[] getParameterTypes() {
            return parameterTypes;
        }
    }
}
