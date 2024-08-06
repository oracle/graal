/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.math.BigInteger;
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
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
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
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.host.HostContext.ToGuestValueNode;
import com.oracle.truffle.host.HostMethodDesc.OverloadedMethod;
import com.oracle.truffle.host.HostMethodDesc.SingleMethod;
import com.oracle.truffle.host.HostTargetMappingNode.SingleMappingNode;
import com.oracle.truffle.host.HostTargetMappingNodeGen.SingleMappingNodeGen;

@ReportPolymorphism
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@SuppressWarnings("truffle-interpreted-performance")
abstract class HostExecuteNode extends Node {
    static final int LIMIT = 3;
    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

    HostExecuteNode() {
    }

    public abstract Object execute(Node node, HostMethodDesc method, Object obj, Object[] args, HostContext hostContext) throws UnsupportedTypeException, ArityException;

    static HostToTypeNode[] createToHost(int argsLength) {
        HostToTypeNode[] toJava = new HostToTypeNode[argsLength];
        for (int i = 0; i < argsLength; i++) {
            toJava[i] = HostToTypeNodeGen.create();
        }
        return toJava;
    }

    @SuppressWarnings("unused")
    @ExplodeLoop
    @Specialization(guards = {"!method.isVarArgs()", "method == cachedMethod"}, limit = "LIMIT")
    static Object doFixed(Node node, SingleMethod method, Object obj, Object[] args, HostContext hostContext,
                    @Cached("method") SingleMethod cachedMethod,
                    @Cached("createToHost(method.getParameterCount())") HostToTypeNode[] toJavaNodes,
                    @Exclusive @Cached ToGuestValueNode toGuest,
                    @Exclusive @Cached InlinedExactClassProfile receiverProfile,
                    @Shared("errorBranch") @Cached InlinedBranchProfile errorBranch,
                    @Shared("seenScope") @Cached InlinedBranchProfile seenDynamicScope,
                    @Cached(value = "hostContext.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws ArityException, UnsupportedTypeException {
        int arity = cachedMethod.getParameterCount();
        if (args.length != arity) {
            errorBranch.enter(node);
            throw ArityException.create(arity, arity, args.length);
        }
        Class<?>[] types = cachedMethod.getParameterTypes();
        Type[] genericTypes = cachedMethod.getGenericParameterTypes();
        Object[] convertedArguments = new Object[args.length];

        HostMethodScope scope = HostMethodScope.openStatic(cachedMethod);
        try {
            try {
                for (int i = 0; i < toJavaNodes.length; i++) {
                    Object operand = HostMethodScope.addToScopeStatic(scope, cachedMethod, i, args[i]);
                    convertedArguments[i] = toJavaNodes[i].execute(toJavaNodes[i], hostContext, operand, types[i], genericTypes[i], true);
                }
            } catch (RuntimeException e) {
                errorBranch.enter(node);
                if (cache.language.access.isEngineException(e)) {
                    throw HostInteropErrors.unsupportedTypeException(args, cache.language.access.unboxEngineException(e));
                }
                throw e;
            }
            return doInvoke(node, cachedMethod, receiverProfile.profile(node, obj), convertedArguments, cache, hostContext, toGuest);
        } finally {
            HostMethodScope.closeStatic(node, scope, cachedMethod, seenDynamicScope);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"method.isVarArgs()", "method == cachedMethod"}, limit = "LIMIT")
    static Object doVarArgs(Node node, SingleMethod method, Object obj, Object[] args, HostContext hostContext,
                    @Cached("method") SingleMethod cachedMethod,
                    @Exclusive @Cached HostToTypeNode toJavaNode,
                    @Exclusive @Cached ToGuestValueNode toGuest,
                    @Exclusive @Cached InlinedExactClassProfile receiverProfile,
                    @Shared("errorBranch") @Cached InlinedBranchProfile errorBranch,
                    @Shared("seenScope") @Cached InlinedBranchProfile seenDynamicScope,
                    @Cached("asVarArgs(args, cachedMethod, hostContext)") boolean asVarArgs,
                    @Cached(value = "hostContext.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws ArityException, UnsupportedTypeException {
        int parameterCount = cachedMethod.getParameterCount();
        int minArity = parameterCount - 1;
        if (args.length < minArity) {
            errorBranch.enter(node);
            throw ArityException.create(minArity, -1, args.length);
        }
        Class<?>[] types = cachedMethod.getParameterTypes();
        Type[] genericTypes = cachedMethod.getGenericParameterTypes();
        Object[] convertedArguments = new Object[args.length];

        HostMethodScope scope = HostMethodScope.openStatic(cachedMethod);
        try {
            try {
                for (int i = 0; i < minArity; i++) {
                    Object operand = HostMethodScope.addToScopeStatic(scope, cachedMethod, i, args[i]);
                    convertedArguments[i] = toJavaNode.execute(node, hostContext, operand, types[i], genericTypes[i], true);
                }
                if (asVarArgs) {
                    for (int i = minArity; i < args.length; i++) {
                        Class<?> expectedType = types[minArity].getComponentType();
                        Type expectedGenericType = getGenericComponentType(genericTypes[minArity]);

                        Object operand = HostMethodScope.addToScopeStatic(scope, cachedMethod, i, args[i]);
                        convertedArguments[i] = toJavaNode.execute(node, hostContext, operand, expectedType, expectedGenericType, true);
                    }
                    convertedArguments = createVarArgsArray(cachedMethod, convertedArguments, parameterCount);
                } else {
                    Object operand = HostMethodScope.addToScopeStatic(scope, cachedMethod, minArity, args[minArity]);
                    convertedArguments[minArity] = toJavaNode.execute(node, hostContext, operand, types[minArity], genericTypes[minArity], true);
                }
            } catch (RuntimeException e) {
                errorBranch.enter(node);
                if (cache.language.access.isEngineException(e)) {
                    throw HostInteropErrors.unsupportedTypeException(args, cache.language.access.unboxEngineException(e));
                }
                throw e;
            }
            return doInvoke(node, cachedMethod, receiverProfile.profile(node, obj), convertedArguments, cache, hostContext, toGuest);
        } finally {
            HostMethodScope.closeStatic(node, scope, cachedMethod, seenDynamicScope);
        }
    }

    @Specialization(replaces = {"doFixed", "doVarArgs"})
    static Object doSingleUncached(Node node, SingleMethod method, Object obj, Object[] args, HostContext hostContext,
                    @Shared("toHost") @Cached HostToTypeNode toJavaNode,
                    @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                    @Shared("varArgsProfile") @Cached InlinedConditionProfile isVarArgsProfile,
                    @Shared("hostMethodProfile") @Cached HostMethodProfileNode methodProfile,
                    @Shared("errorBranch") @Cached InlinedBranchProfile errorBranch,
                    @Shared("seenScope") @Cached InlinedBranchProfile seenScope,
                    @Shared("cache") @Cached(value = "hostContext.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws ArityException, UnsupportedTypeException {
        int parameterCount = method.getParameterCount();
        int minArity;
        int maxArity;
        boolean arityError;
        if (isVarArgsProfile.profile(node, method.isVarArgs())) {
            minArity = parameterCount - 1;
            maxArity = -1;
            arityError = args.length < minArity;
        } else {
            minArity = parameterCount;
            maxArity = method.getParameterCount();
            arityError = args.length != minArity;
        }
        if (arityError) {
            errorBranch.enter(node);
            throw ArityException.create(minArity, maxArity, args.length);
        }
        Object[] convertedArguments;
        HostMethodScope scope = HostMethodScope.openDynamic(node, method, args.length, seenScope);
        try {
            try {
                convertedArguments = prepareArgumentsUncached(node, method, args, hostContext, toJavaNode, scope, isVarArgsProfile);
            } catch (RuntimeException e) {
                errorBranch.enter(node);
                if (cache.language.access.isEngineException(e)) {
                    throw HostInteropErrors.unsupportedTypeException(args, cache.language.access.unboxEngineException(e));
                }
                throw e;
            }
            return doInvoke(node, methodProfile.execute(node, method), obj, convertedArguments, cache, hostContext, toGuest);
        } finally {
            HostMethodScope.closeDynamic(scope, method);
        }
    }

    // Note: checkArgTypes must be evaluated after selectOverload.
    @SuppressWarnings({"unused", "static-method", "truffle-static-method"})
    @ExplodeLoop
    @Specialization(guards = {"method == cachedMethod", "checkArgTypes(args, cachedArgTypes, interop, hostContext, asVarArgs)"}, limit = "LIMIT")
    static final Object doOverloadedCached(Node node, OverloadedMethod method, Object obj, Object[] args, HostContext hostContext,
                    @Cached("method") OverloadedMethod cachedMethod,
                    @Exclusive @Cached HostToTypeNode toJavaNode,
                    @Exclusive @Cached ToGuestValueNode toGuest,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached("createArgTypesArray(args)") TypeCheckNode[] cachedArgTypes,
                    @Cached("selectOverload(node, method, args, hostContext, cachedArgTypes)") SingleMethod overload,
                    @Cached("asVarArgs(args, overload, hostContext)") boolean asVarArgs,
                    @Exclusive @Cached InlinedExactClassProfile receiverProfile,
                    @Shared("errorBranch") @Cached InlinedBranchProfile errorBranch,
                    @Shared("seenScope") @Cached InlinedBranchProfile seenVariableScope,
                    @Cached(value = "hostContext.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws ArityException, UnsupportedTypeException {
        assert overload == selectOverload(node, method, args, hostContext);
        Class<?>[] types = overload.getParameterTypes();
        Type[] genericTypes = overload.getGenericParameterTypes();
        Object[] convertedArguments = new Object[cachedArgTypes.length];

        HostMethodScope scope = HostMethodScope.openStatic(overload);
        try {
            try {
                if (asVarArgs) {
                    assert overload.isVarArgs();
                    int parameterCount = overload.getParameterCount();
                    for (int i = 0; i < cachedArgTypes.length; i++) {
                        Class<?> expectedType = i < parameterCount - 1 ? types[i] : types[parameterCount - 1].getComponentType();
                        Type expectedGenericType = i < parameterCount - 1 ? genericTypes[i] : getGenericComponentType(genericTypes[parameterCount - 1]);
                        Object operand = HostMethodScope.addToScopeStatic(scope, overload, i, args[i]);
                        convertedArguments[i] = toJavaNode.execute(node, hostContext, operand, expectedType, expectedGenericType, true);
                    }
                    convertedArguments = createVarArgsArray(overload, convertedArguments, parameterCount);
                } else {
                    for (int i = 0; i < cachedArgTypes.length; i++) {
                        Object operand = HostMethodScope.addToScopeStatic(scope, overload, i, args[i]);
                        convertedArguments[i] = toJavaNode.execute(node, hostContext, operand, types[i], genericTypes[i], true);
                    }
                }
            } catch (RuntimeException e) {
                errorBranch.enter(node);
                if (cache.language.access.isEngineException(e)) {
                    throw HostInteropErrors.unsupportedTypeException(args, cache.language.access.unboxEngineException(e));
                }
                throw e;
            }
            return doInvoke(node, overload, receiverProfile.profile(node, obj), convertedArguments, cache, hostContext, toGuest);
        } finally {
            HostMethodScope.closeStatic(node, scope, overload, seenVariableScope);
        }
    }

    @SuppressWarnings("static-method")
    @Specialization(replaces = "doOverloadedCached")
    static final Object doOverloadedUncached(Node node, OverloadedMethod method, Object obj, Object[] args, HostContext hostContext,
                    @Shared("toHost") @Cached HostToTypeNode toJavaNode,
                    @Shared("toGuest") @Cached ToGuestValueNode toGuest,
                    @Shared("varArgsProfile") @Cached InlinedConditionProfile isVarArgsProfile,
                    @Shared("hostMethodProfile") @Cached HostMethodProfileNode methodProfile,
                    @Shared("errorBranch") @Cached InlinedBranchProfile errorBranch,
                    @Shared("seenScope") @Cached InlinedBranchProfile seenScope,
                    @Shared("cache") @Cached(value = "hostContext.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws ArityException, UnsupportedTypeException {
        SingleMethod overload = selectOverload(node, method, args, hostContext);
        Object[] convertedArguments;

        HostMethodScope scope = HostMethodScope.openDynamic(node, overload, args.length, seenScope);
        try {
            try {
                convertedArguments = prepareArgumentsUncached(node, overload, args, hostContext, toJavaNode, scope, isVarArgsProfile);
            } catch (RuntimeException e) {
                errorBranch.enter(node);
                if (cache.language.access.isEngineException(e)) {
                    throw HostInteropErrors.unsupportedTypeException(args, cache.language.access.unboxEngineException(e));
                }
                throw e;
            }
            return doInvoke(node, methodProfile.execute(node, overload), obj, convertedArguments, cache, hostContext, toGuest);
        } finally {
            HostMethodScope.closeDynamic(scope, overload);
        }
    }

    private static Object[] prepareArgumentsUncached(Node node, SingleMethod method, Object[] args, HostContext context, HostToTypeNode toJavaNode, HostMethodScope scope,
                    InlinedConditionProfile isVarArgsProfile) {
        Class<?>[] types = method.getParameterTypes();
        Type[] genericTypes = method.getGenericParameterTypes();
        Object[] convertedArguments = new Object[args.length];
        if (isVarArgsProfile.profile(node, method.isVarArgs()) && asVarArgs(args, method, context)) {
            int parameterCount = method.getParameterCount();
            for (int i = 0; i < args.length; i++) {
                Class<?> expectedType = i < parameterCount - 1 ? types[i] : types[parameterCount - 1].getComponentType();
                Type expectedGenericType = i < parameterCount - 1 ? genericTypes[i] : getGenericComponentType(genericTypes[parameterCount - 1]);
                Object operand = HostMethodScope.addToScopeDynamic(scope, args[i]);
                convertedArguments[i] = toJavaNode.execute(node, context, operand, expectedType, expectedGenericType, true);
            }
            convertedArguments = createVarArgsArray(method, convertedArguments, parameterCount);
        } else {
            for (int i = 0; i < args.length; i++) {
                Object operand = HostMethodScope.addToScopeDynamic(scope, args[i]);
                convertedArguments[i] = toJavaNode.execute(node, context, operand, types[i], genericTypes[i], true);
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
    private static void fillArgTypesArray(Node node, Object[] args, TypeCheckNode[] cachedArgTypes, SingleMethod selected, boolean varArgs, List<SingleMethod> applicable, int priority,
                    HostContext context) {
        if (cachedArgTypes == null) {
            return;
        }
        HostClassCache cache = context.getHostClassCache();
        boolean multiple = applicable.size() > 1;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Class<?> targetType = getParameterType(selected.getParameterTypes(), i, varArgs);
            Set<HostTargetMapping> otherPossibleMappings = null;
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
                    if (!HostToTypeNode.canConvert(null, arg, paramType, paramType, null, context, priority,
                                    InteropLibrary.getFactory().getUncached(), HostTargetMappingNode.getUncached())) {
                        HostTargetMapping[] otherMappings = cache.getMappings(paramType);
                        if (otherPossibleMappings == null) {
                            otherPossibleMappings = new LinkedHashSet<>();
                        }
                        for (HostTargetMapping mapping : otherMappings) {
                            otherPossibleMappings.add(mapping);
                        }
                    }
                }
            }
            TypeCheckNode argType;
            if (arg == null) {
                argType = NullCheckNode.INSTANCE;
            } else if (multiple && HostToTypeNode.isPrimitiveOrBigIntegerTarget(context, targetType)) {
                argType = createPrimitiveTargetCheck(applicable, selected, arg, targetType, i, priority, varArgs, context);
            } else if (arg instanceof HostObject) {
                argType = new JavaObjectType(((HostObject) arg).getObjectClass());
            } else {
                argType = new DirectTypeCheck(arg.getClass());
            }
            HostTargetMapping[] mappings = cache.getMappings(targetType);
            if (mappings.length > 0 || otherPossibleMappings != null) {
                HostTargetMapping[] otherMappings = otherPossibleMappings != null ? otherPossibleMappings.toArray(HostClassCache.EMPTY_MAPPINGS) : HostClassCache.EMPTY_MAPPINGS;
                argType = new TargetMappingType(argType, mappings, otherMappings, priority);
            }
            /*
             * We need to eagerly insert as the cachedArgTypes might be used before they are adopted
             * by the DSL.
             */
            cachedArgTypes[i] = node.insert(argType);
        }

        assert checkArgTypes(args, cachedArgTypes, InteropLibrary.getFactory().getUncached(), context, false) : Arrays.toString(cachedArgTypes);
    }

    private static TypeCheckNode createPrimitiveTargetCheck(List<SingleMethod> applicable, SingleMethod selected, Object arg, Class<?> targetType, int parameterIndex, int priority, boolean varArgs,
                    HostContext context) {
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
            if ((HostToTypeNode.isPrimitiveOrBigIntegerTarget(context, paramType) || HostToTypeNode.isPrimitiveOrBigIntegerTarget(context, targetType)) &&
                            isAssignableFrom(targetType, paramType) && !isSubtypeOf(arg, paramType)) {
                otherPossibleTypes.add(paramType);
            }
        }
        return new PrimitiveType(currentTargetType, otherPossibleTypes.toArray(EMPTY_CLASS_ARRAY), priority);
    }

    @ExplodeLoop
    static boolean checkArgTypes(Object[] args, TypeCheckNode[] argTypes, InteropLibrary interop, HostContext context, @SuppressWarnings("unused") boolean dummy) {
        if (args.length != argTypes.length) {
            return false;
        }
        for (int i = 0; i < argTypes.length; i++) {
            TypeCheckNode argType = argTypes[i];
            if (!argType.execute(args[i], interop, context)) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    static boolean asVarArgs(Object[] args, SingleMethod overload, HostContext hostContext) {
        if (overload.isVarArgs()) {
            int parameterCount = overload.getParameterCount();
            if (args.length == parameterCount) {
                Class<?> varArgParamType = overload.getParameterTypes()[parameterCount - 1];
                return !HostToTypeNode.canConvert(null, args[parameterCount - 1], varArgParamType, overload.getGenericParameterTypes()[parameterCount - 1],
                                null, hostContext, HostToTypeNode.COERCE,
                                InteropLibrary.getFactory().getUncached(), HostTargetMappingNode.getUncached());
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
    static SingleMethod selectOverload(Node node, OverloadedMethod method, Object[] args, HostContext hostContext) throws ArityException, UnsupportedTypeException {
        return selectOverload(node, method, args, hostContext, null);
    }

    @TruffleBoundary
    static SingleMethod selectOverload(Node node, OverloadedMethod method, Object[] args, HostContext hostContext, TypeCheckNode[] cachedArgTypes)
                    throws ArityException, UnsupportedTypeException {
        SingleMethod[] overloads = method.getOverloads();
        List<SingleMethod> applicableByArity = new ArrayList<>();
        int minOverallArity = Integer.MAX_VALUE;
        int maxOverallArity = 0;
        boolean anyVarArgs = false;
        assert overloads.length > 0;
        for (SingleMethod overload : overloads) {
            int paramCount = overload.getParameterCount();
            if (overload.isVarArgs()) {
                anyVarArgs = true;
                int fixedParamCount = paramCount - 1;
                if (args.length < fixedParamCount) {
                    minOverallArity = Math.min(minOverallArity, fixedParamCount);
                    maxOverallArity = Math.max(maxOverallArity, fixedParamCount);
                    continue;
                }
            } else {
                if (args.length != paramCount) {
                    minOverallArity = Math.min(minOverallArity, paramCount);
                    maxOverallArity = Math.max(maxOverallArity, paramCount);
                    continue;
                }
            }
            applicableByArity.add(overload);
        }
        if (applicableByArity.isEmpty()) {
            throw ArityException.create(minOverallArity, anyVarArgs ? -1 : maxOverallArity, args.length);
        }

        SingleMethod best;
        for (int priority : HostToTypeNode.PRIORITIES) {
            best = findBestCandidate(node, applicableByArity, args, hostContext, false, priority, cachedArgTypes);
            if (best != null) {
                return best;
            }

            if (anyVarArgs) {
                best = findBestCandidate(node, applicableByArity, args, hostContext, true, priority, cachedArgTypes);
                if (best != null) {
                    return best;
                }
            }
        }
        throw noApplicableOverloadsException(overloads, args);
    }

    private static SingleMethod findBestCandidate(Node node, List<SingleMethod> applicableByArity, Object[] args, HostContext hostContext, boolean varArgs, int priority,
                    TypeCheckNode[] cachedArgTypes) throws UnsupportedTypeException {
        List<SingleMethod> candidates = new ArrayList<>();

        if (!varArgs) {
            for (SingleMethod candidate : applicableByArity) {
                int paramCount = candidate.getParameterCount();
                if (!candidate.isOnlyVisibleFromJniName() && (!candidate.isVarArgs() || paramCount == args.length)) {
                    assert paramCount == args.length;
                    Class<?>[] parameterTypes = candidate.getParameterTypes();
                    Type[] genericParameterTypes = candidate.getGenericParameterTypes();
                    boolean applicable = true;
                    for (int i = 0; i < paramCount; i++) {
                        if (!HostToTypeNode.canConvert(null, args[i], parameterTypes[i], genericParameterTypes[i], null,
                                        hostContext, priority, InteropLibrary.getFactory().getUncached(args[i]),
                                        HostTargetMappingNode.getUncached())) {
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
                if (candidate.isVarArgs() && !candidate.isOnlyVisibleFromJniName()) {
                    int parameterCount = candidate.getParameterCount();
                    Class<?>[] parameterTypes = candidate.getParameterTypes();
                    Type[] genericParameterTypes = candidate.getGenericParameterTypes();
                    boolean applicable = true;
                    for (int i = 0; i < parameterCount - 1; i++) {
                        if (!HostToTypeNode.canConvert(null, args[i], parameterTypes[i], genericParameterTypes[i], null,
                                        hostContext, priority, InteropLibrary.getFactory().getUncached(args[i]),
                                        HostTargetMappingNode.getUncached())) {
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
                            if (!HostToTypeNode.canConvert(null, args[i], varArgsComponentType, varArgsGenericComponentType, null,
                                            hostContext, priority,
                                            InteropLibrary.getFactory().getUncached(args[i]), HostTargetMappingNode.getUncached())) {
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
                    fillArgTypesArray(node, args, cachedArgTypes, best, varArgs, applicableByArity, priority, hostContext);
                }

                return best;
            } else {
                SingleMethod best = findMostSpecificOverload(hostContext, candidates, args, varArgs, priority);
                if (best != null) {
                    if (cachedArgTypes != null) {
                        fillArgTypesArray(node, args, cachedArgTypes, best, varArgs, applicableByArity, priority, hostContext);
                    }

                    return best;
                }
                throw ambiguousOverloadsException(candidates, args);
            }
        }
        return null;
    }

    private static SingleMethod findMostSpecificOverload(HostContext context, List<SingleMethod> candidates, Object[] args, boolean varArgs, int priority) {
        assert candidates.size() >= 2;
        if (candidates.size() == 2) {
            int res = compareOverloads(context, candidates.get(0), candidates.get(1), args, varArgs, priority);
            return res == 0 ? null : (res < 0 ? candidates.get(0) : candidates.get(1));
        }

        Iterator<SingleMethod> candIt = candidates.iterator();
        List<SingleMethod> best = new LinkedList<>();
        best.add(candIt.next());

        while (candIt.hasNext()) {
            SingleMethod cand = candIt.next();
            boolean add = false;
            for (Iterator<SingleMethod> bestIt = best.iterator(); bestIt.hasNext();) {
                int res = compareOverloads(context, cand, bestIt.next(), args, varArgs, priority);
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

    private static int compareOverloads(HostContext context, SingleMethod m1, SingleMethod m2, Object[] args, boolean varArgs, int priority) {
        int res = 0;
        assert !varArgs || m1.isVarArgs() && m2.isVarArgs();
        assert varArgs || (m1.getParameterCount() == m2.getParameterCount() && args.length == m1.getParameterCount());
        for (int i = 0; i < args.length; i++) {
            Class<?> t1 = getParameterType(m1.getParameterTypes(), i, varArgs);
            Class<?> t2 = getParameterType(m2.getParameterTypes(), i, varArgs);
            if (t1 == t2) {
                continue;
            }
            int r = compareByPriority(context, t1, t2, args[i], priority);
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

    private static int compareByPriority(HostContext context, Class<?> t1, Class<?> t2, Object arg, int priority) {
        if (priority <= HostToTypeNode.STRICT) {
            return 0;
        }
        InteropLibrary argInterop = InteropLibrary.getFactory().getUncached(arg);
        HostTargetMappingNode mapping = HostTargetMappingNode.getUncached();
        for (int p : HostToTypeNode.PRIORITIES) {
            if (p > priority) {
                break;
            }
            boolean p1 = HostToTypeNode.canConvert(null, arg, t1, t1, null, context, p, argInterop, mapping);
            boolean p2 = HostToTypeNode.canConvert(null, arg, t2, t2, null, context, p, argInterop, mapping);
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
        } else if (toAsPrimitive == null && fromAsPrimitive != null && toType == BigInteger.class && Number.class.isAssignableFrom(primitiveTypeToBoxedType(fromAsPrimitive))) {
            // primitive|boxed <: BigInteger
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

    private static Object doInvoke(Node node, SingleMethod method, Object obj, Object[] arguments, GuestToHostCodeCache cache, HostContext hostContext, ToGuestValueNode toGuest) {
        assert cache == hostContext.getGuestToHostCache();
        assert arguments.length == method.getParameterCount();
        Object ret = method.invokeGuestToHost(obj, arguments, cache, hostContext, node);
        return toGuest.execute(node, hostContext, ret);
    }

    private static String arrayToStringWithTypes(Object[] args) {
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        for (Object arg : args) {
            sj.add(arg == null ? null : arg.toString() + " (" + arg.getClass().getSimpleName() + ")");
        }
        return sj.toString();
    }

    abstract static class TypeCheckNode extends Node {

        abstract boolean execute(Object test, InteropLibrary interop, HostContext context);

    }

    static final class NullCheckNode extends TypeCheckNode {

        static final NullCheckNode INSTANCE = new NullCheckNode();

        @Override
        boolean execute(Object test, InteropLibrary interop, HostContext context) {
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
        boolean execute(Object test, InteropLibrary interop, HostContext context) {
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
        boolean execute(Object arg, InteropLibrary interop, HostContext context) {
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

        @CompilationFinal(dimensions = 1) final HostTargetMapping[] mappings;
        @CompilationFinal(dimensions = 1) final HostTargetMapping[] otherMappings;

        @Child TypeCheckNode fallback;
        @Children final SingleMappingNode[] mappingNodes;
        @Children final SingleMappingNode[] otherMappingNodes;
        final int priority;

        TargetMappingType(TypeCheckNode fallback,
                        HostTargetMapping[] mappings,
                        HostTargetMapping[] otherMappings,
                        int priority) {
            this.fallback = fallback;
            this.priority = priority;
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
        }

        @Override
        @ExplodeLoop
        boolean execute(Object test, InteropLibrary interop, HostContext context) {
            for (int i = 0; i < otherMappingNodes.length; i++) {
                HostTargetMapping mapping = otherMappings[i];
                if (mapping.hostPriority > priority) {
                    break;
                }
                Object result = otherMappingNodes[i].execute(test, mapping, context, interop, true);
                if (result == Boolean.TRUE) {
                    return false;
                }
            }

            for (int i = 0; i < mappingNodes.length; i++) {
                HostTargetMapping mapping = mappings[i];
                if (mapping.hostPriority > priority) {
                    break;
                }
                Object result = mappingNodes[i].execute(test, mapping, context, interop, true);
                if (result == Boolean.TRUE) {
                    return true;
                }
            }
            return fallback.execute(test, interop, context);
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
        final int priority;

        PrimitiveType(Class<?> targetType, Class<?>[] otherTypes, int priority) {
            this.targetType = targetType;
            this.otherTypes = otherTypes;
            this.priority = priority;
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
        public boolean execute(Object value, InteropLibrary interop, HostContext context) {
            for (Class<?> otherType : otherTypes) {
                if (HostToTypeNode.canConvert(null, value, otherType, otherType, null, context, priority, interop, null)) {
                    return false;
                }
            }
            return HostToTypeNode.canConvert(null, value, targetType, targetType, null, context, priority, interop, null);
        }
    }

    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    abstract static class HostMethodProfileNode extends Node {
        public abstract SingleMethod execute(Node node, SingleMethod method);

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
