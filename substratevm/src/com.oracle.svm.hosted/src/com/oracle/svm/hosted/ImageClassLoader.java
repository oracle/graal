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
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
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

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.imagelayer.LayeredDispatchTableFeature;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.HostedModuleSupport;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.graal.compiler.vmaccess.ResolvedJavaModuleLayer;
import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

/**
 * This class maintains a dictionary of the classes {@linkplain #loadAllClasses() loaded} from the
 * Native Image builder class-path and module-path. Until Terminus is done, it unfortunately also
 * includes classes loaded from the application class-path and module-path. Terminus is working to
 * ensure the latter are _only_ managed by {@link GuestTypes}.
 */
public final class ImageClassLoader {
    /**
     * The types, methods and fields available in the guest context.
     */
    public final GuestTypes guestTypes;

    /**
     * The platform of the target image being built.
     */
    public final Platform platform;

    public final NativeImageClassLoaderSupport classLoaderSupport;
    public final DeadlockWatchdog watchdog;

    /**
     * The set of builder-context classes compatible with the {@linkplain #platform target platform}
     * that should _not_ end up in the image. Once project Terminus is resolved, this should be
     * deleted and only {@link #hostedOnlyClasses} remains.
     */
    private final EconomicSet<Class<?>> builderClasses = EconomicSet.create();

    /**
     * The set of hosted-only classes loaded from the Native Image class-path and module-path.
     */
    private final EconomicSet<Class<?>> hostedOnlyClasses = EconomicSet.create();

    /**
     * Builder modules that comprise the Native Image builder. This does not describe which modules are
     * visible in a guest VM access context.
     */
    private Set<Module> builderModules;

    /**
     * Modules containing SVM runtime code rather than application code. Membership in this set is used
     * by {@link SVMHost#isCoreType(ResolvedJavaType)} to classify SVM runtime types. In an open type
     * world, {@link PointsToAnalysis#isClosed(AnalysisType)} treats their type hierarchies as closed,
     * {@link PointsToAnalysis#isClosed(ResolvedJavaField)} treats their fields as closed by default, and
     * {@link MethodFlowsGraph} excludes their methods from open-world saturation.
     * <p>
     * In layered builds, {@link ExtensionLayerImageFeature} requires these types and their subtypes to
     * be complete in the initial layer, while {@link LayeredDispatchTableFeature} omits calls involving
     * these modules from virtual-call roots persisted for later layers. At image run time,
     * {@link #getDynamicHubClassLoader(Class)} assigns these classes the null class loader.
     * <p>
     * During the Terminus migration, non-isolated builds also include builder modules that still contain
     * SVM runtime code.
     */
    private Set<ResolvedJavaModule> coreGuestModules;

    ImageClassLoader(Platform platform, NativeImageClassLoaderSupport classLoaderSupport) {
        this.platform = platform;
        this.classLoaderSupport = classLoaderSupport;

        OptionValues parsedHostedOptions = classLoaderSupport.getParsedHostedOptions();
        int watchdogInterval = SubstrateOptions.DeadlockWatchdogInterval.getValue(parsedHostedOptions);
        boolean watchdogExitOnTimeout = SubstrateOptions.DeadlockWatchdogExitOnTimeout.getValue(parsedHostedOptions);
        this.watchdog = new DeadlockWatchdog(watchdogInterval, watchdogExitOnTimeout);
        this.guestTypes = new GuestTypes(GuestAccess.get(), classLoaderSupport.annotationExtractor, platform);
        classLoaderSupport.getBuildClassPath().stream().map(Path::toUri).forEach(this.guestTypes.builderURILocations::add);
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
        guestTypes.reportBuilderClassesInApplication(classLoaderSupport.getParsedHostedOptions());

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
        GuestAccess guestAccess = GuestAccess.get();
        return getCoreGuestModules().contains(guestAccess.getModule(guestAccess.lookupType(clazz)));
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
        AnnotationValue av = classLoaderSupport.annotationExtractor.getAnnotationValue(element, Platforms.class);
        if (av != null) {
            List<ResolvedJavaType> platforms = av.getList("value", ResolvedJavaType.class);
            GuestAccess access = GuestAccess.get();
            if (platforms.contains(access.lookupType(Platform.HOSTED_ONLY.class))) {
                return PlatformSupportResult.HOSTED;
            } else if (!NativeImageGenerator.includedIn(access.lookupType(thePlatform.getClass()), platforms)) {
                return PlatformSupportResult.NO;
            }
        }
        return PlatformSupportResult.YES;
    }

    /**
     * Registers a class loaded from the image class-path or module-path.
     */
    void registerClass(Class<?> clazz) {
        PlatformSupportResult res;
        try {
            ResolvedJavaType type = GuestAccess.get().lookupType(clazz);
            res = isPlatformSupported(type, platform);
        } catch (LinkageError error) {
            handleClassLoadingError(error, "host: getting @Platforms annotation value for %s", clazz);
            res = PlatformSupportResult.NO;
        }

        if (res == PlatformSupportResult.HOSTED) {
            synchronized (hostedOnlyClasses) {
                hostedOnlyClasses.add(clazz);
            }
        } else if (res == PlatformSupportResult.YES) {
            synchronized (builderClasses) {
                builderClasses.add(clazz);
            }
        }
    }

