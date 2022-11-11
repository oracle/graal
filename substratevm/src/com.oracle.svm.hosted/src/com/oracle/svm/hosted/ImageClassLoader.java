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
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.util.ReflectionUtil;

public final class ImageClassLoader {

    /*
     * This cannot be a HostedOption because the option parsing already relies on the list of loaded
     * classes.
     */
    private static final int CLASS_LOADING_MAX_SCALING = 8;
    private static final int CLASS_LOADING_TIMEOUT_IN_MINUTES = 10;

    static {
        /*
         * ImageClassLoader is one of the first classes used during image generation, so early
         * enough to ensure that we can use the Word type.
         */
        Word.ensureInitialized();
    }

    public final Platform platform;
    public final NativeImageClassLoaderSupport classLoaderSupport;
    public final DeadlockWatchdog watchdog;

    private final EconomicSet<Class<?>> applicationClasses = EconomicSet.create();
    private final EconomicSet<Class<?>> hostedOnlyClasses = EconomicSet.create();
    private final EconomicSet<Method> systemMethods = EconomicSet.create();
    private final EconomicSet<Field> systemFields = EconomicSet.create();

    ImageClassLoader(Platform platform, NativeImageClassLoaderSupport classLoaderSupport) {
        this.platform = platform;
        this.classLoaderSupport = classLoaderSupport;

        int watchdogInterval = SubstrateOptions.DeadlockWatchdogInterval.getValue(classLoaderSupport.getParsedHostedOptions());
        boolean watchdogExitOnTimeout = SubstrateOptions.DeadlockWatchdogExitOnTimeout.getValue(classLoaderSupport.getParsedHostedOptions());
        this.watchdog = new DeadlockWatchdog(watchdogInterval, watchdogExitOnTimeout);
    }

    public void loadAllClasses() throws InterruptedException {
        ForkJoinPool executor = new ForkJoinPool(Math.min(Runtime.getRuntime().availableProcessors(), CLASS_LOADING_MAX_SCALING)) {
            @Override
            public void execute(Runnable task) {
                super.execute(() -> {
                    task.run();
                    watchdog.recordActivity();
                });
            }
        };
        try {
            classLoaderSupport.loadAllClasses(executor, this);
        } finally {
            executor.shutdown();
            executor.awaitTermination(CLASS_LOADING_TIMEOUT_IN_MINUTES, TimeUnit.MINUTES);
        }
        classLoaderSupport.reportBuilderClassesInApplication();
    }

    private void findSystemElements(Class<?> systemClass) {
        Method[] declaredMethods = null;
        try {
            declaredMethods = systemClass.getDeclaredMethods();
        } catch (Throwable t) {
            handleClassLoadingError(t);
        }
        if (declaredMethods != null) {
            for (Method systemMethod : declaredMethods) {
                if (isInPlatform(systemMethod)) {
                    synchronized (systemMethods) {
                        systemMethods.add(systemMethod);
                    }
                }
            }
        }

        Field[] declaredFields = null;
        try {
            declaredFields = systemClass.getDeclaredFields();
        } catch (Throwable t) {
            handleClassLoadingError(t);
        }
        if (declaredFields != null) {
            for (Field systemField : declaredFields) {
                if (isInPlatform(systemField)) {
                    synchronized (systemFields) {
                        systemFields.add(systemField);
                    }
                }
            }
        }
    }

    private boolean isInPlatform(AnnotatedElement element) {
        try {
            Platforms platformAnnotation = classLoaderSupport.annotationExtractor.extractAnnotation(element, Platforms.class, false);
            return NativeImageGenerator.includedIn(platform, platformAnnotation);
        } catch (Throwable t) {
            handleClassLoadingError(t);
            return false;
        }
    }

    @SuppressWarnings("unused")
    static void handleClassLoadingError(Throwable t) {
        /* we ignore class loading errors due to incomplete paths that people often have */
    }

    private static final Field classAnnotationData = ReflectionUtil.lookupField(Class.class, "annotationData");

