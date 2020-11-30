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
// Checkstyle: stop
import java.lang.reflect.Array;
// Checkstyle: resume
import java.util.Iterator;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.util.ReflectionUtil;

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
public class MethodHandleFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return NativeImageOptions.areMethodHandlesSupported();
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
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "copyAsPrimitiveArray", access.findClassByName("sun.invoke.util.Wrapper"), Object[].class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "identity", Object[].class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "fillNewArray", Integer.class, Object[].class));
        RuntimeReflection.register(ReflectionUtil.lookupMethod(mhImplClazz, "fillNewTypedArray", Object[].class, Integer.class, Object[].class));
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
    }
}

class MethodHandlesSupported implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return NativeImageOptions.areMethodHandlesSupported();
    }
}

class MethodHandlesNotSupported implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return !NativeImageOptions.areMethodHandlesSupported();
    }
}
