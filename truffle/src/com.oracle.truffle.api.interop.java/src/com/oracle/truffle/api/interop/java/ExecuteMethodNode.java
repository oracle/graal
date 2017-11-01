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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

abstract class ExecuteMethodNode extends Node {

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
    @Specialization(guards = {"!method.isVarArgs()", "method == cachedMethod"})
    Object doFixed(SingleMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("method") SingleMethodDesc cachedMethod,
                    @Cached(value = "getTypes(method, method.getParameterCount())", dimensions = 1) TypeAndClass<?>[] types,
                    @Cached("createToJava(method.getParameterCount())") ToJavaNode[] toJavaNodes) {
        int arity = cachedMethod.getParameterCount();
        if (args.length != arity) {
            throw ArityException.raise(arity, args.length);
        }
        Object[] convertedArguments = new Object[args.length];
        for (int i = 0; i < toJavaNodes.length; i++) {
            convertedArguments[i] = toJavaNodes[i].execute(args[i], types[i], languageContext);
        }
        return doInvoke(cachedMethod, obj, convertedArguments, languageContext);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"method.isVarArgs()", "method == cachedMethod"})
    Object doVarArgs(SingleMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("method") SingleMethodDesc cachedMethod,
                    @Cached("create()") ToJavaNode toJavaNode) {
        int minArity = cachedMethod.getParameterCount() - 1;
        if (args.length < minArity) {
            throw ArityException.raise(minArity, args.length);
        }
        TypeAndClass<?>[] types = getTypes(cachedMethod, args.length);
        Object[] convertedArguments = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            convertedArguments[i] = toJavaNode.execute(args[i], types[i], languageContext);
        }
        return doInvoke(cachedMethod, obj, convertedArguments, languageContext);
    }

    @Specialization(replaces = {"doFixed", "doVarArgs"})
    Object doSingleUncached(SingleMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("create()") ToJavaNode toJavaNode) {
        int minArity = method.isVarArgs() ? method.getParameterCount() - 1 : method.getParameterCount();
        if (args.length < minArity) {
            throw ArityException.raise(minArity, args.length);
        }
        TypeAndClass<?>[] types = getTypes(method, args.length);
        Object[] convertedArguments = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            convertedArguments[i] = toJavaNode.execute(args[i], types[i], languageContext);
        }
        return doInvoke(method, obj, convertedArguments, languageContext);
    }

    @SuppressWarnings("unused")
    @ExplodeLoop
    @Specialization(guards = {"method == cachedMethod", "checkArgTypes(args, cachedArgTypes)"})
    Object doOverloadedCached(OverloadedMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("method") OverloadedMethodDesc cachedMethod,
                    @Cached(value = "getArgTypes(args)", dimensions = 1) Type[] cachedArgTypes,
                    @Cached("create()") ToJavaNode toJavaNode,
                    @Cached("selectOverload(method, args, languageContext, toJavaNode)") SingleMethodDesc overload,
                    @Cached(value = "getTypes(overload, args.length)", dimensions = 1) TypeAndClass<?>[] types) {
        assert overload == selectOverload(method, args, languageContext, toJavaNode);
        assert Arrays.equals(types, getTypes(overload, args.length));
        Object[] convertedArguments = new Object[cachedArgTypes.length];
        for (int i = 0; i < cachedArgTypes.length; i++) {
            convertedArguments[i] = toJavaNode.execute(args[i], types[i], languageContext);
        }
        return doInvoke(overload, obj, convertedArguments, languageContext);
    }

    @Specialization(replaces = "doOverloadedCached")
    Object doOverloadedUncached(OverloadedMethodDesc method, Object obj, Object[] args, Object languageContext,
                    @Cached("create()") ToJavaNode toJavaNode) {
        SingleMethodDesc overload = selectOverload(method, args, languageContext, toJavaNode);
        TypeAndClass<?>[] types = getTypes(overload, args.length);
        Object[] convertedArguments = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            convertedArguments[i] = toJavaNode.execute(args[i], types[i], languageContext);
        }
        return doInvoke(overload, obj, convertedArguments, languageContext);
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

    @TruffleBoundary
    static SingleMethodDesc selectOverload(OverloadedMethodDesc method, Object[] args, Object languageContext, ToJavaNode toJavaNode) {
        SingleMethodDesc[] overloads = method.getOverloads();
        List<SingleMethodDesc> applicableByArity = new ArrayList<>();
        int minOverallArity = Integer.MAX_VALUE;
        int maxOverallArity = 0;
        for (SingleMethodDesc overload : overloads) {
            int paramCount = overload.getParameterCount();
            if (!overload.isVarArgs()) {
                if (args.length != paramCount) {
                    minOverallArity = Math.min(minOverallArity, paramCount);
                    maxOverallArity = Math.max(maxOverallArity, paramCount);
                    continue;
                }
            } else {
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

        List<SingleMethodDesc> strictCandidates = new ArrayList<>();
        for (SingleMethodDesc candidate : applicableByArity) {
            int paramCount = candidate.getParameterCount();
            if (!candidate.isVarArgs() || paramCount == args.length) {
                assert paramCount == args.length;
                boolean subtyping = true;
                for (int i = 0; i < paramCount; i++) {
                    Class<?> parameterType = candidate.getParameterTypes()[i];
                    Object argument = args[i];
                    if (!isSubtypeOf(argument, parameterType)) {
                        subtyping = false;
                        break;
                    }
                }
                if (subtyping) {
                    strictCandidates.add(candidate);
                }
            }
        }
        if (!strictCandidates.isEmpty()) {
            if (strictCandidates.size() == 1) {
                return strictCandidates.get(0);
            } else {
                SingleMethodDesc best = findBestOverload(strictCandidates, args, false);
                if (best != null) {
                    return best;
                }
                throw ambiguousOverloadsException(strictCandidates, args);
            }
        }
        strictCandidates = null;

        List<SingleMethodDesc> looseCandidates = new ArrayList<>();
        for (SingleMethodDesc candidate : applicableByArity) {
            int paramCount = candidate.getParameterCount();
            if (!candidate.isVarArgs() || paramCount == args.length) {
                assert paramCount == args.length;
                boolean loose = true;
                for (int i = 0; i < paramCount; i++) {
                    Class<?> parameterType = candidate.getParameterTypes()[i];
                    Object argument = args[i];
                    if (!toJavaNode.canConvert(argument, new TypeAndClass<>(candidate.getGenericParameterTypes()[i], parameterType), languageContext)) {
                        loose = false;
                        break;
                    }
                }
                if (loose) {
                    looseCandidates.add(candidate);
                }
            }
        }
        if (!looseCandidates.isEmpty()) {
            if (looseCandidates.size() == 1) {
                return looseCandidates.get(0);
            } else {
                SingleMethodDesc best = findBestOverload(looseCandidates, args, false);
                if (best != null) {
                    return best;
                }
                throw ambiguousOverloadsException(looseCandidates, args);
            }
        }
        looseCandidates = null;

        List<SingleMethodDesc> varArgCandidates = new ArrayList<>();
        for (SingleMethodDesc candidate : applicableByArity) {
            if (candidate.isVarArgs()) {
                boolean applicable = true;
                for (int i = 0; i < candidate.getParameterCount() - 1; i++) {
                    Class<?> parameterType = candidate.getParameterTypes()[i];
                    Object argument = args[i];
                    if (!isSubtypeOf(argument, parameterType) && !toJavaNode.canConvert(argument, new TypeAndClass<>(candidate.getGenericParameterTypes()[i], parameterType), languageContext)) {
                        applicable = false;
                        break;
                    }
                }
                if (applicable) {
                    Class<?> varArgsComponentType = candidate.getParameterTypes()[candidate.getParameterCount() - 1].getComponentType();
                    Type varArgsGenericComponentType = candidate.getGenericParameterTypes()[candidate.getParameterCount() - 1];
                    if (varArgsGenericComponentType instanceof GenericArrayType) {
                        final GenericArrayType arrayType = (GenericArrayType) varArgsGenericComponentType;
                        varArgsGenericComponentType = arrayType.getGenericComponentType();
                    } else {
                        varArgsGenericComponentType = varArgsComponentType;
                    }
                    for (int i = candidate.getParameterCount() - 1; i < args.length; i++) {
                        Object argument = args[i];
                        if (!isSubtypeOf(argument, varArgsComponentType) &&
                                        !toJavaNode.canConvert(argument, new TypeAndClass<>(varArgsGenericComponentType, varArgsComponentType), languageContext)) {
                            applicable = false;
                            break;
                        }
                    }
                    if (applicable) {
                        varArgCandidates.add(candidate);
                    }
                }
            }
        }

        if (!varArgCandidates.isEmpty()) {
            if (varArgCandidates.size() == 1) {
                return varArgCandidates.get(0);
            } else {
                SingleMethodDesc best = findBestOverload(varArgCandidates, args, true);
                if (best != null) {
                    return best;
                }
                throw ambiguousOverloadsException(varArgCandidates, args);
            }
        }

        throw noApplicableOverloadsException();
    }

    private static SingleMethodDesc findBestOverload(List<SingleMethodDesc> candidates, Object[] args, boolean varArgs) {
        assert candidates.size() >= 2;
        if (candidates.size() == 2) {
            int res = compareOverloads(candidates.get(0), candidates.get(1), args, varArgs);
            return res == 0 ? null : (res == -1 ? candidates.get(0) : candidates.get(1));
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
                } else if (res == -1) {
                    bestIt.remove();
                    add = true;
                } else {
                    assert res == 1;
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
        assert !varArgs || m1.isVarArgs() && m2.isVarArgs();
        assert varArgs || (m1.getParameterCount() == m2.getParameterCount() && args.length == m1.getParameterCount());
        for (int i = 0; i < args.length; i++) {
            Class<?> t1 = varArgs && i >= m1.getParameterCount() - 1 ? m1.getParameterTypes()[m1.getParameterCount() - 1].getComponentType() : m1.getParameterTypes()[i];
            Class<?> t2 = varArgs && i >= m2.getParameterCount() - 1 ? m2.getParameterTypes()[m2.getParameterCount() - 1].getComponentType() : m2.getParameterTypes()[i];
            if (t1 == t2) {
                continue;
            }
            int r;
            if (isAssignableFrom(t1, t2)) {
                r = 1;
            } else if (isAssignableFrom(t2, t1)) {
                r = -1;
            } else {
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

    private static boolean isAssignableFrom(Class<?> t1, Class<?> t2) {
        return (t1.isAssignableFrom(t2)) || (t2.isPrimitive() && t1.isAssignableFrom(primitiveTypeToBoxedType(t2)));
    }

    private static boolean isSubtypeOf(Object argument, Class<?> parameterType) {
        Object value = argument;
        if (argument instanceof JavaObject) {
            value = ((JavaObject) argument).obj;
        }
        if (!parameterType.isPrimitive()) {
            return value == null || parameterType.isInstance(value);
        } else {
            return value != null && value.getClass() == primitiveTypeToBoxedType(parameterType);
        }
    }

    private static RuntimeException ambiguousOverloadsException(List<SingleMethodDesc> candidates, Object[] args) {
        return new IllegalArgumentException(String.format("no single overload found (candidates: %s, arguments: %s)", candidates, arrayToStringWithTypes(args)));
    }

    private static RuntimeException noApplicableOverloadsException() {
        return new IllegalArgumentException("no applicable overload found");
    }

    @TruffleBoundary
    static TypeAndClass<?>[] getTypes(SingleMethodDesc method, int expectedTypeCount) {
        Type[] argumentTypes = method.getGenericParameterTypes();
        Class<?>[] argumentClasses = method.getParameterTypes();
        if (method.isVarArgs()) {
            TypeAndClass<?>[] types = new TypeAndClass<?>[expectedTypeCount];
            for (int i = 0; i < expectedTypeCount; i++) {
                if (i < argumentTypes.length - 1) {
                    types[i] = new TypeAndClass<>(argumentTypes[i], argumentClasses[i]);
                } else {
                    final Type lastArgumentType = argumentTypes[argumentTypes.length - 1];
                    final Class<?> arrayClazz = argumentClasses[argumentClasses.length - 1];
                    if (lastArgumentType instanceof GenericArrayType) {
                        final GenericArrayType arrayType = (GenericArrayType) lastArgumentType;
                        types[i] = new TypeAndClass<>(arrayType.getGenericComponentType(), arrayClazz.getComponentType());
                    } else {
                        types[i] = new TypeAndClass<>(arrayClazz.getComponentType(), arrayClazz.getComponentType());
                    }
                }
            }
            return types;
        } else {
            assert expectedTypeCount == argumentTypes.length;
            TypeAndClass<?>[] types = new TypeAndClass<?>[expectedTypeCount];
            for (int i = 0; i < expectedTypeCount; i++) {
                types[i] = new TypeAndClass<>(argumentTypes[i], argumentClasses[i]);
            }
            return types;
        }
    }

    private static Object doInvoke(SingleMethodDesc method, Object obj, Object[] args, Object languageContext) {
        Object[] arguments;
        int parameterCount = method.getParameterCount();
        if (method.isVarArgs()) {
            arguments = createVarArgsArray(method, args, parameterCount);
        } else {
            arguments = args;
        }
        assert arguments.length == parameterCount;
        return invoke(method, obj, arguments, languageContext);
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

    private static Object invoke(SingleMethodDesc method, Object obj, Object[] arguments, Object languageContext) {
        Object ret;
        try {
            ret = method.invoke(obj, arguments);
        } catch (IllegalArgumentException ex) {
            throw UnsupportedTypeException.raise(ex, arguments);
        } catch (RuntimeException | Error ex) {
            CompilerDirectives.transferToInterpreter();
            throw ex;
        } catch (Throwable ex) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(ex);
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
