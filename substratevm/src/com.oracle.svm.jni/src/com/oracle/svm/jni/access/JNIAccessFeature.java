/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.access;

// Checkstyle: allow reflection

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ReflectionRegistry;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ReflectionConfigurationParser;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl.AfterRegistrationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.meta.MaterializedConstantFields;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.jni.JNIJavaCallWrappers;
import com.oracle.svm.jni.hosted.JNICallTrampolineMethod;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod.CallVariant;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Prepares classes, methods and fields before and during the analysis so that they are accessible
 * via JNI at image runtime.
 */
public class JNIAccessFeature implements Feature {

    @Fold
    public static JNIAccessFeature singleton() {
        return ImageSingletons.lookup(JNIAccessFeature.class);
    }

    private boolean sealed = false;
    private NativeLibraries nativeLibraries;
    private JNICallTrampolineMethod varargsCallTrampolineMethod;
    private JNICallTrampolineMethod arrayCallTrampolineMethod;
    private JNICallTrampolineMethod valistCallTrampolineMethod;
    private JNICallTrampolineMethod varargsNonvirtualCallTrampolineMethod;
    private JNICallTrampolineMethod arrayNonvirtualCallTrampolineMethod;
    private JNICallTrampolineMethod valistNonvirtualCallTrampolineMethod;

    private int loadedConfigurations;

    private final Set<Class<?>> newClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Executable> newMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Field, Boolean> newFields = new ConcurrentHashMap<>();
    private final Map<JNINativeLinkage, JNINativeLinkage> newLinkages = new ConcurrentHashMap<>();

    private final Map<JNINativeLinkage, JNINativeLinkage> nativeLinkages = new ConcurrentHashMap<>();

    private boolean haveJavaRuntimeReflectionSupport;

    public static class Options {
        @Option(help = "Print JNI methods added to generated image")//
        public static final HostedOptionKey<Boolean> PrintJNIMethods = new HostedOptionKey<>(false);
    }

