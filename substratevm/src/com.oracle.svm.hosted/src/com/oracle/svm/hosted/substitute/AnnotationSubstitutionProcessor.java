/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.hosted.substitute;

import static com.oracle.svm.core.SubstrateOptions.VerifyNamingConventions;
import static com.oracle.svm.core.util.UserError.guarantee;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.annotation.AnnotationSubstitutionType;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.option.HostedOptionParser;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class AnnotationSubstitutionProcessor extends SubstitutionProcessor {

    /*
     * The number of array dimensions we create for substitute and alias types. Since every type
     * introduced into the system brings some overhead, we only create up to a reasonable array
     * dimension.
     */
    private static final int ARRAY_DIMENSIONS = 8;

    protected final ImageClassLoader imageClassLoader;
    protected final MetaAccessProvider metaAccess;

    private final Map<Object, Delete> deleteAnnotations;
    private final Map<ResolvedJavaType, ResolvedJavaType> typeSubstitutions;
    private final Map<ResolvedJavaMethod, ResolvedJavaMethod> methodSubstitutions;
    private final Map<ResolvedJavaField, ResolvedJavaField> fieldSubstitutions;

    public AnnotationSubstitutionProcessor(ImageClassLoader imageClassLoader, MetaAccessProvider metaAccess) {
        this.imageClassLoader = imageClassLoader;
        this.metaAccess = metaAccess;

        deleteAnnotations = new HashMap<>();
        typeSubstitutions = new HashMap<>();
        methodSubstitutions = new HashMap<>();
        fieldSubstitutions = new HashMap<>();
    }

    @Override
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        Delete deleteAnnotation = deleteAnnotations.get(type);
        if (deleteAnnotation != null) {
            throw new UnsupportedFeatureException(deleteErrorMessage(type, deleteAnnotation, true));
        }
        ResolvedJavaType substitution = typeSubstitutions.get(type);
        if (substitution != null) {
            return substitution;
        }
        return type;
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType type) {
        if (type instanceof SubstitutionType) {
            return ((SubstitutionType) type).getAnnotated();
        } else if (type instanceof AnnotationSubstitutionType) {
            return ((AnnotationSubstitutionType) type).getOriginal();
        } else if (type instanceof InjectedFieldsType) {
            return ((InjectedFieldsType) type).getOriginal();
        }
        return type;
    }

    @Override
    public ResolvedJavaField lookup(ResolvedJavaField field) {
        Delete deleteAnnotation = deleteAnnotations.get(field);
        if (deleteAnnotation != null) {
            throw new UnsupportedFeatureException(deleteErrorMessage(field, deleteAnnotation, true));
        }
        ResolvedJavaField substitution = fieldSubstitutions.get(field);
        if (substitution != null) {
            return substitution;
        }
        return field;
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        Delete deleteAnnotation = deleteAnnotations.get(method);
        if (deleteAnnotation != null) {
            throw new UnsupportedFeatureException(deleteErrorMessage(method, deleteAnnotation, true));
        }
        ResolvedJavaMethod substitution = methodSubstitutions.get(method);
        if (substitution != null) {
            return substitution;
        }
        return method;
    }

    @Override
    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
        if (method instanceof SubstitutionMethod) {
            return ((SubstitutionMethod) method).getOriginal();
        } else if (method instanceof CustomSubstitutionMethod) {
            return ((CustomSubstitutionMethod) method).getOriginal();
        } else if (method instanceof AnnotatedMethod) {
            return ((AnnotatedMethod) method).getOriginal();
        }
        return method;
    }

    /**
     * Eagerly register all target fields of recomputed value fields as unsafe accessed.
     */
    public void processComputedValueFields(BigBang bb) {
        for (ResolvedJavaField field : fieldSubstitutions.values()) {
            if (field instanceof ComputedValue) {
                ComputedValue cvField = (ComputedValue) field;

                switch (cvField.getRecomputeValueKind()) {
                    case FieldOffset:
                        AnalysisField targetField = bb.getMetaAccess().lookupJavaField(cvField.getTargetField());
                        targetField.registerAsAccessed();
                        targetField.registerAsUnsafeAccessed();
                        break;
                }
            }
        }
    }

    public void init() {
        List<Class<?>> annotatedClasses = findTargetClasses();

        /* Sort by name to make processing order predictable for debugging. */
        annotatedClasses.sort(Comparator.comparing(Class::getName));

        for (Class<?> annotatedClass : annotatedClasses) {
            handleClass(annotatedClass);
        }
    }

    protected List<Class<?>> findTargetClasses() {
        return imageClassLoader.findAnnotatedClasses(TargetClass.class);
    }

    private void handleClass(Class<?> annotatedClass) {
        guarantee(Modifier.isFinal(annotatedClass.getModifiers()) || annotatedClass.isInterface(), "Annotated class must be final: %s", annotatedClass);
        guarantee(annotatedClass.getSuperclass() == Object.class || annotatedClass.isInterface(), "Annotated class must inherit directly from Object: %s", annotatedClass);

        if (!NativeImageGenerator.includedIn(ImageSingletons.lookup(Platform.class), lookupAnnotation(annotatedClass, Platforms.class))) {
            return;
        }

        TargetClass targetClassAnnotation = lookupAnnotation(annotatedClass, TargetClass.class);
        Class<?> originalClass = findTargetClass(annotatedClass, targetClassAnnotation);
        if (originalClass == null) {
            return;
        }

        Delete deleteAnnotation = lookupAnnotation(annotatedClass, Delete.class);
        Substitute substituteAnnotation = lookupAnnotation(annotatedClass, Substitute.class);

        int numAnnotations = (deleteAnnotation != null ? 1 : 0) + (substituteAnnotation != null ? 1 : 0);
        guarantee(numAnnotations <= 1, "Only one of @Delete or @Substitute can be used: %s", annotatedClass);

        if (deleteAnnotation != null) {
            handleDeletedClass(originalClass, deleteAnnotation);
        } else if (substituteAnnotation != null) {
            handleSubstitutionClass(annotatedClass, originalClass);
        } else {
            handleAliasClass(annotatedClass, originalClass);
        }
    }

    private void handleAliasClass(Class<?> annotatedClass, Class<?> originalClass) {
        String expectedName = "Target_" + originalClass.getName().replace('.', '_').replace('$', '_');
        if (VerifyNamingConventions.getValue()) {
            guarantee(annotatedClass.getSimpleName().equals(expectedName), "Naming convention violation: %s must be named %s", annotatedClass, expectedName);
        }

        ResolvedJavaType original = metaAccess.lookupJavaType(originalClass);
        ResolvedJavaType annotated = metaAccess.lookupJavaType(annotatedClass);

        for (int i = 0; i < ARRAY_DIMENSIONS; i++) {
            guarantee(!typeSubstitutions.containsKey(annotated), "Already registered: %s", annotated);
            typeSubstitutions.put(annotated, original);

            original = original.getArrayClass();
            annotated = annotated.getArrayClass();
        }

        for (Method annotatedMethod : annotatedClass.getDeclaredMethods()) {
            handleMethodInAliasClass(annotatedMethod, originalClass);
        }
        for (Constructor<?> annotatedMethod : annotatedClass.getDeclaredConstructors()) {
            handleMethodInAliasClass(annotatedMethod, originalClass);
        }
        for (Field annotatedField : annotatedClass.getDeclaredFields()) {
            handleFieldInAliasClass(annotatedField, originalClass);
        }
    }

    private void handleMethodInAliasClass(Executable annotatedMethod, Class<?> originalClass) {
        if (!NativeImageGenerator.includedIn(ImageSingletons.lookup(Platform.class), lookupAnnotation(annotatedMethod, Platforms.class))) {
            return;
        }

        Delete deleteAnnotation = lookupAnnotation(annotatedMethod, Delete.class);
        Substitute substituteAnnotation = lookupAnnotation(annotatedMethod, Substitute.class);
        AnnotateOriginal annotateOriginalAnnotation = lookupAnnotation(annotatedMethod, AnnotateOriginal.class);
        Alias aliasAnnotation = lookupAnnotation(annotatedMethod, Alias.class);

        int numAnnotations = (deleteAnnotation != null ? 1 : 0) + (substituteAnnotation != null ? 1 : 0) + (annotateOriginalAnnotation != null ? 1 : 0) + (aliasAnnotation != null ? 1 : 0);
        if (numAnnotations == 0) {
            guarantee(annotatedMethod instanceof Constructor, "One of @Delete, @Substitute, @AnnotateOriginal, or @Alias must be used: %s", annotatedMethod);
            return;
        }
        guarantee(numAnnotations == 1, "Only one of @Delete, @Substitute, @AnnotateOriginal, or @Alias can be used: %s", annotatedMethod);

        ResolvedJavaMethod annotated = metaAccess.lookupJavaMethod(annotatedMethod);
        ResolvedJavaMethod original = findOriginalMethod(annotatedMethod, originalClass);

        if (original == null) {
            /* Optional target that is not present, so nothing to do. */
        } else if (deleteAnnotation != null) {
            registerAsDeleted(annotated, original, deleteAnnotation);
        } else if (substituteAnnotation != null) {
            SubstitutionMethod substitution = new SubstitutionMethod(original, annotated);
            register(methodSubstitutions, annotated, original, substitution);
        } else if (annotateOriginalAnnotation != null) {
            AnnotatedMethod substitution = new AnnotatedMethod(original, annotated);
            register(methodSubstitutions, annotated, original, substitution);
        } else if (aliasAnnotation != null) {
            register(methodSubstitutions, annotated, original, original);
        }
    }

    private void handleFieldInAliasClass(Field annotatedField, Class<?> originalClass) {
        if (!NativeImageGenerator.includedIn(ImageSingletons.lookup(Platform.class), lookupAnnotation(annotatedField, Platforms.class))) {
            return;
        }

        ResolvedJavaField annotated = metaAccess.lookupJavaField(annotatedField);

        Delete deleteAnnotation = lookupAnnotation(annotatedField, Delete.class);
        Alias aliasAnnotation = lookupAnnotation(annotatedField, Alias.class);
        Inject injectAnnotation = lookupAnnotation(annotatedField, Inject.class);

        int numAnnotations = (deleteAnnotation != null ? 1 : 0) + (aliasAnnotation != null ? 1 : 0) + (injectAnnotation != null ? 1 : 0);
        if (numAnnotations == 0) {
            guarantee(annotatedField.getName().equals("$assertionsDisabled"), "One of @Delete, @Alias, or @Inject must be used: %s", annotatedField);
            /*
             * The field $assertionsDisabled can be present in the original class, but does not have
             * to. We treat it like an optional @Alias fields without field value recomputation.
             */
            ResolvedJavaField original = findOriginalField(annotatedField, originalClass, true);
            if (original != null) {
                register(fieldSubstitutions, annotated, null, original);
            }
            return;
        }
        guarantee(numAnnotations == 1, "Only one of @Delete, @Alias, or @Inject can be used: %s", annotatedField);

        if (injectAnnotation != null) {
            guarantee(!annotated.isStatic(), "@Inject field must not be static: %s", annotated);

            ResolvedJavaField injected = fieldValueRecomputation(originalClass, annotated, annotated, annotatedField);
            register(fieldSubstitutions, annotated, null, injected);

            ResolvedJavaType original = metaAccess.lookupJavaType(originalClass);
            InjectedFieldsType substitution;
            if (typeSubstitutions.get(original) instanceof InjectedFieldsType) {
                substitution = (InjectedFieldsType) typeSubstitutions.get(original);
                register(typeSubstitutions, annotated.getDeclaringClass(), original, substitution);
            } else {
                substitution = new InjectedFieldsType(original);
                register(typeSubstitutions, annotated.getDeclaringClass(), original, substitution);
            }
            substitution.addInjectedField(injected);

        } else {
            ResolvedJavaField original = findOriginalField(annotatedField, originalClass, false);
            if (original == null) {
                return;
            }

            guarantee(annotated.isStatic() == original.isStatic(), "Static modifier mismatch: %s, %s", annotated, original);
            guarantee(annotated.getJavaKind() == original.getJavaKind(), "Type mismatch: %s, %s", annotated, original);

            if (deleteAnnotation != null) {
                registerAsDeleted(annotated, original, deleteAnnotation);
            } else {
                ResolvedJavaField alias = fieldValueRecomputation(originalClass, original, annotated, annotatedField);
                register(fieldSubstitutions, annotated, original, alias);
            }
        }
    }

    private void handleDeletedClass(Class<?> originalClass, Delete deleteAnnotation) {
        if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            /*
             * We register all methods and fields as deleted. That still allows usage of the type in
             * type checks.
             */
            for (Executable m : originalClass.getDeclaredMethods()) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                registerAsDeleted(null, method, deleteAnnotation);
            }
            for (Executable m : originalClass.getDeclaredConstructors()) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                registerAsDeleted(null, method, deleteAnnotation);
            }
            for (Field f : originalClass.getDeclaredFields()) {
                ResolvedJavaField field = metaAccess.lookupJavaField(f);
                registerAsDeleted(null, field, deleteAnnotation);
            }

        } else {
            deleteAnnotations.put(metaAccess.lookupJavaType(originalClass), deleteAnnotation);
        }
    }

    private void registerAsDeleted(ResolvedJavaMethod annotated, ResolvedJavaMethod original, Delete deleteAnnotation) {
        if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            register(methodSubstitutions, annotated, original, new DeletedMethod(original, deleteAnnotation));
        } else {
            deleteAnnotations.put(original, deleteAnnotation);
            deleteAnnotations.put(annotated, deleteAnnotation);
        }
    }

    private void registerAsDeleted(ResolvedJavaField annotated, ResolvedJavaField original, Delete deleteAnnotation) {
        if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            register(fieldSubstitutions, annotated, original, new AnnotatedField(original, deleteAnnotation));
        } else {
            deleteAnnotations.put(original, deleteAnnotation);
            deleteAnnotations.put(annotated, deleteAnnotation);
        }
    }

    @Delete("The declaring class of this element has been substituted, but this element is not present in the substitution class") //
    static final int SUBSTITUTION_DELETE_HOLDER = 0;

    static final Delete SUBSTITUTION_DELETE;

    static {
        try {
            SUBSTITUTION_DELETE = AnnotationSubstitutionProcessor.class.getDeclaredField("SUBSTITUTION_DELETE_HOLDER").getAnnotation(Delete.class);
        } catch (NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private void handleSubstitutionClass(Class<?> annotatedClass, Class<?> originalClass) {
        // Not sure what happens if the target class is in a hierarchy - so prohibit that for now.
        guarantee(annotatedClass.isInterface() == originalClass.isInterface(), "if original is interface, target must also be interface: %s", annotatedClass);
        guarantee(originalClass.getSuperclass() == Object.class || originalClass.isInterface(), "target class must inherit directly from Object: %s", originalClass);

        ResolvedJavaType original = metaAccess.lookupJavaType(originalClass);
        ResolvedJavaType annotated = metaAccess.lookupJavaType(annotatedClass);

        for (int i = 0; i < ARRAY_DIMENSIONS; i++) {
            ResolvedJavaType substitution = new SubstitutionType(original, annotated);
            register(typeSubstitutions, annotated, original, substitution);

            original = original.getArrayClass();
            annotated = annotated.getArrayClass();
        }

        for (Method m : annotatedClass.getDeclaredMethods()) {
            handleAnnotatedMethodInSubstitutionClass(m, originalClass);
        }
        for (Constructor<?> c : annotatedClass.getDeclaredConstructors()) {
            handleAnnotatedMethodInSubstitutionClass(c, originalClass);
        }
        for (Method m : originalClass.getDeclaredMethods()) {
            handleOriginalMethodInSubstitutionClass(m);
        }
        for (Constructor<?> c : originalClass.getDeclaredConstructors()) {
            handleOriginalMethodInSubstitutionClass(c);
        }

        for (Field f : originalClass.getDeclaredFields()) {
            registerAsDeleted(null, metaAccess.lookupJavaField(f), SUBSTITUTION_DELETE);
        }
        for (Field f : annotatedClass.getDeclaredFields()) {
            ResolvedJavaField field = metaAccess.lookupJavaField(f);
            ResolvedJavaField alias = fieldValueRecomputation(annotatedClass, field, field, f);
            if (!alias.equals(field)) {
                register(fieldSubstitutions, field, null, alias);
            }
        }
    }

    private void handleAnnotatedMethodInSubstitutionClass(Executable annotatedMethod, Class<?> originalClass) {
        Substitute substituteAnnotation = lookupAnnotation(annotatedMethod, Substitute.class);
        KeepOriginal keepOriginalAnnotation = lookupAnnotation(annotatedMethod, KeepOriginal.class);

        int numAnnotations = (substituteAnnotation != null ? 1 : 0) + (keepOriginalAnnotation != null ? 1 : 0);
        if (numAnnotations == 0) {
            /* Unannotated method in substitution class: a regular method, nothing to do. */
            return;
        }
        guarantee(numAnnotations == 1, "only one of @Substitute or @KeepOriginal can be used: %s", annotatedMethod);

        ResolvedJavaMethod annotated = metaAccess.lookupJavaMethod(annotatedMethod);
        ResolvedJavaMethod original = findOriginalMethod(annotatedMethod, originalClass);

        if (original == null) {
            /* Optional target that is not present, so nothing to do. */
        } else if (substituteAnnotation != null) {
            register(methodSubstitutions, annotated, original, annotated);
        } else if (keepOriginalAnnotation != null) {
            register(methodSubstitutions, annotated, original, original);
        }
    }

    private void handleOriginalMethodInSubstitutionClass(Executable m) {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
        if (!methodSubstitutions.containsKey(method)) {
            if (method.isSynthetic()) {
                /*
                 * Synthetic methods are mostly methods generated by javac to access private fields
                 * from inner classes. The naming is not fixed, and it would be tedious anyway to
                 * manually mark such methods as @KeepOriginal. We therefore treat all synthetic
                 * methods as if they were annotated with @KeepOriginal. If the method/field that
                 * the synthetic method is forwarding to is not available, an error message for that
                 * method/field will be produced anyway.
                 */
                register(methodSubstitutions, null, method, method);
            } else {
                registerAsDeleted(null, method, SUBSTITUTION_DELETE);
            }
        }
    }

    private ResolvedJavaMethod findOriginalMethod(Executable annotatedMethod, Class<?> originalClass) {
        TargetElement targetElementAnnotation = lookupAnnotation(annotatedMethod, TargetElement.class);
        String originalName = "";
        boolean optional = false;
        if (targetElementAnnotation != null) {
            originalName = targetElementAnnotation.name();
            optional = targetElementAnnotation.optional();
        }

        if (originalName.length() == 0) {
            originalName = annotatedMethod.getName();
        }

        try {
            if (annotatedMethod instanceof Method && !originalName.equals(TargetElement.CONSTRUCTOR_NAME)) {
                Class<?>[] originalParams = interceptParameterTypes(annotatedMethod.getParameterTypes());
                Method originalMethod = originalClass.getDeclaredMethod(originalName, originalParams);

                guarantee(Modifier.isStatic(annotatedMethod.getModifiers()) == Modifier.isStatic(originalMethod.getModifiers()), "Static modifier mismatch: %s, %s", annotatedMethod, originalMethod);
                return metaAccess.lookupJavaMethod(originalMethod);

            } else {
                guarantee(!Modifier.isStatic(annotatedMethod.getModifiers()), "Constructor Alias method %s must not be static", annotatedMethod);
                Class<?>[] originalParams = interceptParameterTypes(annotatedMethod.getParameterTypes());
                Constructor<?> originalMethod = originalClass.getDeclaredConstructor(originalParams);
                return metaAccess.lookupJavaMethod(originalMethod);
            }

        } catch (NoSuchMethodException ex) {
            guarantee(optional, "could not find non-optional target method: %s", annotatedMethod);
            return null;
        }
    }

    private ResolvedJavaField findOriginalField(Field annotatedField, Class<?> originalClass, boolean forceOptional) {
        TargetElement targetElementAnnotation = lookupAnnotation(annotatedField, TargetElement.class);
        String originalName = "";
        boolean optional = false;
        if (targetElementAnnotation != null) {
            originalName = targetElementAnnotation.name();
            optional = targetElementAnnotation.optional();
        }
        if (originalName.length() == 0) {
            originalName = annotatedField.getName();
        }

        try {
            Field originalField = originalClass.getDeclaredField(originalName);

            guarantee(getTargetClass(annotatedField.getType()).equals(originalField.getType()),
                            "Type mismatch:%n    %s %s%n    %s %s", annotatedField.getType(), annotatedField, originalField.getType(), originalField);

            return metaAccess.lookupJavaField(originalField);

        } catch (NoSuchFieldException ex) {
            /*
             * Some fields are hidden from reflection. Try to find the field via the
             * ResolvedJavaType.
             */
            for (ResolvedJavaField f : metaAccess.lookupJavaType(originalClass).getInstanceFields(true)) {
                if (f.getName().equals(originalName)) {
                    return f;
                }
            }

            guarantee(optional || forceOptional, "could not find non-optional target field: %s", annotatedField);
            return null;
        }
    }

    private static <T> void register(Map<T, T> substitutions, T annotated, T original, T target) {
        if (annotated != null) {
            guarantee(!substitutions.containsKey(annotated) || substitutions.get(annotated) == original || substitutions.get(annotated) == target, "Already registered: %s", annotated);
            substitutions.put(annotated, target);
        }
        if (original != null) {
            guarantee(!substitutions.containsKey(original) || substitutions.get(original) == original || substitutions.get(original) == target, "Already registered: %s", original);
            substitutions.put(original, target);
        }
    }

    private ResolvedJavaField fieldValueRecomputation(Class<?> originalClass, ResolvedJavaField original, ResolvedJavaField annotated, Field annotatedField) {
        RecomputeFieldValue recomputeAnnotation = lookupAnnotation(annotatedField, RecomputeFieldValue.class);
        InjectAccessors injectAccessorsAnnotation = lookupAnnotation(annotatedField, InjectAccessors.class);

        int numAnnotations = (recomputeAnnotation != null ? 1 : 0) + (injectAccessorsAnnotation != null ? 1 : 0);
        guarantee(numAnnotations <= 1, "Only one of @RecomputeFieldValue or @InjectAccessors can be used: %s", annotatedField);

        if (injectAccessorsAnnotation != null) {
            return new AnnotatedField(original, injectAccessorsAnnotation);
        }
        if (recomputeAnnotation == null && !original.isFinal()) {
            return original;
        }

        RecomputeFieldValue.Kind kind = RecomputeFieldValue.Kind.None;
        Class<?> targetClass = originalClass;
        String targetName = "";
        boolean isFinal = false;

        if (recomputeAnnotation != null) {
            kind = recomputeAnnotation.kind();
            targetName = recomputeAnnotation.name();
            isFinal = recomputeAnnotation.isFinal();
            guarantee(!isFinal || ComputedValueField.isFinalValid(kind), "@%s with %s can never be final during analysis: unset isFinal in the annotation on %s",
                            RecomputeFieldValue.class.getSimpleName(), kind, annotated);
            if (recomputeAnnotation.declClass() != RecomputeFieldValue.class) {
                guarantee(recomputeAnnotation.declClassName().isEmpty(), "Both class and class name specified");
                targetClass = recomputeAnnotation.declClass();
            } else if (!recomputeAnnotation.declClassName().isEmpty()) {
                targetClass = imageClassLoader.findClassByName(recomputeAnnotation.declClassName());
            }
        }

        return new ComputedValueField(original, annotated, kind, targetClass, targetName, isFinal);
    }

    public Class<?> getTargetClass(Class<?> annotatedClass) {
        Class<?> annotatedBaseClass = annotatedClass;
        int arrayDepth = 0;
        while (annotatedBaseClass.isArray()) {
            arrayDepth++;
            annotatedBaseClass = annotatedBaseClass.getComponentType();
        }

        TargetClass targetClassAnnotation = lookupAnnotation(annotatedBaseClass, TargetClass.class);
        if (targetClassAnnotation == null) {
            /* No annotation found, so return unchanged argument. */
            return annotatedClass;
        }

        Class<?> targetClass = findTargetClass(annotatedBaseClass, targetClassAnnotation);
        for (int i = 0; i < arrayDepth; i++) {
            /*
             * The only way to look up the array class is to instantiate a dummy array and take the
             * class of the array.
             */
            targetClass = Array.newInstance(targetClass, 0).getClass();
        }
        return targetClass;
    }

    Class<?> findTargetClass(Class<?> annotatedBaseClass, TargetClass target) {
        for (Class<? extends BooleanSupplier> predicateClass : target.onlyWith()) {
            BooleanSupplier predicate;
            try {
                Constructor<? extends BooleanSupplier> constructor = predicateClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                predicate = constructor.newInstance();
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
            if (!predicate.getAsBoolean()) {
                return null;
            }
        }

        String className;
        if (target.value() != TargetClass.class) {
            guarantee(target.className().isEmpty(), "Both class and class name specified for substitution");
            className = target.value().getName();
        } else {
            guarantee(!target.className().isEmpty(), "Neither class nor class name specified for substitution");
            className = target.className();
        }
        Class<?> holder = imageClassLoader.findClassByName(className, false);
        if (holder == null) {
            throw UserError.abort("substitution target for " + annotatedBaseClass.getName() +
                            " is not loaded. Use field `onlyWith` in the `TargetClass` annotation to make substitution only active when needed.");
        }
        if (!target.innerClass().isEmpty()) {
            Class<?> outerClass = holder;
            holder = findInnerClass(outerClass, target.innerClass());
            if (holder == null) {
                throw UserError.abort("substitution target for " + annotatedBaseClass.getName() + " is invalid as inner class " + target.innerClass() + " in " + outerClass.getName() +
                                " can not be found. Make sure that the inner class is present.");
            }
        }

        return holder;
    }

    private static Class<?> findInnerClass(Class<?> outerClass, String innerClassSimpleName) {
        for (Class<?> innerClass : outerClass.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals(innerClassSimpleName)) {
                return innerClass;
            }
        }
        return null;
    }

    private Class<?> interceptParameterType(Class<?> type) {
        TargetClass targetClassAnnotation = lookupAnnotation(type, TargetClass.class);
        if (targetClassAnnotation != null) {
            return findTargetClass(type, targetClassAnnotation);
        }

        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            Class<?> componentRet = interceptParameterType(componentType);
            if (!componentRet.equals(componentType)) {
                Object tmp = Array.newInstance(componentRet, 0);
                return tmp.getClass();
            }
        }

        return type;
    }

    private Class<?>[] interceptParameterTypes(Class<?>[] types) {
        for (int i = 0; i < types.length; i++) {
            types[i] = interceptParameterType(types[i]);
        }
        return types;
    }

    protected <T extends Annotation> T lookupAnnotation(AnnotatedElement element, Class<T> annotationClass) {
        assert element instanceof Class || element instanceof Executable || element instanceof Field : element.getClass();
        return element.getAnnotation(annotationClass);
    }

    protected static String deleteErrorMessage(AnnotatedElement element, Delete deleteAnnotation, boolean hosted) {
        return deleteErrorMessage(element, deleteAnnotation.value(), hosted);
    }

    public static String deleteErrorMessage(AnnotatedElement element, String message, boolean hosted) {
        StringBuilder result = new StringBuilder();
        result.append("Unsupported ");
        if (element instanceof ResolvedJavaField) {
            result.append("field ").append(((ResolvedJavaField) element).format("%H.%n"));
        } else if (element instanceof ResolvedJavaMethod) {
            ResolvedJavaMethod method = (ResolvedJavaMethod) element;
            result.append(method.isConstructor() ? "constructor " : "method ");
            result.append(method.format("%H.%n(%p)"));
        } else if (element instanceof ResolvedJavaType) {
            result.append("type ").append(((ResolvedJavaType) element).toJavaName(true));
        } else {
            throw VMError.shouldNotReachHere();
        }
        result.append(" is reachable");
        if (message != null && !message.isEmpty()) {
            result.append(": ").append(message);
        }
        if (hosted) {
            result.append(System.lineSeparator())
                            .append("To diagnose the issue, you can add the option ").append(HostedOptionParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+"))
                            .append(". The unsupported element is then reported at run time when it is accessed the first time.");
        }
        return result.toString();
    }
}
