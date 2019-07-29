/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ModuleSupport;

public final class ImageClassLoader {

    private static final String CLASS_EXTENSION = ".class";
    private static final int CLASS_EXTENSION_LENGTH = CLASS_EXTENSION.length();
    private static final int CLASS_LOADING_TIMEOUT_IN_MINUTES = 10;

    static {
        /*
         * ImageClassLoader is one of the first classes used during image generation, so early
         * enough to ensure that we can use the Word type.
         */
        Word.ensureInitialized();
    }

    final Platform platform;
    private final NativeImageClassLoader classLoader;
    private final String[] classpath;
    private final EconomicSet<Class<?>> applicationClasses = EconomicSet.create();
    private final EconomicSet<Class<?>> hostedOnlyClasses = EconomicSet.create();
    private final EconomicSet<Method> systemMethods = EconomicSet.create();
    private final EconomicSet<Field> systemFields = EconomicSet.create();

    private ImageClassLoader(Platform platform, String[] classpath, NativeImageClassLoader classLoader) {
        this.platform = platform;
        this.classpath = classpath;
        this.classLoader = classLoader;
    }

    public static ImageClassLoader create(Platform platform, String[] classpathAll, NativeImageClassLoader classLoader) {
        ArrayList<String> classpathFiltered = new ArrayList<>(classpathAll.length);
        classpathFiltered.addAll(Arrays.asList(classpathAll));

        /* If the GraalVM SDK is on the boot class path, and it contains annotated types. */
        final String sunBootClassPath = System.getProperty("sun.boot.class.path");
        if (sunBootClassPath != null) {
            for (String s : sunBootClassPath.split(File.pathSeparator)) {
                if (s.contains("graal-sdk")) {
                    classpathFiltered.add(s);
                }
            }
        }

        ImageClassLoader result = new ImageClassLoader(platform, classpathFiltered.toArray(new String[classpathFiltered.size()]), classLoader);
        result.initAllClasses();
        return result;
    }