    void handleClass(Class<?> clazz) {
        Object initialAnnotationData;
        try {
            initialAnnotationData = classAnnotationData.get(clazz);
        } catch (IllegalAccessException e) {
            throw GraalError.shouldNotReachHere(e);
        }

        boolean inPlatform = true;
        boolean isHostedOnly = false;

        AnnotatedElement cur = clazz.getPackage();
        if (cur == null) {
            cur = clazz;
        }
        do {
            Platforms platformsAnnotation;
            try {
                platformsAnnotation = classLoaderSupport.annotationExtractor.extractAnnotation(cur, Platforms.class, false);
            } catch (Throwable t) {
                handleClassLoadingError(t);
                return;
            }
            if (containsHostedOnly(platformsAnnotation)) {
                isHostedOnly = true;
            } else if (!NativeImageGenerator.includedIn(platform, platformsAnnotation)) {
                inPlatform = false;
            }

            if (cur instanceof Package) {
                cur = clazz;
            } else {
                try {
                    cur = ((Class<?>) cur).getEnclosingClass();
                } catch (Throwable t) {
                    handleClassLoadingError(t);
                    cur = null;
                }
            }
        } while (cur != null);

        if (inPlatform) {
            if (isHostedOnly) {
                synchronized (hostedOnlyClasses) {
                    hostedOnlyClasses.add(clazz);
                }

            } else {
                synchronized (applicationClasses) {
                    applicationClasses.add(clazz);
                }
                findSystemElements(clazz);
            }
        }

        try {
            /*
             * Annotations should not be computed during the scanning of classes, to avoid issues
             * with the Native Image module access setup.
             */
            assert classAnnotationData.get(clazz) == initialAnnotationData;
        } catch (IllegalAccessException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static boolean containsHostedOnly(Platforms platformsAnnotation) {
        if (platformsAnnotation != null) {
            for (Class<? extends Platform> platformClass : platformsAnnotation.value()) {
                if (platformClass == Platform.HOSTED_ONLY.class) {
                    return true;
                }
            }
        }
        return false;
    }

    public Enumeration<URL> findResourcesByName(String resource) throws IOException {
        return classLoaderSupport.getClassLoader().getResources(resource);
    }

    public InputStream findResourceAsStreamByName(String resource) {
        return classLoaderSupport.getClassLoader().getResourceAsStream(resource);
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

    /** Find class or fail if exception occurs. */
    public Class<?> findClassOrFail(String name) {
        return findClass(name).getOrFail();
    }

    /** Find class, return result encoding class or failure reason. */
    public TypeResult<Class<?>> findClass(String name) {
        return findClass(name, true);
    }

    /** Find class, return result encoding class or failure reason. */
    public TypeResult<Class<?>> findClass(String name, boolean allowPrimitives) {
        try {
            if (allowPrimitives && name.indexOf('.') == -1) {
                switch (name) {
                    case "boolean":
                        return TypeResult.forClass(boolean.class);
                    case "char":
                        return TypeResult.forClass(char.class);
                    case "float":
                        return TypeResult.forClass(float.class);
                    case "double":
                        return TypeResult.forClass(double.class);
                    case "byte":
                        return TypeResult.forClass(byte.class);
                    case "short":
                        return TypeResult.forClass(short.class);
                    case "int":
                        return TypeResult.forClass(int.class);
                    case "long":
                        return TypeResult.forClass(long.class);
                    case "void":
                        return TypeResult.forClass(void.class);
                }
            }
            return TypeResult.forClass(forName(name));
        } catch (ClassNotFoundException | LinkageError ex) {
            return TypeResult.forException(name, ex);
        }
    }

    public Class<?> forName(String className) throws ClassNotFoundException {
        return forName(className, false);
    }

    public Class<?> forName(String className, boolean initialize) throws ClassNotFoundException {
        return Class.forName(className, initialize, classLoaderSupport.getClassLoader());
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
        ArrayList<Class<? extends T>> result = new ArrayList<>();
        addSubclasses(applicationClasses, baseClass, result);
        if (includeHostedOnly) {
            addSubclasses(hostedOnlyClasses, baseClass, result);
        }
        return result;
    }

    private static <T> void addSubclasses(EconomicSet<Class<?>> classes, Class<T> baseClass, ArrayList<Class<? extends T>> result) {
        for (Class<?> systemClass : classes) {
            if (baseClass.isAssignableFrom(systemClass)) {
                result.add(systemClass.asSubclass(baseClass));
            }
        }
    }

    public List<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotationClass, boolean includeHostedOnly) {
        ArrayList<Class<?>> result = new ArrayList<>();
        addAnnotatedClasses(applicationClasses, annotationClass, result);
        if (includeHostedOnly) {
            addAnnotatedClasses(hostedOnlyClasses, annotationClass, result);
        }
        return result;
    }

    private void addAnnotatedClasses(EconomicSet<Class<?>> classes, Class<? extends Annotation> annotationClass, ArrayList<Class<?>> result) {
        for (Class<?> systemClass : classes) {
            if (classLoaderSupport.annotationExtractor.hasAnnotation(systemClass, annotationClass)) {
                result.add(systemClass);
            }
        }
    }

    public List<Method> findAnnotatedMethods(Class<? extends Annotation> annotationClass) {
        ArrayList<Method> result = new ArrayList<>();
        for (Method method : systemMethods) {
            if (classLoaderSupport.annotationExtractor.hasAnnotation(method, annotationClass)) {
                result.add(method);
            }
        }
        return result;
    }

    public List<Method> findAnnotatedMethods(Class<? extends Annotation>[] annotationClasses) {
        ArrayList<Method> result = new ArrayList<>();
        for (Method method : systemMethods) {
            boolean match = true;
            for (Class<? extends Annotation> annotationClass : annotationClasses) {
                if (!classLoaderSupport.annotationExtractor.hasAnnotation(method, annotationClass)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                result.add(method);
            }
        }
        return result;
    }

    public List<Field> findAnnotatedFields(Class<? extends Annotation> annotationClass) {
        ArrayList<Field> result = new ArrayList<>();
        for (Field field : systemFields) {
            if (classLoaderSupport.annotationExtractor.hasAnnotation(field, annotationClass)) {
                result.add(field);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Class<? extends Annotation>> allAnnotations() {
        return StreamSupport.stream(applicationClasses.spliterator(), false)
                        .filter(Class::isAnnotation)
                        .map(clazz -> (Class<? extends Annotation>) clazz)
                        .collect(Collectors.toList());
    }

    /**
     * Returns all annotations on classes, methods, and fields (enabled or disabled based on the
     * parameters) of the given annotation class.
     */
    public <T extends Annotation> List<T> findAnnotations(Class<T> annotationClass) {
        List<T> result = new ArrayList<>();
        for (Class<?> clazz : findAnnotatedClasses(annotationClass, false)) {
            result.add(classLoaderSupport.annotationExtractor.extractAnnotation(clazz, annotationClass, false));
        }
        for (Method method : findAnnotatedMethods(annotationClass)) {
            result.add(classLoaderSupport.annotationExtractor.extractAnnotation(method, annotationClass, false));
        }
        for (Field field : findAnnotatedFields(annotationClass)) {
            result.add(classLoaderSupport.annotationExtractor.extractAnnotation(field, annotationClass, false));
        }
        return result;
    }

    public ClassLoader getClassLoader() {
        return classLoaderSupport.getClassLoader();
    }

    public Optional<String> getMainClassFromModule(Object module) {
        return classLoaderSupport.getMainClassFromModule(module);
    }

    public Optional<Module> findModule(String moduleName) {
        return classLoaderSupport.findModule(moduleName);
    }

    public String getMainClassNotFoundErrorMessage(String className) {
        List<Path> classPath = applicationClassPath();
        String classPathString = classPath.isEmpty() ? "" : String.format("%nclasspath: '%s'", pathsToString(classPath));
        List<Path> modulePath = applicationModulePath();
        String modulePathString = modulePath.isEmpty() ? "" : String.format("%nmodulepath: '%s'", pathsToString(modulePath));
        return String.format("Main entry point class '%s' neither found on the classpath nor on the modulepath.%s%s", className, classPathString, modulePathString);
    }

    private static String pathsToString(List<Path> paths) {
        return paths.stream().map(n -> String.valueOf(n)).collect(Collectors.joining(File.pathSeparator));
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
}
