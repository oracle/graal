/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import static com.oracle.svm.core.SubstrateOptions.VerifyNamingConventions;
import static com.oracle.svm.core.util.UserError.guarantee;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.AssertionsSupport;
import com.oracle.svm.core.BuilderUtil;
import com.oracle.svm.core.SubstrateOptions;
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
import com.oracle.svm.core.fieldvaluetransformer.ArrayBaseOffsetFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.ArrayIndexScaleFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.ArrayIndexShiftFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.ConstantValueFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.FieldOffsetFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.NewInstanceOfFixedClassFieldValueTransformer;
import com.oracle.svm.core.fieldvaluetransformer.StaticFieldBaseFieldValueTransformer;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport.WrappedFieldValueTransformer;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.GuestAnnotationAccess;
import com.oracle.svm.util.JVMCIFieldValueTransformer;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.vmaccess.InvocationException;
import jdk.internal.reflect.Reflection;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ModifiersProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * The main substitution processor for Native Image. The annotations supported by this processor
 * are:
 * <ul>
 * <li>{@link TargetClass}</li>
 * <li>{@link Substitute}</li>
 * <li>{@link TargetElement}</li>
 * <li>{@link Alias}</li>
 * <li>{@link AnnotateOriginal}</li>
 * <li>{@link Delete}</li>
 * <li>{@link Inject}</li>
 * <li>{@link InjectAccessors}</li>
 * <li>{@link KeepOriginal}</li>
 * <li>{@link RecomputeFieldValue}</li>
 * </ul>
 * Code tagged with these annotations is preprocessed during Native Image setup when the processor
 * is {@link AnnotationSubstitutionProcessor#init(FieldValueInterceptionSupport) initialized}. Then,
 * hosted code corresponding to the substitution code is intercepted and replaced without modifying
 * the class files during {@link AnalysisUniverse} lookups. See each annotation's JavaDoc for more
 * details, starting with {@link TargetClass}. See also {@link HostedUniverse} for a comprehensive
 * description of the substitution layer.
 */
public class AnnotationSubstitutionProcessor extends SubstitutionProcessor {

    /**
     * The number of array dimensions we create for @{@link Substitute} types, i.e., the maximum
     * array dimension allowed by the JVM spec. For @{@link Alias} types the array substitution
     * mappings are created on demand.
     *
     * @see <a href= "https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.9">
     *      Constraints on Java Virtual Machine Code</a>
     */
    private static final int SUBSTITUTE_ARRAY_DIMENSIONS = 255;

    protected final ImageClassLoader imageClassLoader;
    protected final MetaAccessProvider metaAccess;
    private final GuestAccess guestAccess;
    private final AnnotationValue substitutionDelete;
    private FieldValueInterceptionSupport fieldValueInterceptionSupport;

    /**
     * Contains all elements marked with {@code @Delete}, regardless of whether they are reported at
     * build time or run time.
     */
    private final Map<Object, AnnotationValue> deleteAnnotations;
    private final Map<ResolvedJavaType, ResolvedJavaType> typeSubstitutions;
    private final Map<ResolvedJavaMethod, ResolvedJavaMethod> methodSubstitutions;
    private final Map<ResolvedJavaMethod, ResolvedJavaMethod> polymorphicMethodSubstitutions;
    private final Map<ResolvedJavaField, ResolvedJavaField> fieldSubstitutions;
    private Map<ResolvedJavaField, Object> unsafeAccessedFields = new HashMap<>();
    private final ClassInitializationSupport classInitializationSupport;
    private final Set<String> disabledSubstitutions;
    private final boolean reportUnsupportedElementAtRuntime;

    public AnnotationSubstitutionProcessor(ImageClassLoader imageClassLoader, MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport) {
        this.imageClassLoader = imageClassLoader;
        this.metaAccess = metaAccess;
        this.guestAccess = GuestAccess.get();
        this.substitutionDelete = GuestAnnotationAccess.newAnnotationValue(guestAccess.elements.Delete, "value",
                        "The declaring class of this element has been substituted, but this element is not present in the substitution class");
        this.classInitializationSupport = classInitializationSupport;

        deleteAnnotations = new HashMap<>();
        typeSubstitutions = new ConcurrentHashMap<>();
        methodSubstitutions = new ConcurrentHashMap<>();
        polymorphicMethodSubstitutions = new HashMap<>();
        fieldSubstitutions = new ConcurrentHashMap<>();
        disabledSubstitutions = Set.copyOf(SubstrateOptions.DisableSubstitution.getValue().values());
        reportUnsupportedElementAtRuntime = NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue();
    }

    @Override
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        AnnotationValue deleteAnnotation = deleteAnnotations.get(type);
        if (deleteAnnotation != null && !reportUnsupportedElementAtRuntime) {
            throw new DeletedElementException(deleteErrorMessage(type, deleteAnnotation, true));
        }
        ResolvedJavaType substitution = findTypeSubstitution(type);
        if (substitution != null) {
            return substitution;
        }
        return type;
    }

    private ResolvedJavaType findTypeSubstitution(ResolvedJavaType type) {
        ResolvedJavaType substitution = typeSubstitutions.get(type);
        if (substitution != null) {
            return substitution;
        }
        if (type.isArray()) {
            ResolvedJavaType elementalType = type.getElementalType();
            ResolvedJavaType elementalTypeSubstitution = typeSubstitutions.get(elementalType);
            if (elementalTypeSubstitution != null) {
                /*
                 * The elementalType must be an alias type and an alias for an array type of the
                 * requested dimension has not yet been created. The registered substitution is the
                 * original type that the alias is pointing to.
                 */
                int dimension = BuilderUtil.arrayTypeDimension(type);

                /*
                 * Eagerly register all array types of dimensions up to the required type dimension.
                 * These would be created anyway when iterating through the component types in the
                 * AnalysisType constructor.
                 */
                ResolvedJavaType annotated = elementalType;
                ResolvedJavaType original = elementalTypeSubstitution;
                for (int i = 0; i < dimension; i++) {
                    annotated = annotated.getArrayClass();
                    original = original.getArrayClass();
                    typeSubstitutions.putIfAbsent(annotated, original);
                }

                return original;
            }
        }
        return null;
    }

    @Override
    public ResolvedJavaField lookup(ResolvedJavaField field) {
        AnnotationValue deleteAnnotation = deleteAnnotations.get(field);
        if (deleteAnnotation != null && !reportUnsupportedElementAtRuntime) {
            throw new DeletedElementException(deleteErrorMessage(field, deleteAnnotation, true));
        }

        ResolvedJavaField existing = fieldSubstitutions.get(field);
        return existing != null ? existing : field;
    }

    /**
     * A field is deleted when it was recorded directly in {@link #deleteAnnotations}, or when its
     * declaring type is deleted. In report-at-runtime mode, deleted types are also expanded into
     * member deletions. In fail-fast mode, the type-level entry is the only representation. Fields
     * whose declaring type is fully substituted are also implicitly deleted when no replacement
     * field was provided.
     */
    public boolean isDeleted(ResolvedJavaField field) {
        return deleteAnnotations.get(field) != null || isDeleted(field.getDeclaringClass()) || isImplicitlyDeleted(field);
    }

    /**
     * When an entire type is fully substituted, for example when {@link Class} is replaced with
     * {@link DynamicHub}, we replace all fields of the original type. All the fields that are
     * not @{@link Substitute} are implicitly considered as @{@link Delete}. However, not all
     * implicitly deleted fields are present in the {@link #deleteAnnotations} map because when the
     * original class fields are iterated {@link Class#getDeclaredFields()} applies
     * {@link Reflection#filterFields(Class, java.lang.reflect.Field[])} and excludes several fields
     * access.
     */
    private boolean isImplicitlyDeleted(ResolvedJavaField field) {
        /*
         * If a field's type is fully substituted but the field was not substituted, then it is
         * considered implicitly deleted.
         */
        return typeSubstitutions.get(field.getDeclaringClass()) instanceof SubstitutionType && !fieldSubstitutions.containsKey(field);
    }

    public boolean hasInjectAccessors(ResolvedJavaField field) {
        return isAnnotationPresentOnSubstitutionField(field, InjectAccessors.class);
    }

    /**
     * Checks whether the substitution field registered for {@code field} carries
     * {@code annotationClass}. This is deliberately different from checking annotations on
     * {@code field} itself, which is the original field that hosted analysis is resolving.
     */
    private boolean isAnnotationPresentOnSubstitutionField(ResolvedJavaField field, Class<? extends Annotation> annotationClass) {
        ResolvedJavaField substitutionField = fieldSubstitutions.get(field);
        if (substitutionField != null) {
            return GuestAnnotationAccess.isAnnotationPresent(substitutionField, annotationClass);
        }
        return false;
    }

    public boolean isDeleted(ResolvedJavaType type) {
        return deleteAnnotations.containsKey(OriginalClassProvider.getOriginalType(type));
    }

    /**
     * Returns the original types explicitly marked with a class-level {@link Delete}.
     */
    public List<ResolvedJavaType> getDeletedTypes() {
        return deleteAnnotations.keySet().stream().filter(ResolvedJavaType.class::isInstance).map(ResolvedJavaType.class::cast).toList();
    }

    /**
     * Returns methods recorded as deleted. For an explicit method-level {@link Delete}, this includes
     * both the original target method and the annotated deletion declaration.
     */
    public List<ResolvedJavaMethod> getDeletedMethods() {
        return deleteAnnotations.keySet().stream().filter(ResolvedJavaMethod.class::isInstance).map(ResolvedJavaMethod.class::cast).toList();
    }

    public Optional<ResolvedJavaField> findSubstitution(ResolvedJavaField field) {
        assert !isDeleted(field) : "Field " + field.format("%H.%n") + "is deleted.";
        return Optional.ofNullable(fieldSubstitutions.get(field));
    }

    public Optional<ResolvedJavaType> findFullSubstitution(ResolvedJavaType type) {
        /*
         * When a type is substituted there is a mapping from the original type to the substitution
         * type (and another mapping from the annotated type to the substitution type).
         */
        ResolvedJavaType subst = findTypeSubstitution(type);
        return (subst instanceof SubstitutionType) ? Optional.of(subst) : Optional.empty();
    }

    public boolean isAliased(ResolvedJavaType type) {
        /*
         * When a type is aliased there is a mapping from the alias type to the original type. If
         * the type is an array type then its alias is constructed on demand, but there should be a
         * mapping from the aliased component type to the original component type.
         */
        if (type instanceof SubstitutionType) {
            return false;
        }
        return typeSubstitutions.containsValue(type) || typeSubstitutions.containsValue(type.getElementalType());
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        AnnotationValue deleteAnnotation = deleteAnnotations.get(method);
        if (deleteAnnotation != null && !reportUnsupportedElementAtRuntime) {
            throw new DeletedElementException(deleteErrorMessage(method, deleteAnnotation, true));
        }
        ResolvedJavaMethod substitution = methodSubstitutions.get(method);
        if (substitution != null) {
            return substitution;
        }
        for (ResolvedJavaMethod baseMethod : polymorphicMethodSubstitutions.keySet()) {
            if (method.getDeclaringClass().equals(baseMethod.getDeclaringClass()) && method.getName().equals(baseMethod.getName())) {
                SubstitutionMethod substitutionBaseMethod = (SubstitutionMethod) polymorphicMethodSubstitutions.get(baseMethod);
                if (method.isVarArgs()) {
                    /*
                     * The only version of the polymorphic method that has varargs is the base one.
                     */
                    return substitutionBaseMethod;
                }

                PolymorphicSignatureWrapperMethod wrapperMethod = new PolymorphicSignatureWrapperMethod(substitutionBaseMethod, method);
                SubstitutionMethod substitutionMethod = new SubstitutionMethod(method, wrapperMethod, false, false);
                synchronized (methodSubstitutions) {
                    /*
                     * It may happen that, during analysis, two threads are trying to register the
                     * same variant of a polymorphic method simultaneously. This check ensures that
                     * when this happens, the variant is registered only once and both lookups
                     * return the same substitution.
                     */
                    ResolvedJavaMethod currentSubstitution = methodSubstitutions.get(method);
                    if (currentSubstitution != null) {
                        return currentSubstitution;
                    }
                    register(methodSubstitutions, wrapperMethod, method, substitutionMethod);
                }

                return substitutionMethod;
            }
        }
        return method;
    }

    /**
     * A method is deleted when it was recorded directly in {@link #deleteAnnotations}, or when its
     * declaring type is deleted. In report-at-runtime mode, deleted types are also expanded into
     * member deletions. In fail-fast mode, the type-level entry is the only representation. Methods
     * whose declaring type is fully substituted are also implicitly deleted when no replacement
     * method was provided.
     */
    public boolean isDeleted(ResolvedJavaMethod method) {
        return deleteAnnotations.get(method) != null || isDeleted(method.getDeclaringClass()) || isImplicitlyDeleted(method);
    }

    /**
     * When an entire type is fully substituted, for example when {@link Class} is replaced with
     * {@link DynamicHub}, we replace all methods of the original type. All the methods that are
     * not @{@link Substitute} are implicitly considered as @{@link Delete}. However, not all
     * implicitly deleted methods are present in the {@link #deleteAnnotations} map because when the
     * original class methods are iterated {@link Class#getDeclaredMethods()} applies
     * {@link Reflection#filterMethods(Class, java.lang.reflect.Method[])} and excludes several methods from
     * reflection access.
     */
    private boolean isImplicitlyDeleted(ResolvedJavaMethod method) {
        /*
         * If a method's type is fully substituted but the method was not substituted, then it is
         * considered implicitly deleted.
         */
        return typeSubstitutions.get(method.getDeclaringClass()) instanceof SubstitutionType && !methodSubstitutions.containsKey(method);
    }

    /**
     * Eagerly register all target fields of recomputed value fields as unsafe accessed.
     */
    public void registerUnsafeAccessedFields(BigBang bb) {
        for (var entry : unsafeAccessedFields.entrySet()) {
            AnalysisField targetField = bb.getUniverse().lookup(entry.getKey());
            assert !GuestAnnotationAccess.isAnnotationPresent(targetField, Delete.class);
            targetField.registerAsUnsafeAccessed(entry.getValue());
        }
        /* Prevent later additions that would go unnoticed. */
        unsafeAccessedFields = null;
    }

    public void init(FieldValueInterceptionSupport newFieldValueInterceptionSupport) {
        /*
         * Cannot set this field in the constructor due to cyclic dependencies between the two
         * classes.
         */
        this.fieldValueInterceptionSupport = newFieldValueInterceptionSupport;

        List<ResolvedJavaType> annotatedClasses = findTargetClasses();

        /* Sort by name to make processing order predictable for debugging. */
        annotatedClasses.sort(Comparator.comparing(ResolvedJavaType::getName));

        for (ResolvedJavaType annotatedClass : annotatedClasses) {
            handleClass(annotatedClass);
        }
    }

    protected List<ResolvedJavaType> findTargetClasses() {
        return imageClassLoader.guestTypes.findAnnotatedTypes(TargetClass.class, false);
    }

    protected void handleClass(ResolvedJavaType annotatedType) {
        guarantee(Modifier.isFinal(annotatedType.getModifiers()) || annotatedType.isInterface(), "Annotated class must be final: %s", annotatedType);
        guarantee(annotatedType.isInterface() || annotatedType.getSuperclass().equals(guestAccess.elements.java_lang_Object), "Annotated class must inherit directly from Object: %s", annotatedType);
        guarantee(JVMCIReflectionUtil.getDeclaringType(annotatedType) == null || Modifier.isStatic(JVMCIReflectionUtil.getJavaLanguageModifiers(annotatedType)),
                        "Annotated class must be a static inner class, or a top-level class: %s", annotatedType);
        boolean platformSupported = imageClassLoader.guestTypes.isPlatformSupported(annotatedType,
                        ImageSingletons.lookup(Platform.class)) == ImageClassLoader.PlatformSupportResult.YES;
        if (!platformSupported) {
            return;
        }

        boolean userSubstitution = imageClassLoader.guestTypes.isUserType(annotatedType);
        if (NativeImageOptions.compatibilityMode() && userSubstitution) {
            return;
        }

        TargetClassGuestValue targetClassAnnotation = TargetClassGuestValue.get(annotatedType);
        ResolvedJavaType originalType = findTargetClass(annotatedType, targetClassAnnotation);
        if (originalType == null) {
            return;
        }

        /*
         * The annotatedClass is never used directly, i.e., never wrapped in an AnalysisType. So we
         * need to ensure manually here that its static initializer runs.
         */
        classInitializationSupport.forceInitializeHosted(annotatedType, "substitutions are always initialized", false);

        AnnotationValue deleteAnnotation = lookupAnnotation(annotatedType, Delete.class);
        AnnotationValue substituteAnnotation = lookupAnnotation(annotatedType, Substitute.class);

        int numAnnotations = (deleteAnnotation != null ? 1 : 0) + (substituteAnnotation != null ? 1 : 0);
        guarantee(numAnnotations <= 1, "Only one of @Delete or @Substitute can be used: %s", annotatedType.toClassName());

        if (deleteAnnotation != null) {
            handleDeletedClass(originalType, deleteAnnotation);
        } else if (substituteAnnotation != null) {
            handleSubstitutionClass(annotatedType, originalType, userSubstitution);
        } else {
            handleAliasClass(annotatedType, originalType, targetClassAnnotation, userSubstitution);
        }
    }

    private static String substitutionName(ResolvedJavaType originalType) {
        return "Target_" + originalType.toClassName().replace('.', '_').replace('$', '_');
    }

    private void handleAliasClass(ResolvedJavaType annotated, ResolvedJavaType original, TargetClassGuestValue targetClassAnnotation, boolean userSubstitution) {
        if (VerifyNamingConventions.getValue() && targetClassAnnotation.classNameProvider().equals(metaAccess.lookupJavaType(TargetClass.NoClassNameProvider.class))) {
            String expectedName = substitutionName(original);
            // Checkstyle: allow Class.getSimpleName
            String actualName = JVMCIReflectionUtil.getSimpleName(annotated);
            // Checkstyle: disallow Class.getSimpleName
            guarantee(actualName.equals(expectedName) || actualName.startsWith(expectedName + "_"),
                            "Naming convention violation: %s must be named %s or %s_<suffix>", annotated, expectedName, expectedName);
        }

        guarantee(!typeSubstitutions.containsKey(annotated), "Already registered: %s", annotated);
        typeSubstitutions.put(annotated, original);

        /* The aliases for array types are registered on demand. */

        for (ResolvedJavaMethod annotatedMethod : annotated.getDeclaredMethods()) {
            handleMethodInAliasClass(annotatedMethod, original, userSubstitution);
        }
        for (ResolvedJavaMethod annotatedMethod : annotated.getDeclaredConstructors()) {
            handleMethodInAliasClass(annotatedMethod, original, userSubstitution);
        }
        for (ResolvedJavaField annotatedField : annotated.getStaticFields()) {
            handleFieldInAliasClass(annotatedField, original);
        }
        for (ResolvedJavaField annotatedField : annotated.getInstanceFields(false)) {
            handleFieldInAliasClass(annotatedField, original);
        }
    }

    private void handleMethodInAliasClass(ResolvedJavaMethod annotated, ResolvedJavaType originalType, boolean userSubstitution) {
        if (skipExcludedPlatform(annotated) || annotated.isSynthetic()) {
            return;
        }

        AnnotationValue deleteAnnotation = lookupAnnotation(annotated, Delete.class);
        SubstituteGuestValue substituteAnnotation = SubstituteGuestValue.get(annotated);
        AnnotationValue annotateOriginalAnnotation = lookupAnnotation(annotated, AnnotateOriginal.class);
        AnnotationValue aliasAnnotation = lookupAnnotation(annotated, Alias.class);

        int numAnnotations = (deleteAnnotation != null ? 1 : 0) + (substituteAnnotation != null ? 1 : 0) + (annotateOriginalAnnotation != null ? 1 : 0) + (aliasAnnotation != null ? 1 : 0);
        if (numAnnotations == 0) {
            if (!annotated.isConstructor() && annotated.getName().startsWith("lambda$")) {
                String targetClass = annotated.getDeclaringClass().toClassName();
                String[] methodNameParts = annotated.getName().split("[$]");
                String method = methodNameParts.length > 1 ? methodNameParts[1] : annotated.getName();

                throw UserError.abort("Lambda usage detected in the substitution method: %s#%s. Lambdas are not supported inside" +
                                " substitution methods. To fix the issue, replace the culprit lambda with an equivalent anonymous class.", targetClass, method);
            }
            guarantee(annotated.isConstructor(), "One of @Delete, @Substitute, @AnnotateOriginal, or @Alias must be used: %s", annotated);
            return;
        }
        guarantee(numAnnotations == 1, "Only one of @Delete, @Substitute, @AnnotateOriginal, or @Alias can be used: %s", annotated);

        ResolvedJavaMethod original = findOriginalMethod(annotated, originalType);
        if (original == null) {
            /* Optional target that is not present, so nothing to do. */
            return;
        }

        if (!disabledSubstitutions.isEmpty()) {
            /*
             * Substitutions can be disabled on the command line. The three formats to match are
             * specified in the help text of the option DisableSubstitution.
             */
            if (disabledSubstitutions.contains(annotated.format("%H")) ||
                            disabledSubstitutions.contains(annotated.format("%H.%n")) ||
                            disabledSubstitutions.contains(annotated.format("%H.%n(%P)"))) {
                return;
            }
        }

        if (deleteAnnotation != null) {
            if (SubstrateOptions.VerifyNamingConventions.getValue()) {
                int modifiers = original.getModifiers();
                if (Modifier.isProtected(modifiers) || Modifier.isPublic(modifiers)) {
                    String format = "Detected a public or protected method annotated with @Delete: %s. " +
                                    "Such usages of @Delete are not permited since these methods can be called " +
                                    "from third party code and can lead to unsupported features. " +
                                    "Instead the method should be replaced with a @Substitute method and `throw VMError.unsupportedFeature()`.";
                    throw UserError.abort(format, annotated);
                }
            }
            registerAsDeleted(annotated, original, deleteAnnotation);
        } else if (substituteAnnotation != null) {
            if (GuestAnnotationAccess.isAnnotationPresent(annotated, guestAccess.elements.Uninterruptible) && !isEffectivelyFinal(original)) {
                throw UserError.abort("@Uninterruptible may only be combined with @Substitute if the original method is effectively final: %s", annotated);
            }

            SubstitutionMethod substitution = new SubstitutionMethod(original, annotated, false, userSubstitution);
            if (substituteAnnotation.polymorphicSignature()) {
                register(polymorphicMethodSubstitutions, annotated, original, substitution);
            }
            register(methodSubstitutions, annotated, original, substitution);
        } else if (annotateOriginalAnnotation != null) {
            if (GuestAnnotationAccess.isAnnotationPresent(annotated, guestAccess.elements.Uninterruptible) && !isEffectivelyFinal(original)) {
                throw UserError.abort("@Uninterruptible may only be combined with @AnnotateOriginal if the original method is effectively final: %s", annotated);
            }

            AnnotatedMethod substitution = new AnnotatedMethod(original, annotated);
            register(methodSubstitutions, annotated, original, substitution);
        } else if (aliasAnnotation != null) {
            register(methodSubstitutions, annotated, original, original);
        }
    }

    private static boolean isEffectivelyFinal(ResolvedJavaMethod original) {
        return original.isPrivate() || original.isStatic() || original.isFinalFlagSet() || original.getDeclaringClass().isFinalFlagSet();
    }

    private boolean skipExcludedPlatform(Annotated annotatedElement) {
        return imageClassLoader.guestTypes.isPlatformSupported(annotatedElement, ImageSingletons.lookup(Platform.class)) != ImageClassLoader.PlatformSupportResult.YES;
    }

    private void handleFieldInAliasClass(ResolvedJavaField annotated, ResolvedJavaType originalType) {
        /* Assertion status fields must be mapped so runtime-initialized alias classes can retain their assertion code. */
        boolean assertionStatusField = annotated.isSynthetic() && annotated.getName().startsWith(AssertionsSupport.SYNTHETIC_ASSERTIONS_DISABLED_FIELD_NAME);
        if (skipExcludedPlatform(annotated) || (annotated.isSynthetic() && !assertionStatusField)) {
            return;
        }

        AnnotationValue deleteAnnotation = lookupAnnotation(annotated, Delete.class);
        AnnotationValue aliasAnnotation = lookupAnnotation(annotated, Alias.class);
        AnnotationValue injectAnnotation = lookupAnnotation(annotated, Inject.class);

        int numAnnotations = (deleteAnnotation != null ? 1 : 0) + (aliasAnnotation != null ? 1 : 0) + (injectAnnotation != null ? 1 : 0);
        if (numAnnotations == 0) {
            guarantee(annotated.getName().startsWith(AssertionsSupport.SYNTHETIC_ASSERTIONS_DISABLED_FIELD_NAME), "One of @Delete, @Alias, or @Inject must be used: %s", annotated);
            /*
             * The field $assertionsDisabled can be present in the original class, but does not have
             * to be. We treat it like an optional @Alias field without field value recomputation.
             */
            ResolvedJavaField original = findOriginalField(annotated, originalType, true);
            if (original != null) {
                register(fieldSubstitutions, annotated, null, original);
            }
            return;
        }
        guarantee(numAnnotations == 1, "Only one of @Delete, @Alias, or @Inject can be used: %s", annotated);

        if (injectAnnotation != null) {
            guarantee(!annotated.isStatic(), "@Inject field must not be static: %s", annotated);

            ResolvedJavaField injected = fieldValueRecomputation(originalType, annotated, annotated);
            register(fieldSubstitutions, annotated, null, injected);

            InjectedFieldsType substitution;
            if (typeSubstitutions.get(originalType) instanceof InjectedFieldsType) {
                substitution = (InjectedFieldsType) typeSubstitutions.get(originalType);
                register(typeSubstitutions, annotated.getDeclaringClass(), originalType, substitution);
            } else {
                substitution = new InjectedFieldsType(originalType);
                register(typeSubstitutions, annotated.getDeclaringClass(), originalType, substitution);
            }
            substitution.addInjectedField(injected);

        } else {
            ResolvedJavaField original = findOriginalField(annotated, originalType, false);
            if (original == null) {
                return;
            }

            guarantee(annotated.isStatic() == original.isStatic(), "Static modifier mismatch: %s, %s", annotated, original);
            guarantee(annotated.getJavaKind() == original.getJavaKind(), "Type mismatch: %s, %s", annotated, original);

            RecomputeFieldValueGuestValue recomputeAnnotation = RecomputeFieldValueGuestValue.get(annotated);
            if (annotated.isStatic() && (recomputeAnnotation == null || recomputeAnnotation.kind() != RecomputeFieldValue.Kind.FromAlias)) {
                guarantee(hasDefaultValue(annotated), "The value assigned to a static @Alias field is ignored unless @RecomputeFieldValue with kind=FromAlias is used: %s", annotated);
            }
            guarantee(!annotated.isFinal(), "The `final` modifier for the @Alias field is ignored and therefore misleading: %s", annotated);

            if (deleteAnnotation != null) {
                registerAsDeleted(annotated, original, deleteAnnotation);
            } else {
                ResolvedJavaField computedAlias = fieldValueRecomputation(originalType, original, annotated);

                ResolvedJavaField existingAlias = fieldSubstitutions.get(original);
                ResolvedJavaField alias = computedAlias;
                if (existingAlias != null) {
                    /*
                     * Allow multiple @Alias definitions for the same field as long as only one of
                     * them has a @RecomputeValueField annotation.
                     */
                    if (computedAlias.equals(original) || isCompatible(computedAlias, existingAlias)) {
                        /*
                         * The currently processed field does not have a @RecomputeValueField
                         * annotation. Use whatever alias was registered previously.
                         */
                        alias = existingAlias;
                    } else if (existingAlias.equals(original)) {
                        /*
                         * The alias registered previously does not have a @RecomputeValueField
                         * annotation. We need to patch the previous registration. But we do not
                         * know which annotated field that was, so we need to iterate the whole
                         * field substitution registry and look for the matching value.
                         */
                        fieldSubstitutions.replaceAll((_, value) -> value.equals(existingAlias) ? computedAlias : value);
                    } else {
                        /*
                         * Both the current and the previous registration have
                         * a @RecomputeValueField annotation or there is some other mismatch. Let
                         * the register() call below report an error.
                         */
                    }
                }

                register(fieldSubstitutions, annotated, original, alias);
            }
        }
    }

    private static boolean isCompatible(ResolvedJavaField computedAlias, ResolvedJavaField existingAlias) {
        /* The only use case at the moment are multiple @Alias definitions for a final field. */
        if (computedAlias instanceof AliasField computed && existingAlias instanceof AliasField existing) {
            return computed.original.equals(existing.original) && computed.isFinal == existing.isFinal;
        }
        return false;
    }

    private static boolean hasDefaultValue(ResolvedJavaField annotatedField) {
        annotatedField.getDeclaringClass().initialize();
        JavaConstant value = GuestAccess.get().getProviders().getConstantReflection().readFieldValue(annotatedField, null);
        return value.isDefaultForKind();
    }

    private void handleDeletedClass(ResolvedJavaType type, AnnotationValue deleteAnnotation) {
        if (reportUnsupportedElementAtRuntime) {

            try {
                type.link();
            } catch (LinkageError ignored) {
                /*
                 * Ignore any linking errors. A type that cannot be linked doesn't need elements
                 * replaced: it will simply fail at runtime with the same linkage error before
                 * reaching those elements.
                 */
                return;
            }

            /*
             * We register all methods and fields as deleted. That still allows usage of the type in
             * type checks.
             */
            for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
                registerAsDeleted(null, method, deleteAnnotation);
            }
            for (ResolvedJavaMethod constructor : type.getDeclaredConstructors()) {
                registerAsDeleted(null, constructor, deleteAnnotation);
            }
            for (ResolvedJavaField f : type.getInstanceFields(false)) {
                registerAsDeleted(null, f, deleteAnnotation);
            }
            for (ResolvedJavaField f : type.getStaticFields()) {
                registerAsDeleted(null, f, deleteAnnotation);
            }
        }
        deleteAnnotations.put(type, deleteAnnotation);
    }

    private void registerAsDeleted(ResolvedJavaMethod annotated, ResolvedJavaMethod original, AnnotationValue deleteAnnotation) {
        if (reportUnsupportedElementAtRuntime) {
            register(methodSubstitutions, annotated, original, new DeletedMethod(original, deleteAnnotation));
        }
        deleteAnnotations.put(original, deleteAnnotation);
        deleteAnnotations.put(annotated, deleteAnnotation);
    }

    private void registerAsDeleted(ResolvedJavaField annotated, ResolvedJavaField original, AnnotationValue deleteAnnotation) {
        if (reportUnsupportedElementAtRuntime) {
            AnnotatedField annotatedField = new AnnotatedField(original, deleteAnnotation);
            register(fieldSubstitutions, annotated, original, annotatedField);
            fieldValueInterceptionSupport.registerFieldValueTransformer(original, null, new ValueNeverAvailableFieldValueTransformer(annotatedField));
        }
        deleteAnnotations.put(original, deleteAnnotation);
        deleteAnnotations.put(annotated, deleteAnnotation);
    }

    /**
     *
     * @param substitute a {@link Substitute} annotated class whose {@link TargetElement}
     *            annotation denotes {@code original}
     * @param userSubstitution is the substitution coming from the classpath or the module path
     */
    private void handleSubstitutionClass(ResolvedJavaType substitute, ResolvedJavaType original, boolean userSubstitution) {
        // Not sure what happens if the target class is in a hierarchy - so prohibit that for now.
        guarantee(substitute.isInterface() == original.isInterface(), "if original is interface, target must also be interface: %s", substitute);
        guarantee(original.isInterface() || original.getSuperclass().equals(guestAccess.elements.java_lang_Object), "target class must inherit directly from Object: %s", original);

        boolean keepOriginalElements = lookupAnnotation(substitute, KeepOriginal.class) != null;

        SubstitutionType substitution = new SubstitutionType(original, substitute, userSubstitution);
        register(typeSubstitutions, substitute, original, substitution);

        registerArrayTypes(original, substitute, userSubstitution);

        for (ResolvedJavaMethod method : substitute.getDeclaredMethods()) {
            handleAnnotatedMethodInSubstitutionClass(method, original, userSubstitution);
        }
        for (ResolvedJavaMethod constructor : substitute.getDeclaredConstructors()) {
            handleAnnotatedMethodInSubstitutionClass(constructor, original, userSubstitution);
        }
        for (ResolvedJavaMethod method : original.getDeclaredMethods()) {
            handleOriginalMethodInSubstitutionClass(method, keepOriginalElements);
        }
        for (ResolvedJavaMethod constructor : original.getDeclaredConstructors()) {
            handleOriginalMethodInSubstitutionClass(constructor, keepOriginalElements);
        }

        for (ResolvedJavaField field : JVMCIReflectionUtil.getAllFields(substitute)) {
            if (field.isInternal()) {
                continue;
            }
            ResolvedJavaField alias = fieldValueRecomputation(substitute, field, field);
            if (!alias.equals(field)) {
                ResolvedJavaField originalField = findOriginalField(field, original, true);
                guarantee(originalField == null || !(alias.isFinal() && !originalField.isFinal()), "a non-final field cannot be redeclared as final through substitution: %s", field);
                register(fieldSubstitutions, field, originalField, alias);
            } else {
                handleAnnotatedFieldInSubstitutionClass(field, original, userSubstitution);
            }
        }

        for (ResolvedJavaField field : JVMCIReflectionUtil.getAllFields(original)) {
            handleOriginalFieldInSubstitutionClass(field, keepOriginalElements, substitution);
        }
    }

    /**
     * Registers the array types for element type {@code original} up to
     * {@link #SUBSTITUTE_ARRAY_DIMENSIONS} dimensions.
     *
     * @param substitute the substitute class for {@code original}
     * @param userSubstitution is the substitution coming from the classpath or the module path
     */
    private void registerArrayTypes(ResolvedJavaType original, ResolvedJavaType substitute, boolean userSubstitution) {
        ResolvedJavaType originalArray = original;
        ResolvedJavaType substituteArray = substitute;
        for (int i = 1; i <= SUBSTITUTE_ARRAY_DIMENSIONS; i++) {
            originalArray = originalArray.getArrayClass();
            substituteArray = substituteArray.getArrayClass();
            SubstitutionType arrayTypeSubstitution = new SubstitutionType(originalArray, substituteArray, userSubstitution);
            register(typeSubstitutions, substituteArray, originalArray, arrayTypeSubstitution);
        }
    }

    private void handleAnnotatedMethodInSubstitutionClass(ResolvedJavaMethod annotated, ResolvedJavaType originalType, boolean userSubstitution) {
        if (skipExcludedPlatform(annotated)) {
            return;
        }

        if (annotated.isSynthetic()) {
            /*
             * Synthetic bridge methods for co-variant return types inherit the annotations. We
             * ignore such methods here, and handleOriginalMethodInSubstitutionClass keeps the
             * original implementation of such methods.
             */
            return;
        }

        SubstituteGuestValue substituteAnnotation = SubstituteGuestValue.get(annotated);
        AnnotationValue keepOriginalAnnotation = lookupAnnotation(annotated, KeepOriginal.class);

        int numAnnotations = (substituteAnnotation != null ? 1 : 0) + (keepOriginalAnnotation != null ? 1 : 0);
        if (numAnnotations == 0) {
            /* Unannotated method in substitution class: a regular method, nothing to do. */
            return;
        }
        guarantee(numAnnotations == 1, "only one of @Substitute or @KeepOriginal can be used: %s", annotated);

        ResolvedJavaMethod original = findOriginalMethod(annotated, originalType);

        if (original == null) {
            /* Optional target that is not present, so nothing to do. */
        } else if (substituteAnnotation != null) {
            SubstitutionMethod substitution = new SubstitutionMethod(original, annotated, true, userSubstitution);
            if (substituteAnnotation.polymorphicSignature()) {
                register(polymorphicMethodSubstitutions, annotated, original, substitution);
            }
            register(methodSubstitutions, annotated, original, substitution);
        } else if (keepOriginalAnnotation != null) {
            register(methodSubstitutions, annotated, original, original);
        }
    }

    private void handleAnnotatedFieldInSubstitutionClass(ResolvedJavaField annotated, ResolvedJavaType originalType, boolean userSubstitution) {
        if (skipExcludedPlatform(annotated)) {
            return;
        }

        AnnotationValue substituteAnnotation = lookupAnnotation(annotated, Substitute.class);

        if (substituteAnnotation == null) {
            /* Unannotated field in substitution class: a regular field, nothing to do. */
            return;
        }

        ResolvedJavaField original = findOriginalField(annotated, originalType, false);

        if (original == null) {
            /* Optional target that is not present, so nothing to do. */
        } else {
            register(fieldSubstitutions, annotated, original, new SubstitutionField(original, annotated, userSubstitution));
        }
    }

    private void handleOriginalMethodInSubstitutionClass(ResolvedJavaMethod method, boolean keepOriginalElements) {
        if (!methodSubstitutions.containsKey(method)) {
            if (keepOriginalElements || method.isSynthetic()) {
                /*
                 * Synthetic methods are mostly methods generated by javac to access private fields
                 * from inner classes. The naming is not fixed, and it would be tedious anyway to
                 * manually mark such methods as @KeepOriginal. We therefore treat all synthetic
                 * methods as if they were annotated with @KeepOriginal. If the method/field that
                 * the synthetic method is forwarding to is not available, an error message for that
                 * method/field will be produced anyway.
                 *
                 * This also treats synthetic bridge methods as @KeepOriginal, so that
                 * handleAnnotatedMethodInSubstitutionClass does not need to handle them.
                 */
                register(methodSubstitutions, null, method, method);
            } else {
                registerAsDeleted(null, method, substitutionDelete);
            }
        }
    }

    private void handleOriginalFieldInSubstitutionClass(ResolvedJavaField field, boolean keepOriginalElements, SubstitutionType substitution) {
        if (!fieldSubstitutions.containsKey(field)) {
            if (keepOriginalElements || field.isSynthetic()) {
                register(fieldSubstitutions, null, field, field);
                if (!field.isStatic()) {
                    substitution.addInstanceField(field);
                }
            } else {
                registerAsDeleted(null, field, substitutionDelete);
            }
        }
    }

    private ResolvedJavaMethod findOriginalMethod(ResolvedJavaMethod annotatedMethod, ResolvedJavaType originalType) {
        String originalName = findOriginalElementName(annotatedMethod, originalType);
        if (originalName == null) {
            return null;
        }

        ResolvedJavaType[] originalParams = interceptParameterTypes(annotatedMethod);

        try {
            if (!annotatedMethod.isConstructor() && !originalName.equals(TargetElement.CONSTRUCTOR_NAME)) {
                ResolvedJavaType targetReturnType = interceptParameterType(annotatedMethod.getSignature().getReturnType(annotatedMethod.getDeclaringClass()), annotatedMethod.getDeclaringClass());
                ResolvedJavaMethod originalMethod = JVMCIReflectionUtil.getDeclaredMethod(true, originalType, originalName, targetReturnType, originalParams);
                if (originalMethod == null) {
                    ResolvedJavaMethod originalMethodWithDifferentReturnType = JVMCIReflectionUtil.getUniqueDeclaredMethod(true, originalType, originalName, originalParams);
                    guarantee(originalMethodWithDifferentReturnType == null,
                                    "Return type mismatch:%n    %s%n    %s", annotatedMethod, originalMethodWithDifferentReturnType);
                    throw UserError.abort("Could not find target method: %s", annotatedMethod);
                }

                guarantee(annotatedMethod.isStatic() == originalMethod.isStatic(), "Static modifier mismatch: %s, %s", annotatedMethod, originalMethod);
                return originalMethod;

            } else {
                guarantee(!annotatedMethod.isStatic(), "Constructor Alias method %s must not be static", annotatedMethod);
                ResolvedJavaMethod originalMethod = JVMCIReflectionUtil.getDeclaredConstructor(true, originalType, originalParams);
                if (originalMethod == null) {
                    throw UserError.abort("Could not find target method: %s", annotatedMethod);
                }
                return originalMethod;
            }

        } catch (LinkageError error) {
            throw UserError.abort("Cannot find %s.%s, %s can not be loaded, due to %s not being available in the classpath. Are you missing a dependency in your classpath?",
                            originalType.toClassName(), originalName, originalType.toClassName(), error.getMessage());
        }
    }

    private ResolvedJavaField findOriginalField(ResolvedJavaField annotatedField, ResolvedJavaType originalType, boolean forceOptional) {
        String originalName = findOriginalElementName(annotatedField, originalType);
        if (originalName == null) {
            return null;
        }

        /*
         * This lookup is specific to substitutions: static aliases only consider declared fields,
         * while instance aliases also fall back to inherited fields.
         */
        ResolvedJavaField originalField = null;
        ResolvedJavaField[] fields = annotatedField.isStatic() ? originalType.getStaticFields() : originalType.getInstanceFields(false);
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals(originalName)) {
                originalField = field;
                break;
            }
        }
        if (originalField == null && !annotatedField.isStatic()) {
            for (ResolvedJavaField field : originalType.getInstanceFields(true)) {
                if (field.getName().equals(originalName)) {
                    originalField = field;
                    break;
                }
            }
        }
        if (originalField == null) {
            guarantee(forceOptional, "could not find target field: %s", annotatedField);
            return null;
        }

        ResolvedJavaType targetType = getTargetType(annotatedField.getType().resolve(annotatedField.getDeclaringClass()));
        ResolvedJavaType originalFieldType = originalField.getType().resolve(originalField.getDeclaringClass());
        guarantee(targetType.equals(originalFieldType),
                        "Type mismatch:%n    %s %s%n    %s %s", annotatedField.getType(), annotatedField, originalField.getType(), originalField);

        return originalField;
    }

    private static String findOriginalElementName(ResolvedJavaField annotatedField, ResolvedJavaType originalType) {
        TargetElementGuestValue targetElementAnnotation = TargetElementGuestValue.get(annotatedField);
        if (!isIncluded(targetElementAnnotation, originalType, annotatedField)) {
            return null;
        }
        return targetElementAnnotation == null || targetElementAnnotation.name().isEmpty() ? annotatedField.getName() : targetElementAnnotation.name();
    }

    static String findOriginalElementName(ResolvedJavaMethod annotatedMethod, ResolvedJavaType originalType) {
        TargetElementGuestValue targetElementAnnotation = TargetElementGuestValue.get(annotatedMethod);
        if (!isIncluded(targetElementAnnotation, originalType, annotatedMethod)) {
            return null;
        }
        return targetElementAnnotation == null || targetElementAnnotation.name().isEmpty() ? annotatedMethod.getName() : targetElementAnnotation.name();
    }

    public static boolean isIncluded(Annotated annotated, ResolvedJavaType originalType, Object context) {
        return isIncluded(TargetElementGuestValue.get(annotated), originalType, context);
    }

    private static boolean isIncluded(TargetElementGuestValue targetElementAnnotation, ResolvedJavaType originalType, Object context) {
        if (targetElementAnnotation == null) {
            return true;
        }
        return SVMHost.evaluateOnlyWith(targetElementAnnotation.onlyWith(), context.toString(), originalType);
    }

    /**
     * Registers a mapping between annotated, original, and target objects in the provided
     * substitutions map. Ensures that no conflicting substitutions are added, preserving
     * consistency.
     *
     * @param substitutions The map where substitutions are maintained. Maps objects of type
     *            {@code T} to their substitutions.
     * @param annotated The annotated object to be mapped to the target. May be null.
     * @param original The original object to be mapped to the target or itself. May be null.
     * @param target The target object to map annotated and original to.
     * @param <T> A type that extends {@code ModifiersProvider}.
     * @throws IllegalArgumentException If attempting to add a conflicting substitution.
     */
    private static <T extends ModifiersProvider> void register(Map<T, T> substitutions, T annotated, T original, T target) {
        if (annotated != null) {
            guarantee(!substitutions.containsKey(annotated) || substitutions.get(annotated).equals(original) || substitutions.get(annotated).equals(target),
                            "Substitution: %s -> %s conflicts with previously registered: %s", annotated, target, substitutions.get(annotated));
            substitutions.put(annotated, target);
        }
        if (original != null) {
            boolean isMethodAlias = original == target && original instanceof ResolvedJavaMethod;
            /*
             * if there was already a substitution, and we are only adding a self-mapping for an
             * alias, skip that self mapping in favor of the substitution.
             */
            if (!isMethodAlias || substitutions.get(original) == null) {
                guarantee(!substitutions.containsKey(original) || substitutions.get(original).equals(original) || substitutions.get(original).equals(target),
                                "Substitution: %s -> %s conflicts with previously registered: %s", original, target, substitutions.get(original));
                substitutions.put(original, target);
            } else {
                // GR-74443
                ResolvedJavaMethod originalMethod = (ResolvedJavaMethod) original;
                if (!original.isStatic() && !originalMethod.isConstructor() && !originalMethod.isPrivate()) {
                    ResolvedJavaMethod aliasMethod = (ResolvedJavaMethod) Objects.requireNonNull(annotated);
                    ResolvedJavaMethod targetMethod = (ResolvedJavaMethod) target;
                    UserError.abort("Cannot have both an alias and a substitution to a non-static, non-<init>, non-private method: %s -> %s",
                                    aliasMethod.format("%H.%n(%p)"),
                                    targetMethod.format("%H.%n(%p)"));
                }
            }
        }
    }

    private ResolvedJavaField fieldValueRecomputation(ResolvedJavaType originalType, ResolvedJavaField original, ResolvedJavaField annotated) {
        RecomputeFieldValueGuestValue recomputeAnnotation = RecomputeFieldValueGuestValue.get(annotated);
        AnnotationValue injectAccessorsAnnotation = lookupAnnotation(annotated, InjectAccessors.class);

        int numAnnotations = (recomputeAnnotation != null ? 1 : 0) + (injectAccessorsAnnotation != null ? 1 : 0);
        guarantee(numAnnotations <= 1, "Only one of @RecomputeFieldValue or @InjectAccessors can be used: %s", annotated);

        if (injectAccessorsAnnotation != null) {
            AnnotatedField result = new AnnotatedField(original, injectAccessorsAnnotation);
            fieldValueInterceptionSupport.registerFieldValueTransformer(original, null, new ValueNeverAvailableFieldValueTransformer(result));
            return result;
        }
        if (recomputeAnnotation == null && !original.isFinal()) {
            return original;
        }

        RecomputeFieldValue.Kind kind = RecomputeFieldValue.Kind.None;
        ResolvedJavaType targetType = originalType;
        String targetName = "";
        boolean isFinal = original.isFinal() && annotated.isFinal();

        if (recomputeAnnotation != null) {
            kind = recomputeAnnotation.kind();
            targetName = recomputeAnnotation.name();
            isFinal = recomputeAnnotation.isFinal();
            guarantee(!isFinal || (kind != RecomputeFieldValue.Kind.FieldOffset && kind != RecomputeFieldValue.Kind.TranslateFieldOffset && kind != RecomputeFieldValue.Kind.AtomicFieldUpdaterOffset),
                            "@%s with %s can never be final during analysis: unset isFinal in the annotation on %s",
                            RecomputeFieldValue.class.getSimpleName(), kind, annotated);
            if (!recomputeAnnotation.declClass().equals(guestAccess.lookupType(RecomputeFieldValue.class))) {
                guarantee(recomputeAnnotation.declClassName().isEmpty(), "Both class and class name specified");
                targetType = recomputeAnnotation.declClass();
            } else if (!recomputeAnnotation.declClassName().isEmpty()) {
                targetType = imageClassLoader.guestTypes.findType(recomputeAnnotation.declClassName()).getOrFail();
            }
        }
        ResolvedJavaType transformedValueAllowedType = getTargetType(annotated.getType().resolve(annotated.getDeclaringClass()));

        JVMCIFieldValueTransformer newTransformer = switch (kind) {
            case None, Manual -> null;
            case Reset -> ConstantValueFieldValueTransformer.defaultValueForField(original);
            case NewInstance -> new NewInstanceOfFixedClassFieldValueTransformer(targetType, false);
            case NewInstanceWhenNotNull -> new NewInstanceOfFixedClassFieldValueTransformer(targetType, true);
            case FromAlias -> {
                if (!Modifier.isStatic(annotated.getModifiers())) {
                    throw UserError.abort("Cannot use " + kind + " on non-static alias " + annotated.format("%H.%n"));
                }
                yield new FromAliasFieldValueTransformer(annotated);
            }
            case FieldOffset -> {
                var targetField = getField(annotated, targetType, targetName);
                unsafeAccessedFields.put(targetField, original);
                yield new FieldOffsetFieldValueTransformer(targetField, original.getType().getJavaKind());
            }
            case StaticFieldBase -> {
                var targetField = getField(annotated, targetType, targetName);
                if (!Modifier.isStatic(targetField.getModifiers())) {
                    throw UserError.abort("Target field must be static for " + kind + " computation of alias " + annotated.format("%H.%n"));
                }
                yield new StaticFieldBaseFieldValueTransformer(targetField);
            }
            case ArrayBaseOffset ->
                new ArrayBaseOffsetFieldValueTransformer(targetType, original.getType().getJavaKind());
            case ArrayIndexScale ->
                new ArrayIndexScaleFieldValueTransformer(targetType, original.getType().getJavaKind());
            case ArrayIndexShift ->
                new ArrayIndexShiftFieldValueTransformer(targetType, original.getType().getJavaKind());
            case AtomicFieldUpdaterOffset -> new AtomicFieldUpdaterOffsetFieldValueTransformer(original);
            case TranslateFieldOffset -> new TranslateFieldOffsetFieldValueTransformer(original, targetType);
            case Custom -> {
                if (guestAccess.lookupType(JVMCIFieldValueTransformer.class).isAssignableFrom(targetType)) {
                    /*
                     * In fully isolated mode, this should not be possible because
                     * JVMCIFieldValueTransformer should not be reachable from the guest. This is
                     * just a transitional situation.
                     */
                    Class<?> targetClass = OriginalClassProvider.getJavaClass(targetType);
                    yield (JVMCIFieldValueTransformer) ReflectionUtil.newInstance(targetClass);
                } else {
                    yield WrappedFieldValueTransformer.create(JVMCIReflectionUtil.newInstance(targetType));
                }
            }
        };

        if (newTransformer != null) {
            JVMCIFieldValueTransformer existingTransformer = fieldValueInterceptionSupport.lookupAlreadyRegisteredTransformer(original);
            if (existingTransformer != null) {
                if (existingTransformer.equals(newTransformer)) {
                    /* Equivalent transformations are allowed, nothing to do. */
                } else {
                    throw UserError.abort("Field value recomputation %s conflicts with an already registered field value transformer.", annotated.format("%H.%n"));
                }
            } else {
                fieldValueInterceptionSupport.registerFieldValueTransformer(original, transformedValueAllowedType, newTransformer);
            }
        }

        return new AliasField(original, annotated, isFinal);
    }

    private static ResolvedJavaField getField(ResolvedJavaField annotated, ResolvedJavaType targetType, String targetName) {
        try {
            return JVMCIReflectionUtil.getUniqueDeclaredField(targetType, targetName);
        } catch (NoSuchFieldError e) {
            throw UserError.abort("Could not find target field %s.%s for alias %s.", targetType.toClassName(), targetName, annotated == null ? null : annotated.format("%H.%n"));
        }
    }

    public ResolvedJavaType getTargetType(ResolvedJavaType annotatedType) {
        ResolvedJavaType annotatedBaseType = annotatedType;
        int arrayDepth = 0;
        while (annotatedBaseType.isArray()) {
            arrayDepth++;
            annotatedBaseType = annotatedBaseType.getComponentType();
        }

        TargetClassGuestValue targetClassAnnotation = TargetClassGuestValue.get(annotatedBaseType);
        if (targetClassAnnotation == null) {
            return annotatedType;
        }

        ResolvedJavaType targetType = findTargetClass(annotatedBaseType, targetClassAnnotation);
        for (int i = 0; i < arrayDepth; i++) {
            targetType = targetType.getArrayClass();
        }
        return targetType;
    }

    ResolvedJavaType findTargetClass(ResolvedJavaType annotatedBaseClass, TargetClassGuestValue target) {
        return findTargetClass(annotatedBaseClass, target, true);
    }

    protected ResolvedJavaType findTargetClass(ResolvedJavaType annotatedBaseClass, TargetClassGuestValue target, boolean checkOnlyWith) {
        return findTargetClass(metaAccess.lookupJavaType(TargetClass.class), metaAccess.lookupJavaType(TargetClass.NoClassNameProvider.class),
                        annotatedBaseClass, target.value(), target.className(), target.classNameProvider(), target.innerClass(),
                        checkOnlyWith ? target.onlyWith() : null);
    }

    protected ResolvedJavaType findTargetClass(ResolvedJavaType annotationType, ResolvedJavaType noClassNameProviderType,
                    ResolvedJavaType annotatedBaseType, ResolvedJavaType value, String targetClassName, ResolvedJavaType classNameProviderType,
                    List<String> innerClasses, List<ResolvedJavaType> onlyWith) {
        ResolvedJavaType holder;
        String className;
        if (!value.equals(annotationType)) {
            guarantee(targetClassName.isEmpty(), "Both class and class name specified for substitution");
            guarantee(classNameProviderType.equals(noClassNameProviderType), "Both class and classNameProvider specified for substitution");

            holder = value;
            className = holder.toClassName();
        } else {
            holder = null;
            if (!classNameProviderType.equals(noClassNameProviderType)) {
                className = guestAccess.asHostString(guestAccess.callFunction(classNameProviderType, guestAccess.getAnnotation(annotatedBaseType, annotationType)));
            } else {
                guarantee(!targetClassName.isEmpty(), "Neither class, className, nor classNameProvider specified for substitution");
                className = targetClassName;
            }
        }
        if (onlyWith != null) {
            for (ResolvedJavaType onlyWithType : onlyWith) {
                boolean onlyWithResult;
                if (guestAccess.elements.java_util_function_BooleanSupplier.isAssignableFrom(onlyWithType)) {
                    onlyWithResult = guestAccess.callBooleanSupplier(onlyWithType);
                } else if (guestAccess.elements.java_util_function_Predicate.isAssignableFrom(onlyWithType)) {
                    onlyWithResult = guestAccess.callPredicate(onlyWithType, guestAccess.asGuestString(className));
                } else {
                    throw UserError.abort("Class specified as onlyWith for %s does not implement %s or %s", annotatedBaseType.toJavaName(),
                                    BooleanSupplier.class.getSimpleName(), Predicate.class.getSimpleName());
                }

                if (!onlyWithResult) {
                    return null;
                }
            }
        }

        if (holder == null) {
            var substitutionsClassLoaders = imageClassLoader.classLoaderSupport.getClassLoaders();
            for (var substitutionsClassLoaderConstant : substitutionsClassLoaders) {
                try {
                    JavaConstant targetClass = guestAccess.invokeStatic(guestAccess.elements.java_lang_Class_forName,
                                    guestAccess.asGuestString(className), JavaConstant.FALSE, substitutionsClassLoaderConstant);
                    holder = guestAccess.getProviders().getConstantReflection().asJavaType(targetClass);
                    break;
                } catch (Throwable t) {
                    if (!isClassNotFoundException(t)) {
                        throw t;
                    }
                    if (substitutionsClassLoaderConstant.equals(substitutionsClassLoaders.getLast())) {
                        throw UserError.abort("Substitution target for %s is not loaded. Use field `onlyWith` in the `TargetClass` annotation to make substitution only active when needed.",
                                        annotatedBaseType.toClassName());
                    }
                }
            }
        }
        if (!innerClasses.isEmpty()) {
            for (String innerClass : innerClasses) {
                ResolvedJavaType prevHolder = holder;
                holder = findInnerClass(prevHolder, innerClass);
                if (holder == null) {
                    throw UserError.abort("Substitution target for %s is invalid as inner class %s in %s can not be found. Make sure that the inner class is present.",
                                    annotatedBaseType.toClassName(), innerClass, prevHolder.toClassName());
                }
            }
        }

        return holder;
    }

    private boolean isClassNotFoundException(Throwable throwable) {
        if (throwable instanceof InvocationException invocationException) {
            JavaConstant exceptionObject = invocationException.getExceptionObject();
            return exceptionObject != null && guestAccess.elements.java_lang_ClassNotFoundException.isInstance(exceptionObject);
        }
        /*
         * GuestAccess can unwrap a guest exception in non-isolated host mode, so also recognize the
         * resulting direct ClassNotFoundException.
         */
        return !guestAccess.isFullyIsolated() && throwable instanceof ClassNotFoundException;
    }

    protected ResolvedJavaType findInnerClass(ResolvedJavaType outerClass, String innerClassSimpleName) {
        ResolvedJavaType innerClass = guestAccess.lookupType(outerClass.toClassName() + "$" + innerClassSimpleName);
        return innerClass != null && outerClass.equals(innerClass.getEnclosingType()) ? innerClass : null;
    }

    private ResolvedJavaType interceptParameterType(JavaType type, ResolvedJavaType accessingType) {
        return getTargetType(type.resolve(accessingType));
    }

    private ResolvedJavaType[] interceptParameterTypes(ResolvedJavaMethod method) {
        var signature = method.getSignature();
        ResolvedJavaType[] result = new ResolvedJavaType[signature.getParameterCount(false)];
        for (int i = 0; i < result.length; i++) {
            result[i] = interceptParameterType(signature.getParameterType(i, method.getDeclaringClass()), method.getDeclaringClass());
        }
        return result;
    }

    protected <T extends Annotation> AnnotationValue lookupAnnotation(Annotated element, Class<T> annotationClass) {
        return GuestAnnotationAccess.getAnnotationValue(element, annotationClass);
    }

    protected <T extends Annotation, U> U lookupAnnotation(Annotated element, Class<T> annotationClass, Function<AnnotationValue, U> factory) {
        return factory.apply(lookupAnnotation(element, annotationClass));
    }

    protected static String deleteErrorMessage(Annotated element, AnnotationValue deleteAnnotation, boolean hosted) {
        return deleteErrorMessage(element, deleteAnnotation.getString("value"), hosted);
    }

    public static String deleteErrorMessage(Annotated element, String message, boolean hosted) {
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
            throw VMError.shouldNotReachHere("Unknown @Delete annotated element " + element);
        }
        result.append(" is reachable");
        if (message != null && !message.isEmpty()) {
            result.append(": ").append(message);
        }
        if (hosted) {
            result.append(System.lineSeparator()).append("To diagnose the issue, you can add the option ").append(
                            SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+")).append(
                                            ". The unsupported element is then reported at run time when it is accessed the first time.");
        }
        return result.toString();
    }
}
