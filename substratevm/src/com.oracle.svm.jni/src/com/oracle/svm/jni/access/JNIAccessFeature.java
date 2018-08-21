/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.jni.hosted.JNIFeature.Options.JNIConfigurationFiles;
import static com.oracle.svm.jni.hosted.JNIFeature.Options.JNIConfigurationResources;

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
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.ReflectionRegistry;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.AfterRegistrationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.config.ReflectionConfigurationParser;
import com.oracle.svm.jni.JNIJavaCallWrappers;
import com.oracle.svm.jni.hosted.JNICallTrampolineMethod;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod.CallVariant;
import com.oracle.svm.jni.hosted.JNIRuntimeAccess.JNIRuntimeAccessibilitySupport;

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

    private final Set<Class<?>> newClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Executable> newMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Field> newFields = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<JNINativeLinkage, JNINativeLinkage> newLinkages = new ConcurrentHashMap<>();

    private final Map<JNINativeLinkage, JNINativeLinkage> nativeLinkages = new ConcurrentHashMap<>();

    private boolean haveJavaRuntimeReflectionSupport;

    private void abortIfSealed() {
        UserError.guarantee(!sealed, "Classes, methods and fields must be registered for JNI access before the analysis has completed.");
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess arg) {
        AfterRegistrationAccessImpl access = (AfterRegistrationAccessImpl) arg;

        JNIReflectionDictionary.initialize();

        JNIRuntimeAccessibilitySupportImpl registry = new JNIRuntimeAccessibilitySupportImpl();
        ImageSingletons.add(JNIRuntimeAccessibilitySupport.class, registry);

        ReflectionConfigurationParser parser = new ReflectionConfigurationParser(registry, access.getImageClassLoader());
        parser.parseAndRegisterConfigurations("JNI", JNIConfigurationFiles, JNIConfigurationResources);
    }

    private class JNIRuntimeAccessibilitySupportImpl implements JNIRuntimeAccessibilitySupport, ReflectionRegistry {
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
        public void register(Field... fields) {
            abortIfSealed();
            newFields.addAll(Arrays.asList(fields));
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
        UserError.guarantee(!sealed, "All linkages for JNI calls must be created before the analysis has completed.");
        JNINativeLinkage key = new JNINativeLinkage(declaringClass, name, descriptor);
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

        for (Field field : newFields) {
            addField(field, access);
        }
        newFields.clear();

        JNIReflectionDictionary.singleton().addLinkages(newLinkages);
        newLinkages.clear();

        access.requireAnalysisIteration();
    }

    private static JNIAccessibleClass addClass(Class<?> classObj, DuringAnalysisAccessImpl access) {
        return JNIReflectionDictionary.singleton().addClassIfAbsent(classObj, c -> {
            AnalysisType analysisClass = access.getMetaAccess().lookupJavaType(classObj);
            if (analysisClass.isArray() || (analysisClass.isInstanceClass() && !analysisClass.isAbstract())) {
                analysisClass.registerAsAllocated(null);
            }
            return new JNIAccessibleClass(classObj);
        });
    }

    private void addMethod(Executable method, DuringAnalysisAccessImpl access) {
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

            JNIAccessibleMethod jniMethod = new JNIAccessibleMethod(method.getModifiers(), jniClass, varargsCallWrapper, arrayCallWrapper, valistCallWrapper,
                            varargsNonvirtualCallWrapper, arrayNonvirtualCallWrapper, valistNonvirtualCallWrapper);
            wrappers.forEach(wrapper -> {
                AnalysisMethod analysisWrapper = access.getUniverse().lookup(wrapper);
                access.getBigBang().addRootMethod(analysisWrapper);
                analysisWrapper.registerAsEntryPoint(jniMethod); // ensures C calling convention
            });
            return jniMethod;
        });
    }

    private static void addField(Field reflField, DuringAnalysisAccessImpl access) {
        BigBang bigBang = access.getBigBang();
        JNIAccessibleClass jniClass = addClass(reflField.getDeclaringClass(), access);
        jniClass.addFieldIfAbsent(reflField.getName(), n -> {
            AnalysisField field = access.getMetaAccess().lookupJavaField(reflField);
            field.registerAsAccessed();
            // Same as BigBang.addSystemField() and BigBang.addSystemStaticField():
            // create type flows for any subtype of the field's declared type
            TypeFlow<?> declaredTypeFlow = field.getType().getTypeFlow(bigBang, true);
            if (field.isStatic()) {
                declaredTypeFlow.addUse(bigBang, field.getStaticFieldFlow());
            } else {
                FieldTypeFlow instanceFieldFlow = field.getDeclaringClass().getContextInsensitiveAnalysisObject().getInstanceFieldFlow(bigBang, field, true);
                declaredTypeFlow.addUse(bigBang, instanceFieldFlow);
            }
            return new JNIAccessibleField(jniClass, reflField.getName(), field.getJavaKind(), field.getModifiers());
        });
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
        CompilationAccessImpl access = (CompilationAccessImpl) a;
        for (JNIAccessibleClass clazz : JNIReflectionDictionary.singleton().getClasses()) {
            for (JNIAccessibleField field : clazz.getFields()) {
                field.fillOffset(access);
            }
            for (JNIAccessibleMethod method : clazz.getMethods()) {
                method.resolveJavaCallWrapper(access);
                access.registerAsImmutable(method); // for constant address to use as identifier
            }
        }
    }

    @Fold
    public boolean haveJavaRuntimeReflectionSupport() {
        return haveJavaRuntimeReflectionSupport;
    }
}
