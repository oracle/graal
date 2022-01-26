/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.methodhandles;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.invoke.MethodHandleIntrinsic;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import sun.invoke.util.ValueConversions;
import sun.invoke.util.Wrapper;

/**
 * Method handles are implemented in Native Image through reflection. A method handle can have one
 * of two forms:
 *
 * <ul>
 * <li>DirectMethodHandle: a method handle that is bound to an actual method. This type of handle
 * contains a MemberName field which links to the Reflection API representation of the method, and
 * is used to invoke it.
 * <li>BoundMethodHandle: a method handle that links to other method handles. It contains a
 * LambdaForm, which is a tree of method handle invocations. These invocations can take three types
 * of arguments: the arguments to the BoundMethodHandle invocation, the results of previous
 * invocations in the tree, or cached parameters stored in the BoundMethodHandle. The return value
 * of the handle is usually the result of the last invocation in the tree.
 * </ul>
 *
 * Both types of method handles are created through the API defined in the {@link java.lang.invoke}
 * package. We mostly reuse the JDK implementation for those, with some exceptions which are
 * detailed in the substitution methods in this package, notably to avoid the runtime compilation of
 * method handle trees into optimized invokers.
 *
 * Some direct method handles with particular semantics (defined in {@link MethodHandleIntrinsic})
 * are directly executed without going through the reflection API. We also substitute the native
 * calls into the JDK internals with equivalent implementations (see
 * {@link Target_java_lang_invoke_MethodHandleNatives}).
 */
@AutomaticFeature
@SuppressWarnings("unused")
public class MethodHandleFeature implements Feature {

    private Set<MethodHandle> seenMethodHandles;
    private Class<?> directMethodHandleClass;
    private Class<?> boundMethodHandleClass;
    private Class<?> delegatingMethodHandleClass;
    private Method getDelegatingMethodHandleTarget;
    private Method methodHandleInternalMemberName;
    private Method memberNameGetDeclaringClass;
    private Method memberNameGetName;
    private Method memberNameIsMethod;
    private Method memberNameIsConstructor;
    private Method memberNameIsField;
    private Method memberNameGetParameterTypes;
    private Field methodHandleInternalForm;
    private Field lambdaFormNames;
    private Field lambdaFormArity;
    private Field nameFunction;
    private Field namedFunctionMemberName;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        seenMethodHandles = ConcurrentHashMap.newKeySet();
        directMethodHandleClass = access.findClassByName("java.lang.invoke.DirectMethodHandle");
        boundMethodHandleClass = access.findClassByName("java.lang.invoke.BoundMethodHandle");
        delegatingMethodHandleClass = access.findClassByName("java.lang.invoke.DelegatingMethodHandle");
        getDelegatingMethodHandleTarget = ReflectionUtil.lookupMethod(delegatingMethodHandleClass, "getTarget");
        methodHandleInternalMemberName = ReflectionUtil.lookupMethod(MethodHandle.class, "internalMemberName");
        methodHandleInternalForm = ReflectionUtil.lookupField(MethodHandle.class, "form");

        Class<?> memberNameClass = access.findClassByName("java.lang.invoke.MemberName");
        memberNameGetDeclaringClass = ReflectionUtil.lookupMethod(memberNameClass, "getDeclaringClass");
        memberNameGetName = ReflectionUtil.lookupMethod(memberNameClass, "getName");
        memberNameIsMethod = ReflectionUtil.lookupMethod(memberNameClass, "isMethod");
        memberNameIsConstructor = ReflectionUtil.lookupMethod(memberNameClass, "isConstructor");
        memberNameIsField = ReflectionUtil.lookupMethod(memberNameClass, "isField");
        memberNameGetParameterTypes = ReflectionUtil.lookupMethod(memberNameClass, "getParameterTypes");

        Class<?> lambdaFormClass = access.findClassByName("java.lang.invoke.LambdaForm");
        lambdaFormNames = ReflectionUtil.lookupField(lambdaFormClass, "names");
        lambdaFormArity = ReflectionUtil.lookupField(lambdaFormClass, "arity");
        Class<?> nameClass = access.findClassByName("java.lang.invoke.LambdaForm$Name");
        nameFunction = ReflectionUtil.lookupField(nameClass, "function");
        Class<?> namedFunctionClass = access.findClassByName("java.lang.invoke.LambdaForm$NamedFunction");
        namedFunctionMemberName = ReflectionUtil.lookupField(namedFunctionClass, "member");

