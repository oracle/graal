/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jni;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.configure.ReflectionConfigurationParser;
import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.jni.CallVariant;
import com.oracle.svm.core.jni.JNIJavaCallTrampolineHolder;
import com.oracle.svm.core.jni.access.JNIAccessibleClass;
import com.oracle.svm.core.jni.access.JNIAccessibleField;
import com.oracle.svm.core.jni.access.JNIAccessibleMethod;
import com.oracle.svm.core.jni.access.JNIAccessibleMethodDescriptor;
import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.core.jni.access.JNIReflectionDictionary;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl.AfterRegistrationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.KnownOffsetsFeature;
import com.oracle.svm.hosted.meta.MaterializedConstantFields;
import com.oracle.svm.hosted.reflect.NativeImageConditionResolver;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.hosted.reflect.proxy.DynamicProxyFeature;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.word.Word;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Prepares classes, methods and fields before and during the analysis so that they are accessible
 * via JNI at image runtime.
 */
public class JNIAccessFeature implements Feature {

    @Fold
    public static JNIAccessFeature singleton() {
        return ImageSingletons.lookup(JNIAccessFeature.class);
    }

    /** A group of wrappers for the same target signature, but different JNI call variants. */
    static final class JNIJavaCallVariantWrapperGroup {
        final JNIJavaCallVariantWrapperMethod varargs;
        final JNIJavaCallVariantWrapperMethod array;
        final JNIJavaCallVariantWrapperMethod valist;

        JNIJavaCallVariantWrapperGroup(JNIJavaCallVariantWrapperMethod varargs, JNIJavaCallVariantWrapperMethod array, JNIJavaCallVariantWrapperMethod valist) {
            this.varargs = varargs;
            this.array = array;
            this.valist = valist;
        }
    }

    static final class JNICallableJavaMethod {
        final JNIAccessibleMethodDescriptor descriptor;
        final JNIAccessibleMethod jniMethod;
        final ResolvedJavaMethod targetMethod;
        final JNIJavaCallWrapperMethod callWrapper;
        final ResolvedJavaMethod newObjectMethod;
        final JNIJavaCallVariantWrapperGroup variantWrappers;
        final JNIJavaCallVariantWrapperGroup nonvirtualVariantWrappers;

        JNICallableJavaMethod(JNIAccessibleMethodDescriptor descriptor, JNIAccessibleMethod jniMethod, ResolvedJavaMethod targetMethod, JNIJavaCallWrapperMethod callWrapper,
                        ResolvedJavaMethod newObjectMethod, JNIJavaCallVariantWrapperGroup variantWrappers, JNIJavaCallVariantWrapperGroup nonvirtualVariantWrappers) {
            this.descriptor = descriptor;
            assert (targetMethod.isStatic() || targetMethod.isAbstract()) == (nonvirtualVariantWrappers == null);
            this.jniMethod = jniMethod;
            this.targetMethod = targetMethod;
            this.callWrapper = callWrapper;
            this.newObjectMethod = newObjectMethod;
            this.variantWrappers = variantWrappers;
            this.nonvirtualVariantWrappers = nonvirtualVariantWrappers;
        }
    }

    private JNIRuntimeAccessibilitySupportImpl runtimeSupport = new JNIRuntimeAccessibilitySupportImpl();

    private final Map<String, JNICallTrampolineMethod> trampolineMethods = new ConcurrentHashMap<>();
    private final Map<ResolvedSignature<ResolvedJavaType>, JNIJavaCallWrapperMethod> javaCallWrapperMethods = new ConcurrentHashMap<>();
    private final Map<ResolvedSignature<ResolvedJavaType>, JNIJavaCallVariantWrapperGroup> callVariantWrappers = new ConcurrentHashMap<>();
    private final Map<ResolvedSignature<ResolvedJavaType>, JNIJavaCallVariantWrapperGroup> nonvirtualCallVariantWrappers = new ConcurrentHashMap<>();
    private final List<JNICallableJavaMethod> calledJavaMethods = new ArrayList<>();

    private int loadedConfigurations;

