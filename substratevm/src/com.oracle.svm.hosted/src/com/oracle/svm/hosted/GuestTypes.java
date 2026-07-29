/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.driver.LayerOptionsSupport;
import com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.LocatableMultiOptionValue;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAnnotationAccess;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * GuestTypes is the central service used during GraalVM Native Image analysis to discover and query
 * the application's guest code: types, methods, and fields that are present on the application
 * class path and module path. This class is to be used (typically via
 * {@link ImageClassLoader#guestTypes}) whenever you want to know about guest types found when
 * scanning the guest module- and class-path. {@link GuestAccess} is the underlying mechanism used
 * during scanning to load the types.
 */
public final class GuestTypes {

    /**
     * Support for loading/resolving types in the guest context.
     */
    private final GuestAccess guestAccess;

    /**
     * The names of types that were discovered during path scanning.
     */
    private final EconomicMap<URI, EconomicSet<String>> discoveredClassNames = EconomicMap.create();

    /**
     * The names of packages containing at least one type that was discovered during path scanning.
     */
    private final EconomicMap<URI, EconomicSet<String>> discoveredPackageNames = EconomicMap.create();

    private final EconomicSet<String> emptySet = EconomicSet.emptySet();

    final EconomicSet<URI> builderURILocations = EconomicSet.create();

    /**
     * Types discovered in containers that belong to the Native Image implementation rather than
     * the application class path or module path.
     */
    private final Set<ResolvedJavaType> builderTypes = ConcurrentHashMap.newKeySet();

    /**
     * The platform of the target image being built.
     */
    private final Platform platform;

    /**
     * The set of types compatible with the {@linkplain #platform target platform} that will
     * potentially end up in the image.
     */
    private final EconomicSet<ResolvedJavaType> applicationTypes = EconomicSet.create();

    /**
     * The set of methods declared by {@link #applicationTypes} that are compatible with the
     * {@linkplain #platform target platform}.
     */
    private final EconomicSet<ResolvedJavaMethod> applicationMethods = EconomicSet.create();

    /**
     * The set of fields declared by {@link #applicationTypes} that are compatible with the
     * {@linkplain #platform target platform}.
     */
    private final EconomicSet<ResolvedJavaField> applicationFields = EconomicSet.create();

    /**
     * The set of hosted-only types loaded from the Native Image class-path and module-path.
     */
    private final EconomicSet<ResolvedJavaType> hostedOnlyTypes = EconomicSet.create();

    private final Set<ResolvedJavaType> typesToPreserve = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> classNamesToPreserve = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Set<ResolvedJavaType> typesToIncludeUnconditionally = ConcurrentHashMap.newKeySet();
    private final Set<String> includedJavaPackages = ConcurrentHashMap.newKeySet();

    public GuestTypes(GuestAccess guestAccess, Platform platform) {
        this.guestAccess = guestAccess;
        this.platform = platform;
    }

    public GuestAccess getGuestAccess() {
        return guestAccess;
    }

    /**
     * Finds the type named by {@code name}.
     *
     * @param name the name of a class as expected by {@link Class#forName(String)}
     * @return the found class or the error that occurred locating the type
     */
    public TypeResult<ResolvedJavaType> findType(String name) {
        return findType(name, true);
    }

    /**
     * Find class, return result encoding class or failure reason.
     */
    public TypeResult<ResolvedJavaType> findType(String name, boolean allowPrimitives) {
        try {
            if (allowPrimitives && name.indexOf('.') == -1) {
                ResolvedJavaType primitive = typeForPrimitive(name);
                if (primitive != null) {
                    return TypeResult.forType(name, primitive);
                }
            }
            return TypeResult.forType(name, typeForName(name));
        } catch (ClassNotFoundException | LinkageError ex) {
            return TypeResult.forException(name, ex);
        }
    }

    public ResolvedJavaType typeForName(String className) throws ClassNotFoundException {
        ResolvedJavaType type = guestAccess.lookupAppClassLoaderType(className);
        if (type == null) {
            throw new ClassNotFoundException(className);
        }
        return type;
    }

    public ResolvedJavaType typeForPrimitive(String name) {
        Class<?> c = ImageClassLoader.forPrimitive(name);
        return c == null ? null : guestAccess.lookupType(c);
    }

    private static ResolvedJavaType getEnclosingTypeOrNull(ResolvedJavaType javaType) {
        try {
            return javaType.getEnclosingType();
        } catch (LinkageError e) {
            return null;
        }
    }

    /**
     * Determines if {@code element} is supported on {@code thePlatform} by consulting the
     * {@link Platforms} annotation on {@code element}.
     * <p>
     * If {@code element} is a {@link ResolvedJavaType}, the {@link Platforms} annotation on its
     * enclosing classes and package are consulted as well.
     */
    public ImageClassLoader.PlatformSupportResult isPlatformSupported(Annotated element, Platform thePlatform) {
        if (element instanceof ResolvedJavaType javaType) {
            ImageClassLoader.PlatformSupportResult res = isPlatformSupported0(element, thePlatform);
            if (res == ImageClassLoader.PlatformSupportResult.NO) {
                return res;
            }
            ResolvedJavaPackage p = JVMCIReflectionUtil.getPackage(javaType);
            if (p != null) {
                res = res.and(isPlatformSupported0(p, thePlatform));
                if (res == ImageClassLoader.PlatformSupportResult.NO) {
                    return res;
                }
            }
            ResolvedJavaType enclosingType = getEnclosingTypeOrNull(javaType);
            while (enclosingType != null && res != ImageClassLoader.PlatformSupportResult.NO) {
                res = res.and(isPlatformSupported0(enclosingType, thePlatform));
                enclosingType = getEnclosingTypeOrNull(enclosingType);
            }
            return res;
        } else {
            return isPlatformSupported0(element, thePlatform);
        }
    }

    /**
     * Helper for {@link #isPlatformSupported(Annotated, Platform)}.
     */
    private ImageClassLoader.PlatformSupportResult isPlatformSupported0(Annotated element, Platform thePlatform) {
        if (thePlatform == null) {
            return ImageClassLoader.PlatformSupportResult.YES;
        }
        AnnotationValue av = GuestAnnotationAccess.getAnnotationValue(element, Platforms.class);
        if (av != null) {
            List<ResolvedJavaType> platforms = av.getList("value", ResolvedJavaType.class);
            if (platforms.contains(guestAccess.lookupType(Platform.HOSTED_ONLY.class))) {
                return ImageClassLoader.PlatformSupportResult.HOSTED;
            } else if (!NativeImageGenerator.includedIn(guestAccess.lookupType(thePlatform.getClass()), platforms)) {
                return ImageClassLoader.PlatformSupportResult.NO;
            }
        }
        return ImageClassLoader.PlatformSupportResult.YES;
    }

    public List<ResolvedJavaType> findSubtypes(ResolvedJavaType baseType, boolean includeHostedOnly) {
        ArrayList<ResolvedJavaType> subtypes = new ArrayList<>();
        addSubtypes(applicationTypes, baseType, subtypes);
        if (includeHostedOnly) {
            addSubtypes(hostedOnlyTypes, baseType, subtypes);
        }

        return subtypes;
    }

    private static void addSubtypes(EconomicSet<ResolvedJavaType> types, ResolvedJavaType baseClass, ArrayList<ResolvedJavaType> result) {
        for (ResolvedJavaType systemType : types) {
            if (baseClass.isAssignableFrom(systemType)) {
                result.add(systemType);
            }
        }
    }

    public void addClassNameToPreserve(String className) {
        classNamesToPreserve.add(className);
    }

    public void addClassToPreserve(String name) {
        typesToPreserve.add(guestAccess.lookupAppClassLoaderType(name));
    }

    /**
     * Gets the candidate types to {@linkplain SubstrateOptions#Preserve preserve}.
     */
    public Stream<ResolvedJavaType> getTypesToPreserve() {
        return typesToPreserve.stream();
    }

    public Set<String> getClassNamesToPreserve() {
        return Collections.unmodifiableSet(classNamesToPreserve);
    }

    public EconomicSet<ResolvedJavaType> getApplicationTypes() {
        return applicationTypes;
    }

    /**
     * Returns whether {@code type} was discovered from the application class path or module path,
     * rather than from a Native Image builder container.
     */
    public boolean isUserType(ResolvedJavaType type) {
        return !builderTypes.contains(type);
    }

    public List<ResolvedJavaMethod> findAnnotatedMethods(Class<? extends Annotation> annotationClass) {
        ArrayList<ResolvedJavaMethod> result = new ArrayList<>();
        for (ResolvedJavaMethod method : applicationMethods) {
            if (GuestAnnotationAccess.getAnnotationValue(method, annotationClass) != null) {
                result.add(method);
            }
        }
        return result;
    }

    public Stream<ResolvedJavaType> getTypesToIncludeUnconditionally() {
        return typesToIncludeUnconditionally.stream()
                        .sorted(Comparator.comparing(ResolvedJavaType::getName));
    }

    /**
     * Gets the names of the classes discovered from {@code container} during path scanning.
     */
    public EconomicSet<String> getDiscoveredClassNames(URI container) {
        return discoveredClassNames.get(container, emptySet);
    }

    /**
     * Gets the names of the packages defined in {@code container} for which at least one class was
     * discovered during path scanning.
     */
    public EconomicSet<String> getDiscoveredPackageNames(URI container) {
        return discoveredPackageNames.get(container, emptySet);
    }

    public boolean noEntryForURI(EconomicSet<String> set) {
        return set == emptySet;
    }

    public void reportBuilderClassesInApplication(OptionValues parsedHostedOptions) {
        EconomicMap<URI, EconomicSet<String>> builderClasses = EconomicMap.create();
        EconomicMap<URI, EconomicSet<String>> applicationClasses = EconomicMap.create();
        MapCursor<URI, EconomicSet<String>> classesEntries = discoveredClassNames.getEntries();
        while (classesEntries.advance()) {
            var destinationMap = builderURILocations.contains(classesEntries.getKey()) ? builderClasses : applicationClasses;
            destinationMap.put(classesEntries.getKey(), classesEntries.getValue());
        }
        boolean tolerateViolations = SubstrateOptions.AllowDeprecatedBuilderClassesOnImageClasspath.getValue(parsedHostedOptions);
        MapCursor<URI, EconomicSet<String>> applicationClassesEntries = applicationClasses.getEntries();
        while (applicationClassesEntries.advance()) {
            var applicationClassContainer = applicationClassesEntries.getKey();
            for (String applicationClass : applicationClassesEntries.getValue()) {
                MapCursor<URI, EconomicSet<String>> builderClassesEntries = builderClasses.getEntries();
                while (builderClassesEntries.advance()) {
                    var builderClassContainer = builderClassesEntries.getKey();
                    if (builderClassesEntries.getValue().contains(applicationClass)) {
                        String message = String.format("Class-path entry %s contains class %s. This class is part of the image builder itself (in %s) and must not be passed via -cp.",
                                        applicationClassContainer, applicationClass, builderClassContainer);
                        if (!tolerateViolations) {
                            String errorMessage = String.join(" ", message,
                                            "This can be caused by a fat-jar that illegally includes svm.jar (or graal-sdk.jar) due to its build-time dependency on it.",
                                            "As a workaround, %s allows turning this error into a warning. Note that this option is deprecated and will be removed in a future version.");
                            throw UserError.abort(errorMessage, SubstrateOptionsParser.commandArgument(SubstrateOptions.AllowDeprecatedBuilderClassesOnImageClasspath, "+"));
                        } else {
                            LogUtils.warning(message);
                        }
                    }
                }
            }
        }
    }

    /**
     * Registers a type loaded from the image class-path or module-path.
     *
     * @param type a type that must be {@link VMAccess#owns owned} by the guest VM
     */
    void registerType(ResolvedJavaType type) {
        assert guestAccess.owns(type) : type;
        ImageClassLoader.PlatformSupportResult res;
        try {
            res = isPlatformSupported(type, platform);
        } catch (LinkageError error) {
            ImageClassLoader.handleClassLoadingError(error, "guest: getting @Platforms annotation value for %s", type);
            res = ImageClassLoader.PlatformSupportResult.NO;
        }

        if (res == ImageClassLoader.PlatformSupportResult.HOSTED) {
            synchronized (hostedOnlyTypes) {
                hostedOnlyTypes.add(type);
            }
        } else if (res == ImageClassLoader.PlatformSupportResult.YES) {
            synchronized (applicationTypes) {
                applicationTypes.add(type);
            }
            registerFieldsAndMethods(type);
        }
    }

    /**
     * Registers the fields and methods declared by {@code applicationType} that are compatible with
     * the {@linkplain #platform target platform}.
     */
    private void registerFieldsAndMethods(ResolvedJavaType applicationType) {
        List<ResolvedJavaMethod> declaredMethods = null;
        try {
            declaredMethods = applicationType.getAllMethods(true);
        } catch (LinkageError t) {
            ImageClassLoader.handleClassLoadingError(t, "guest: getting all methods of %s", applicationType);
        }
        if (declaredMethods != null) {
            for (ResolvedJavaMethod systemMethod : declaredMethods) {
                if (isInPlatform(systemMethod)) {
                    synchronized (applicationMethods) {
                        applicationMethods.add(systemMethod);
                    }
                }
            }
        }

        List<ResolvedJavaField> declaredFields = null;
        try {
            declaredFields = JVMCIReflectionUtil.getAllFields(applicationType);
        } catch (LinkageError t) {
            ImageClassLoader.handleClassLoadingError(t, "guest: getting all fields of %s", applicationType);
        }
        if (declaredFields != null) {
            for (ResolvedJavaField systemField : declaredFields) {
                if (isInPlatform(systemField)) {
                    synchronized (applicationFields) {
                        applicationFields.add(systemField);
                    }
                }
            }
        }
    }

    /**
     * Determines if {@code element} is compatible with the {@linkplain #platform target platform}.
     */
    private boolean isInPlatform(Annotated element) {
        try {
            AnnotationValue av = GuestAnnotationAccess.getAnnotationValue(element, Platforms.class);
            return av == null || NativeImageGenerator.includedIn(GuestAccess.get().lookupType(platform.getClass()), av.getList("value", ResolvedJavaType.class));
        } catch (LinkageError t) {
            ImageClassLoader.handleClassLoadingError(t, "guest: getting @Platforms annotation value for %s", element);
            return false;
        }
    }

    void handleClassFileName(URI container,
                    Module module,
                    String className,
                    String packageName,
                    boolean includeUnconditionally,
                    boolean classRequiresInit,
                    boolean preserveReflectionMetadata,
                    boolean isBuilderContainer,
                    NativeImageClassLoaderSupport.PackageRequest includePackages,
                    NativeImageClassLoaderSupport.PackageRequest preservePackages) {
        recordDiscoveredClassName(container, className, packageName);

        ResolvedJavaType type = null;
        try {
            type = typeForName(className);
        } catch (AssertionError error) {
            VMError.shouldNotReachHere(error);
        } catch (ClassNotFoundException | SecurityException | LinkageError t) {
            if (preserveReflectionMetadata) {
                addClassNameToPreserve(className);
            }
            LinkageError le = t instanceof LinkageError l ? l : (LinkageError) new NoClassDefFoundError(className).initCause(t);
            ImageClassLoader.handleClassLoadingError(le, "guest: resolving class %s in %s", className, module);
        }

        if (type != null) {
            if (isBuilderContainer) {
                builderTypes.add(type);
            }
            includedJavaPackages.add(packageName);
            if (includeUnconditionally || includePackages.shouldInclude(packageName)) {
                typesToIncludeUnconditionally.add(type);
            }
            if (classRequiresInit) {
                registerType(type);
            }
            if (preserveReflectionMetadata || preservePackages.shouldInclude(packageName)) {
                addClassToPreserve(className);
            }
        }
    }

    private void recordDiscoveredClassName(URI container, String className, String packageName) {
        synchronized (discoveredClassNames) {
            EconomicSet<String> classNames = discoveredClassNames.get(container);
            if (classNames == null) {
                classNames = EconomicSet.create();
                discoveredClassNames.put(container, classNames);
            }
            classNames.add(className);
        }
        synchronized (discoveredPackageNames) {
            EconomicSet<String> packageNames = discoveredPackageNames.get(container);
            if (packageNames == null) {
                packageNames = EconomicSet.create();
                discoveredPackageNames.put(container, packageNames);
            }
            packageNames.add(packageName);
        }
    }

    public List<ResolvedJavaField> findAnnotatedFields(Class<? extends Annotation> annotationClass) {
        ArrayList<ResolvedJavaField> result = new ArrayList<>();
        for (ResolvedJavaField field : applicationFields) {
            if (GuestAnnotationAccess.getAnnotationValue(field, annotationClass) != null) {
                result.add(field);
            }
        }
        return result;
    }

    public List<ResolvedJavaType> findAnnotatedTypes(Class<? extends Annotation> annotationClass, boolean includeHostedOnly) {
        ArrayList<ResolvedJavaType> types = new ArrayList<>();
        addAnnotatedClasses(applicationTypes, annotationClass, types);
        if (includeHostedOnly) {
            addAnnotatedClasses(hostedOnlyTypes, annotationClass, types);
        }

        return types;
    }

    private static void addAnnotatedClasses(EconomicSet<ResolvedJavaType> types, Class<? extends Annotation> annotationClass, ArrayList<ResolvedJavaType> result) {
        for (ResolvedJavaType systemType : types) {
            if (GuestAnnotationAccess.getAnnotationValue(systemType, annotationClass) != null) {
                result.add(systemType);
            }
        }
    }

    /* Report package inclusion requests that did not have any effect. */
    void validatePackageInclusionRequests(NativeImageClassLoaderSupport.PackageRequest request, HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> optionString,
                    OptionValues parsedHostedOptions) {
        List<LayerOptionsSupport.PackageOptionValue> unusedRequests = new ArrayList<>();
        for (String requestedPackage : request.requestedPackages()) {
            if (!includedJavaPackages.contains(requestedPackage)) {
                unusedRequests.add(new LayerOptionsSupport.PackageOptionValue(requestedPackage, false));
            }
        }
        var unusedWildcardRequests = new LinkedHashSet<>(request.requestedPackageWildcards());
        if (!unusedWildcardRequests.isEmpty()) {
            for (String includedPackage : includedJavaPackages) {
                unusedWildcardRequests.removeIf(wildcardRequest -> includedPackage.startsWith(wildcardRequest.name()));
            }
        }
        if (!(unusedRequests.isEmpty() && unusedWildcardRequests.isEmpty())) {
            var requestsStrings = Stream.concat(unusedRequests.stream(), unusedWildcardRequests.stream())
                            .map(packageOptionValue -> '\'' + packageOptionValue.toString() + '\'')
                            .toList();
            boolean plural = requestsStrings.size() > 1;
            String pluralS = plural ? "s" : "";
            throw UserError.abort("Package request%s (package=...) %s %s could not find requested package%s. " +
                            "Provide a class/module-path that contains the package%s or remove %s from option.",
                            pluralS, String.join(", ", requestsStrings), createOptionStr(optionString, parsedHostedOptions), pluralS,
                            pluralS, plural ? "entries" : "entry");
        }
    }

    private static String createOptionStr(HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> option, OptionValues parsedHostedOptions) {
        LocatableMultiOptionValue.ValueWithOrigin<String> layerCreateValue = option.getValue(parsedHostedOptions).lastValueWithOrigin().orElseThrow();
        String layerCreateArgument = SubstrateOptionsParser.commandArgument(option, layerCreateValue.value());
        return "specified with '%s' from %s".formatted(layerCreateArgument, layerCreateValue.origin());
    }
}