        access.registerObjectReplacer(this::registerMethodHandle);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /* java.lang.invoke functions called through reflection */
        Class<?> mhImplClazz = access.findClassByName("java.lang.invoke.MethodHandleImpl");

        access.registerReachabilityHandler(MethodHandleFeature::registerMHImplFunctionsForReflection,
                        ReflectionUtil.lookupMethod(mhImplClazz, "createFunction", byte.class));

        access.registerReachabilityHandler(MethodHandleFeature::registerMHImplConstantHandlesForReflection,
                        ReflectionUtil.lookupMethod(mhImplClazz, "makeConstantHandle", int.class));

        access.registerReachabilityHandler(MethodHandleFeature::registerMHImplCountingWrapperFunctionsForReflection,
                        access.findClassByName("java.lang.invoke.MethodHandleImpl$CountingWrapper"));

        access.registerReachabilityHandler(MethodHandleFeature::registerInvokersFunctionsForReflection,
                        ReflectionUtil.lookupMethod(access.findClassByName("java.lang.invoke.Invokers"), "createFunction", byte.class));

        access.registerReachabilityHandler(MethodHandleFeature::registerValueConversionBoxFunctionsForReflection,
                        ReflectionUtil.lookupMethod(ValueConversions.class, "boxExact", Wrapper.class));

        access.registerReachabilityHandler(MethodHandleFeature::registerValueConversionUnboxFunctionsForReflection,
                        ReflectionUtil.lookupMethod(ValueConversions.class, "unbox", Wrapper.class, int.class));

        access.registerReachabilityHandler(MethodHandleFeature::registerValueConversionConvertFunctionsForReflection,
                        ReflectionUtil.lookupMethod(ValueConversions.class, "convertPrimitive", Wrapper.class, Wrapper.class));

        access.registerReachabilityHandler(MethodHandleFeature::registerValueConversionIgnoreForReflection,
                        ReflectionUtil.lookupMethod(ValueConversions.class, "ignore"));

        access.registerClassInitializerReachabilityHandler(MethodHandleFeature::registerDelegatingMHFunctionsForReflection,
                        access.findClassByName("java.lang.invoke.DelegatingMethodHandle"));

        access.registerReachabilityHandler(MethodHandleFeature::registerCallSiteGetTargetForReflection,
                        ReflectionUtil.lookupMethod(CallSite.class, "getTargetHandle"));

        access.registerReachabilityHandler(MethodHandleFeature::registerUninitializedCallSiteForReflection,
                        ReflectionUtil.lookupMethod(CallSite.class, "uninitializedCallSiteHandle"));

