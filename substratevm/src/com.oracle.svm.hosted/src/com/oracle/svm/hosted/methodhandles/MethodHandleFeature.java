/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.methodhandles;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.invoke.MethodHandleIntrinsic;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
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
 * {@code Target_java_lang_invoke_MethodHandleNatives}).
 */
@AutomaticallyRegisteredFeature
@SuppressWarnings("unused")
public class MethodHandleFeature implements InternalFeature {

    private Method memberNameIsMethod;
    private Method memberNameIsConstructor;
    private Method memberNameIsField;
    private Method memberNameGetMethodType;
    private Field lambdaFormLFIdentity;
    private Field lambdaFormLFZero;
    private Field lambdaFormNFIdentity;
    private Field lambdaFormNFZero;
    private Field typedAccessors;

    /**
     * A new {@link MethodType} interning table which contains only objects that are already part of
     * the image. We cannot replace it with an empty table like we do for other caches because the
     * method handle code uses reference comparisons on {@link MethodType} objects and assumes that
     * unidentical objects are not equal. This breaks if an object is created at runtime because an
     * equivalent image heap object is not part of the table and subsequently fails a comparison.
     */
    private Object runtimeMethodTypeInternTable;
    private Method concurrentWeakInternSetAdd;

    private MethodHandleInvokerRenamingSubstitutionProcessor substitutionProcessor;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        Class<?> memberNameClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.MemberName");
        memberNameIsMethod = ReflectionUtil.lookupMethod(memberNameClass, "isMethod");
        memberNameIsConstructor = ReflectionUtil.lookupMethod(memberNameClass, "isConstructor");
        memberNameIsField = ReflectionUtil.lookupMethod(memberNameClass, "isField");
        memberNameGetMethodType = ReflectionUtil.lookupMethod(memberNameClass, "getMethodType");

        Class<?> lambdaFormClass = access.findClassByName("java.lang.invoke.LambdaForm");
        lambdaFormLFIdentity = ReflectionUtil.lookupField(lambdaFormClass, "LF_identity");
        lambdaFormLFZero = ReflectionUtil.lookupField(lambdaFormClass, "LF_zero");
        lambdaFormNFIdentity = ReflectionUtil.lookupField(lambdaFormClass, "NF_identity");
        lambdaFormNFZero = ReflectionUtil.lookupField(lambdaFormClass, "NF_zero");

        Class<?> arrayAccessorClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.MethodHandleImpl$ArrayAccessor");
        typedAccessors = ReflectionUtil.lookupField(arrayAccessorClass, "TYPED_ACCESSORS");

        Class<?> concurrentWeakInternSetClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.MethodType$ConcurrentWeakInternSet");
        runtimeMethodTypeInternTable = ReflectionUtil.newInstance(concurrentWeakInternSetClass);
        concurrentWeakInternSetAdd = ReflectionUtil.lookupMethod(concurrentWeakInternSetClass, "add", Object.class);

