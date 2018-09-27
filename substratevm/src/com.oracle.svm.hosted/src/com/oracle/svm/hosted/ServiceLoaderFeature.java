/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package com.oracle.svm.hosted;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeReflection;

/**
 * Adds all service loader files and classes except for system ones.
 *
 * <p>
 * Each used service implementation class is added for reflection (using
 * {@link org.graalvm.nativeimage.RuntimeReflection#register(Class[])})
 * and for reflective instantiation (using
 * {@link org.graalvm.nativeimage.RuntimeReflection#registerForReflectiveInstantiation(Class[])}).
 * <p>
 * Each service loader file (resources/META-INF/services/*) is added as a resource to the image.
 *
 */
@AutomaticFeature
public class ServiceLoaderFeature implements Feature {
    /**
     * Command line options for this feature.
     */
    public static class Options {
        @Option(help = "When enabled, service loader files will be included in image as resources, and implementation "
                + "classes will be enabled for reflection")
        public static final HostedOptionKey<Boolean> EnableServiceLoader = new HostedOptionKey<>(false);
        @Option(help = "When enabled, each service loader resource and class will be printed out to standard output")
        public static final HostedOptionKey<Boolean> ServiceLoaderDebug = new HostedOptionKey<>(false);
    }

    // key is service interface class name
    private final Map<String, ServiceLoaderManifest> serviceLoaderManifests = new HashMap<>();
    private boolean debug;

    public ServiceLoaderFeature() {
        this.debug = Options.ServiceLoaderDebug.getValue();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!Options.EnableServiceLoader.getValue()) {
            return;
        }

        // process all service loader manifests and store them for future reference
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader instanceof URLClassLoader) {
            URLClassLoader classLoader = (URLClassLoader) contextClassLoader;

            try {
                processClassLoader(classLoader);
            } catch (ServiceFinderException e) {
                throw UserError.abort(e.getMessage(), e);
            }
        } else {
            throw UserError.abort("Context classloader is not a URLClassLoader, service manifests cannot be located");
        }

        if (debug) {
            serviceLoaderManifests.forEach((serviceClass, manifest) -> {
                System.out.println("Discovered service loader: " + manifest.resourceLocation);
                for (String impl : manifest.serviceImplementationClasses) {
                    System.out.print('\t');
                    System.out.println(impl);
                }
            });
        }
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        DuringAnalysisAccessImpl accessImpl = (DuringAnalysisAccessImpl) access;

        boolean requiresUpdate = false;
        List<AnalysisType> types = accessImpl.getUniverse().getTypes();

        Set<String> serviceInterfaceClasses = serviceLoaderManifests.keySet();

        for (AnalysisType type : types) {
            String className = type.toClassName();

            if (serviceInterfaceClasses.contains(className)) {
                if (debug) {
                    System.out.println("Discovered service use: " + className);
                }

                // this interface is loaded
                // service interface found - it is used and I must add it
                ServiceLoaderManifest manifest = serviceLoaderManifests.remove(className);
                if (null == manifest) {
                    System.out.println("Unexpected: service " + className + " is known, yet manifest is missing");
                    continue;
                }
                StringBuilder resourceValue = new StringBuilder(1024);
                for (String aClass : manifest.serviceImplementationClasses) {
                    Class<?> serviceImplClass = access.findClassByName(aClass);
                    registerReflection(serviceImplClass, access);
                    resourceValue.append(aClass);
                    resourceValue.append('\n');
                }

                registerResource(manifest.resourceLocation, resourceValue.toString());
                requiresUpdate = true;
            }
        }

        if (requiresUpdate) {
            access.requireAnalysisIteration();
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (debug && !serviceLoaderManifests.isEmpty()) {
            System.out.println("The following services (and their implementations) were not added to the image:");
            for (String serviceInterface : serviceLoaderManifests.keySet()) {
                System.out.print('\t');
                System.out.println(serviceInterface);
            }
        }
    }

    private void registerResource(String resourceLocation, String stringValue) {
        Resources.registerResource(resourceLocation, new ByteArrayInputStream(stringValue.getBytes(StandardCharsets.UTF_8)));
    }

    private void registerReflection(Class<?> aClass, DuringAnalysisAccess access) {
        access.registerAsUsed(aClass);
        RuntimeReflection.register(aClass);
        RuntimeReflection.registerForReflectiveInstantiation(aClass);
    }

    private void processClassLoader(URLClassLoader classLoader) {
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

        for (Path element : todo) {
            try {
                if (Files.isDirectory(element)) {
                    scanExpanded(element, "");
                } else {
                    scanJar(element);
                }
            } catch (IOException ex) {
                throw new ServiceFinderException("Unable to handle classpath element '" + element + "'. Make sure that all "
                                                         + "classpath entries are "
                                                         + "either directories or valid jar files.", ex);
            }
        }
    }

    private void scanExpanded(Path toProcess,
                              String relativePath) throws IOException {
        if (Files.isDirectory(toProcess)) {
            Files.list(toProcess)
                    .forEach(path -> {
                        try {
                            scanExpanded(path,
                                         relativePath.isEmpty()
                                                 ? path.getFileName().toString()
                                                 : (relativePath + "/" + path.getFileName()));
                        } catch (IOException e) {
                            throw new ServiceFinderException("Failed to process classpath element: " + path, e);
                        }
                    });
        } else {
            if (matches(relativePath)) {
                ServiceLoaderManifest manifest = serviceLoaderManifests
                        .computeIfAbsent(toServiceInterfaceClassName(relativePath),
                                         key -> new ServiceLoaderManifest(relativePath));

                Files.readAllLines(toProcess).stream()
                        .map(String::trim)
                        .filter(line -> !line.startsWith("#"))
                        .filter(line -> !line.isEmpty())
                        .forEach(manifest::add);
            }
        }
    }

    private String toServiceInterfaceClassName(String resourcePath) {
        // such as META-INF/services/a.b.c.ServiceInterface
        int index = resourcePath.lastIndexOf('/');
        if (index > 0) {
            return resourcePath.substring(index + 1);
        }

        return resourcePath;
    }

    private void scanJar(Path element) throws IOException {
        try (JarFile jf = new JarFile(element.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (e.getName().endsWith("/")) {
                    continue;
                }
                String relativePath = e.getName();

                if (matches(relativePath)) {
                    try (InputStream is = jf.getInputStream(e)) {
                        ServiceLoaderManifest manifest = serviceLoaderManifests
                                .computeIfAbsent(toServiceInterfaceClassName(relativePath),
                                                 key -> new ServiceLoaderManifest(relativePath));

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
                                    manifest.add(line);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean matches(String relativePath) {
        return relativePath.startsWith("META-INF/services");
    }

    static final class ServiceFinderException extends RuntimeException {
        private static final long serialVersionUID = 47;

        private ServiceFinderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ServiceLoaderManifest {
        private final Set<String> serviceImplementationClasses = new LinkedHashSet<>();
        private final String resourceLocation;

        private ServiceLoaderManifest(String resourceLocation) {
            this.resourceLocation = resourceLocation;
        }

        void add(String className) {
            serviceImplementationClasses.add(className);
        }
    }
}