        access.registerSubtypeReachabilityHandler(MethodHandleFeature::registerVarHandleMethodsForReflection,
                        access.findClassByName("java.lang.invoke.VarHandle"));
    }

    private static void registerMHImplFunctionsForReflection(DuringAnalysisAccess access) {
        Class<?> mhImplClazz = access.findClassByName("java.lang.invoke.MethodHandleImpl");
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "checkSpreadArgument", Object.class, int.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "guardWithCatch", MethodHandle.class, Class.class, MethodHandle.class, Object[].class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "tryFinally", MethodHandle.class, MethodHandle.class, Object[].class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "loop", access.findClassByName("[Ljava.lang.invoke.LambdaForm$BasicType;"),
                        access.findClassByName("java.lang.invoke.MethodHandleImpl$LoopClauses"), Object[].class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "throwException", Throwable.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "profileBoolean", boolean.class, int[].class));
    }

    private static void registerMHImplConstantHandlesForReflection(DuringAnalysisAccess access) {
        Class<?> mhImplClazz = access.findClassByName("java.lang.invoke.MethodHandleImpl");
        if (JavaVersionUtil.JAVA_SPEC <= 11) {
            RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "copyAsPrimitiveArray", access.findClassByName("sun.invoke.util.Wrapper"), Object[].class));
            RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "identity", Object[].class));
            RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "fillNewArray", Integer.class, Object[].class));
            RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "fillNewTypedArray", Object[].class, Integer.class, Object[].class));
        }
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "selectAlternative", boolean.class, MethodHandle.class, MethodHandle.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "countedLoopPredicate", int.class, int.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "countedLoopStep", int.class, int.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "initIterator", Iterable.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "iteratePredicate", Iterator.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "iterateNext", Iterator.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(Array.class, "newInstance", Class.class, int.class));
    }

    private static void registerMHImplCountingWrapperFunctionsForReflection(DuringAnalysisAccess access) {
        RuntimeReflection.register(ReflectionUtil.lookupMethod(access.findClassByName("java.lang.invoke.MethodHandleImpl$CountingWrapper"), "maybeStopCounting", Object.class));
    }

    private static void registerInvokersFunctionsForReflection(DuringAnalysisAccess access) {
        Class<?> invokersClazz = access.findClassByName("java.lang.invoke.Invokers");
        RuntimeReflection.register(ReflectionUtil.lookupMethod(invokersClazz, "checkExactType", MethodHandle.class, MethodType.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(invokersClazz, "checkGenericType", MethodHandle.class, MethodType.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(invokersClazz, "getCallSiteTarget", CallSite.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(invokersClazz, "checkCustomized", MethodHandle.class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(invokersClazz, "checkVarHandleGenericType", access.findClassByName("java.lang.invoke.VarHandle"),
                        access.findClassByName("java.lang.invoke.VarHandle$AccessDescriptor")));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(invokersClazz, "checkVarHandleExactType", access.findClassByName("java.lang.invoke.VarHandle"),
                        access.findClassByName("java.lang.invoke.VarHandle$AccessDescriptor")));
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            RuntimeReflection.register(ReflectionUtil.lookupMethod(invokersClazz, "directVarHandleTarget", access.findClassByName("java.lang.invoke.VarHandle")));
        }
    }

    private static void registerValueConversionBoxFunctionsForReflection(DuringAnalysisAccess access) {
        for (Wrapper type : Wrapper.values()) {
            if (type.primitiveType().isPrimitive() && type != Wrapper.VOID) {
                RuntimeReflection.register(ReflectionUtil.lookupMethod(ValueConversions.class, "box" + type.wrapperSimpleName(), type.primitiveType()));
            }
        }
    }

    private static void registerValueConversionUnboxFunctionsForReflection(DuringAnalysisAccess access) {
        for (Wrapper type : Wrapper.values()) {
            if (type.primitiveType().isPrimitive() && type != Wrapper.VOID) {
                RuntimeReflection.register(ReflectionUtil.lookupMethod(ValueConversions.class, "unbox" + type.wrapperSimpleName(), type.wrapperType()));
                RuntimeReflection.register(ReflectionUtil.lookupMethod(ValueConversions.class, "unbox" + type.wrapperSimpleName(), Object.class, boolean.class));
            }
        }
    }

    private static void registerValueConversionConvertFunctionsForReflection(DuringAnalysisAccess access) {
        for (Wrapper src : Wrapper.values()) {
            for (Wrapper dest : Wrapper.values()) {
                if (src != dest && src.primitiveType().isPrimitive() && src != Wrapper.VOID && dest.primitiveType().isPrimitive() && dest != Wrapper.VOID) {
                    RuntimeReflection.register(ReflectionUtil.lookupMethod(ValueConversions.class, valueConverterName(src, dest), src.primitiveType()));
                }
            }
        }
    }

    private static String valueConverterName(Wrapper src, Wrapper dest) {
        String srcType = src.primitiveSimpleName();
        String destType = dest.primitiveSimpleName();
        /* Capitalize first letter of destination type */
        return srcType + "To" + destType.substring(0, 1).toUpperCase() + destType.substring(1);
    }

    private static void registerValueConversionIgnoreForReflection(DuringAnalysisAccess access) {
        RuntimeReflection.register(ReflectionUtil.lookupMethod(ValueConversions.class, "ignore", Object.class));
    }

    private static void registerDelegatingMHFunctionsForReflection(DuringAnalysisAccess access) {
        Class<?> delegatingMHClazz = access.findClassByName("java.lang.invoke.DelegatingMethodHandle");
        RuntimeReflection.register(ReflectionUtil.lookupMethod(delegatingMHClazz, "getTarget"));
    }

    private static void registerCallSiteGetTargetForReflection(DuringAnalysisAccess access) {
        RuntimeReflection.register(ReflectionUtil.lookupMethod(CallSite.class, "getTarget"));
    }

    private static void registerUninitializedCallSiteForReflection(DuringAnalysisAccess access) {
        RuntimeReflection.register(ReflectionUtil.lookupMethod(CallSite.class, "uninitializedCallSite", Object[].class));
    }

    private static void registerVarHandleMethodsForReflection(DuringAnalysisAccess access, Class<?> subtype) {
        if (subtype.getPackage().getName().equals("java.lang.invoke") && subtype != access.findClassByName("java.lang.invoke.VarHandle")) {
            RuntimeReflection.register(subtype.getDeclaredMethods());
        }
    }

    private Object registerMethodHandle(Object obj) {
        if (!BuildPhaseProvider.isAnalysisFinished()) {
            registerMethodHandleRecurse(obj);
        }
        return obj;
    }

    private void registerMethodHandleRecurse(Object obj) {
        if (!(obj instanceof MethodHandle) || seenMethodHandles.contains(obj)) {
            return;
        }
        MethodHandle handle = (MethodHandle) obj;
        seenMethodHandles.add(handle);

        if (directMethodHandleClass.isAssignableFrom(handle.getClass())) {
            try {
                registerMemberName(methodHandleInternalMemberName.invoke(handle));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw VMError.shouldNotReachHere(e);
            }
        } else if (boundMethodHandleClass.isAssignableFrom(handle.getClass())) {
            /* Allow access to species class args at runtime */
            for (Field field : handle.getClass().getDeclaredFields()) {
                if (field.getName().startsWith("arg")) {
                    RuntimeReflection.register(field);
                    if (!field.getType().isPrimitive()) {
                        try {
                            field.setAccessible(true);
                            registerMethodHandleRecurse(field.get(handle));
                        } catch (IllegalAccessException e) {
                            throw VMError.shouldNotReachHere(e);
                        }
                    }
                }
            }
            /* Recursively register all methods called by the handle */
            try {
                Object form = methodHandleInternalForm.get(handle);
                Object[] names = (Object[]) lambdaFormNames.get(form);
                int arity = (int) lambdaFormArity.get(form);
                for (int i = arity; i < names.length; ++i) {
                    Object function = nameFunction.get(names[i]);
                    if (function != null) {
                        registerMemberName(namedFunctionMemberName.get(function));
                    }
                }
            } catch (IllegalAccessException e) {
                VMError.shouldNotReachHere(e);
            }
        } else if (delegatingMethodHandleClass.isAssignableFrom(handle.getClass())) {
            try {
                MethodHandle wrappedHandle = (MethodHandle) getDelegatingMethodHandleTarget.invoke(handle);
                registerMethodHandleRecurse(wrappedHandle);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }

    private void registerMemberName(Object memberName) {
        try {
            Class<?> declaringClass = (Class<?>) memberNameGetDeclaringClass.invoke(memberName);
            boolean isMethod = (boolean) memberNameIsMethod.invoke(memberName);
            boolean isConstructor = (boolean) memberNameIsConstructor.invoke(memberName);
            boolean isField = (boolean) memberNameIsField.invoke(memberName);
            String name = (isMethod || isField) ? (String) memberNameGetName.invoke(memberName) : null;
            Class<?>[] paramTypes = (isMethod || isConstructor) ? (Class<?>[]) memberNameGetParameterTypes.invoke(memberName) : null;
            if (isMethod) {
                RuntimeReflection.register(declaringClass.getDeclaredMethod(name, paramTypes));
            } else if (isConstructor) {
                RuntimeReflection.register(declaringClass.getDeclaredConstructor(paramTypes));
            } else if (isField) {
                RuntimeReflection.register(declaringClass.getDeclaredField(name));
            }
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            /* Internal, malformed or illegal member name, we do not need to register it */
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}