    private void abortIfSealed() {
        UserError.guarantee(!sealed, "Classes, methods and fields must be registered for JNI access before the analysis has completed.");
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess arg) {
        AfterRegistrationAccessImpl access = (AfterRegistrationAccessImpl) arg;

        JNIReflectionDictionary.initialize();

        JNIRuntimeAccessibilitySupportImpl registry = new JNIRuntimeAccessibilitySupportImpl();
        ImageSingletons.add(JNIRuntimeAccess.JNIRuntimeAccessibilitySupport.class, registry);

        ReflectionConfigurationParser<Class<?>> parser = ConfigurationParserUtils.create(registry, access.getImageClassLoader());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, access.getImageClassLoader(), "JNI",
                        ConfigurationFiles.Options.JNIConfigurationFiles, ConfigurationFiles.Options.JNIConfigurationResources, ConfigurationFiles.JNI_NAME);
    }

    private class JNIRuntimeAccessibilitySupportImpl implements JNIRuntimeAccess.JNIRuntimeAccessibilitySupport, ReflectionRegistry {
        @Override
        public void register(Class<?>... classes) {
            abortIfSealed();
            newClasses.addAll(Arrays.asList(classes));
        }

        @Override
        public void register(Executable... methods) {
            abortIfSealed();
            newMethods.addAll(Arrays.asList(methods));
        }

        @Override
        public void register(boolean finalIsWritable, boolean allowUnsafeAccess, Field... fields) {
            UserError.guarantee(!allowUnsafeAccess, "Unsafe access cannot be controlled through JNI configuration.");
            abortIfSealed();
            for (Field field : fields) {
                boolean writable = finalIsWritable || !Modifier.isFinal(field.getModifiers());
                newFields.put(field, writable);
            }
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess arg) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) arg;
        this.nativeLibraries = access.getNativeLibraries();
        this.haveJavaRuntimeReflectionSupport = ImageSingletons.contains(RuntimeReflectionSupport.class);

        varargsCallTrampolineMethod = createJavaCallTrampoline(access, CallVariant.VARARGS, false);
        arrayCallTrampolineMethod = createJavaCallTrampoline(access, CallVariant.ARRAY, false);
        valistCallTrampolineMethod = createJavaCallTrampoline(access, CallVariant.VA_LIST, false);
        varargsNonvirtualCallTrampolineMethod = createJavaCallTrampoline(access, CallVariant.VARARGS, true);
        arrayNonvirtualCallTrampolineMethod = createJavaCallTrampoline(access, CallVariant.ARRAY, true);
        valistNonvirtualCallTrampolineMethod = createJavaCallTrampoline(access, CallVariant.VA_LIST, true);
    }

    private static JNICallTrampolineMethod createJavaCallTrampoline(BeforeAnalysisAccessImpl access, CallVariant variant, boolean nonVirtual) {
        MetaAccessProvider wrappedMetaAccess = access.getMetaAccess().getWrapped();
        ResolvedJavaField field = JNIAccessibleMethod.getCallWrapperField(wrappedMetaAccess, variant, nonVirtual);
        access.registerAsAccessed(access.getUniverse().lookup(field));
        ResolvedJavaMethod method = JNIJavaCallWrappers.lookupJavaCallTrampoline(wrappedMetaAccess, variant, nonVirtual);
        JNICallTrampolineMethod trampoline = new JNICallTrampolineMethod(method, field, nonVirtual);
        access.registerAsCompiled(access.getUniverse().lookup(trampoline));
        return trampoline;
    }

    public JNICallTrampolineMethod getCallTrampolineMethod(CallVariant variant, boolean nonVirtual) {
        JNICallTrampolineMethod method = null;
        if (variant == CallVariant.VARARGS) {
            method = nonVirtual ? varargsNonvirtualCallTrampolineMethod : varargsCallTrampolineMethod;
        } else if (variant == CallVariant.ARRAY) {
            method = nonVirtual ? arrayNonvirtualCallTrampolineMethod : arrayCallTrampolineMethod;
        } else if (variant == CallVariant.VA_LIST) {
            method = nonVirtual ? valistNonvirtualCallTrampolineMethod : valistCallTrampolineMethod;
        }
        assert method != null;
        return method;
    }

    public JNINativeLinkage makeLinkage(String declaringClass, String name, String descriptor) {
        UserError.guarantee(!sealed,
                        "All linkages for JNI calls must be created before the analysis has completed.%nOffending class: %s name: %s descriptor: %s",
                        declaringClass, name, descriptor);

        JNINativeLinkage key = new JNINativeLinkage(declaringClass, name, descriptor);

        // Checkstyle: stop
        if (JNIAccessFeature.Options.PrintJNIMethods.getValue()) {
            System.out.println("Creating a new JNINativeLinkage: " + key.toString());
        }
        // Checkstyle: resume

        return nativeLinkages.computeIfAbsent(key, linkage -> {
            newLinkages.put(linkage, linkage);
            return linkage;
        });
    }

    private boolean wereElementsAdded() {
        return !(newClasses.isEmpty() && newMethods.isEmpty() && newFields.isEmpty() && newLinkages.isEmpty());
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

        for (Executable method : newMethods) {
            addMethod(method, access);
        }
        newMethods.clear();

        newFields.forEach((field, writable) -> {
            addField(field, writable, access);
        });
        newFields.clear();

        JNIReflectionDictionary.singleton().addLinkages(newLinkages);
        newLinkages.clear();

        access.requireAnalysisIteration();
    }

    private static JNIAccessibleClass addClass(Class<?> classObj, DuringAnalysisAccessImpl access) {
        if (SubstitutionReflectivityFilter.shouldExclude(classObj, access.getMetaAccess())) {
            return null;
        }
        return JNIReflectionDictionary.singleton().addClassIfAbsent(classObj, c -> {
            AnalysisType analysisClass = access.getMetaAccess().lookupJavaType(classObj);
            if (analysisClass.isInterface() || (analysisClass.isInstanceClass() && analysisClass.isAbstract())) {
                analysisClass.registerAsInTypeCheck();
            } else {
                analysisClass.registerAsAllocated(null);
            }
            return new JNIAccessibleClass(classObj);
        });
    }

    private void addMethod(Executable method, DuringAnalysisAccessImpl access) {
        if (SubstitutionReflectivityFilter.shouldExclude(method, access.getMetaAccess())) {
            return;
        }
        JNIAccessibleClass jniClass = addClass(method.getDeclaringClass(), access);
        JNIAccessibleMethodDescriptor descriptor = JNIAccessibleMethodDescriptor.of(method);
        jniClass.addMethodIfAbsent(descriptor, d -> {
            MetaAccessProvider wrappedMetaAccess = access.getMetaAccess().getWrapped();

            JNIJavaCallWrapperMethod varargsCallWrapper = new JNIJavaCallWrapperMethod(method, CallVariant.VARARGS, false, wrappedMetaAccess, nativeLibraries);
            JNIJavaCallWrapperMethod arrayCallWrapper = new JNIJavaCallWrapperMethod(method, CallVariant.ARRAY, false, wrappedMetaAccess, nativeLibraries);
            JNIJavaCallWrapperMethod valistCallWrapper = new JNIJavaCallWrapperMethod(method, CallVariant.VA_LIST, false, wrappedMetaAccess, nativeLibraries);
            Stream<JNIJavaCallWrapperMethod> wrappers = Stream.of(varargsCallWrapper, arrayCallWrapper, valistCallWrapper);

            JNIJavaCallWrapperMethod varargsNonvirtualCallWrapper = null;
            JNIJavaCallWrapperMethod arrayNonvirtualCallWrapper = null;
            JNIJavaCallWrapperMethod valistNonvirtualCallWrapper = null;
            if (!Modifier.isStatic(method.getModifiers()) && !Modifier.isAbstract(method.getModifiers())) {
                varargsNonvirtualCallWrapper = new JNIJavaCallWrapperMethod(method, CallVariant.VARARGS, true, wrappedMetaAccess, nativeLibraries);
                arrayNonvirtualCallWrapper = new JNIJavaCallWrapperMethod(method, CallVariant.ARRAY, true, wrappedMetaAccess, nativeLibraries);
                valistNonvirtualCallWrapper = new JNIJavaCallWrapperMethod(method, CallVariant.VA_LIST, true, wrappedMetaAccess, nativeLibraries);
                wrappers = Stream.concat(wrappers, Stream.of(varargsNonvirtualCallWrapper, arrayNonvirtualCallWrapper, valistNonvirtualCallWrapper));
            }

            JNIAccessibleMethod jniMethod = new JNIAccessibleMethod(d, method.getModifiers(), jniClass, varargsCallWrapper, arrayCallWrapper, valistCallWrapper,
                            varargsNonvirtualCallWrapper, arrayNonvirtualCallWrapper, valistNonvirtualCallWrapper);
            CEntryPointData unpublished = CEntryPointData.createCustomUnpublished();
            wrappers.forEach(wrapper -> {
                AnalysisMethod analysisWrapper = access.getUniverse().lookup(wrapper);
                access.getBigBang().addRootMethod(analysisWrapper);
                analysisWrapper.registerAsEntryPoint(unpublished); // ensures C calling convention
            });
            return jniMethod;
        });
    }

    private static void addField(Field reflField, boolean writable, DuringAnalysisAccessImpl access) {
        if (SubstitutionReflectivityFilter.shouldExclude(reflField, access.getMetaAccess())) {
            return;
        }
        JNIAccessibleClass jniClass = addClass(reflField.getDeclaringClass(), access);
        AnalysisField field = access.getMetaAccess().lookupJavaField(reflField);
        jniClass.addFieldIfAbsent(field.getName(), name -> new JNIAccessibleField(jniClass, name, field.getJavaKind(), field.getModifiers()));
        field.registerAsRead(null);
        if (writable) {
            field.registerAsWritten(null);
            AnalysisType fieldType = field.getType();
            if (fieldType.isArray() && !access.isReachable(fieldType)) {
                // For convenience, make the array type reachable if its elemental type becomes
                // such, allowing the array creation via JNI without an explicit reflection config.
                access.registerReachabilityHandler(a -> fieldType.registerAsAllocated(null),
                                ((AnalysisType) fieldType.getElementalType()).getJavaClass());
            }
        } else if (field.isStatic() && field.isFinal()) {
            MaterializedConstantFields.singleton().register(field);
        }
        // Same as BigBang.addSystemField() and BigBang.addSystemStaticField():
        // create type flows for any subtype of the field's declared type
        BigBang bigBang = access.getBigBang();
        TypeFlow<?> declaredTypeFlow = field.getType().getTypeFlow(bigBang, true);
        if (field.isStatic()) {
            declaredTypeFlow.addUse(bigBang, field.getStaticFieldFlow());
        } else {
            FieldTypeFlow instanceFieldFlow = field.getDeclaringClass().getContextInsensitiveAnalysisObject().getInstanceFieldFlow(bigBang, field, writable);
            declaredTypeFlow.addUse(bigBang, instanceFieldFlow);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        sealed = true;
        if (wereElementsAdded()) {
            abortIfSealed();
        }
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
        for (JNIAccessibleClass clazz : JNIReflectionDictionary.singleton().getClasses()) {
            for (JNIAccessibleField field : clazz.getFields()) {
                field.finishBeforeCompilation(access);
            }
            for (JNIAccessibleMethod method : clazz.getMethods()) {
                method.finishBeforeCompilation(access);
                access.registerAsImmutable(method); // for constant address to use as identifier
            }
        }
    }

    @Fold
    public boolean haveJavaRuntimeReflectionSupport() {
        return haveJavaRuntimeReflectionSupport;
    }
}
