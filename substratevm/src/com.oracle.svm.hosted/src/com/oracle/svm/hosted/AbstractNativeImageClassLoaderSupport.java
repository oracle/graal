/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.option.HostedOptionParser;

public abstract class AbstractNativeImageClassLoaderSupport {

    final List<Path> imagecp;
    private final List<Path> buildcp;

    protected final URLClassLoader classPathClassLoader;

    protected AbstractNativeImageClassLoaderSupport(ClassLoader defaultSystemClassLoader, String[] classpath) {

        classPathClassLoader = new URLClassLoader(Util.verifyClassPathAndConvertToURLs(classpath), defaultSystemClassLoader);

        imagecp = Collections.unmodifiableList(Arrays.stream(classPathClassLoader.getURLs()).map(Util::urlToPath).collect(Collectors.toList()));
        String builderClassPathString = System.getProperty("java.class.path");
        String[] builderClassPathEntries = builderClassPathString.isEmpty() ? new String[0] : builderClassPathString.split(File.pathSeparator);
        if (Arrays.asList(builderClassPathEntries).contains(".")) {
            VMError.shouldNotReachHere("The classpath of " + NativeImageGeneratorRunner.class.getName() +
                            " must not contain \".\". This can happen implicitly if the builder runs exclusively on the --module-path" +
                            " but specifies the " + NativeImageGeneratorRunner.class.getName() + " main class without --module.");
        }
        buildcp = Collections.unmodifiableList(Arrays.stream(builderClassPathEntries)
                        .map(Paths::get).map(Path::toAbsolutePath)
                        .collect(Collectors.toList()));
    }

    List<Path> classpath() {
        return Stream.concat(imagecp.stream(), buildcp.stream()).collect(Collectors.toList());
    }

    List<Path> applicationClassPath() {
        return imagecp;
    }

    public ClassLoader getClassLoader() {
        return classPathClassLoader;
    }

    protected abstract Class<?> loadClassFromModule(Object module, String className) throws ClassNotFoundException;

    protected abstract Optional<String> getMainClassFromModule(Object module);

    protected abstract List<Path> modulepath();

    protected abstract List<Path> applicationModulePath();

    protected abstract Optional<? extends Object> findModule(String moduleName);

    private HostedOptionParser hostedOptionParser;
    private OptionValues parsedHostedOptions;
    private List<String> remainingArguments;

    public void setupHostedOptionParser(List<String> arguments) {
        hostedOptionParser = new HostedOptionParser(getClassLoader());
        remainingArguments = Collections.unmodifiableList((hostedOptionParser.parse(arguments)));
        parsedHostedOptions = new OptionValues(hostedOptionParser.getHostedValues());
    }

    public HostedOptionParser getHostedOptionParser() {
        return hostedOptionParser;
    }

    public List<String> getRemainingArguments() {
        return remainingArguments;
    }

    public OptionValues getParsedHostedOptions() {
        return parsedHostedOptions;
    }

    protected abstract void processClassLoaderOptions();

    public abstract void propagateQualifiedExports(String fromTargetModule, String toTargetModule);

    protected abstract void initAllClasses(ForkJoinPool executor, ImageClassLoader imageClassLoader);

    protected static class Util {

        static URL[] verifyClassPathAndConvertToURLs(String[] classpath) {
            Stream<Path> pathStream = new LinkedHashSet<>(Arrays.asList(classpath)).stream().flatMap(Util::toClassPathEntries);
            return pathStream.map(v -> {
                try {
                    return v.toAbsolutePath().toUri().toURL();
                } catch (MalformedURLException e) {
                    throw UserError.abort("Invalid classpath element '%s'. Make sure that all paths provided with '%s' are correct.", v, SubstrateOptions.IMAGE_CLASSPATH_PREFIX);
                }
            }).toArray(URL[]::new);
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

        static Path urlToPath(URL url) {
            try {
                return Paths.get(url.toURI());
            } catch (URISyntaxException e) {
                throw VMError.shouldNotReachHere();
            }
        }
    }

    protected class ClassInit {