        if (!SubstrateOptions.UseOldMethodHandleIntrinsics.getValue()) {
            /*
             * Renaming is not crucial with old method handle intrinsics, so if those are requested
             * explicitly, disable renaming to offer a fallback in case it causes problems.
             */
            var accessImpl = (DuringSetupAccessImpl) access;
            substitutionProcessor = new MethodHandleInvokerRenamingSubstitutionProcessor(accessImpl.getBigBang());
            accessImpl.registerSubstitutionProcessor(substitutionProcessor);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {

        eagerlyInitializeMHImplFunctions();
        eagerlyInitializeMHImplConstantHandles();
        eagerlyInitializeInvokersFunctions();
        eagerlyInitializeValueConversionsCaches();
        eagerlyInitializeCallSite();

        access.registerSubtypeReachabilityHandler(MethodHandleFeature::registerVarHandleMethodsForReflection, VarHandle.class);
        access.registerSubtypeReachabilityHandler(MethodHandleFeature::scanBoundMethodHandle, ReflectionUtil.lookupClass(false, "java.lang.invoke.BoundMethodHandle"));

        AnalysisMetaAccess metaAccess = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getMetaAccess();
        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(ReflectionUtil.lookupClass(false, "java.lang.invoke.ClassSpecializer"), "cache"),
                        new FieldValueTransformerWithAvailability() {
                            private static final Class<?> SPECIES_DATA_CLASS = ReflectionUtil.lookupClass(false, "java.lang.invoke.ClassSpecializer$SpeciesData");

                            /*
                             * The value of the ClassSpecializer.cache is not seen by the analysis
                             * because the transformer declares the AfterAnalysis availability. This
                             * is unsafe, and it relies on the fact that the underlying data
                             * structure, a ConcurrentHashMap, was already seen by the analysis from
                             * other uses, and that the analysis already has a full view of its type
                             * structure. GR-46027 will implement a safe solution.
                             */
                            @Override
                            public FieldValueTransformerWithAvailability.ValueAvailability valueAvailability() {
                                return FieldValueTransformerWithAvailability.ValueAvailability.AfterAnalysis;
                            }

                            @Override
                            @SuppressWarnings("unchecked")
                            public Object transform(Object receiver, Object originalValue) {
                                ConcurrentHashMap<Object, Object> originalMap = (ConcurrentHashMap<Object, Object>) originalValue;
                                ConcurrentHashMap<Object, Object> filteredMap = new ConcurrentHashMap<>();
                                originalMap.forEach((key, speciesData) -> {
                                    if (isSpeciesReachable(speciesData)) {
                                        filteredMap.put(key, speciesData);
                                    }
                                });
                                return filteredMap;
                            }

                            private boolean isSpeciesReachable(Object speciesData) {
                                Class<?> speciesClass = ReflectionUtil.readField(SPECIES_DATA_CLASS, "speciesCode", speciesData);
                                Optional<AnalysisType> analysisType = metaAccess.optionalLookupJavaType(speciesClass);
                                return analysisType.isPresent() && analysisType.get().isReachable();
                            }
                        });
        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(ReflectionUtil.lookupClass(false, "java.lang.invoke.MethodType"), "internTable"),
                        (receiver, originalValue) -> runtimeMethodTypeInternTable);
    }

    private static void eagerlyInitializeMHImplFunctions() {
        var methodHandleImplClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.MethodHandleImpl");
        int count = ((Object[]) ReflectionUtil.readStaticField(methodHandleImplClass, "NFS")).length;
        var getFunctionMethod = ReflectionUtil.lookupMethod(methodHandleImplClass, "getFunction", byte.class);
        for (int i = 0; i < count; i++) {
            ReflectionUtil.invokeMethod(getFunctionMethod, null, (byte) i);
        }
    }

    private static void eagerlyInitializeMHImplConstantHandles() {
        var methodHandleImplClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.MethodHandleImpl");
        int count = ((Object[]) ReflectionUtil.readStaticField(methodHandleImplClass, "HANDLES")).length;
        var getConstantHandleMethod = ReflectionUtil.lookupMethod(methodHandleImplClass, "getConstantHandle", int.class);
        for (int i = 0; i < count; i++) {
            ReflectionUtil.invokeMethod(getConstantHandleMethod, null, i);
        }
    }

    private static void eagerlyInitializeInvokersFunctions() {
        var invokerksClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.Invokers");
        int count = ((Object[]) ReflectionUtil.readStaticField(invokerksClass, "NFS")).length;
        var getFunctionMethod = ReflectionUtil.lookupMethod(invokerksClass, "getFunction", byte.class);
        for (int i = 0; i < count; i++) {
            ReflectionUtil.invokeMethod(getFunctionMethod, null, (byte) i);
        }
    }

    /**
     * Eagerly initialize method handle caches in {@link ValueConversions} so that 1) we avoid
     * reflection registration for conversion methods, and 2) the static analysis already sees a
     * consistent snapshot that does not change after analysis when the JDK needs more conversions.
     */
    private static void eagerlyInitializeValueConversionsCaches() {
        ValueConversions.ignore();

        for (Wrapper src : Wrapper.values()) {
            if (src != Wrapper.VOID && src.primitiveType().isPrimitive()) {
                ValueConversions.boxExact(src);

                ValueConversions.unboxExact(src, false);
                ValueConversions.unboxExact(src, true);
                ValueConversions.unboxWiden(src);
                ValueConversions.unboxCast(src);
            }

            for (Wrapper dst : Wrapper.values()) {
                if (src != Wrapper.VOID && dst != Wrapper.VOID && (src == dst || (src.primitiveType().isPrimitive() && dst.primitiveType().isPrimitive()))) {
                    ValueConversions.convertPrimitive(src, dst);
                }
            }
        }
    }

    private static void eagerlyInitializeCallSite() {
        ReflectionUtil.invokeMethod(ReflectionUtil.lookupMethod(CallSite.class, "getTargetHandle"), null);
        ReflectionUtil.invokeMethod(ReflectionUtil.lookupMethod(CallSite.class, "uninitializedCallSiteHandle"), null);
    }

    private static void registerVarHandleMethodsForReflection(FeatureAccess access, Class<?> subtype) {
        if (subtype.getPackage().getName().equals("java.lang.invoke") && subtype != VarHandle.class) {
            RuntimeReflection.register(subtype.getDeclaredMethods());
        }
    }

    public void registerHeapMethodType(MethodType methodType) {
        try {
            concurrentWeakInternSetAdd.invoke(runtimeMethodTypeInternTable, methodType);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    public void registerHeapMemberName(Member memberName) {
        /*
         * We used to register only MemberName instances which are reachable from MethodHandle
         * objects, but code optimizations can eliminate a MethodHandle object which might never
         * become reachable otherwise and leave a MemberName object behind which is still used for a
         * call or field access. Therefore, we register all MemberName instances in the image heap,
         * which should only come into existence via MethodHandle objects, in any case.
         */
        try {
            Class<?> declaringClass = memberName.getDeclaringClass();
            boolean isMethod = (boolean) memberNameIsMethod.invoke(memberName);
            boolean isConstructor = (boolean) memberNameIsConstructor.invoke(memberName);
            boolean isField = (boolean) memberNameIsField.invoke(memberName);
            String name = (isMethod || isField) ? memberName.getName() : null;
            Class<?>[] paramTypes = null;
            if (isMethod || isConstructor) {
                MethodType methodType = (MethodType) memberNameGetMethodType.invoke(memberName);
                paramTypes = methodType.parameterArray();
            }
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

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        access.rescanRoot(lambdaFormLFIdentity);
        access.rescanRoot(lambdaFormLFZero);
        access.rescanRoot(lambdaFormNFIdentity);
        access.rescanRoot(lambdaFormNFZero);
        access.rescanRoot(typedAccessors);
        access.rescanObject(runtimeMethodTypeInternTable);
    }

    private static void scanBoundMethodHandle(DuringAnalysisAccess a, Class<?> bmhSubtype) {
        /* Allow access to species class args at runtime */
        for (Field field : bmhSubtype.getDeclaredFields()) {
            if (field.getName().startsWith("arg")) {
                RuntimeReflection.register(field);
                if (!field.getType().isPrimitive()) {
                    field.setAccessible(true);
                }
            }
        }

        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        access.getBigBang().postTask(unused -> {
            Field bmhSpeciesField = ReflectionUtil.lookupField(true, bmhSubtype, "BMH_SPECIES");
            if (bmhSpeciesField != null) {
                access.rescanRoot(bmhSpeciesField);
            }
        });

        if (!access.getBigBang().executorIsStarted()) {
            access.requireAnalysisIteration();
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        assert substitutionProcessor.checkAllTypeNames();
    }
}