    private final Set<Class<?>> newClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> newNegativeClassLookups = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Executable> newMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Class<?>, Set<Pair<String, Class<?>[]>>> newNegativeMethodLookups = new ConcurrentHashMap<>();
    private final Map<Field, Boolean> newFields = new ConcurrentHashMap<>();
    private final Map<Class<?>, Set<String>> newNegativeFieldLookups = new ConcurrentHashMap<>();
    private final Map<JNINativeLinkage, JNINativeLinkage> newLinkages = new ConcurrentHashMap<>();

    private final Map<JNINativeLinkage, JNINativeLinkage> nativeLinkages = new ConcurrentHashMap<>();

    public static class Options {
        @Option(help = "Print JNI methods added to generated image")//
        public static final HostedOptionKey<Boolean> PrintJNIMethods = new HostedOptionKey<>(false);
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        // Ensure that KnownOffsets is fully initialized before we access it
        return List.of(KnownOffsetsFeature.class, DynamicProxyFeature.class, ReflectionFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess arg) {
        AfterRegistrationAccessImpl access = (AfterRegistrationAccessImpl) arg;

        JNIReflectionDictionary.create();

        ImageSingletons.add(RuntimeJNIAccessSupport.class, runtimeSupport);

        ConfigurationConditionResolver<ConfigurationCondition> conditionResolver = new NativeImageConditionResolver(access.getImageClassLoader(),
                        ClassInitializationSupport.singleton());
        ReflectionConfigurationParser<ConfigurationCondition, Class<?>> parser = ConfigurationParserUtils.create(ConfigurationFile.JNI, true, conditionResolver, runtimeSupport, null, null, null,
                        access.getImageClassLoader());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurationsFromCombinedFile(parser, access.getImageClassLoader(), "JNI");
        ReflectionConfigurationParser<ConfigurationCondition, Class<?>> legacyParser = ConfigurationParserUtils.create(ConfigurationFile.JNI, false, conditionResolver, runtimeSupport, null, null,
                        null,
                        access.getImageClassLoader());
        loadedConfigurations += ConfigurationParserUtils.parseAndRegisterConfigurations(legacyParser, access.getImageClassLoader(), "JNI",
                        ConfigurationFiles.Options.JNIConfigurationFiles, ConfigurationFiles.Options.JNIConfigurationResources, ConfigurationFile.JNI.getFileName());
    }

    private final class JNIRuntimeAccessibilitySupportImpl extends ConditionalConfigurationRegistry
                    implements RuntimeJNIAccessSupport, LayeredImageSingleton {

        @Override
        public void register(ConfigurationCondition condition, boolean unsafeAllocated, Class<?> clazz) {
            assert !unsafeAllocated : "unsafeAllocated can be only set via Unsafe.allocateInstance, not via JNI.";
            Objects.requireNonNull(clazz, () -> nullErrorMessage("class", "JNI access"));
            abortIfSealed();
            registerConditionalConfiguration(condition, (cnd) -> newClasses.add(clazz));
        }

        @Override
        public void register(ConfigurationCondition condition, boolean queriedOnly, Executable... executables) {
            requireNonNull(executables, "executable", "JNI access");
            abortIfSealed();
            if (!queriedOnly) {
                registerConditionalConfiguration(condition, (cnd) -> newMethods.addAll(Arrays.asList(executables)));
            }
        }

        @Override
        public void register(ConfigurationCondition condition, boolean finalIsWritable, Field... fields) {
            requireNonNull(fields, "field", "JNI access");
            abortIfSealed();
            registerConditionalConfiguration(condition, (cnd) -> registerFields(finalIsWritable, fields));
        }

        private void registerFields(boolean finalIsWritable, Field[] fields) {
            for (Field field : fields) {
                boolean writable = finalIsWritable || !Modifier.isFinal(field.getModifiers());
                newFields.put(field, writable);
            }
        }

        @Override
        public void registerClassLookup(ConfigurationCondition condition, String reflectionName) {
            try {
                register(condition, false, Class.forName(reflectionName));
            } catch (ClassNotFoundException e) {
                String jniName = ClassNameSupport.reflectionNameToJNIName(reflectionName);
                newNegativeClassLookups.add(jniName);
            }
        }

        @Override
        public void registerFieldLookup(ConfigurationCondition condition, Class<?> declaringClass, String fieldName) {
            try {
                register(condition, false, declaringClass.getDeclaredField(fieldName));
            } catch (NoSuchFieldException e) {
                newNegativeFieldLookups.computeIfAbsent(declaringClass, (clazz) -> new HashSet<>()).add(fieldName);
            }
        }

        @Override
        public void registerMethodLookup(ConfigurationCondition condition, Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
            try {
                register(condition, false, declaringClass.getDeclaredMethod(methodName, parameterTypes));
            } catch (NoSuchMethodException e) {
                newNegativeMethodLookups.computeIfAbsent(declaringClass, (clazz) -> new HashSet<>()).add(Pair.create(methodName, parameterTypes));
            }
        }

        @Override
        public void registerConstructorLookup(ConfigurationCondition condition, Class<?> declaringClass, Class<?>... parameterTypes) {
            try {
                register(condition, false, declaringClass.getDeclaredConstructor(parameterTypes));
            } catch (NoSuchMethodException e) {
                newNegativeMethodLookups.computeIfAbsent(declaringClass, (clazz) -> new HashSet<>()).add(Pair.create("<init>", parameterTypes));
            }
        }

        @Override
        public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
            return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
        }

