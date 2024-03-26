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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.fieldvaluetransformer.NewEmptyArrayFieldValueTransformer;
import com.oracle.svm.core.invoke.MethodHandleIntrinsic;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
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
    private Field typedCollectors;

    /**
     * A new {@link MethodType} interning table which contains only objects that are already part of
     * the image. We cannot replace it with an empty table like we do for other caches because the
     * method handle code uses reference comparisons on {@link MethodType} objects and assumes that
     * unidentical objects are not equal. This breaks if an object is created at runtime because an
     * equivalent image heap object is not part of the table and subsequently fails a comparison.
     */
    private Object runtimeMethodTypeInternTable;
    private Method referencedKeySetAdd;

    private MethodHandleInvokerRenamingSubstitutionProcessor substitutionProcessor;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        Class<?> memberNameClass = access.findClassByName("java.lang.invoke.MemberName");
        memberNameIsMethod = ReflectionUtil.lookupMethod(memberNameClass, "isMethod");
        memberNameIsConstructor = ReflectionUtil.lookupMethod(memberNameClass, "isConstructor");
        memberNameIsField = ReflectionUtil.lookupMethod(memberNameClass, "isField");
        memberNameGetMethodType = ReflectionUtil.lookupMethod(memberNameClass, "getMethodType");

        Class<?> lambdaFormClass = access.findClassByName("java.lang.invoke.LambdaForm");
        lambdaFormLFIdentity = ReflectionUtil.lookupField(lambdaFormClass, "LF_identity");
        lambdaFormLFZero = ReflectionUtil.lookupField(lambdaFormClass, "LF_zero");
        lambdaFormNFIdentity = ReflectionUtil.lookupField(lambdaFormClass, "NF_identity");
        lambdaFormNFZero = ReflectionUtil.lookupField(lambdaFormClass, "NF_zero");

        Class<?> arrayAccessorClass = access.findClassByName("java.lang.invoke.MethodHandleImpl$ArrayAccessor");
        typedAccessors = ReflectionUtil.lookupField(arrayAccessorClass, "TYPED_ACCESSORS");
        Class<?> methodHandleImplClass = access.findClassByName("java.lang.invoke.MethodHandleImpl$Makers");
        typedCollectors = ReflectionUtil.lookupField(methodHandleImplClass, "TYPED_COLLECTORS");

        if (JavaVersionUtil.JAVA_SPEC >= 22) {
            try {
                Class<?> referencedKeySetClass = access.findClassByName("jdk.internal.util.ReferencedKeySet");
                Method create = ReflectionUtil.lookupMethod(referencedKeySetClass, "create", boolean.class, boolean.class, Supplier.class);
                // The following call must match the static initializer of MethodType#internTable.
                runtimeMethodTypeInternTable = create.invoke(null,
                                /* isSoft */ false, /* useNativeQueue */ true, (Supplier<Object>) () -> new ConcurrentHashMap<>(512));
                referencedKeySetAdd = ReflectionUtil.lookupMethod(referencedKeySetClass, "add", Object.class);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere(e);
            }
        } else {
            Class<?> concurrentWeakInternSetClass = access.findClassByName("java.lang.invoke.MethodType$ConcurrentWeakInternSet");
            runtimeMethodTypeInternTable = ReflectionUtil.newInstance(concurrentWeakInternSetClass);
            referencedKeySetAdd = ReflectionUtil.lookupMethod(concurrentWeakInternSetClass, "add", Object.class);
        }

        var accessImpl = (DuringSetupAccessImpl) access;
        substitutionProcessor = new MethodHandleInvokerRenamingSubstitutionProcessor(accessImpl.getBigBang());
        accessImpl.registerSubstitutionProcessor(substitutionProcessor);

        accessImpl.registerObjectReachableCallback(memberNameClass, (a1, member, reason) -> registerHeapMemberName((Member) member));
        accessImpl.registerObjectReachableCallback(MethodType.class, (a1, methodType, reason) -> registerHeapMethodType(methodType));
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        var access = (BeforeAnalysisAccessImpl) a;

        /* java.lang.invoke functions called through reflection */
        Class<?> mhImplClazz = access.findClassByName("java.lang.invoke.MethodHandleImpl");

        access.registerReachabilityHandler(MethodHandleFeature::registerMHImplFunctionsForReflection,
                        ReflectionUtil.lookupMethod(mhImplClazz, "getFunction", byte.class));

        access.registerReachabilityHandler(MethodHandleFeature::registerMHImplConstantHandlesForReflection,
                        ReflectionUtil.lookupMethod(mhImplClazz, "makeConstantHandle", int.class));

        access.registerReachabilityHandler(MethodHandleFeature::registerMHImplCountingWrapperFunctionsForReflection,
                        access.findClassByName("java.lang.invoke.MethodHandleImpl$CountingWrapper"));

        access.registerReachabilityHandler(MethodHandleFeature::registerInvokersFunctionsForReflection,
                        ReflectionUtil.lookupMethod(access.findClassByName("java.lang.invoke.Invokers"), "getFunction", byte.class));

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

        access.registerSubtypeReachabilityHandler(MethodHandleFeature::scanBoundMethodHandle,
                        access.findClassByName("java.lang.invoke.BoundMethodHandle"));

        AnalysisMetaAccess metaAccess = access.getMetaAccess();
        ImageHeapScanner heapScanner = access.getUniverse().getHeapScanner();

        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(ReflectionUtil.lookupClass(false, "java.lang.invoke.ClassSpecializer"), "cache"),
                        new FieldValueTransformerWithAvailability() {
                            private static final Class<?> speciesDataClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.ClassSpecializer$SpeciesData");

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
                                    if (isSpeciesTypeInstantiated(speciesData)) {
                                        filteredMap.put(key, speciesData);
                                    }
                                });
                                return filteredMap;
                            }

                            private boolean isSpeciesTypeInstantiated(Object speciesData) {
                                Class<?> speciesClass = ReflectionUtil.readField(speciesDataClass, "speciesCode", speciesData);
                                Optional<AnalysisType> analysisType = metaAccess.optionalLookupJavaType(speciesClass);
                                return analysisType.isPresent() && analysisType.get().isInstantiated();
                            }
                        });
        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(ReflectionUtil.lookupClass(false, "java.lang.invoke.DirectMethodHandle"), "ACCESSOR_FORMS"),
                        NewEmptyArrayFieldValueTransformer.INSTANCE);
        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(ReflectionUtil.lookupClass(false, "java.lang.invoke.MethodType"), "internTable"),
                        (receiver, originalValue) -> runtimeMethodTypeInternTable);

        /*
         * SpeciesData.transformHelpers is a lazily initialized cache of MethodHandle objects. We do
         * not want to make a MethodHandle reachable just because the image builder initialized the
         * cache, so we filter out unreachable objects. This also solves the problem when late image
         * heap re-scanning after static analysis would see a method handle that was not yet cached
         * during static analysis, in which case image building would fail because new types would
         * be made reachable after analysis.
         */
        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(ReflectionUtil.lookupClass(false, "java.lang.invoke.ClassSpecializer$SpeciesData"), "transformHelpers"),
                        new FieldValueTransformerWithAvailability() {
                            @Override
                            public FieldValueTransformerWithAvailability.ValueAvailability valueAvailability() {
                                return FieldValueTransformerWithAvailability.ValueAvailability.AfterAnalysis;
                            }

                            @Override
                            @SuppressWarnings("unchecked")
                            public Object transform(Object receiver, Object originalValue) {
                                MethodHandle[] originalArray = (MethodHandle[]) originalValue;
                                MethodHandle[] filteredArray = new MethodHandle[originalArray.length];
                                for (int i = 0; i < originalArray.length; i++) {
                                    MethodHandle handle = originalArray[i];
                                    if (handle != null && heapScanner.isObjectReachable(handle)) {
                                        filteredArray[i] = handle;
                                    }
                                }
                                return filteredArray;
                            }
                        });
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
        RuntimeReflection.register(ReflectionUtil.lookupMethod(invokersClazz, "directVarHandleTarget", access.findClassByName("java.lang.invoke.VarHandle")));
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
        return srcType + "To" + destType.substring(0, 1).toUpperCase(Locale.ROOT) + destType.substring(1);
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

    private static void registerVarHandleMethodsForReflection(FeatureAccess access, Class<?> subtype) {
        if (subtype.getPackage().getName().equals("java.lang.invoke") && subtype != access.findClassByName("java.lang.invoke.VarHandle")) {
            RuntimeReflection.register(subtype.getDeclaredMethods());
        }
    }

    public void registerHeapMethodType(MethodType methodType) {
        try {
            referencedKeySetAdd.invoke(runtimeMethodTypeInternTable, methodType);
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
        access.rescanRoot(typedCollectors);
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
        assert substitutionProcessor == null || substitutionProcessor.checkAllTypeNames();
    }
}