    public Enumeration<URL> findResourcesByName(String resource) throws IOException {
        return getClassLoader().getResources(resource);
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
     * Find class, return result encoding class or failure reason.
     */
    public TypeResult<Class<?>> findClass(String name, boolean allowPrimitives) {
        return findClass(name, allowPrimitives, getClassLoader());
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

    public Class<?> forName(String className) throws ClassNotFoundException {
        return forName(className, false);
    }

    public Class<?> forName(String className, boolean initialize) throws ClassNotFoundException {
        return forName(className, initialize, getClassLoader());
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
        ArrayList<Class<? extends T>> subclasses = new ArrayList<>();
        addSubclasses(builderClasses, baseClass, subclasses);
        if (includeHostedOnly) {
            addSubclasses(hostedOnlyClasses, baseClass, subclasses);
        }

        return subclasses;
    }

    private static <T> void addSubclasses(EconomicSet<Class<?>> classes, Class<T> baseClass, ArrayList<Class<? extends T>> result) {
        for (Class<?> c : classes) {
            if (baseClass.isAssignableFrom(c)) {
                result.add(c.asSubclass(baseClass));
            }
        }
    }

    public List<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationClass, boolean includeHostedOnly) {
        ArrayList<Class<?>> result = new ArrayList<>();
        addAnnotatedClasses(builderClasses, annotationClass, result);
        if (includeHostedOnly) {
            addAnnotatedClasses(hostedOnlyClasses, annotationClass, result);
        }
        return result;
    }

    private void addAnnotatedClasses(EconomicSet<Class<?>> classes, Class<? extends Annotation> annotationClass, ArrayList<Class<?>> result) {
        for (Class<?> c : classes) {
            if (classLoaderSupport.annotationExtractor.hasAnnotation(c, annotationClass)) {
                result.add(c);
            }
        }
    }

    public ClassLoader getClassLoader() {
        return classLoaderSupport.getClassLoader();
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

    public Set<Module> getBuilderModules() {
        assert builderModules != null : "Builder modules not yet initialized.";
        return builderModules;
    }

    public void initBuilderModules() {
        builderModules = Collections.unmodifiableSet(NativeImageGeneratorRunner.getNativeImageBuilderModules());
    }

    public Set<ResolvedJavaModule> getCoreGuestModules() {
        assert coreGuestModules != null : "Core modules not yet initialized.";
        return coreGuestModules;
    }

    public void initCoreModules() {
        VMError.guarantee(BuildPhaseProvider.isFeatureRegistrationFinished(), "Querying core modules is only possible after feature registration is finished.");
        GuestAccess guestAccess = GuestAccess.get();
        Set<ResolvedJavaModule> modules = new LinkedHashSet<>();
        /*
         * Runtime code still spans host SVM modules until GR-76917 moves the remaining core runtime
         * ownership to guest modules. Non-isolated HostVMAccess can resolve those host modules from
         * host class literals, so include them here. Fully isolated Terminus excludes host-side
         * modules because they are invisible to the guest.
         */
        if (!guestAccess.isFullyIsolated()) {
            VMError.guarantee(ImageSingletons.contains(VMFeature.class), "Querying host-side core modules is only possible after VMFeature is available.");
            modules.add(guestAccess.getModule(guestAccess.lookupType(ImageSingletons.lookup(VMFeature.class).getClass())));
            modules.add(guestAccess.getModule(guestAccess.lookupType(SVMHost.class)));
        }
        ResolvedJavaModuleLayer guestModuleLayer = guestModuleLayer(guestAccess);
        HostedModuleSupport.GUEST_MODULES.forEach(moduleName -> addCoreModule(modules, guestModuleLayer, moduleName));
        if (SubstrateOptions.useLLVMBackend()) {
            String llvmBackendModule = "org.graalvm.nativeimage.llvm";
            modules.add(guestAccess.bootModuleLayer().findModule(llvmBackendModule)
                            .orElseThrow(() -> UserError.abort("The LLVM backend module '%s' is not available. Use --tool:llvm-backend or select a different compiler backend.",
                                            llvmBackendModule)));
        }
        coreGuestModules = Collections.unmodifiableSet(modules);
    }

    private static void addCoreModule(Set<ResolvedJavaModule> modules, ResolvedJavaModuleLayer moduleLayer, String moduleName) {
        ResolvedJavaModule module = moduleLayer.findModule(moduleName)
                        .orElseThrow(() -> VMError.shouldNotReachHere("Could not resolve SVM core module in active VMAccess context: " + moduleName));
        VMError.guarantee(moduleName.equals(module.getName()), "Expected to resolve SVM core module %s, got %s.", moduleName, module.getName());
        modules.add(module);
    }

    private static ResolvedJavaModuleLayer guestModuleLayer(GuestAccess guestAccess) {
        /*
         * The guest module layer is not necessarily the boot module layer in HostVMAccess. Locate it
         * through a dedicated marker in the pure guest module so all guest modules can be resolved
         * uniformly. The builder module does not read the guest module, so the marker must be looked
         * up by name rather than referenced with a class literal.
         */
        String markerClassName = "com.oracle.svm.guest.GuestModuleLayerMarker";
        ResolvedJavaType markerType = guestAccess.lookupType(markerClassName);
        VMError.guarantee(markerType != null, "Could not resolve guest module layer marker class %s.", markerClassName);
        ResolvedJavaModule markerModule = guestAccess.getModule(markerType);
        String markerModuleName = "org.graalvm.nativeimage.guest";
        VMError.guarantee(markerModuleName.equals(markerModule.getName()), "Expected guest module layer marker class %s to resolve module %s, got %s.",
                        markerClassName, markerModuleName, markerModule.getName());
        ResolvedJavaModuleLayer moduleLayer = markerModule.getLayer();
        VMError.guarantee(moduleLayer != null, "Guest module layer marker class %s is not in a module layer.", markerClassName);
        return moduleLayer;
    }
}