    private static Path toRealPath(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Path.toRealPath failed for " + p, e);
        }
    }

    private void initAllClasses() {
        final ForkJoinPool executor = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

        if (JavaVersionUtil.JAVA_SPEC > 8) {
            for (String moduleResource : ModuleSupport.getJVMCIModuleResources()) {
                if (moduleResource.endsWith(CLASS_EXTENSION)) {
                    executor.execute(() -> handleClassFileName(moduleResource, '/'));
                }
            }
        }

        Set<Path> uniquePaths = new TreeSet<>(Comparator.comparing(ImageClassLoader::toRealPath));
        uniquePaths.addAll(
                        Arrays.stream(classpath)
                                        .flatMap(ImageClassLoader::toClassPathEntries)
                                        .collect(Collectors.toList()));
        uniquePaths.parallelStream().forEach(path -> loadClassesFromPath(executor, path));

        executor.awaitQuiescence(CLASS_LOADING_TIMEOUT_IN_MINUTES, TimeUnit.MINUTES);
    }

    static Stream<Path> toClassPathEntries(String classPathEntry) {
        Path entry = ClasspathUtils.stringToClasspath(classPathEntry);
        if (entry.endsWith(ClasspathUtils.cpWildcardSubstitute)) {
            try {
                return Files.list(entry.getParent()).filter(ClasspathUtils::isJar);
            } catch (IOException e) {
                return Stream.empty();
            }
        }
        if (Files.isReadable(entry)) {
            return Stream.of(entry);
        }
        return Stream.empty();
    }

    private static Set<Path> excludeDirectories = getExcludeDirectories();

    private static Set<Path> getExcludeDirectories() {
        Path root = Paths.get("/");
        return Arrays.asList("dev", "sys", "proc", "etc", "var", "tmp", "boot", "lost+found")
                        .stream().map(root::resolve).collect(Collectors.toSet());
    }

    private void loadClassesFromPath(ForkJoinPool executor, Path path) {
        if (Files.exists(path)) {
            if (Files.isRegularFile(path)) {
                try {
                    URI jarURI = new URI("jar:" + path.toAbsolutePath().toUri());
                    FileSystem probeJarFileSystem;
                    try {
                        probeJarFileSystem = FileSystems.newFileSystem(jarURI, Collections.emptyMap());
                    } catch (UnsupportedOperationException e) {
                        /* Silently ignore invalid jar-files on image-classpath */
                        probeJarFileSystem = null;
                    }
                    if (probeJarFileSystem != null) {
                        try (FileSystem jarFileSystem = probeJarFileSystem) {
                            initAllClasses(jarFileSystem.getPath("/"), Collections.emptySet(), executor);
                        }
                    }
                } catch (ClosedByInterruptException ignored) {
                    throw new InterruptImageBuilding();
                } catch (IOException e) {
                    throw shouldNotReachHere(e);
                } catch (URISyntaxException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                initAllClasses(path, excludeDirectories, executor);
            }
        }
    }

    private void findSystemElements(Class<?> systemClass) {
        try {
            for (Method systemMethod : systemClass.getDeclaredMethods()) {
                if (NativeImageGenerator.includedIn(platform, systemMethod.getAnnotation(Platforms.class))) {
                    synchronized (systemMethods) {
                        systemMethods.add(systemMethod);
                    }
                }
            }
        } catch (Throwable t) {
            handleClassLoadingError(t);
        }
        try {
            for (Field systemField : systemClass.getDeclaredFields()) {
                if (NativeImageGenerator.includedIn(platform, systemField.getAnnotation(Platforms.class))) {
                    synchronized (systemFields) {
                        systemFields.add(systemField);
                    }
                }
            }
        } catch (Throwable t) {
            handleClassLoadingError(t);
        }
    }

    @SuppressWarnings("unused")
    private void handleClassLoadingError(Throwable t) {
        /* we ignore class loading errors due to incomplete paths that people often have */
    }

    private void handleClassFileName(String unversionedClassFileName, char fileSystemSeparatorChar) {
        String unversionedClassFileNameWithoutSuffix = unversionedClassFileName.substring(0, unversionedClassFileName.length() - CLASS_EXTENSION_LENGTH);
        if (unversionedClassFileNameWithoutSuffix.equals("module-info")) {
            return;
        }
        String className = unversionedClassFileNameWithoutSuffix.replace(fileSystemSeparatorChar, '.');
        try {
            handleClass(forName(className));
        } catch (Throwable t) {
            handleClassLoadingError(t);
        }
    }

    private void initAllClasses(final Path root, Set<Path> excludes, ForkJoinPool executor) {
        FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
            private final char fileSystemSeparatorChar = root.getFileSystem().getSeparator().charAt(0);

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (excludes.contains(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (excludes.contains(file.getParent())) {
                    return FileVisitResult.SKIP_SIBLINGS;
                }
                String fileName = root.relativize(file).toString();
                if (fileName.endsWith(CLASS_EXTENSION)) {
                    executor.execute(() -> handleClassFileName(unversionedFileName(fileName), fileSystemSeparatorChar));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                /* Silently ignore inaccessible files or directories. */
                return FileVisitResult.CONTINUE;
            }

            /**
             * Take a file name from a possibly-multi-versioned jar file and remove the versioning
             * information. See https://docs.oracle.com/javase/9/docs/api/java/util/jar/JarFile.html
             * for the specification of the versioning strings.
             *
             * Then, depend on the JDK class loading mechanism to prefer the appropriately-versioned
             * class when the class is loaded. The same class name be loaded multiple times, but
             * each request will return the same appropriately-versioned class. If a
             * higher-versioned class is not available in a lower-versioned JDK, a
             * ClassNotFoundException will be thrown, which will be handled appropriately.
             */
            private String unversionedFileName(String fileName) {
                final String versionedPrefix = "META-INF/versions/";
                final String versionedSuffix = "/";
                String result = fileName;
                if (fileName.startsWith(versionedPrefix)) {
                    final int versionedSuffixIndex = fileName.indexOf(versionedSuffix, versionedPrefix.length());
                    if (versionedSuffixIndex >= 0) {
                        result = fileName.substring(versionedSuffixIndex + versionedSuffix.length());
                    }
                }
                return result;
            }

        };

        try {
            Files.walkFileTree(root, visitor);
        } catch (IOException ex) {
            throw shouldNotReachHere(ex);
        }
    }

    private void handleClass(Class<?> clazz) {
        boolean inPlatform = true;
        boolean isHostedOnly = false;

        AnnotatedElement cur = clazz.getPackage();
        if (cur == null) {
            cur = clazz;
        }
        do {
            Platforms platformsAnnotation = cur.getAnnotation(Platforms.class);
            if (containsHostedOnly(platformsAnnotation)) {
                isHostedOnly = true;
            } else if (!NativeImageGenerator.includedIn(platform, platformsAnnotation)) {
                inPlatform = false;
            }

            if (cur instanceof Package) {
                cur = clazz;
            } else {
                cur = ((Class<?>) cur).getEnclosingClass();
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
        return classLoader.getResources(resource);
    }

    public InputStream findResourceAsStreamByName(String resource) {
        return classLoader.getResourceAsStream(resource);
    }

    public Class<?> findClassByName(String name) {
        return findClassByName(name, true);
    }

    public Class<?> findClassByName(String name, boolean failIfClassMissing) {
        try {
            if (name.indexOf('.') == -1) {
                switch (name) {
                    case "boolean":
                        return boolean.class;
                    case "char":
                        return char.class;
                    case "float":
                        return float.class;
                    case "double":
                        return double.class;
                    case "byte":
                        return byte.class;
                    case "short":
                        return short.class;
                    case "int":
                        return int.class;
                    case "long":
                        return long.class;
                    case "void":
                        return void.class;
                }
            }
            return forName(name);
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            if (failIfClassMissing) {
                throw shouldNotReachHere("class " + name + " not found");
            }
            return null;
        }
    }

    private Class<?> forName(String name) throws ClassNotFoundException {
        return Class.forName(name, false, classLoader);
    }

    public List<String> getClasspath() {
        return Collections.unmodifiableList(Arrays.asList(classpath));
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
        return classLoader;
    }

}
