/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.polyglot.HostMethodDesc.OverloadedMethod;
import com.oracle.truffle.polyglot.HostMethodDesc.SingleMethod;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;
import com.oracle.truffle.polyglot.TargetMappingNode.SingleMappingNode;
import com.oracle.truffle.polyglot.TargetMappingNodeGen.SingleMappingNodeGen;

@ReportPolymorphism
@GenerateUncached
abstract class HostExecuteNode extends Node {
    static final int LIMIT = 3;
    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

    HostExecuteNode() {
    }

    static HostExecuteNode create() {
        return HostExecuteNodeGen.create();
    }

    public abstract Object execute(HostMethodDesc method, Object obj, Object[] args, PolyglotLanguageContext languageContext) throws UnsupportedTypeException, ArityException;

    static ToHostNode[] createToHost(int argsLength) {
        ToHostNode[] toJava = new ToHostNode[argsLength];
        for (int i = 0; i < argsLength; i++) {
            toJava[i] = ToHostNodeGen.create();
        }
        return toJava;
    }

    @SuppressWarnings("unused")
    @ExplodeLoop
    @Specialization(guards = {"!method.isVarArgs()", "method == cachedMethod"}, limit = "LIMIT")
    Object doFixed(SingleMethod method, Object obj, Object[] args, PolyglotLanguageContext languageContext,
                    @Cached("method") SingleMethod cachedMethod,
                    @Cached("createToHost(method.getParameterCount())") ToHostNode[] toJavaNodes,
                    @Cached ToGuestValueNode toGuest,
                    @Cached("createClassProfile()") ValueProfile receiverProfile,
                    @Cached BranchProfile errorBranch,
                    @Cached(value = "languageContext.context.engine", allowUncached = true) PolyglotEngineImpl engine) throws ArityException, UnsupportedTypeException {
        int arity = cachedMethod.getParameterCount();
        if (args.length != arity) {
            errorBranch.enter();
            throw ArityException.create(arity, args.length);
        }
        Class<?>[] types = cachedMethod.getParameterTypes();
        Type[] genericTypes = cachedMethod.getGenericParameterTypes();
        Object[] convertedArguments = new Object[args.length];
        try {
            for (int i = 0; i < toJavaNodes.length; i++) {
                convertedArguments[i] = toJavaNodes[i].execute(args[i], types[i], genericTypes[i], languageContext, true);
            }
        } catch (PolyglotEngineException e) {
            errorBranch.enter();
            throw HostInteropErrors.unsupportedTypeException(args, e.e);
        }
        return doInvoke(cachedMethod, receiverProfile.profile(obj), convertedArguments, engine, languageContext, toGuest);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"method.isVarArgs()", "method == cachedMethod"}, limit = "LIMIT")
    Object doVarArgs(SingleMethod method, Object obj, Object[] args, PolyglotLanguageContext languageContext,
                    @Cached("method") SingleMethod cachedMethod,
                    @Cached ToHostNode toJavaNode,
                    @Cached ToGuestValueNode toGuest,
                    @Cached("createClassProfile()") ValueProfile receiverProfile,
                    @Cached BranchProfile errorBranch,
                    @Cached(value = "languageContext.context.engine", allowUncached = true) PolyglotEngineImpl engine) throws ArityException, UnsupportedTypeException {
        int parameterCount = cachedMethod.getParameterCount();
        int minArity = parameterCount - 1;
        if (args.length < minArity) {
            errorBranch.enter();
            throw ArityException.create(minArity, args.length);
        }
        Class<?>[] types = cachedMethod.getParameterTypes();
        Type[] genericTypes = cachedMethod.getGenericParameterTypes();
        Object[] convertedArguments = new Object[args.length];
        try {
            for (int i = 0; i < minArity; i++) {
                convertedArguments[i] = toJavaNode.execute(args[i], types[i], genericTypes[i], languageContext, true);
            }
            if (asVarArgs(args, cachedMethod, languageContext)) {
                for (int i = minArity; i < args.length; i++) {
                    Class<?> expectedType = types[minArity].getComponentType();
                    Type expectedGenericType = getGenericComponentType(genericTypes[minArity]);
                    convertedArguments[i] = toJavaNode.execute(args[i], expectedType, expectedGenericType, languageContext, true);
                }
                convertedArguments = createVarArgsArray(cachedMethod, convertedArguments, parameterCount);
            } else {
                convertedArguments[minArity] = toJavaNode.execute(args[minArity], types[minArity], genericTypes[minArity], languageContext, true);
            }
        } catch (PolyglotEngineException e) {
            errorBranch.enter();
            throw HostInteropErrors.unsupportedTypeException(args, e.e);
        }
        return doInvoke(cachedMethod, receiverProfile.profile(obj), convertedArguments, engine, languageContext, toGuest);
    }

    @Specialization(replaces = {"doFixed", "doVarArgs"})
    static Object doSingleUncached(SingleMethod method, Object obj, Object[] args, PolyglotLanguageContext languageContext,
                    @Shared("toHost") @Cached ToHostNode toJavaNode,
                    @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                    @Shared("varArgsProfile") @Cached ConditionProfile isVarArgsProfile,
                    @Shared("hostMethodProfile") @Cached HostMethodProfileNode methodProfile,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("engine") @Cached(value = "languageContext.context.engine", allowUncached = true) PolyglotEngineImpl engine) throws ArityException, UnsupportedTypeException {
        int parameterCount = method.getParameterCount();
        int minArity = method.isVarArgs() ? parameterCount - 1 : parameterCount;
        if (args.length < minArity) {
            errorBranch.enter();
            throw ArityException.create(minArity, args.length);
        }
        Object[] convertedArguments;
        try {
            convertedArguments = prepareArgumentsUncached(method, args, languageContext, toJavaNode, isVarArgsProfile);
        } catch (PolyglotEngineException e) {
            errorBranch.enter();
            throw HostInteropErrors.unsupportedTypeException(args, e.e);
        }
        return doInvoke(methodProfile.execute(method), obj, convertedArguments, engine, languageContext, toGuest);
    }

    // Note: checkArgTypes must be evaluated after selectOverload.
    @SuppressWarnings({"unused", "static-method"})
    @ExplodeLoop
    @Specialization(guards = {"method == cachedMethod", "checkArgTypes(args, cachedArgTypes, interop, languageContext, asVarArgs)"}, limit = "LIMIT")
    final Object doOverloadedCached(OverloadedMethod method, Object obj, Object[] args, PolyglotLanguageContext languageContext,
                    @Cached("method") OverloadedMethod cachedMethod,
                    @Cached ToHostNode toJavaNode,
                    @Cached ToGuestValueNode toGuest,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached("createArgTypesArray(args)") TypeCheckNode[] cachedArgTypes,
                    @Cached("selectOverload(method, args, languageContext, cachedArgTypes)") SingleMethod overload,
                    @Cached("asVarArgs(args, overload, languageContext)") boolean asVarArgs,
                    @Cached("createClassProfile()") ValueProfile receiverProfile,
                    @Cached BranchProfile errorBranch,
                    @Cached(value = "languageContext.context.engine", allowUncached = true) PolyglotEngineImpl engine) throws ArityException, UnsupportedTypeException {
        assert overload == selectOverload(method, args, languageContext);
        Class<?>[] types = overload.getParameterTypes();
        Type[] genericTypes = overload.getGenericParameterTypes();
        Object[] convertedArguments = new Object[cachedArgTypes.length];
        try {
            if (asVarArgs) {
                assert overload.isVarArgs();
                int parameterCount = overload.getParameterCount();
                for (int i = 0; i < cachedArgTypes.length; i++) {
                    Class<?> expectedType = i < parameterCount - 1 ? types[i] : types[parameterCount - 1].getComponentType();
                    Type expectedGenericType = i < parameterCount - 1 ? genericTypes[i] : getGenericComponentType(genericTypes[parameterCount - 1]);
                    convertedArguments[i] = toJavaNode.execute(args[i], expectedType, expectedGenericType, languageContext, true);
                }
                convertedArguments = createVarArgsArray(overload, convertedArguments, parameterCount);
            } else {
                for (int i = 0; i < cachedArgTypes.length; i++) {
                    convertedArguments[i] = toJavaNode.execute(args[i], types[i], genericTypes[i], languageContext, true);
                }
            }
        } catch (PolyglotEngineException e) {
            errorBranch.enter();
            throw HostInteropErrors.unsupportedTypeException(args, e.e);
        }
        return doInvoke(overload, receiverProfile.profile(obj), convertedArguments, engine, languageContext, toGuest);
    }

    @SuppressWarnings("static-method")
    @Specialization(replaces = "doOverloadedCached")
    final Object doOverloadedUncached(OverloadedMethod method, Object obj, Object[] args, PolyglotLanguageContext languageContext,
                    @Shared("toHost") @Cached ToHostNode toJavaNode,
                    @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                    @Shared("varArgsProfile") @Cached ConditionProfile isVarArgsProfile,
                    @Shared("hostMethodProfile") @Cached HostMethodProfileNode methodProfile,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("engine") @Cached(value = "languageContext.context.engine", allowUncached = true) PolyglotEngineImpl engine) throws ArityException, UnsupportedTypeException {
        SingleMethod overload = selectOverload(method, args, languageContext);
        Object[] convertedArguments;
        try {
            convertedArguments = prepareArgumentsUncached(overload, args, languageContext, toJavaNode, isVarArgsProfile);
        } catch (PolyglotEngineException e) {
            errorBranch.enter();
            throw HostInteropErrors.unsupportedTypeException(args, e.e);
        }
        return doInvoke(methodProfile.execute(overload), obj, convertedArguments, engine, languageContext, toGuest);
    }

    private static Object[] prepareArgumentsUncached(SingleMethod method, Object[] args, PolyglotLanguageContext languageContext, ToHostNode toJavaNode, ConditionProfile isVarArgsProfile) {
        Class<?>[] types = method.getParameterTypes();
        Type[] genericTypes = method.getGenericParameterTypes();
        Object[] convertedArguments = new Object[args.length];
        if (isVarArgsProfile.profile(method.isVarArgs()) && asVarArgs(args, method, languageContext)) {
            int parameterCount = method.getParameterCount();
            for (int i = 0; i < args.length; i++) {
                Class<?> expectedType = i < parameterCount - 1 ? types[i] : types[parameterCount - 1].getComponentType();
                Type expectedGenericType = i < parameterCount - 1 ? genericTypes[i] : getGenericComponentType(genericTypes[parameterCount - 1]);
                convertedArguments[i] = toJavaNode.execute(args[i], expectedType, expectedGenericType, languageContext, true);
            }
            convertedArguments = createVarArgsArray(method, convertedArguments, parameterCount);
        } else {
            for (int i = 0; i < args.length; i++) {
                convertedArguments[i] = toJavaNode.execute(args[i], types[i], genericTypes[i], languageContext, true);
            }
        }
        return convertedArguments;
    }

    static TypeCheckNode[] createArgTypesArray(Object[] args) {
        TypeCheckNode[] nodes = new TypeCheckNode[args.length];
        // fill with null checks so the DSL does not complain when it tries to adopt
        Arrays.fill(nodes, NullCheckNode.INSTANCE);
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private void fillArgTypesArray(Object[] args, TypeCheckNode[] cachedArgTypes, SingleMethod selected, boolean varArgs, List<SingleMethod> applicable, int priority,
                    PolyglotLanguageContext languageContext) {
        if (cachedArgTypes == null) {
            return;
        }
        HostClassCache cache = languageContext.getEngine().getHostClassCache();
        boolean multiple = applicable.size() > 1;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Class<?> targetType = getParameterType(selected.getParameterTypes(), i, varArgs);
            Set<PolyglotTargetMapping> otherPossibleMappings = null;
            if (multiple) {
                for (SingleMethod other : applicable) {
                    if (other == selected) {
                        continue;
                    }
                    if (other.isVarArgs() != varArgs) {
                        continue;
                    }
                    Class<?> paramType = getParameterType(other.getParameterTypes(), i, varArgs);
                    if (paramType == targetType) {
                        continue;
                    }
                    /*
                     * All converters that are currently not applicable of other methods need to be
                     * checked for not applicable in order to ensure that mappings with priority
                     * continue to not apply.
                     */
                    if (!ToHostNode.canConvert(arg, paramType, paramType, null, languageContext, priority,
                                    InteropLibrary.getFactory().getUncached(), TargetMappingNodeGen.getUncached())) {
                        PolyglotTargetMapping[] otherMappings = cache.getMappings(paramType);
                        if (otherPossibleMappings == null) {
                            otherPossibleMappings = new LinkedHashSet<>();
                        }
                        otherPossibleMappings.addAll(Arrays.asList(otherMappings));
                    }
                }
            }
            TypeCheckNode argType;
            if (arg == null) {
                argType = NullCheckNode.INSTANCE;
            } else if (multiple && ToHostNode.isPrimitiveTarget(targetType)) {
                argType = createPrimitiveTargetCheck(applicable, selected, arg, targetType, i, varArgs);
            } else if (arg instanceof HostObject) {
                argType = new JavaObjectType(((HostObject) arg).getObjectClass());
            } else {
                argType = new DirectTypeCheck(arg.getClass());
            }
            PolyglotTargetMapping[] mappings = cache.getMappings(targetType);
            if (mappings.length > 0 || otherPossibleMappings != null) {
                PolyglotTargetMapping[] otherMappings = otherPossibleMappings != null ? otherPossibleMappings.toArray(HostClassCache.EMPTY_MAPPINGS) : HostClassCache.EMPTY_MAPPINGS;
                argType = new TargetMappingType(argType, mappings, otherMappings);
            }
            /*
             * We need to eagerly insert as the cachedArgTypes might be used before they are adopted
             * by the DSL.
             */
            cachedArgTypes[i] = insert(argType);
        }

        assert checkArgTypes(args, cachedArgTypes, InteropLibrary.getFactory().getUncached(), languageContext, false) : Arrays.toString(cachedArgTypes);
    }

    private static TypeCheckNode createPrimitiveTargetCheck(List<SingleMethod> applicable, SingleMethod selected, Object arg, Class<?> targetType, int parameterIndex, boolean varArgs) {
        Class<?> currentTargetType = targetType;

        Collection<Class<?>> otherPossibleTypes = new ArrayList<>();
        for (SingleMethod other : applicable) {
            if (other == selected) {
                continue;
            }
            if (other.isVarArgs() != varArgs) {
                continue;
            }
            Class<?> paramType = getParameterType(other.getParameterTypes(), parameterIndex, varArgs);
            if (paramType == targetType) {
                continue;
            } else if (otherPossibleTypes.contains(paramType)) {
                continue;
            }
            /*
             * If the other param type is a subtype of this param type, and the argument is not
             * already a subtype of it, another value may change the outcome of overload resolution,
             * so we have to guard against it. If the argument is already a subtype of the other
             * param type, we must not guard against the other param type, and we do not have to as
             * this overload was better fit regardless.
             */
            if ((ToHostNode.isPrimitiveTarget(paramType) || ToHostNode.isPrimitiveTarget(targetType)) &&
                            isAssignableFrom(targetType, paramType) && !isSubtypeOf(arg, paramType)) {
                otherPossibleTypes.add(paramType);
            }
        }
        return new PrimitiveType(currentTargetType, otherPossibleTypes.toArray(EMPTY_CLASS_ARRAY));
    }

    @ExplodeLoop
    static boolean checkArgTypes(Object[] args, TypeCheckNode[] argTypes, InteropLibrary interop, PolyglotLanguageContext languageContext, @SuppressWarnings("unused") boolean dummy) {
        if (args.length != argTypes.length) {
            return false;
        }
        for (int i = 0; i < argTypes.length; i++) {
            TypeCheckNode argType = argTypes[i];
            if (!argType.execute(args[i], interop, languageContext)) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    static boolean asVarArgs(Object[] args, SingleMethod overload, PolyglotLanguageContext languageContext) {
        if (overload.isVarArgs()) {
            int parameterCount = overload.getParameterCount();
            if (args.length == parameterCount) {
                Class<?> varArgParamType = overload.getParameterTypes()[parameterCount - 1];
                return !isSubtypeOf(args[parameterCount - 1], varArgParamType) &&
                                !ToHostNode.canConvert(args[parameterCount - 1], varArgParamType, overload.getGenericParameterTypes()[parameterCount - 1],
                                                null, languageContext, ToHostNode.LOOSE,
                                                InteropLibrary.getFactory().getUncached(), TargetMappingNode.getUncached());
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
    SingleMethod selectOverload(OverloadedMethod method, Object[] args, PolyglotLanguageContext languageContext) throws ArityException, UnsupportedTypeException {
        return selectOverload(method, args, languageContext, null);
    }

    @TruffleBoundary
    SingleMethod selectOverload(OverloadedMethod method, Object[] args, PolyglotLanguageContext languageContext, TypeCheckNode[] cachedArgTypes)
                    throws ArityException, UnsupportedTypeException {
        SingleMethod[] overloads = method.getOverloads();
        List<SingleMethod> applicableByArity = new ArrayList<>();
        int minOverallArity = Integer.MAX_VALUE;
        int maxOverallArity = 0;
        boolean anyVarArgs = false;
        for (SingleMethod overload : overloads) {
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
            throw ArityException.create((args.length > maxOverallArity ? maxOverallArity : minOverallArity), args.length);
        }

        SingleMethod best;
        for (int priority : ToHostNode.PRIORITIES) {
            best = findBestCandidate(applicableByArity, args, languageContext, false, priority, cachedArgTypes);
            if (best != null) {
                return best;
            }
        }
        if (anyVarArgs) {
            for (int priority : ToHostNode.PRIORITIES) {
                best = findBestCandidate(applicableByArity, args, languageContext, true, priority, cachedArgTypes);
                if (best != null) {
                    return best;
                }
            }
        }

        throw noApplicableOverloadsException(overloads, args);
    }

    @SuppressWarnings("static-method")
    private SingleMethod findBestCandidate(List<SingleMethod> applicableByArity, Object[] args, PolyglotLanguageContext languageContext, boolean varArgs, int priority,
                    TypeCheckNode[] cachedArgTypes) throws UnsupportedTypeException {
        List<SingleMethod> candidates = new ArrayList<>();

        if (!varArgs) {
            for (SingleMethod candidate : applicableByArity) {
                int paramCount = candidate.getParameterCount();
                if (!candidate.isVarArgs() || paramCount == args.length) {
                    assert paramCount == args.length;
                    Class<?>[] parameterTypes = candidate.getParameterTypes();
                    Type[] genericParameterTypes = candidate.getGenericParameterTypes();
                    boolean applicable = true;
                    for (int i = 0; i < paramCount; i++) {
                        if (!isSubtypeOf(args[i], parameterTypes[i]) &&
                                        !ToHostNode.canConvert(args[i], parameterTypes[i], genericParameterTypes[i], null,
                                                        languageContext, priority, InteropLibrary.getFactory().getUncached(args[i]),
                                                        TargetMappingNode.getUncached())) {
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
            for (SingleMethod candidate : applicableByArity) {
                if (candidate.isVarArgs()) {
                    int parameterCount = candidate.getParameterCount();
                    Class<?>[] parameterTypes = candidate.getParameterTypes();
                    Type[] genericParameterTypes = candidate.getGenericParameterTypes();
                    boolean applicable = true;
                    for (int i = 0; i < parameterCount - 1; i++) {
                        if (!isSubtypeOf(args[i], parameterTypes[i]) &&
                                        !ToHostNode.canConvert(args[i], parameterTypes[i], genericParameterTypes[i], null,
                                                        languageContext, priority, InteropLibrary.getFactory().getUncached(args[i]),
                                                        TargetMappingNode.getUncached())) {
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
                            if (!isSubtypeOf(args[i], varArgsComponentType) &&
                                            !ToHostNode.canConvert(args[i], varArgsComponentType, varArgsGenericComponentType, null,
                                                            languageContext, priority,
                                                            InteropLibrary.getFactory().getUncached(args[i]), TargetMappingNode.getUncached())) {
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
                SingleMethod best = candidates.get(0);

                if (cachedArgTypes != null) {
                    fillArgTypesArray(args, cachedArgTypes, best, varArgs, applicableByArity, priority, languageContext);
                }

                return best;
            } else {
                SingleMethod best = findMostSpecificOverload(languageContext, candidates, args, varArgs, priority);
                if (best != null) {
                    if (cachedArgTypes != null) {
                        fillArgTypesArray(args, cachedArgTypes, best, varArgs, applicableByArity, priority, languageContext);
                    }

                    return best;
                }
                throw ambiguousOverloadsException(candidates, args);
            }
        }
        return null;
    }

    private static SingleMethod findMostSpecificOverload(PolyglotLanguageContext languageContext, List<SingleMethod> candidates, Object[] args, boolean varArgs, int priority) {
        assert candidates.size() >= 2;
        if (candidates.size() == 2) {
            int res = compareOverloads(languageContext, candidates.get(0), candidates.get(1), args, varArgs, priority);
            return res == 0 ? null : (res < 0 ? candidates.get(0) : candidates.get(1));
        }

        Iterator<SingleMethod> candIt = candidates.iterator();
        List<SingleMethod> best = new LinkedList<>();
        best.add(candIt.next());

        while (candIt.hasNext()) {
            SingleMethod cand = candIt.next();
            boolean add = false;
            for (Iterator<SingleMethod> bestIt = best.iterator(); bestIt.hasNext();) {
                int res = compareOverloads(languageContext, cand, bestIt.next(), args, varArgs, priority);
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

    private static int compareOverloads(PolyglotLanguageContext languageContext, SingleMethod m1, SingleMethod m2, Object[] args, boolean varArgs, int priority) {
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
            int r = compareByPriority(languageContext, t1, t2, args[i], priority);
            if (r == 0) {
                r = compareAssignable(t1, t2);
                if (r == 0) {
                    continue;
                }
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

    private static int compareByPriority(PolyglotLanguageContext languageContext, Class<?> t1, Class<?> t2, Object arg, int priority) {
        if (priority <= ToHostNode.STRICT) {
            return 0;
        }
        InteropLibrary argInterop = InteropLibrary.getFactory().getUncached(arg);
        TargetMappingNode mapping = TargetMappingNode.getUncached();
        for (int p : ToHostNode.PRIORITIES) {
            if (p > priority) {
                break;
            }
            boolean p1 = ToHostNode.canConvert(arg, t1, t1, null, languageContext, p, argInterop, mapping);
            boolean p2 = ToHostNode.canConvert(arg, t2, t2, null, languageContext, p, argInterop, mapping);
            if (p1 != p2) {
                return p1 ? -1 : 1;
            }
        }
        return 0;
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
        boolean fromIsPrimitive = fromType.isPrimitive();
        boolean toIsPrimitive = toType.isPrimitive();
        Class<?> fromAsPrimitive = fromIsPrimitive ? fromType : boxedTypeToPrimitiveType(fromType);
        Class<?> toAsPrimitive = toIsPrimitive ? toType : boxedTypeToPrimitiveType(toType);
        if (toAsPrimitive != null && fromAsPrimitive != null) {
            if (toAsPrimitive == fromAsPrimitive) {
                assert fromIsPrimitive != toIsPrimitive;
                // primitive <: boxed
                return fromIsPrimitive;
            } else if (isWideningPrimitiveConversion(toAsPrimitive, fromAsPrimitive)) {
                // primitive|boxed <: wider primitive|boxed
                return true;
            }
        } else if (fromAsPrimitive == char.class && (toType == String.class || toType == CharSequence.class)) {
            // char|Character <: String|CharSequence
            return true;
        } else if (toAsPrimitive == null && fromAsPrimitive != null && toType.isAssignableFrom(primitiveTypeToBoxedType(fromAsPrimitive))) {
            // primitive|boxed <: Number et al
            return true;
        }
        return false;
    }

    private static boolean isSubtypeOf(Object argument, Class<?> parameterType) {
        Object value = argument;
        if (argument instanceof HostObject) {
            value = ((HostObject) argument).obj;
        }
        if (!parameterType.isPrimitive()) {
            return value == null || (parameterType.isInstance(value) && !(value instanceof TruffleObject));
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

    private static RuntimeException ambiguousOverloadsException(List<SingleMethod> candidates, Object[] args) throws UnsupportedTypeException {
        String message = String.format("Multiple applicable overloads found for method name %s (candidates: %s, arguments: %s)", candidates.get(0).getName(), candidates, arrayToStringWithTypes(args));
        throw UnsupportedTypeException.create(args, message);
    }

    private static RuntimeException noApplicableOverloadsException(SingleMethod[] overloads, Object[] args) throws UnsupportedTypeException {
        String message = String.format("no applicable overload found (overloads: %s, arguments: %s)", Arrays.toString(overloads), arrayToStringWithTypes(args));
        throw UnsupportedTypeException.create(args, message);
    }

    private static Type getGenericComponentType(Type type) {
        return type instanceof GenericArrayType ? ((GenericArrayType) type).getGenericComponentType() : ((Class<?>) type).getComponentType();
    }

    @TruffleBoundary
    private static Object[] createVarArgsArray(SingleMethod method, Object[] args, int parameterCount) {
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

    private static Object doInvoke(SingleMethod method, Object obj, Object[] arguments, PolyglotEngineImpl engine, PolyglotLanguageContext languageContext, ToGuestValueNode toGuest) {
        assert engine == languageContext.context.engine;
        assert arguments.length == method.getParameterCount();
        Object ret = method.invokeGuestToHost(obj, arguments, engine, languageContext, toGuest);
        return toGuest.execute(languageContext, ret);
    }

    private static String arrayToStringWithTypes(Object[] args) {
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        for (Object arg : args) {
            sj.add(arg == null ? null : arg.toString() + " (" + arg.getClass().getSimpleName() + ")");
        }
        return sj.toString();
    }

    abstract static class TypeCheckNode extends Node {

        abstract boolean execute(Object test, InteropLibrary interop, PolyglotLanguageContext languageContext);

    }

    static final class NullCheckNode extends TypeCheckNode {

        static final NullCheckNode INSTANCE = new NullCheckNode();

        @Override
        boolean execute(Object test, InteropLibrary interop, PolyglotLanguageContext languageContext) {
            return test == null;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        public String toString() {
            return "null";
        }

    }

    static final class DirectTypeCheck extends TypeCheckNode {
        final Class<?> clazz;

        DirectTypeCheck(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        boolean execute(Object test, InteropLibrary interop, PolyglotLanguageContext languageContext) {
            return test != null && test.getClass() == clazz;
        }

        @Override
        public String toString() {
            return clazz.toString();
        }
    }

    static final class JavaObjectType extends TypeCheckNode {
        final Class<?> clazz;

        JavaObjectType(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        boolean execute(Object arg, InteropLibrary interop, PolyglotLanguageContext languageContext) {
            return arg instanceof HostObject && ((HostObject) arg).getObjectClass() == clazz;
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
            return "JavaObject[" + clazz.getTypeName() + "]";
        }
    }

    static final class TargetMappingType extends TypeCheckNode {

        @CompilationFinal(dimensions = 1) final PolyglotTargetMapping[] mappings;
        @CompilationFinal(dimensions = 1) final PolyglotTargetMapping[] otherMappings;

        @Child TypeCheckNode fallback;
        @Child TargetMappingNode targetMapping;
        @Children final SingleMappingNode[] mappingNodes;
        @Children final SingleMappingNode[] otherMappingNodes;

        TargetMappingType(TypeCheckNode fallback,
                        PolyglotTargetMapping[] mappings,
                        PolyglotTargetMapping[] otherMappings) {
            this.fallback = fallback;
            this.mappings = mappings;
            this.otherMappings = otherMappings;
            this.mappingNodes = new SingleMappingNode[mappings.length];
            for (int i = 0; i < mappings.length; i++) {
                mappingNodes[i] = SingleMappingNodeGen.create();
            }
            this.otherMappingNodes = new SingleMappingNode[otherMappings.length];
            for (int i = 0; i < otherMappings.length; i++) {
                otherMappingNodes[i] = SingleMappingNodeGen.create();
            }
            this.targetMapping = TargetMappingNode.create();
        }

        @Override
        @ExplodeLoop
        boolean execute(Object test, InteropLibrary interop, PolyglotLanguageContext languageContext) {
            for (int i = 0; i < otherMappingNodes.length; i++) {
                Object result = otherMappingNodes[i].execute(test, otherMappings[i], languageContext, interop, true);
                if (result == Boolean.TRUE) {
                    return false;
                }
            }

            for (int i = 0; i < mappingNodes.length; i++) {
                Object result = mappingNodes[i].execute(test, mappings[i], languageContext, interop, true);
                if (result == Boolean.TRUE) {
                    return true;
                }
            }
            return fallback.execute(test, interop, languageContext);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TargetMappingType)) {
                return false;
            }
            TargetMappingType other = (TargetMappingType) obj;
            return Arrays.equals(this.mappings, other.mappings);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(mappings);
        }

    }

    static final class PrimitiveType extends TypeCheckNode {
        final Class<?> targetType;
        @CompilationFinal(dimensions = 1) final Class<?>[] otherTypes;

        PrimitiveType(Class<?> targetType, Class<?>[] otherTypes) {
            this.targetType = targetType;
            this.otherTypes = otherTypes;
        }

        @Override
        public int hashCode() {
            return ((targetType == null) ? 0 : targetType.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PrimitiveType)) {
                return false;
            }
            PrimitiveType other = (PrimitiveType) obj;
            return Objects.equals(this.targetType, other.targetType) && Arrays.equals(this.otherTypes, other.otherTypes);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Primitive[");
            sb.append(targetType.getTypeName());
            if (otherTypes.length > 0) {
                for (Class<?> otherType : otherTypes) {
                    sb.append(", !");
                    sb.append(otherType.getTypeName());
                }
            }
            sb.append(']');
            return sb.toString();
        }

        @ExplodeLoop
        @Override
        public boolean execute(Object value, InteropLibrary interop, PolyglotLanguageContext languageContext) {
            for (Class<?> otherType : otherTypes) {
                if (ToHostNode.canConvertToPrimitive(value, otherType, interop)) {
                    return false;
                }
            }
            return ToHostNode.canConvertToPrimitive(value, targetType, interop);
        }
    }

    @GenerateUncached
    abstract static class HostMethodProfileNode extends Node {
        public abstract SingleMethod execute(SingleMethod method);

        @Specialization
        static SingleMethod mono(SingleMethod.MHBase method) {
            return method;
        }

        @Specialization
        static SingleMethod mono(SingleMethod.ReflectBase method) {
            return method;
        }

        @Specialization(replaces = "mono")
        static SingleMethod poly(SingleMethod method) {
            return method;
        }
    }
}