        @Override
        public PersistFlags preparePersist(ImageSingletonWriter writer) {
            return PersistFlags.NOTHING;
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess arg) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) arg;

        for (CallVariant variant : CallVariant.values()) {
            registerJavaCallTrampoline(access, variant, false);
            registerJavaCallTrampoline(access, variant, true);
        }

        singleton().runtimeSupport.setAnalysisAccess(access);
    }

    private static void registerJavaCallTrampoline(BeforeAnalysisAccessImpl access, CallVariant variant, boolean nonVirtual) {
        MetaAccessProvider originalMetaAccess = access.getMetaAccess().getWrapped();
        ResolvedJavaField field = JNIAccessibleMethod.getCallVariantWrapperField(originalMetaAccess, variant, nonVirtual);
        access.registerAsAccessed(access.getUniverse().lookup(field), "it is registered for JNI accessed");
        String name = JNIJavaCallTrampolineHolder.getTrampolineName(variant, nonVirtual);
        Method method = ReflectionUtil.lookupMethod(JNIJavaCallTrampolineHolder.class, name);
        access.registerAsRoot(method, true, "Registered in " + JNIAccessFeature.class);
    }

    public JNICallTrampolineMethod getCallTrampolineMethod(CallVariant variant, boolean nonVirtual) {
        String name = JNIJavaCallTrampolineHolder.getTrampolineName(variant, nonVirtual);
        return getCallTrampolineMethod(name);
    }

    public JNICallTrampolineMethod getCallTrampolineMethod(String trampolineName) {
        JNICallTrampolineMethod trampoline = trampolineMethods.get(trampolineName);
        assert trampoline != null;
        return trampoline;
    }

    public JNICallTrampolineMethod getOrCreateCallTrampolineMethod(MetaAccessProvider metaAccess, String trampolineName) {
        return trampolineMethods.computeIfAbsent(trampolineName, name -> {
            Method reflectionMethod = ReflectionUtil.lookupMethod(JNIJavaCallTrampolineHolder.class, name);
            boolean nonVirtual = JNIJavaCallTrampolineHolder.isNonVirtual(name);
            ResolvedJavaField field = JNIAccessibleMethod.getCallVariantWrapperField(metaAccess, JNIJavaCallTrampolineHolder.getVariant(name), nonVirtual);
            ResolvedJavaMethod method = metaAccess.lookupJavaMethod(reflectionMethod);
            return new JNICallTrampolineMethod(method, field, nonVirtual);
        });
    }

    public JNINativeLinkage makeLinkage(String declaringClass, String name, String descriptor) {
        UserError.guarantee(!runtimeSupport.isSealed(),
                        "All linkages for JNI calls must be created before the analysis has completed.%nOffending class: %s name: %s descriptor: %s",
                        declaringClass, name, descriptor);

        assert declaringClass.startsWith("L") && declaringClass.endsWith(";") : declaringClass;
        JNINativeLinkage key = new JNINativeLinkage(declaringClass, name, descriptor);

        if (JNIAccessFeature.Options.PrintJNIMethods.getValue()) {
            System.out.println("Creating a new JNINativeLinkage: " + key);
        }

        return nativeLinkages.computeIfAbsent(key, linkage -> {
            newLinkages.put(linkage, linkage);
            return linkage;
        });
    }

    private boolean wereElementsAdded() {
        return !(newClasses.isEmpty() && newMethods.isEmpty() && newFields.isEmpty() && newLinkages.isEmpty() &&
                        newNegativeClassLookups.isEmpty() && newNegativeFieldLookups.isEmpty() && newNegativeMethodLookups.isEmpty());
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        if (!wereElementsAdded()) {
            return;
        }

        for (Class<?> clazz : newClasses) {
            addClass(clazz, access);
        }
        newClasses.clear();

        for (String className : newNegativeClassLookups) {
            addNegativeClassLookup(className);
        }
        newNegativeClassLookups.clear();

        for (Executable method : newMethods) {
            addMethod(method, access);
        }
        newMethods.clear();

        newNegativeMethodLookups.forEach((clazz, signatures) -> {
            for (Pair<String, Class<?>[]> signature : signatures) {
                addNegativeMethodLookup(clazz, signature.getLeft(), signature.getRight(), access);
            }
        });
        newNegativeMethodLookups.clear();

        newFields.forEach((field, writable) -> {
            addField(field, writable, access);
        });
        newFields.clear();

        newNegativeFieldLookups.forEach((clazz, fieldNames) -> {
            for (String fieldName : fieldNames) {
                addNegativeFieldLookup(clazz, fieldName, access);
            }
        });
        newNegativeFieldLookups.clear();

        JNIReflectionDictionary.currentLayer().addLinkages(newLinkages);
        newLinkages.clear();

        access.requireAnalysisIteration();
    }

    private static JNIAccessibleClass addClass(Class<?> classObj, DuringAnalysisAccessImpl access) {
        if (classObj.isPrimitive()) {
            return null; // primitives cannot be looked up by name and have no methods or fields
        }
        if (SubstitutionReflectivityFilter.shouldExclude(classObj, access.getMetaAccess(), access.getUniverse())) {
            return null;
        }
        return JNIReflectionDictionary.currentLayer().addClassIfAbsent(classObj, c -> {
            AnalysisType analysisClass = access.getMetaAccess().lookupJavaType(classObj);
            analysisClass.registerAsReachable("is accessed via JNI");
            return new JNIAccessibleClass(classObj);
        });
    }

    private static void addNegativeClassLookup(String className) {
        JNIReflectionDictionary.currentLayer().addNegativeClassLookupIfAbsent(className);
    }

    public void addMethod(Executable method, DuringAnalysisAccessImpl access) {
        if (SubstitutionReflectivityFilter.shouldExclude(method, access.getMetaAccess(), access.getUniverse())) {
            return;
        }
        JNIAccessibleClass jniClass = addClass(method.getDeclaringClass(), access);
        JNIAccessibleMethodDescriptor descriptor = JNIAccessibleMethodDescriptor.of(method);
        jniClass.addMethodIfAbsent(descriptor, d -> {
            AnalysisUniverse universe = access.getUniverse();
            MetaAccessProvider originalMetaAccess = universe.getOriginalMetaAccess();
            ResolvedJavaMethod targetMethod = originalMetaAccess.lookupJavaMethod(method);

            JNIJavaCallWrapperMethod.Factory factory = ImageSingletons.lookup(JNIJavaCallWrapperMethod.Factory.class);
            AnalysisMethod aTargetMethod = universe.lookup(targetMethod);
            if (!targetMethod.isConstructor() || factory.canInvokeConstructorOnObject(targetMethod, originalMetaAccess)) {
                access.registerAsRoot(aTargetMethod, targetMethod.isConstructor(), "JNI method, registered in " + JNIAccessFeature.class);
            } // else: function pointers will be an error stub

            ResolvedJavaMethod newObjectMethod = null;
            if (targetMethod.isConstructor() && !targetMethod.getDeclaringClass().isAbstract()) {
                var aFactoryMethod = FactoryMethodSupport.singleton().lookup(access.getMetaAccess(), aTargetMethod, false);
                access.registerAsRoot(aFactoryMethod, true, "JNI constructor, registered in " + JNIAccessFeature.class);
                /*
                 * Constructors can be invoked on objects allocated separately via AllocObject,
                 * which we implement via Unsafe.
                 */
                access.registerAsUnsafeAllocated(aTargetMethod.getDeclaringClass());
                newObjectMethod = aFactoryMethod.getWrapped();
            }

            var compatibleSignature = JNIJavaCallWrapperMethod.getGeneralizedSignatureForTarget(targetMethod, originalMetaAccess);
            JNIJavaCallWrapperMethod callWrapperMethod = javaCallWrapperMethods.computeIfAbsent(compatibleSignature,
                            signature -> factory.create(signature, originalMetaAccess, access.getBigBang().getWordTypes()));
            access.registerAsRoot(universe.lookup(callWrapperMethod), true, "JNI call wrapper, registered in " + JNIAccessFeature.class);

            JNIJavaCallVariantWrapperGroup variantWrappers = createJavaCallVariantWrappers(access, callWrapperMethod.getSignature(), false, method);
            JNIJavaCallVariantWrapperGroup nonvirtualVariantWrappers = null;
            if (!Modifier.isStatic(method.getModifiers()) && !Modifier.isAbstract(method.getModifiers())) {
                nonvirtualVariantWrappers = createJavaCallVariantWrappers(access, callWrapperMethod.getSignature(), true, method);
            }
            JNIAccessibleMethod jniMethod = new JNIAccessibleMethod(jniClass, method.getModifiers());
            calledJavaMethods.add(new JNICallableJavaMethod(descriptor, jniMethod, targetMethod, callWrapperMethod, newObjectMethod, variantWrappers, nonvirtualVariantWrappers));
            return jniMethod;
        });
    }

    private static void addNegativeMethodLookup(Class<?> declaringClass, String methodName, Class<?>[] parameterTypes, DuringAnalysisAccessImpl access) {
        JNIAccessibleClass jniClass = addClass(declaringClass, access);
        JNIAccessibleMethodDescriptor descriptor = JNIAccessibleMethodDescriptor.of(methodName, parameterTypes);
        jniClass.addMethodIfAbsent(descriptor, d -> JNIAccessibleMethod.negativeMethodQuery(jniClass));
    }

    private JNIJavaCallVariantWrapperGroup createJavaCallVariantWrappers(DuringAnalysisAccessImpl access, ResolvedSignature<ResolvedJavaType> wrapperSignature, boolean nonVirtual, Executable method) {
        var map = nonVirtual ? nonvirtualCallVariantWrappers : callVariantWrappers;
        return map.computeIfAbsent(wrapperSignature, signature -> {
            MetaAccessProvider originalMetaAccess = access.getUniverse().getOriginalMetaAccess();
            WordTypes wordTypes = access.getBigBang().getWordTypes();
            var varargs = new JNIJavaCallVariantWrapperMethod(method, signature, CallVariant.VARARGS, nonVirtual, originalMetaAccess, wordTypes);
            var array = new JNIJavaCallVariantWrapperMethod(method, signature, CallVariant.ARRAY, nonVirtual, originalMetaAccess, wordTypes);
            var valist = new JNIJavaCallVariantWrapperMethod(method, signature, CallVariant.VA_LIST, nonVirtual, originalMetaAccess, wordTypes);
            Stream<JNIJavaCallVariantWrapperMethod> wrappers = Stream.of(varargs, array, valist);
            CEntryPointData unpublished = CEntryPointData.createCustomUnpublished();
            wrappers.forEach(wrapper -> {
                AnalysisMethod analysisWrapper = access.getUniverse().lookup(wrapper);
                access.getBigBang().addRootMethod(analysisWrapper, true, "Registerd in " + JNIAccessFeature.class);
                /* ensures C calling convention */
                analysisWrapper.registerAsNativeEntryPoint(unpublished);
            });
            return new JNIJavaCallVariantWrapperGroup(varargs, array, valist);
        });
    }

    private static void addField(Field reflField, boolean writable, DuringAnalysisAccessImpl access) {
        if (SubstitutionReflectivityFilter.shouldExclude(reflField, access.getMetaAccess(), access.getUniverse())) {
            return;
        }
        JNIAccessibleClass jniClass = addClass(reflField.getDeclaringClass(), access);
        AnalysisField field = access.getMetaAccess().lookupJavaField(reflField);
        jniClass.addFieldIfAbsent(field.getName(), name -> new JNIAccessibleField(jniClass, field.getJavaKind(), field.getModifiers()));
        field.registerAsRead("it is registered for as JNI accessed");
        if (writable) {
            field.registerAsWritten("it is registered as JNI writable");
            AnalysisType fieldType = field.getType();
            if (fieldType.isArray() && !access.isReachable(fieldType)) {
                // For convenience, make the array type reachable if its elemental type becomes
                // such, allowing the array creation via JNI without an explicit reflection config.
                access.registerReachabilityHandler(a -> fieldType.registerAsInstantiated("Is accessed via JNI."),
                                (fieldType.getElementalType()).getJavaClass());
            }
        } else if (field.isStatic() && field.isFinal()) {
            MaterializedConstantFields.singleton().register(field);
        }
        StaticFieldsSupport.StaticFieldValidator.checkFieldOffsetAllowed(field);

        BigBang bb = access.getBigBang();
        bb.registerAsJNIAccessed(field, writable);
    }

    private static void addNegativeFieldLookup(Class<?> declaringClass, String fieldName, DuringAnalysisAccessImpl access) {
        JNIAccessibleClass jniClass = addClass(declaringClass, access);
        jniClass.addFieldIfAbsent(fieldName, d -> JNIAccessibleField.negativeFieldQuery(jniClass));
    }

    @Override
    @SuppressWarnings("unused")
    public void afterAnalysis(AfterAnalysisAccess access) {
        runtimeSupport.sealed();
        if (wereElementsAdded()) {
            runtimeSupport.abortIfSealed();
        }

        int numClasses = 0;
        int numFields = 0;
        int numMethods = 0;
        for (JNIAccessibleClass clazz : JNIReflectionDictionary.currentLayer().getClasses()) {
            numClasses++;
            var fieldsCursor = clazz.getFields();
            while (fieldsCursor.advance()) {
                if (!fieldsCursor.getValue().isNegativeHosted()) {
                    numFields++;
                }
            }
            var methodsCursor = clazz.getMethods();
            while (methodsCursor.advance()) {
                if (!methodsCursor.getValue().isNegative()) {
                    numMethods++;
                }
            }
        }
        ProgressReporter.singleton().setJNIInfo(numClasses, numFields, numMethods);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        if (ImageSingletons.contains(FallbackFeature.class)) {
            FallbackFeature.FallbackImageRequest jniFallback = ImageSingletons.lookup(FallbackFeature.class).jniFallback;
            if (jniFallback != null && loadedConfigurations == 0) {
                throw jniFallback;
            }
        }

        CompilationAccessImpl access = (CompilationAccessImpl) a;
        DynamicHubLayout dynamicHubLayout = DynamicHubLayout.singleton();
        for (JNIAccessibleClass clazz : JNIReflectionDictionary.currentLayer().getClasses()) {
            UnmodifiableMapCursor<CharSequence, JNIAccessibleField> cursor = clazz.getFields();
            while (cursor.advance()) {
                String name = (String) cursor.getKey();
                finishFieldBeforeCompilation(name, cursor.getValue(), access, dynamicHubLayout);
            }
        }
        for (JNICallableJavaMethod method : calledJavaMethods) {
            finishMethodBeforeCompilation(method, access);
            access.registerAsImmutable(method.jniMethod); // contains relocatable pointers
        }
    }

    private static boolean isStaticallyBound(HostedMethod hTarget) {
        if (hTarget.isConstructor()) {
            /*
             * Constructors are always statically bound.
             */
            return true;
        } else if (hTarget.canBeStaticallyBound()) {
            return true;
        }

        return false;
    }

    private static void finishMethodBeforeCompilation(JNICallableJavaMethod method, CompilationAccessImpl access) {
        HostedUniverse hUniverse = access.getUniverse();
        AnalysisUniverse aUniverse = access.getUniverse().getBigBang().getUniverse();
        HostedMethod hTarget = hUniverse.lookup(aUniverse.lookup(method.targetMethod));
        int vtableIndex;
        int interfaceTypeID;
        if (isStaticallyBound(hTarget)) {
            vtableIndex = JNIAccessibleMethod.VTABLE_INDEX_STATICALLY_BOUND_METHOD;
            interfaceTypeID = JNIAccessibleMethod.INTERFACE_TYPEID_UNNEEDED;
        } else {
            vtableIndex = hTarget.getVTableIndex();
            if (SubstrateOptions.useClosedTypeWorldHubLayout()) {
                interfaceTypeID = JNIAccessibleMethod.INTERFACE_TYPEID_UNNEEDED;
            } else {
                HostedType indirectCallTargetClass = hTarget.getIndirectCallTarget().getDeclaringClass();
                interfaceTypeID = indirectCallTargetClass.isInterface() ? indirectCallTargetClass.getTypeID() : JNIAccessibleMethod.INTERFACE_TYPEID_CLASS_TABLE;
            }
        }
        CodePointer nonvirtualTarget = new MethodPointer(hTarget);
        PointerBase newObjectTarget = null;
        if (method.newObjectMethod != null) {
            newObjectTarget = new MethodPointer(hUniverse.lookup(aUniverse.lookup(method.newObjectMethod)));
        } else if (method.targetMethod.isConstructor()) {
            assert method.targetMethod.getDeclaringClass().isAbstract();
            newObjectTarget = Word.signed(JNIAccessibleMethod.NEW_OBJECT_TARGET_INVALID_FOR_ABSTRACT_TYPE);
        }
        CodePointer callWrapper = new MethodPointer(hUniverse.lookup(aUniverse.lookup(method.callWrapper)));
        CodePointer varargs = new MethodPointer(hUniverse.lookup(aUniverse.lookup(method.variantWrappers.varargs)));
        CodePointer array = new MethodPointer(hUniverse.lookup(aUniverse.lookup(method.variantWrappers.array)));
        CodePointer valist = new MethodPointer(hUniverse.lookup(aUniverse.lookup(method.variantWrappers.valist)));
        CodePointer varargsNonvirtual = null;
        CodePointer arrayNonvirtual = null;
        CodePointer valistNonvirtual = null;
        if (method.nonvirtualVariantWrappers != null) {
            varargsNonvirtual = new MethodPointer(hUniverse.lookup(aUniverse.lookup(method.nonvirtualVariantWrappers.varargs)));
            arrayNonvirtual = new MethodPointer(hUniverse.lookup(aUniverse.lookup(method.nonvirtualVariantWrappers.array)));
            valistNonvirtual = new MethodPointer(hUniverse.lookup(aUniverse.lookup(method.nonvirtualVariantWrappers.valist)));
        }
        EconomicSet<Class<?>> hidingSubclasses = findHidingSubclasses(hTarget.getDeclaringClass(), sub -> anyMethodMatchesIgnoreReturnType(sub, method.descriptor));
        method.jniMethod.finishBeforeCompilation(hidingSubclasses, vtableIndex, interfaceTypeID, nonvirtualTarget, newObjectTarget, callWrapper,
                        varargs, array, valist, varargsNonvirtual, arrayNonvirtual, valistNonvirtual);
    }

    private static boolean anyMethodMatchesIgnoreReturnType(ResolvedJavaType sub, JNIAccessibleMethodDescriptor descriptor) {
        try {
            for (ResolvedJavaMethod method : sub.getDeclaredMethods(false)) {
                if (descriptor.matchesIgnoreReturnType(method)) {
                    return true;
                }
            }
            return false;

        } catch (LinkageError ex) {
            /*
             * Ignore any linkage errors due to looking up the declared methods. Unfortunately, it
             * is not possible to look up methods (even a single declared method with a known
             * signature using reflection) if any other method of the class references a missing
             * type. In this case, we have to assume that the subclass does not have a matching
             * method.
             */
            return false;
        }
    }

    /**
     * Determines which subclasses of a member's declaring class contain a declaration that cause
     * this member to be hidden in that subclass and all of its subclasses.
     */
    private static EconomicSet<Class<?>> findHidingSubclasses(HostedType type, Predicate<ResolvedJavaType> predicate) {
        return findHidingSubclasses0(type, predicate, null, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static EconomicSet<Class<?>> findHidingSubclasses0(HostedType type, Predicate<ResolvedJavaType> predicate, EconomicSet<Class<?>> existing, Set<HostedType> visitedTypes) {
        EconomicSet<Class<?>> map = existing;
        /*
         * HostedType.getSubTypes() only gives us subtypes that are part of our analyzed closed
         * world, but this is fine because JNI lookups can only be done on those.
         */
        for (HostedType subType : type.getSubTypes()) {
            if (visitedTypes.contains(subType)) {
                continue;
            }
            visitedTypes.add(subType);
            if (subType.isInstantiated() || subType.getWrapped().isReachable()) {
                /*
                 * We must use the unwrapped type to query its members in the predicate: HostedType
                 * and AnalysisType provide only members that are in our closed world, but members
                 * which are not part of it can still legitimately hide our member that is, and in
                 * that case, we must not return our member in a JNI lookup. Note that we have to
                 * use JVMCI and not reflection here to avoid errors due to unresolved types.
                 */
                ResolvedJavaType originalType = subType.getWrapped().getWrapped();
                assert !(originalType instanceof WrappedJavaType) : "need fully unwrapped type for member lookups";
                if (predicate.test(originalType)) {
                    if (map == null) {
                        map = EconomicSet.create(Equivalence.IDENTITY);
                    }
                    map.add(subType.getJavaClass());
                    // no need to explore further subclasses
                } else {
                    map = findHidingSubclasses0(subType, predicate, map, visitedTypes);
                }
            } else {
                assert findHidingSubclasses0(subType, predicate, null, visitedTypes) == null : "Class hiding a member exists in the image, but its superclass does not";
            }
        }
        return map;
    }

    private static void finishFieldBeforeCompilation(String name, JNIAccessibleField field, CompilationAccessImpl access, DynamicHubLayout dynamicHubLayout) {
        try {
            int offset = -1;
            int layerNumber = MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER;
            EconomicSet<Class<?>> hidingSubclasses = null;
            if (!field.isNegativeHosted()) {
                Class<?> declaringClass = field.getDeclaringClass().getClassObject();
                Field reflField = declaringClass.getDeclaredField(name);
                HostedField hField = access.getMetaAccess().lookupJavaField(reflField);
                if (dynamicHubLayout.isInlinedField(hField)) {
                    throw VMError.shouldNotReachHere("DynamicHub inlined fields are not accessible %s", hField);
                } else if (HybridLayout.isHybridField(hField)) {
                    assert !hField.hasLocation();
                    HybridLayout hybridLayout = new HybridLayout((HostedInstanceClass) hField.getDeclaringClass(),
                                    ImageSingletons.lookup(ObjectLayout.class), access.getMetaAccess());
                    assert hField.equals(hybridLayout.getArrayField()) : "JNI access to hybrid objects is implemented only for the array field";
                    offset = hybridLayout.getArrayBaseOffset();
                } else {
                    assert hField.hasLocation() : hField;
                    offset = hField.getLocation();
                    if (hField.isStatic()) {
                        layerNumber = hField.getInstalledLayerNum();
                    }
                }
                hidingSubclasses = findHidingSubclasses(hField.getDeclaringClass(), sub -> anyFieldMatches(sub, name));
            }

            field.finishBeforeCompilation(offset, layerNumber, hidingSubclasses);

        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean anyFieldMatches(ResolvedJavaType sub, String name) {
        try {
            return Stream.concat(Stream.of(sub.getInstanceFields(false)), Stream.of(sub.getStaticFields()))
                            .anyMatch(f -> f.getName().equals(name));

        } catch (LinkageError ex) {
            /*
             * Ignore any linkage errors due to looking up the field. If any field references a
             * missing type, we have to assume that there is no matching field.
             */
            return false;
        }
    }
}
