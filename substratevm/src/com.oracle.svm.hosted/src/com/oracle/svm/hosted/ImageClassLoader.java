/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ClassLoaderQuery;
import com.oracle.svm.core.TypeResult;

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
    final NativeImageClassLoaderSupport classLoaderSupport;

    private final EconomicSet<Class<?>> applicationClasses = EconomicSet.create();
    private final EconomicSet<Class<?>> hostedOnlyClasses = EconomicSet.create();
    private final EconomicSet<Method> systemMethods = EconomicSet.create();
    private final EconomicSet<Field> systemFields = EconomicSet.create();

    ImageClassLoader(Platform platform, NativeImageClassLoaderSupport classLoaderSupport) {
        this.platform = platform;
        this.classLoaderSupport = classLoaderSupport;
    }

    public void initAllClasses() {
        final ForkJoinPool executor = new ForkJoinPool(Math.min(Runtime.getRuntime().availableProcessors(), CLASS_LOADING_MAX_SCALING));
        classLoaderSupport.initAllClasses(executor, this);
        boolean completed = executor.awaitQuiescence(CLASS_LOADING_TIMEOUT_IN_MINUTES, TimeUnit.MINUTES);
        if (!completed) {
            throw shouldNotReachHere("timed out while initializing classes");
        }
        executor.shutdownNow();
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
                if (annotationsAvailable(systemMethod) && NativeImageGenerator.includedIn(platform, systemMethod.getAnnotation(Platforms.class))) {
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
                if (annotationsAvailable(systemField) && NativeImageGenerator.includedIn(platform, systemField.getAnnotation(Platforms.class))) {
                    synchronized (systemFields) {
                        systemFields.add(systemField);
                    }
                }
            }
        }
    }

    /**
     * @param element The element to check
     * @return Returns true if the annotations on the {@code element} can be loaded without any
     *         errors.
     */
    private static boolean canLoadAnnotations(AnnotatedElement element) {
        try {
            element.getAnnotations();
            return true;
        } catch (Throwable t) {
            handleClassLoadingError(t);
            return false;
        }
    }

    /**
     * @param element The element to check
     * @return Returns true if and only if the the {@code element} has any annotations present and
     *         the {@link AnnotatedElement#getAnnotations()} did not throw any error.
     */
    private static boolean annotationsAvailable(AnnotatedElement element) {
        try {
            final Annotation[] annotations = element.getAnnotations();
            return annotations.length != 0;
        } catch (Throwable t) {
            handleClassLoadingError(t);
            return false;
        }
    }

    @SuppressWarnings("unused")
    static void handleClassLoadingError(Throwable t) {
        /* we ignore class loading errors due to incomplete paths that people often have */
    }

    void handleClass(Class<?> clazz) {
        boolean inPlatform = true;
        boolean isHostedOnly = false;

        AnnotatedElement cur = clazz.getPackage();
        if (cur == null) {
            cur = clazz;
        }
        do {
            if (!canLoadAnnotations(cur)) {
                return;
            }
            Platforms platformsAnnotation = cur.getAnnotation(Platforms.class);
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
        try {
            if (name.indexOf('.') == -1) {
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

    Class<?> forName(String name) throws ClassNotFoundException {
        return Class.forName(name, false, classLoaderSupport.getClassLoader());
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

    private static void addAnnotatedClasses(EconomicSet<Class<?>> classes, Class<? extends Annotation> annotationClass, ArrayList<Class<?>> result) {
        for (Class<?> systemClass : classes) {
            if (systemClass.getAnnotation(annotationClass) != null) {
                result.add(systemClass);
            }
        }
    }

    public List<Method> findAnnotatedMethods(Class<? extends Annotation> annotationClass) {
        ArrayList<Method> result = new ArrayList<>();
        for (Method method : systemMethods) {
            if (method.getAnnotation(annotationClass) != null) {
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
                if (method.getAnnotation(annotationClass) == null) {
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
            if (field.getAnnotation(annotationClass) != null) {
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
    <T extends Annotation> List<T> findAnnotations(Class<T> annotationClass) {
        List<T> result = new ArrayList<>();
        for (Class<?> clazz : findAnnotatedClasses(annotationClass, false)) {
            result.add(clazz.getAnnotation(annotationClass));
        }
        for (Method method : findAnnotatedMethods(annotationClass)) {
            result.add(method.getAnnotation(annotationClass));
        }
        for (Field field : findAnnotatedFields(annotationClass)) {
            result.add(field.getAnnotation(annotationClass));
        }
        return result;
    }

    public ClassLoader getClassLoader() {
        return classLoaderSupport.getClassLoader();
    }

    public Class<?> loadClassFromModule(Object module, String className) throws ClassNotFoundException {
        return classLoaderSupport.loadClassFromModule(module, className);
    }
}

class ClassLoaderQueryImpl implements ClassLoaderQuery {

    private final ClassLoader imageClassLoader;

    ClassLoaderQueryImpl(ClassLoader imageClassLoader) {
        this.imageClassLoader = imageClassLoader;
    }

    @Override
    public boolean isNativeImageClassLoader(ClassLoader classLoader) {
        return classLoader == imageClassLoader;
    }
}
