/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package com.oracle.svm.core.jdk;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeReflection;

/**
 * Adds all service loader files and classes except for system ones.
 *
 * <p>
 * Each service implementation class is added for reflection (using
 * {@link org.graalvm.nativeimage.RuntimeReflection#register(Class[])})
 * and for reflective instantiation (using
 * {@link org.graalvm.nativeimage.RuntimeReflection#registerForReflectiveInstantiation(Class[])}).
 * <p>
 * Each service loader file (resources/META-INF/services/*) is added as a resource to the image.
 *
 * <p>
 * Excluded services:
 * <ul>
 * <li>{@code META-INF/services/com.sun.*}</li>
 * <li>{@code META-INF/services/sun.*}</li>
 * <li>{@code META-INF/services/javax.print.*}</li>
 * <li>{@code META-INF/services/javax.sound.*}</li>
 * <li>{@code META-INF/services/javax.script.*}</li>
 * <li>{@code META-INF/services/java.nio.file.spi.FileSystemProvider}</li>
 * <li>{@code META-INF/services/org.graalvm.*}</li>
 * <li>{@code META-INF/services/jdk.vm.*}</li>
 * <li>{@code META-INF/services/com.oracle.truffle.*}</li>
 * </ul>
 *
 * This feature expects that each service resource file is combined (e.g. by using shade plugin ServicesResourceTransformer).
 */
@AutomaticFeature
public class ServiceLoaderFeature implements Feature {
    private static final Set<String> IGNORED_SERVICE_PREFIXES = new HashSet<>();

    static {
        // java internal stuff
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/com.sun.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/sun.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/javax.print.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/javax.sound.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/javax.script.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/java.nio.file.spi.FileSystemProvider");

        // Graal & Substrate VM internal stuff
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/org.graalvm.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/jdk.vm.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/com.oracle.truffle.");
    }

    public static class Options {
        @Option(help = "When enabled, service loader files will be included in image as resources, and implementation "
                + "classes will be enabled for reflection")
        public static final HostedOptionKey<Boolean> EnableServiceLoader = new HostedOptionKey<>(false);
        @Option(help = "When enabled, each service loader resource and class will be printed out to standard output")
        public static final HostedOptionKey<Boolean> ServiceLoaderDebug = new HostedOptionKey<>(false);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!Options.EnableServiceLoader.getValue()) {
            return;
        }

        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader instanceof URLClassLoader) {
            URLClassLoader classLoader = (URLClassLoader) contextClassLoader;

            try {
                processClassLoader(classLoader, access);
            } catch (ServiceFinderException e) {
                throw UserError.abort(e.getMessage(), e);
            }
        } else {
            throw UserError.abort("Context classloader is not a URLClassLoader, service manifests cannot be located");
        }
    }

    private void processClassLoader(URLClassLoader classLoader, BeforeAnalysisAccess access) {
        process(classLoader,
                access,
                // callback to register resource files to be added to image
                Resources::registerResource,
                // callback to register service implementation classes for reflection
                this::registerReflection);
    }

    private void registerReflection(Class<?> aClass) {
        RuntimeReflection.register(aClass);
        RuntimeReflection.registerForReflectiveInstantiation(aClass);
    }

    void process(URLClassLoader classLoader,
                 BeforeAnalysisAccess access,
                 BiConsumer<String, InputStream> resourceConsumer,
                 Consumer<Class<?>> serviceClassConsumer) {

        Set<Path> todo = new HashSet<>();

        for (URL url : classLoader.getURLs()) {
            try {
                todo.add(Paths.get(url.toURI()));
            } catch (URISyntaxException | IllegalArgumentException e) {
                throw new ServiceFinderException("Unable to handle classpath element '" + url
                        .toExternalForm() + "'. Make sure that all classpath entries are either directories or valid jar "
                                                         + "files.", e);
            }
        }

        Set<String> found = new HashSet<>();

        for (Path element : todo) {
            try {
                if (Files.isDirectory(element)) {
                    scanExpanded(resourceConsumer, found, element, "");
                } else {
                    scanJar(resourceConsumer, found, element);
                }
            } catch (IOException ex) {
                throw new ServiceFinderException("Unable to handle classpath element '" + element + "'. Make sure that all "
                                                         + "classpath entries are "
                                                         + "either directories or valid jar files.", ex);
            }
        }

        boolean debug = Options.ServiceLoaderDebug.getValue();

        for (String className : found) {
            try {
                if (debug) {
                    System.out.println("Found service implementation class: " + className);
                }
                serviceClassConsumer.accept(access.findClassByName(className));
            } catch (Exception e) {
                throw new ServiceFinderException("A ServiceLoader service implementation " + className + " could not be created",
                                                 e);
            }
        }
    }

    private void scanExpanded(BiConsumer<String, InputStream> consumer,
                              Set<String> found,
                              Path toProcess,
                              String relativePath) throws IOException {
        if (Files.isDirectory(toProcess)) {
            Files.list(toProcess)
                    .forEach(path -> {
                        try {
                            scanExpanded(consumer,
                                         found,
                                         path,
                                         relativePath.isEmpty()
                                                 ? path.getFileName().toString()
                                                 : (relativePath + "/" + path.getFileName()));
                        } catch (IOException e) {
                            throw new ServiceFinderException("Failed to process classpath element: " + path, e);
                        }
                    });
        } else {
            if (matches(relativePath)) {
                Files.readAllLines(toProcess).stream()
                        .map(String::trim)
                        .filter(line -> !line.startsWith("#"))
                        .filter(line -> !line.isEmpty())
                        .forEach(found::add);

                consumer.accept(relativePath, Files.newInputStream(toProcess));
            }
        }
    }

    private static void scanJar(BiConsumer<String, InputStream> consumer,
                                Set<String> found,
                                Path element) throws IOException {
        try (JarFile jf = new JarFile(element.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (e.getName().endsWith("/")) {
                    continue;
                }
                if (matches(e.getName())) {
                    try (InputStream is = jf.getInputStream(e)) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = is.read(buffer)) > 0) {
                            baos.write(buffer, 0, read);
                        }
                        try (BufferedReader br =
                                new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(baos.toByteArray())))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                line = line.trim();
                                if (!line.startsWith("#") && !line.isEmpty()) {
                                    found.add(line);
                                }
                            }
                        }
                    }
                    try (InputStream is = jf.getInputStream(e)) {
                        consumer.accept(e.getName(), is);
                    }
                }
            }
        }
    }

    private static boolean matches(String relativePath) {
        if (relativePath.startsWith("META-INF/services")) {
            // good start, now let`s filter out services we are NOT interested in¨
            for (String prefix : IGNORED_SERVICE_PREFIXES) {
                if (relativePath.startsWith(prefix)) {
                    return false;
                }
            }

            if (Options.ServiceLoaderDebug.getValue()) {
                System.out.println("Found service resource: " + relativePath);
            }

            return true;
        }
        return false;
    }

    static final class ServiceFinderException extends RuntimeException {
        private static final long serialVersionUID = 47;

        private ServiceFinderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
