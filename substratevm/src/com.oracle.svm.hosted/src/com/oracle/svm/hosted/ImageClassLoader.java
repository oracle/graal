/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.vmaccess.ResolvedJavaPackage;
import com.oracle.graal.vmaccess.VMAccess;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.GraalAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalFieldProvider;
import com.oracle.svm.util.OriginalMethodProvider;
import com.oracle.svm.util.TypeResult;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * This class maintains a dictionary of the classes {@linkplain #loadAllClasses() loaded} from the
 * Native Image class-path and module-path as well as their declared fields and methods.
 */
public final class ImageClassLoader {

    /**
     * The platform of the target image being built.
     */
    public final Platform platform;

    public final NativeImageClassLoaderSupport classLoaderSupport;
    public final VMAccess vmAccess;
    public final DeadlockWatchdog watchdog;

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

    /**
     * Modules containing all {@code svm.core} and {@code svm.hosted} classes.
     */
    private Set<Module> builderModules;

    ImageClassLoader(Platform platform, NativeImageClassLoaderSupport classLoaderSupport, VMAccess vmAccess) {
        this.platform = platform;
        this.vmAccess = vmAccess;
        this.classLoaderSupport = classLoaderSupport;

        int watchdogInterval = SubstrateOptions.DeadlockWatchdogInterval.getValue(classLoaderSupport.getParsedHostedOptions());
        boolean watchdogExitOnTimeout = SubstrateOptions.DeadlockWatchdogExitOnTimeout.getValue(classLoaderSupport.getParsedHostedOptions());
        this.watchdog = new DeadlockWatchdog(watchdogInterval, watchdogExitOnTimeout);
    }

    @SuppressWarnings("unused")
    public void loadAllClasses() throws InterruptedException {
        ForkJoinPool executor = ForkJoinPool.commonPool();
        try {
            classLoaderSupport.loadAllClasses(executor, this);
        } finally {
            boolean isQuiescence = false;
            while (!isQuiescence) {
                isQuiescence = executor.awaitQuiescence(10, TimeUnit.MINUTES);
                if (!isQuiescence) {
                    LogUtils.warning("Class loading is slow. Waiting for tasks to complete...");
                    /* DeadlockWatchdog should fail the build eventually. */
                }
            }
        }
        classLoaderSupport.allClassesLoaded();
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
            handleClassLoadingError(t, "getting all methods of %s", applicationType);
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
            handleClassLoadingError(t, "getting all fields of %s", applicationType);
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
        Platforms platformAnnotation = classLoaderSupport.annotationExtractor.getAnnotation(element, Platforms.class);
        return NativeImageGenerator.includedIn(platform, platformAnnotation);
    }

    /**
     * Controls printing of otherwise silently swallowed LinkageErrors. This can be very useful when
     * diagnosing obscure Native Image problems.
     */
    private static final boolean PRINT_LINKAGE_ERRORS = Boolean.getBoolean(ImageClassLoader.class.getName() + ".traceSwallowedLinkageErrors");

    @SuppressWarnings("unused")
    static void handleClassLoadingError(LinkageError t, String format, Object... args) {
        /* we ignore class loading errors due to incomplete paths that people often have */
        if (PRINT_LINKAGE_ERRORS) {
            PrintStream out = System.out;
            out.println("Error " + format.formatted(args));
            t.printStackTrace(out);
        }
    }

    private static ResolvedJavaType getEnclosingTypeOrNull(ResolvedJavaType javaType) {
        try {
            return javaType.getEnclosingType();
        } catch (LinkageError e) {
            return null;
        }
    }

    public ClassLoader getDynamicHubClassLoader(Class<?> clazz) {
        if (isCoreType(clazz)) {
            /*
             * Use null-loader for VM implementation classes. Our own VM implementation code (e.g.
             * com.oracle.svm.core classes) are unrelated to the application code of the image and
             * should not share the same classloader at image run-time. Using null as the
             * classloader for such classes is in line with other use of the null-loader in Java.
             */
            return null;
        } else {
            /*
             * If the class is an application class then it was loaded by NativeImageClassLoader.
             * The ClassLoaderFeature object replacer will unwrap the original AppClassLoader from
             * the NativeImageClassLoader.
             */
            return clazz.getClassLoader();
        }
    }

    public boolean isCoreType(Class<?> clazz) {
        return getBuilderModules().contains(clazz.getModule());
    }

    /**
     * Type of result returned by {@link ImageClassLoader#isPlatformSupported}.
     */
    public enum PlatformSupportResult {
        /**
         * The element is not supported.
         */
        NO,

        /**
         * The element is supported but only during native image generation.
         */
        HOSTED,

        /**
         * The element is supported.
         */
        YES;

        /**
         * Returns the most restrictive value between this result and {@code other}.
         */
        public PlatformSupportResult and(PlatformSupportResult other) {
            if (ordinal() < other.ordinal()) {
                return this;
            }
            return other;
        }

        static {
            assert NO.and(YES) == NO;
            assert YES.and(NO) == NO;
            assert NO.and(HOSTED) == NO;
            assert HOSTED.and(NO) == NO;
            assert YES.and(HOSTED) == HOSTED;
            assert HOSTED.and(YES) == HOSTED;
        }
    }

    /**
     * Determines if {@code element} is supported on {@code thePlatform} by consulting the
     * {@link Platforms} annotation on {@code element}.
     * <p>
     * If {@code element} is a {@link ResolvedJavaType}, the {@link Platforms} annotation on its
     * enclosing classes and package are consulted as well.
     */
    public PlatformSupportResult isPlatformSupported(Annotated element, Platform thePlatform) {
        if (element instanceof ResolvedJavaType javaType) {
            PlatformSupportResult res = isPlatformSupported0(element, thePlatform);
            if (res == PlatformSupportResult.NO) {
                return res;
            }
            ResolvedJavaPackage p = JVMCIReflectionUtil.getPackage(javaType);
            if (p != null) {
                res = res.and(isPlatformSupported0(p, thePlatform));
                if (res == PlatformSupportResult.NO) {
                    return res;
                }
            }
            ResolvedJavaType enclosingType = getEnclosingTypeOrNull(javaType);
            while (enclosingType != null && res != PlatformSupportResult.NO) {
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
    private PlatformSupportResult isPlatformSupported0(Annotated element, Platform thePlatform) {
        if (thePlatform == null) {
            return PlatformSupportResult.YES;
        }
        Platforms platforms = classLoaderSupport.annotationExtractor.getAnnotation(element, Platforms.class);
        if (platforms != null) {
            if (Arrays.asList(platforms.value()).contains(Platform.HOSTED_ONLY.class)) {
                return PlatformSupportResult.HOSTED;
            } else if (!NativeImageGenerator.includedIn(thePlatform, platforms)) {
                return PlatformSupportResult.NO;
            }
        }
        return PlatformSupportResult.YES;
    }

    /**
     * Registers an {@linkplain OriginalClassProvider#getOriginalType original type} loaded from the
     * image class-path or module-path.
     */
    void registerType(ResolvedJavaType type) {
        assert OriginalClassProvider.getOriginalType(type).equals(type) : type;
        PlatformSupportResult res = isPlatformSupported(type, platform);
        if (res == PlatformSupportResult.HOSTED) {
            synchronized (hostedOnlyTypes) {
                hostedOnlyTypes.add(type);
            }
        } else if (res == PlatformSupportResult.YES) {
            synchronized (applicationTypes) {
                applicationTypes.add(type);
            }
            registerFieldsAndMethods(type);
        }
    }

    public Enumeration<URL> findResourcesByName(String resource) throws IOException {
        return getClassLoader().getResources(resource);
    }

    /**
     * @deprecated use {@link #findClassOrFail(String)} instead.
     */
    @Deprecated
    public Class<?> findClassByName(String name) {
        return findClassByName(name, true);
    }

    /**
     * @deprecated use {@link #findClass(String)} or {@link #findClassOrFail(String)} instead.
     */
    @Deprecated
    public Class<?> findClassByName(String name, boolean failIfClassMissing) {
        TypeResult<Class<?>> result = findClass(name);
        if (failIfClassMissing) {
            return result.getOrFail();
        } else {
            return result.get();
        }
    }

    /**
     * Find class or fail if exception occurs.
     */
    public Class<?> findClassOrFail(String name) {
        return findClass(name).getOrFail();
    }

    /**
     * Find class, return result encoding class or failure reason.
     */
    public TypeResult<Class<?>> findClass(String name) {
        return findClass(name, true);
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
    public TypeResult<Class<?>> findClass(String name, boolean allowPrimitives) {
        return findClass(name, allowPrimitives, getClassLoader());
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

    /**
     * Find class, return result encoding class or failure reason.
     */
    public static TypeResult<Class<?>> findClass(String name, boolean allowPrimitives, ClassLoader loader) {
        try {
            if (allowPrimitives && name.indexOf('.') == -1) {
                Class<?> primitive = forPrimitive(name);
                if (primitive != null) {
                    return TypeResult.forClass(primitive);
                }
            }
            return TypeResult.forClass(forName(name, false, loader));
        } catch (ClassNotFoundException | LinkageError ex) {
            return TypeResult.forException(name, ex);
        }
    }

    public static Class<?> forPrimitive(String name) {
        return switch (name) {
            case "boolean" -> boolean.class;
            case "char" -> char.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "void" -> void.class;
            default -> null;
        };
    }

    public static ResolvedJavaType typeForPrimitive(String name) {
        Class<?> c = forPrimitive(name);
        return c == null ? null : GraalAccess.lookupType(c);
    }

    public ResolvedJavaType typeForName(String className) throws ClassNotFoundException {
        ResolvedJavaType type = vmAccess.lookupAppClassLoaderType(className);
        if (type == null) {
            throw new ClassNotFoundException(className);
        }
        return type;
    }

    public Class<?> forName(String className) throws ClassNotFoundException {
        return forName(className, false, getClassLoader());
    }

    public static Class<?> forName(String className, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(className, initialize, loader);
    }

    public Class<?> forName(String className, Module module) throws ClassNotFoundException {
        if (module == null) {
            return forName(className);
        }
        return classLoaderSupport.loadClassFromModule(module, className);
    }

    /**
     * Deprecated. Use {@link ImageClassLoader#classpath()} instead.
     *
     * @return image classpath as a list of strings.
     */
    @Deprecated
    public List<String> getClasspath() {
        return classpath().stream().map(Path::toString).collect(Collectors.toList());
    }

    public List<Path> classpath() {
        return classLoaderSupport.classpath();
    }

    public List<Path> modulepath() {
        return classLoaderSupport.modulepath();
    }

    public List<Path> applicationClassPath() {
        return classLoaderSupport.applicationClassPath();
    }

    public List<Path> applicationModulePath() {
        return classLoaderSupport.applicationModulePath();
    }

    public <T> List<Class<? extends T>> findSubclasses(Class<T> baseClass, boolean includeHostedOnly) {
        ResolvedJavaType baseType = GraalAccess.lookupType(baseClass);
        ArrayList<ResolvedJavaType> subtypes = new ArrayList<>();
        addSubclasses(applicationTypes, baseType, subtypes);
        if (includeHostedOnly) {
            addSubclasses(hostedOnlyTypes, baseType, subtypes);
        }
        ArrayList<Class<? extends T>> result = new ArrayList<>(subtypes.size());
        for (ResolvedJavaType subtype : subtypes) {
            result.add(OriginalClassProvider.getJavaClass(subtype).asSubclass(baseClass));
        }
        return result;
    }

    private static <T> void addSubclasses(EconomicSet<ResolvedJavaType> types, ResolvedJavaType baseClass, ArrayList<ResolvedJavaType> result) {
        for (ResolvedJavaType systemType : types) {
            if (baseClass.isAssignableFrom(systemType)) {
                result.add(systemType);
            }
        }
    }

    public List<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationClass, boolean includeHostedOnly) {
        ArrayList<ResolvedJavaType> types = new ArrayList<>();
        addAnnotatedClasses(applicationTypes, annotationClass, types);
        if (includeHostedOnly) {
            addAnnotatedClasses(hostedOnlyTypes, annotationClass, types);
        }
        ArrayList<Class<?>> result = new ArrayList<>(types.size());
        for (ResolvedJavaType type : types) {
            result.add(OriginalClassProvider.getJavaClass(type));
        }
        return result;
    }

    private void addAnnotatedClasses(EconomicSet<ResolvedJavaType> classes, Class<? extends Annotation> annotationClass, ArrayList<ResolvedJavaType> result) {
        for (ResolvedJavaType systemType : classes) {
            if (classLoaderSupport.annotationExtractor.getAnnotation(systemType, annotationClass) != null) {
                result.add(systemType);
            }
        }
    }

    public List<Method> findAnnotatedMethods(Class<? extends Annotation> annotationClass) {
        ArrayList<Method> result = new ArrayList<>();
        for (ResolvedJavaMethod method : applicationMethods) {
            if (classLoaderSupport.annotationExtractor.getAnnotation(method, annotationClass) != null) {
                Method javaMethod = (Method) OriginalMethodProvider.getJavaMethod(method);
                if (javaMethod != null) {
                    result.add(javaMethod);
                }
            }
        }
        return result;
    }

    public List<Method> findAnnotatedMethods(Class<? extends Annotation>[] annotationClasses) {
        ArrayList<Method> result = new ArrayList<>();
        for (ResolvedJavaMethod method : applicationMethods) {
            boolean match = true;
            for (Class<? extends Annotation> annotationClass : annotationClasses) {
                if (classLoaderSupport.annotationExtractor.getAnnotation(method, annotationClass) == null) {
                    match = false;
                    break;
                }
            }
            if (match) {
                Method javaMethod = (Method) OriginalMethodProvider.getJavaMethod(method);
                if (javaMethod != null) {
                    result.add(javaMethod);
                }
            }
        }
        return result;
    }

    public List<Field> findAnnotatedFields(Class<? extends Annotation> annotationClass) {
        ArrayList<Field> result = new ArrayList<>();
        for (ResolvedJavaField field : applicationFields) {
            if (classLoaderSupport.annotationExtractor.getAnnotation(field, annotationClass) != null) {
                Field javaField = OriginalFieldProvider.getJavaField(field);
                if (javaField != null) {
                    result.add(javaField);
                }
            }
        }
        return result;
    }

    public ClassLoader getClassLoader() {
        return classLoaderSupport.getClassLoader();
    }

    public static Optional<String> getMainClassFromModule(Object module) {
        return NativeImageClassLoaderSupport.getMainClassFromModule(module);
    }

    public Optional<Module> findModule(String moduleName) {
        return classLoaderSupport.findModule(moduleName);
    }

    public String getMainClassNotFoundErrorMessage(String className) {
        List<Path> classPath = applicationClassPath();
        String classPathString = classPath.isEmpty() ? "empty classpath" : "classpath: '%s'".formatted(pathsToString(classPath));
        List<Path> modulePath = applicationModulePath();
        String modulePathString = modulePath.isEmpty() ? "empty modulepath" : "modulepath: '%s'".formatted(pathsToString(modulePath));
        return String.format("Main entry point class '%s' neither found on %n%s nor%n%s.", className, classPathString, modulePathString);
    }

    private static String pathsToString(List<Path> paths) {
        return paths.stream().map(String::valueOf).collect(Collectors.joining(File.pathSeparator));
    }

    public EconomicSet<String> classes(URI container) {
        return classLoaderSupport.classes(container);
    }

    public EconomicSet<String> packages(URI container) {
        return classLoaderSupport.packages(container);
    }

    public boolean noEntryForURI(EconomicSet<String> set) {
        return classLoaderSupport.noEntryForURI(set);
    }

    public Set<Module> getBuilderModules() {
        assert builderModules != null : "Builder modules not yet initialized.";
        return builderModules;
    }

    public void initBuilderModules() {
        VMError.guarantee(BuildPhaseProvider.isFeatureRegistrationFinished() && ImageSingletons.contains(VMFeature.class),
                        "Querying builder modules is only possible after feature registration is finished.");
        Module m0 = ImageSingletons.lookup(VMFeature.class).getClass().getModule();
        Module m1 = SVMHost.class.getModule();
        builderModules = m0.equals(m1) ? Set.of(m0) : Set.of(m0, m1);
    }

    /**
     * Gets the set of types that will potentially end up in the image.
     */
    public EconomicSet<ResolvedJavaType> getApplicationTypes() {
        return applicationTypes;
    }
}
