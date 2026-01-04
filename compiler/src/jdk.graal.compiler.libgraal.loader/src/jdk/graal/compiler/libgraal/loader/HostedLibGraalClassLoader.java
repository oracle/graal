/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.libgraal.loader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.libgraal.hosted.LibGraalLoader;

import jdk.internal.jimage.BasicImageReader;
import jdk.internal.module.ModulePath;
import jdk.internal.module.Modules;

/**
 * A classloader that reads class files and resources from a jimage file and a module path at image
 * build time. The {@code java.home} of the JDK containing the jimage can be obtained by converting
 * the bytes of {@code getResourceAsStream("META-INF/libgraal.java.home")} to a string.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class HostedLibGraalClassLoader extends ClassLoader implements LibGraalLoader {

    /**
     * Name of the system property specifying the {@code java.home} of the JDK whose runtime image
     * contains the Graal and JVMCI classes from which libgraal will be built.
     */
    private static final String LIBGRAAL_JAVA_HOME_PROPERTY_NAME = "libgraal.java.home";

    /**
     * The {@code java.home} of the JDK whose runtime image contains the Graal and JVMCI classes
     * from which libgraal will be built.
     */
    private static final Path LIBGRAAL_JAVA_HOME = Path.of(System.getProperty(LIBGRAAL_JAVA_HOME_PROPERTY_NAME, System.getProperty("java.home")));

    /**
     * Name of the system property specifying a module path for the module(s) containing
     * {@code LibGraalFeature} and its dependencies that are not available in the runtime image.
     */
    private static final String LIBGRAAL_MODULE_PATH_PROPERTY_NAME = "libgraal.module.path";

    /**
     * Reader for the image.
     */
    private final BasicImageReader imageReader;

    /**
     * A resource located in the jimage file or on the module path.
     */
    abstract static class Resource {
        final String name;

        Resource(String name) {
            this.name = name;
        }

        /**
         * Gets the bytes of the resource.
         *
         * @throws ClassNotFoundException if the bytes cannot be accessed
         */
        abstract byte[] readBytes() throws ClassNotFoundException;
    }

    /**
     * A resource located in the jimage file.
     */
    class ImageResource extends Resource {
        ImageResource(String name) {
            super(name);
        }

        @Override
        byte[] readBytes() throws ClassNotFoundException {
            byte[] resource = imageReader.getResource(name);
            if (resource == null) {
                throw new ClassNotFoundException(name);
            }
            return resource;
        }
    }

    /**
     * A resource located on the module path specified by
     * {@link #LIBGRAAL_MODULE_PATH_PROPERTY_NAME}.
     */
    static class ModulePathResource extends Resource {
        private final ModuleReference mref;

        ModulePathResource(String name, ModuleReference mref) {
            super(name);
            this.mref = mref;
        }

        @Override
        byte[] readBytes() throws ClassNotFoundException {
            try (ModuleReader reader = mref.open()) {
                Optional<InputStream> oin = reader.open(name);
                if (oin.isEmpty()) {
                    throw new ClassNotFoundException(name);
                }
                try (InputStream in = oin.get()) {
                    return in.readAllBytes();
                }
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    /**
     * Map from the name of a resource (without module qualifier) to its path in the image.
     */
    private final Map<String, Resource> resources = new LinkedHashMap<>();

    /**
     * Map from a service name to a list of providers.
     */
    private final Map<String, List<String>> services = new LinkedHashMap<>();

    /**
     * Map from the {@linkplain Class#forName(String) name} of a class to the name of its enclosing
     * module.
     */
    private final Map<String, String> modules;

    /**
     * Modules containing classes that can be annotated by {@code LibGraalService}.
     */
    private static final Set<String> LIBGRAAL_MODULES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                    "jdk.internal.vm.ci",
                    "jdk.graal.compiler",
                    "jdk.graal.compiler.management",
                    "jdk.graal.compiler.libgraal",
                    "org.graalvm.truffle.compiler",
                    "com.oracle.graal.graal_enterprise")));

    static {
        ClassLoader.registerAsParallelCapable();
    }

    /**
     * Converts the module path entry {@code s} to a {@link Path}.
     *
     * @throws RuntimeException if {@code s} does not denote a readable path
     */
    Path parseModulePathEntry(String s) {
        Path path = Path.of(s);
        if (!Files.isReadable(path)) {
            throw new RuntimeException("%s specified by the %s system property is not readable".formatted(path, LIBGRAAL_MODULE_PATH_PROPERTY_NAME));
        }
        return path;
    }

    @SuppressWarnings("unused")
    public HostedLibGraalClassLoader() {
        // This loader delegates to the class loader that loaded its own class.
        super("LibGraalClassLoader", HostedLibGraalClassLoader.class.getClassLoader());

        try {
            /*
             * Access to jdk.internal.jimage classes is needed by this Classloader implementation.
             */
            var javaBaseModule = Object.class.getModule();
            Modules.addExports(javaBaseModule, "jdk.internal.jimage", HostedLibGraalClassLoader.class.getModule());

            /*
             * The classes that will get loaded by this loader require access to several internal
             * packages of java.base. Make sure packages will be accessible to those classes.
             */
            Module unnamedModuleOfThisLoader = getUnnamedModule();
            Modules.addExports(javaBaseModule, "jdk.internal.vm", unnamedModuleOfThisLoader);
            Modules.addExports(javaBaseModule, "jdk.internal.misc", unnamedModuleOfThisLoader);

            Map<String, String> modulesMap = new LinkedHashMap<>();

            Path imagePath = LIBGRAAL_JAVA_HOME.resolve(Path.of("lib", "modules"));
            this.imageReader = BasicImageReader.open(imagePath);
            for (var entry : imageReader.getEntryNames()) {
                int secondSlash = entry.indexOf('/', 1);
                if (secondSlash != -1) {
                    String module = entry.substring(1, secondSlash);
                    if (LIBGRAAL_MODULES.contains(module)) {
                        String resource = entry.substring(secondSlash + 1);
                        resources.put(resource, new ImageResource(entry));
                        if (resource.endsWith(".class")) {
                            String className = resource.substring(0, resource.length() - ".class".length()).replace('/', '.');
                            if (resource.equals("module-info.class")) {
                                ModuleDescriptor md = ModuleDescriptor.read(imageReader.getResourceBuffer(imageReader.findLocation(entry)));
                                for (var p : md.provides()) {
                                    services.computeIfAbsent(p.service(), k -> new ArrayList<>()).addAll(p.providers());
                                }
                            } else {
                                modulesMap.put(className, module);
                            }
                        }
                    }
                }
            }

            String prop = System.getProperty(LIBGRAAL_MODULE_PATH_PROPERTY_NAME);
            if (prop != null) {
                ModuleFinder libgraalModulePath = ModulePath.of(Stream.of(prop.split(File.pathSeparator)).map(this::parseModulePathEntry).toArray(Path[]::new));
                for (ModuleReference mr : libgraalModulePath.findAll()) {
                    ModuleDescriptor md = mr.descriptor();
                    if (LIBGRAAL_MODULES.contains(md.name())) {
                        for (var p : md.provides()) {
                            services.computeIfAbsent(p.service(), k -> new ArrayList<>()).addAll(p.providers());
                        }
                        try (ModuleReader reader = mr.open()) {
                            reader.list().forEach(entry -> {
                                resources.put(entry, new ModulePathResource(entry, mr));
                                if (entry.endsWith(".class")) {
                                    String className = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
                                    if (!entry.equals("module-info.class")) {
                                        modulesMap.put(className, md.name());
                                    }
                                }
                            });
                        }
                    }
                }
            }

            modules = Collections.unmodifiableMap(new LinkedHashMap<>(modulesMap));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets an unmodifiable map from the {@linkplain Class#forName(String) name} of a class to the
     * name of its enclosing module.
     */
    @Override
    public Map<String, String> getClassModuleMap() {
        return modules;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!modules.containsKey(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }
            return findClass(name);
        }
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        String path = name.replace('.', '/').concat(".class");

        Resource resource = resources.get(path);
        if (resource != null) {
            ByteBuffer bb = ByteBuffer.wrap(resource.readBytes());
            ProtectionDomain pd = null;
            return super.defineClass(name, bb, pd);
        }
        throw new ClassNotFoundException(name);
    }

    /**
     * Name of the protocol for accessing a {@link java.util.ServiceLoader} provider-configuration
     * file named by the {@code "META-INF/services/"} convention.
     */
    private static final String SERVICE_PROTOCOL = "service-config";

    /**
     * Name of the protocol for accessing a file whose contents are the {@code java.home} of the JDK
     * whose runtime image contains the Graal and JVMCI * classes from which libgraal will be built.
     */
    private static final String LIBGRAAL_JAVA_HOME_PROTOCOL = "libgraal-java-home";

    /**
     * Name of the protocol for accessing entries in {@link #resources}.
     */
    private static final String RESOURCE_PROTOCOL = "resource";

    private URLStreamHandler serviceHandler;

    @Override
    protected URL findResource(String name) {
        URLStreamHandler handler = this.serviceHandler;
        if (handler == null) {
            this.serviceHandler = handler = new ImageURLStreamHandler();
        }
        if (name.equals("META-INF/libgraal.java.home")) {
            try {
                var uri = new URI(LIBGRAAL_JAVA_HOME_PROTOCOL, "libgraal.java.home", null);
                return URL.of(uri, handler);
            } catch (URISyntaxException | MalformedURLException e) {
                return null;
            }
        } else if (name.startsWith("META-INF/services/")) {
            String service = name.substring("META-INF/services/".length());
            if (services.containsKey(service)) {
                try {
                    var uri = new URI(SERVICE_PROTOCOL, service, null);
                    return URL.of(uri, handler);
                } catch (URISyntaxException | MalformedURLException e) {
                    return null;
                }
            }
        } else {
            Resource resource = resources.get(name);
            if (resource != null) {
                try {
                    var uri = new URI(RESOURCE_PROTOCOL, name, null);
                    return URL.of(uri, handler);
                } catch (URISyntaxException | MalformedURLException e) {
                    return null;
                }
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        URL resource = findResource(name);
        if (resource == null) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(List.of(resource));
    }

    /**
     * A {@link URLStreamHandler} for use with URLs returned by
     * {@link HostedLibGraalClassLoader#findResource(String)}.
     */
    private final class ImageURLStreamHandler extends URLStreamHandler {
        @Override
        public URLConnection openConnection(URL u) {
            String protocol = u.getProtocol();
            if (protocol.equalsIgnoreCase(LIBGRAAL_JAVA_HOME_PROTOCOL)) {
                if (!u.getPath().equals("libgraal.java.home")) {
                    throw new IllegalArgumentException(u.toString());
                }
                return new ImageURLConnection(u, LIBGRAAL_JAVA_HOME.toString().getBytes());
            } else if (protocol.equalsIgnoreCase(SERVICE_PROTOCOL)) {
                List<String> providers = services.get(u.getPath());
                if (providers != null) {
                    return new ImageURLConnection(u, String.join("\n", providers).getBytes());
                }
            } else if (protocol.equalsIgnoreCase(RESOURCE_PROTOCOL)) {
                Resource resource = resources.get(u.getPath());
                if (resource != null) {
                    try {
                        return new ImageURLConnection(u, resource.readBytes());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException(u.toString(), e);
                    }
                }
            }
            throw new IllegalArgumentException(u.toString());
        }
    }

    private static class ImageURLConnection extends URLConnection {
        private final byte[] bytes;
        private InputStream in;

        ImageURLConnection(URL u, byte[] bytes) {
            super(u);
            this.bytes = bytes;
        }

        @Override
        public void connect() {
            if (!connected) {
                in = new ByteArrayInputStream(bytes);
                connected = true;
            }
        }

        @Override
        public InputStream getInputStream() {
            connect();
            return in;
        }

        @Override
        public long getContentLengthLong() {
            return bytes.length;
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }
    }
}
