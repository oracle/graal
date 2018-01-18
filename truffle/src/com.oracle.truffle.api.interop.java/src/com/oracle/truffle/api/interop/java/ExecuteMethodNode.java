/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.interop.java;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

abstract class ExecuteMethodNode extends Node {
    static final int LIMIT = 3;

    ExecuteMethodNode() {
    }

    static ExecuteMethodNode create() {
        return ExecuteMethodNodeGen.create();
    }

    public abstract Object execute(JavaMethodDesc method, Object obj, Object[] args, Object languageContext);

    static ToJavaNode[] createToJava(int argsLength) {
        ToJavaNode[] toJava = new ToJavaNode[argsLength];
        for (int i = 0; i < argsLength; i++) {
            toJava[i] = ToJavaNode.create();
        }
        return toJava;
    }

    @SuppressWarnings("unused")
    @ExplodeLoop
    @Specialization(guards = {"!method.isVarArgs()", "method == cachedMethod"}, limit = "LIMIT")
    Object doFixed(SingleMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("method") SingleMethodDesc cachedMethod,
                    @Cached("createToJava(method.getParameterCount())") ToJavaNode[] toJavaNodes) {
        int arity = cachedMethod.getParameterCount();
        if (args.length != arity) {
            throw ArityException.raise(arity, args.length);
        }
        Class<?>[] types = cachedMethod.getParameterTypes();
        Type[] genericTypes = cachedMethod.getGenericParameterTypes();
        Object[] convertedArguments = new Object[args.length];
        for (int i = 0; i < toJavaNodes.length; i++) {
            convertedArguments[i] = toJavaNodes[i].execute(args[i], types[i], genericTypes[i], languageContext);
        }
        return doInvoke(cachedMethod, obj, convertedArguments, languageContext);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"method.isVarArgs()", "method == cachedMethod"}, limit = "LIMIT")
    Object doVarArgs(SingleMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("method") SingleMethodDesc cachedMethod,
                    @Cached("create()") ToJavaNode toJavaNode) {
        int parameterCount = cachedMethod.getParameterCount();
        int minArity = parameterCount - 1;
        if (args.length < minArity) {
            throw ArityException.raise(minArity, args.length);
        }
        Class<?>[] types = cachedMethod.getParameterTypes();
        Type[] genericTypes = cachedMethod.getGenericParameterTypes();
        Object[] convertedArguments = new Object[args.length];
        for (int i = 0; i < minArity; i++) {
            convertedArguments[i] = toJavaNode.execute(args[i], types[i], genericTypes[i], languageContext);
        }
        if (asVarArgs(args, cachedMethod)) {
            for (int i = minArity; i < args.length; i++) {
                Class<?> expectedType = types[minArity].getComponentType();
                Type expectedGenericType = getGenericComponentType(genericTypes[minArity]);
                convertedArguments[i] = toJavaNode.execute(args[i], expectedType, expectedGenericType, languageContext);
            }
            convertedArguments = createVarArgsArray(cachedMethod, convertedArguments, parameterCount);
        } else {
            convertedArguments[minArity] = toJavaNode.execute(args[minArity], types[minArity], genericTypes[minArity], languageContext);
        }
        return doInvoke(cachedMethod, obj, convertedArguments, languageContext);
    }

    @Specialization(replaces = {"doFixed", "doVarArgs"})
    Object doSingleUncached(SingleMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("create()") ToJavaNode toJavaNode,
                    @Cached("createBinaryProfile()") ConditionProfile isVarArgsProfile) {
        int parameterCount = method.getParameterCount();
        int minArity = method.isVarArgs() ? parameterCount - 1 : parameterCount;
        if (args.length < minArity) {
            throw ArityException.raise(minArity, args.length);
        }
        Object[] convertedArguments = prepareArgumentsUncached(method, args, languageContext, toJavaNode, isVarArgsProfile);
        return doInvoke(method, obj, convertedArguments, languageContext);
    }

    @SuppressWarnings("unused")
    @ExplodeLoop
    @Specialization(guards = {"method == cachedMethod", "checkArgTypes(args, cachedArgTypes)"}, limit = "LIMIT")
    Object doOverloadedCached(OverloadedMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("method") OverloadedMethodDesc cachedMethod,
                    @Cached(value = "getArgTypes(args)", dimensions = 1) Type[] cachedArgTypes,
                    @Cached("create()") ToJavaNode toJavaNode,
                    @Cached("selectOverload(method, args, languageContext, toJavaNode)") SingleMethodDesc overload,
                    @Cached("asVarArgs(args, overload)") boolean asVarArgs) {
        assert overload == selectOverload(method, args, languageContext, toJavaNode);
        Class<?>[] types = overload.getParameterTypes();
        Type[] genericTypes = overload.getGenericParameterTypes();
        Object[] convertedArguments = new Object[cachedArgTypes.length];
        if (asVarArgs) {
            assert overload.isVarArgs();
            int parameterCount = overload.getParameterCount();
            for (int i = 0; i < cachedArgTypes.length; i++) {
                Class<?> expectedType = i < parameterCount - 1 ? types[i] : types[parameterCount - 1].getComponentType();
                Type expectedGenericType = i < parameterCount - 1 ? genericTypes[i] : getGenericComponentType(genericTypes[parameterCount - 1]);
                convertedArguments[i] = toJavaNode.execute(args[i], expectedType, expectedGenericType, languageContext);
            }
            convertedArguments = createVarArgsArray(overload, convertedArguments, parameterCount);
        } else {
            for (int i = 0; i < cachedArgTypes.length; i++) {
                convertedArguments[i] = toJavaNode.execute(args[i], types[i], genericTypes[i], languageContext);
            }
        }
        return doInvoke(overload, obj, convertedArguments, languageContext);
    }

    @Specialization(replaces = "doOverloadedCached")
    Object doOverloadedUncached(OverloadedMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("create()") ToJavaNode toJavaNode,
                    @Cached("createBinaryProfile()") ConditionProfile isVarArgsProfile) {
        SingleMethodDesc overload = selectOverload(method, args, languageContext, toJavaNode);
        Object[] convertedArguments = prepareArgumentsUncached(overload, args, languageContext, toJavaNode, isVarArgsProfile);
        return doInvoke(overload, obj, convertedArguments, languageContext);
    }

    private static Object[] prepareArgumentsUncached(SingleMethodDesc method, Object[] args, Object languageContext, ToJavaNode toJavaNode, ConditionProfile isVarArgsProfile) {
        Class<?>[] types = method.getParameterTypes();
        Type[] genericTypes = method.getGenericParameterTypes();
        Object[] convertedArguments = new Object[args.length];
        if (isVarArgsProfile.profile(method.isVarArgs()) && asVarArgs(args, method)) {
            int parameterCount = method.getParameterCount();
            for (int i = 0; i < args.length; i++) {
                Class<?> expectedType = i < parameterCount - 1 ? types[i] : types[parameterCount - 1].getComponentType();
                Type expectedGenericType = i < parameterCount - 1 ? genericTypes[i] : getGenericComponentType(genericTypes[parameterCount - 1]);
                convertedArguments[i] = toJavaNode.execute(args[i], expectedType, expectedGenericType, languageContext);
            }
            convertedArguments = createVarArgsArray(method, convertedArguments, parameterCount);
        } else {
            for (int i = 0; i < args.length; i++) {
                convertedArguments[i] = toJavaNode.execute(args[i], types[i], genericTypes[i], languageContext);
            }
        }
        return convertedArguments;
    }

    static Type[] getArgTypes(Object[] args) {
        Type[] argTypes = new Type[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = getArgType(args[i]);
        }
        return argTypes;
    }

    static Type getArgType(Object arg) {
        if (arg == null) {
            return null;
        } else if (arg instanceof JavaObject) {
            return new JavaObjectType(((JavaObject) arg).clazz);
        } else {
            return arg.getClass();
        }
    }

    @ExplodeLoop
    static boolean checkArgTypes(Object[] args, Type[] argTypes) {
        if (args.length != argTypes.length) {
            return false;
        }
        for (int i = 0; i < argTypes.length; i++) {
            Type argType = argTypes[i];
            Object arg = args[i];
            if (arg == null) {
                if (argType != null) {
                    return false;
                }
            } else {
                if (argType instanceof JavaObjectType) {
                    if (!(arg instanceof JavaObject && ((JavaObject) arg).clazz == ((JavaObjectType) argType).clazz)) {
                        return false;
                    }
                } else {
                    assert argType instanceof Class<?>;
                    if (arg.getClass() != argType) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @TruffleBoundary
    static boolean asVarArgs(Object[] args, SingleMethodDesc overload) {
        if (overload.isVarArgs()) {
            int parameterCount = overload.getParameterCount();
            if (args.length == parameterCount) {
                return !isSubtypeOf(args[parameterCount - 1], overload.getParameterTypes()[parameterCount - 1]);
            } else {
                assert args.length != parameterCount;
                return true;
            }
        } else {
            return false;
        }
    }

    static Class<?> primitiveTypeToBoxedType(Class<?> primitiveType) {
        assert primitiveType.isPrimitive();
        if (primitiveType == boolean.class) {
            return Boolean.class;
        } else if (primitiveType == byte.class) {
            return Byte.class;
        } else if (primitiveType == short.class) {
            return Short.class;
        } else if (primitiveType == char.class) {
            return Character.class;
        } else if (primitiveType == int.class) {
            return Integer.class;
        } else if (primitiveType == long.class) {
            return Long.class;
        } else if (primitiveType == float.class) {
            return Float.class;
        } else if (primitiveType == double.class) {
            return Double.class;
        } else {
            throw new IllegalArgumentException();
        }
    }

    static Class<?> boxedTypeToPrimitiveType(Class<?> primitiveType) {
        if (primitiveType == Boolean.class) {
            return boolean.class;
        } else if (primitiveType == Byte.class) {
            return byte.class;
        } else if (primitiveType == Short.class) {
            return short.class;
        } else if (primitiveType == Character.class) {
            return char.class;
        } else if (primitiveType == Integer.class) {
            return int.class;
        } else if (primitiveType == Long.class) {
            return long.class;
        } else if (primitiveType == Float.class) {
            return float.class;
        } else if (primitiveType == Double.class) {
            return double.class;
        } else {
            return null;
        }
    }

    @TruffleBoundary
    static SingleMethodDesc selectOverload(OverloadedMethodDesc method, Object[] args, Object languageContext, ToJavaNode toJavaNode) {
        SingleMethodDesc[] overloads = method.getOverloads();
        List<SingleMethodDesc> applicableByArity = new ArrayList<>();
        int minOverallArity = Integer.MAX_VALUE;
        int maxOverallArity = 0;
        boolean anyVarArgs = false;
        for (SingleMethodDesc overload : overloads) {
            int paramCount = overload.getParameterCount();
            if (!overload.isVarArgs()) {
                if (args.length != paramCount) {
                    minOverallArity = Math.min(minOverallArity, paramCount);
                    maxOverallArity = Math.max(maxOverallArity, paramCount);
                    continue;
                }
            } else {
                anyVarArgs = true;
                int fixedParamCount = paramCount - 1;
                if (args.length < fixedParamCount) {
                    minOverallArity = Math.min(minOverallArity, fixedParamCount);
                    maxOverallArity = Math.max(maxOverallArity, fixedParamCount);
                    continue;
                }
            }
            applicableByArity.add(overload);
        }
        if (applicableByArity.isEmpty()) {
            throw ArityException.raise((args.length > maxOverallArity ? maxOverallArity : minOverallArity), args.length);
        }

        SingleMethodDesc best;
        best = findBestCandidate(applicableByArity, args, languageContext, toJavaNode, false, true);
        if (best != null) {
            return best;
        }
        best = findBestCandidate(applicableByArity, args, languageContext, toJavaNode, false, false);
        if (best != null) {
            return best;
        }
        if (anyVarArgs) {
            best = findBestCandidate(applicableByArity, args, languageContext, toJavaNode, true, true);
            if (best != null) {
                return best;
            }
            best = findBestCandidate(applicableByArity, args, languageContext, toJavaNode, true, false);
            if (best != null) {
                return best;
            }
        }

        throw noApplicableOverloadsException(overloads, args);
    }

    private static SingleMethodDesc findBestCandidate(List<SingleMethodDesc> applicableByArity, Object[] args, Object languageContext, ToJavaNode toJavaNode, boolean varArgs, boolean strict) {
        List<SingleMethodDesc> candidates = new ArrayList<>();

        if (!varArgs) {
            for (SingleMethodDesc candidate : applicableByArity) {
                int paramCount = candidate.getParameterCount();
                if (!candidate.isVarArgs() || paramCount == args.length) {
                    assert paramCount == args.length;
                    Class<?>[] parameterTypes = candidate.getParameterTypes();
                    boolean applicable = true;
                    for (int i = 0; i < paramCount; i++) {
                        Class<?> parameterType = parameterTypes[i];
                        Type[] genericParameterTypes = candidate.getGenericParameterTypes();
                        Object argument = args[i];
                        if (!isSubtypeOf(argument, parameterType) && !toJavaNode.canConvert(args[i], parameterTypes[i], genericParameterTypes[i], languageContext, strict)) {
                            applicable = false;
                            break;
                        }
                    }
                    if (applicable) {
                        candidates.add(candidate);
                    }
                }
            }
        } else {
            for (SingleMethodDesc candidate : applicableByArity) {
                if (candidate.isVarArgs()) {
                    int parameterCount = candidate.getParameterCount();
                    Class<?>[] parameterTypes = candidate.getParameterTypes();
                    Type[] genericParameterTypes = candidate.getGenericParameterTypes();
                    boolean applicable = true;
                    for (int i = 0; i < parameterCount - 1; i++) {
                        if (!isSubtypeOf(args[i], parameterTypes[i]) && !toJavaNode.canConvert(args[i], parameterTypes[i], genericParameterTypes[i], languageContext, strict)) {
                            applicable = false;
                            break;
                        }
                    }
                    if (applicable) {
                        Class<?> varArgsComponentType = parameterTypes[parameterCount - 1].getComponentType();
                        Type varArgsGenericComponentType = genericParameterTypes[parameterCount - 1];
                        if (varArgsGenericComponentType instanceof GenericArrayType) {
                            final GenericArrayType arrayType = (GenericArrayType) varArgsGenericComponentType;
                            varArgsGenericComponentType = arrayType.getGenericComponentType();
                        } else {
                            varArgsGenericComponentType = varArgsComponentType;
                        }
                        for (int i = parameterCount - 1; i < args.length; i++) {
                            if (!isSubtypeOf(args[i], varArgsComponentType) && !toJavaNode.canConvert(args[i], varArgsComponentType, varArgsGenericComponentType, languageContext, strict)) {
                                applicable = false;
                                break;
                            }
                        }
                        if (applicable) {
                            candidates.add(candidate);
                        }
                    }
                }
            }
        }

        if (!candidates.isEmpty()) {
            if (candidates.size() == 1) {
                return candidates.get(0);
            } else {
                SingleMethodDesc best = findMostSpecificOverload(candidates, args, varArgs);
                if (best != null) {
                    return best;
                }
                throw ambiguousOverloadsException(candidates, args);
            }
        }
        return null;
    }

    private static SingleMethodDesc findMostSpecificOverload(List<SingleMethodDesc> candidates, Object[] args, boolean varArgs) {
        assert candidates.size() >= 2;
        if (candidates.size() == 2) {
            int res = compareOverloads(candidates.get(0), candidates.get(1), args, varArgs);
            return res == 0 ? null : (res < 0 ? candidates.get(0) : candidates.get(1));
        }

        Iterator<SingleMethodDesc> candIt = candidates.iterator();
        List<SingleMethodDesc> best = new LinkedList<>();
        best.add(candIt.next());

        while (candIt.hasNext()) {
            SingleMethodDesc cand = candIt.next();
            boolean add = false;
            for (Iterator<SingleMethodDesc> bestIt = best.iterator(); bestIt.hasNext();) {
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

    private static int compareOverloads(SingleMethodDesc m1, SingleMethodDesc m2, Object[] args, boolean varArgs) {
        int res = 0;
        int maxParamCount = Math.max(m1.getParameterCount(), m2.getParameterCount());
        assert !varArgs || m1.isVarArgs() && m2.isVarArgs();
        assert varArgs || (m1.getParameterCount() == m2.getParameterCount() && args.length == m1.getParameterCount());
        assert maxParamCount <= args.length;
        for (int i = 0; i < maxParamCount; i++) {
            Class<?> t1 = getParameterType(m1.getParameterTypes(), i, varArgs);
            Class<?> t2 = getParameterType(m2.getParameterTypes(), i, varArgs);
            if (t1 == t2) {
                continue;
            }
            int r = compareAssignable(t1, t2);
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

    private static Class<?> getParameterType(Class<?>[] parameterTypes, int i, boolean varArgs) {
        return varArgs && i >= parameterTypes.length - 1 ? parameterTypes[parameterTypes.length - 1].getComponentType() : parameterTypes[i];
    }

    private static int compareAssignable(Class<?> t1, Class<?> t2) {
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

    private static boolean isAssignableFrom(Class<?> toType, Class<?> fromType) {
        if (toType.isAssignableFrom(fromType)) {
            return true;
        }
        if (fromType.isPrimitive()) {
            if (toType.isPrimitive()) {
                if (isWideningPrimitiveConversion(toType, fromType)) {
                    return true;
                }
            } else if (toType.isAssignableFrom(primitiveTypeToBoxedType(fromType))) {
                // primitive <: boxed
                return true;
            }
        } else if (toType.isPrimitive()) {
            Class<?> boxedToPrimitive = boxedTypeToPrimitiveType(fromType);
            if (boxedToPrimitive != null && isWideningPrimitiveConversion(toType, boxedToPrimitive)) {
                // boxed <: wider primitive
                return true;
            }
        }
        return false;
    }

    private static boolean isSubtypeOf(Object argument, Class<?> parameterType) {
        Object value = argument;
        if (argument instanceof JavaObject) {
            value = ((JavaObject) argument).obj;
        }
        if (!parameterType.isPrimitive()) {
            return value == null || parameterType.isInstance(value);
        } else {
            if (value != null) {
                Class<?> boxedToPrimitive = boxedTypeToPrimitiveType(value.getClass());
                if (boxedToPrimitive != null) {
                    return (boxedToPrimitive == parameterType || isWideningPrimitiveConversion(parameterType, boxedToPrimitive));
                }
            }
        }
        return false;
    }

    private static boolean isWideningPrimitiveConversion(Class<?> toType, Class<?> fromType) {
        assert toType.isPrimitive();
        if (fromType == byte.class) {
            return toType == short.class || toType == int.class || toType == long.class || toType == float.class || toType == double.class;
        } else if (fromType == short.class) {
            return toType == int.class || toType == long.class || toType == float.class || toType == double.class;
        } else if (fromType == char.class) {
            return toType == int.class || toType == long.class || toType == float.class || toType == double.class;
        } else if (fromType == int.class) {
            return toType == long.class || toType == float.class || toType == double.class;
        } else if (fromType == long.class) {
            return toType == float.class || toType == double.class;
        } else if (fromType == float.class) {
            return toType == double.class;
        } else {
            return false;
        }
    }

    private static RuntimeException ambiguousOverloadsException(List<SingleMethodDesc> candidates, Object[] args) {
        String message = String.format("no single overload found (candidates: %s, arguments: %s)", candidates, arrayToStringWithTypes(args));
        return UnsupportedTypeException.raise(new IllegalArgumentException(message), args);
    }

    private static RuntimeException noApplicableOverloadsException(SingleMethodDesc[] overloads, Object[] args) {
        String message = String.format("no applicable overload found (overloads: %s, arguments: %s)", Arrays.toString(overloads), arrayToStringWithTypes(args));
        return UnsupportedTypeException.raise(new IllegalArgumentException(message), args);
    }

    private static Type getGenericComponentType(Type type) {
        return type instanceof GenericArrayType ? ((GenericArrayType) type).getGenericComponentType() : ((Class<?>) type).getComponentType();
    }

    @TruffleBoundary
    private static Object[] createVarArgsArray(SingleMethodDesc method, Object[] args, int parameterCount) {
        Object[] arguments;
        Class<?>[] parameterTypes = method.getParameterTypes();
        arguments = new Object[parameterCount];
        for (int i = 0; i < parameterCount - 1; i++) {
            arguments[i] = args[i];
        }
        Class<?> varArgsType = parameterTypes[parameterCount - 1].getComponentType();
        Object varArgs = Array.newInstance(varArgsType, args.length - parameterCount + 1);
        for (int i = parameterCount - 1, j = 0; i < args.length; i++, j++) {
            Array.set(varArgs, j, args[i]);
        }
        arguments[parameterCount - 1] = varArgs;
        return arguments;
    }

    private static Object doInvoke(SingleMethodDesc method, Object obj, Object[] arguments, Object languageContext) {
        assert arguments.length == method.getParameterCount();
        Object ret;
        try {
            ret = method.invoke(obj, arguments);
        } catch (Throwable e) {
            throw JavaInteropReflect.rethrow(JavaInterop.wrapHostException(e));
        }
        return JavaInterop.toGuestValue(ret, languageContext);
    }

    private static String arrayToStringWithTypes(Object[] args) {
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        for (Object arg : args) {
            sj.add(arg == null ? null : arg.toString() + " (" + arg.getClass().getSimpleName() + ")");
        }
        return sj.toString();
    }

    static class JavaObjectType implements Type {
        final Class<?> clazz;

        JavaObjectType(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public int hashCode() {
            return ((clazz == null) ? 0 : clazz.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof JavaObjectType)) {
                return false;
            }
            JavaObjectType other = (JavaObjectType) obj;
            return Objects.equals(this.clazz, other.clazz);
        }

        @Override
        public String toString() {
            return "JavaObject[" + clazz.getCanonicalName() + "]";
        }
    }
}