        protected final ForkJoinPool executor;
        protected final ImageClassLoader imageClassLoader;

        protected ClassInit(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
            this.executor = executor;
            this.imageClassLoader = imageClassLoader;
        }

        protected void init() {
            Set<Path> uniquePaths = new TreeSet<>(Comparator.comparing(this::toRealPath));
            uniquePaths.addAll(classpath());
            uniquePaths.parallelStream().forEach(path -> loadClassesFromPath(path));
        }

        private Path toRealPath(Path p) {
            try {
                return p.toRealPath();
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("Path.toRealPath failed for " + p, e);
            }
        }

        private final Set<Path> excludeDirectories = getExcludeDirectories();

        private Set<Path> getExcludeDirectories() {
            Path root = Paths.get("/");
            return Stream.of("dev", "sys", "proc", "etc", "var", "tmp", "boot", "lost+found")
                            .map(root::resolve).collect(Collectors.toSet());
        }

        private void loadClassesFromPath(Path path) {
            if (ClasspathUtils.isJar(path)) {
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
                            loadClassesFromPath(jarFileSystem.getPath("/"), Collections.emptySet());
                        }
                    }
                } catch (ClosedByInterruptException ignored) {
                    throw new InterruptImageBuilding();
                } catch (IOException | URISyntaxException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                loadClassesFromPath(path, excludeDirectories);
            }
        }

        protected static final String CLASS_EXTENSION = ".class";

        private void loadClassesFromPath(Path root, Set<Path> excludes) {
            FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                private final char fileSystemSeparatorChar = root.getFileSystem().getSeparator().charAt(0);

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (excludes.contains(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    assert !excludes.contains(file.getParent()) : "Visiting file '" + file + "' with excluded parent directory";
                    String fileName = root.relativize(file).toString();
                    if (fileName.endsWith(CLASS_EXTENSION)) {
                        executor.execute(() -> handleClassFileName(null, fileName, fileSystemSeparatorChar));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    /* Silently ignore inaccessible files or directories. */
                    return FileVisitResult.CONTINUE;
                }
            };

            try {
                Files.walkFileTree(root, visitor);
            } catch (IOException ex) {
                throw shouldNotReachHere(ex);
            }
        }

        /**
         * Take a file name from a possibly-multi-versioned jar file and remove the versioning
         * information. See https://docs.oracle.com/javase/9/docs/api/java/util/jar/JarFile.html for
         * the specification of the versioning strings.
         *
         * Then, depend on the JDK class loading mechanism to prefer the appropriately-versioned
         * class when the class is loaded. The same class name be loaded multiple times, but each
         * request will return the same appropriately-versioned class. If a higher-versioned class
         * is not available in a lower-versioned JDK, a ClassNotFoundException will be thrown, which
         * will be handled appropriately.
         */
        private String strippedClassFileName(String fileName) {
            final String versionedPrefix = "META-INF/versions/";
            final String versionedSuffix = "/";
            String result = fileName;
            if (fileName.startsWith(versionedPrefix)) {
                final int versionedSuffixIndex = fileName.indexOf(versionedSuffix, versionedPrefix.length());
                if (versionedSuffixIndex >= 0) {
                    result = fileName.substring(versionedSuffixIndex + versionedSuffix.length());
                }
            }
            return result.substring(0, result.length() - CLASS_EXTENSION.length());
        }

        protected void handleClassFileName(Object module, String fileName, char fileSystemSeparatorChar) {
            String strippedClassFileName = strippedClassFileName(fileName);
            if (strippedClassFileName.equals("module-info")) {
                return;
            }
            String className = strippedClassFileName.replace(fileSystemSeparatorChar, '.');
            Class<?> clazz = null;
            try {
                clazz = imageClassLoader.forName(className, module);
            } catch (AssertionError error) {
                VMError.shouldNotReachHere(error);
            } catch (Throwable t) {
                ImageClassLoader.handleClassLoadingError(t);
            }
            if (clazz != null) {
                imageClassLoader.handleClass(clazz);
            }
        }
    }
}
