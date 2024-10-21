/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.VMError;

import com.oracle.svm.util.ModuleSupport;
import jdk.internal.jimage.BasicImageReader;
import jdk.internal.jimage.ImageLocation;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * A classloader, that reads class files and resources from a jimage file at image build time.
 */
public class LibGraalClassLoader extends ClassLoader {

    /**
     * Reader for the image.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final BasicImageReader imageReader;

    /**
     * Map from the name of a resource (without module qualifier) to its path in the image.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Map<String, String> resources = new HashMap<>();

    /**
     * Map from the {@linkplain Class#forName(String) name} of a class to the image path of its
     * class file.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Map<String, String> classes = new HashMap<>();

    /**
     * Map from a service name to a list of providers.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Map<String, List<String>> services = new HashMap<>();

    /**
     * Map from the {@linkplain Class#forName(String) name} of a class to the name of its enclosing
     * module.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Map<String, String> modules;

    /**
     * Modules in which Graal classes and their dependencies are defined.
     */
    private static final Set<String> LIBGRAAL_MODULES = Set.of(
                    "jdk.internal.vm.ci",
                    "org.graalvm.collections",
                    "org.graalvm.word",
                    "org.graalvm.nativeimage",
                    "jdk.graal.compiler",
                    "org.graalvm.truffle.compiler",
                    "com.oracle.graal.graal_enterprise");

    static {
        ClassLoader.registerAsParallelCapable();
    }

    /**
     * @param imagePath path to the runtime image of a Java installation
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    LibGraalClassLoader(Path imagePath) {
        super("LibGraalClassLoader", null);
        Map<String, String> modulesMap = new HashMap<>();
        try {
            // Need access to jdk.internal.jimage
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, getClass(), false,
                            "java.base", "jdk.internal.jimage");
            this.imageReader = BasicImageReader.open(imagePath);
            for (var entry : imageReader.getEntryNames()) {
                int secondSlash = entry.indexOf('/', 1);
                if (secondSlash != -1) {
                    String module = entry.substring(1, secondSlash);
                    if (LIBGRAAL_MODULES.contains(module)) {
                        String resource = entry.substring(secondSlash + 1);
                        resources.put(resource, entry);
                        if (resource.endsWith(".class")) {
                            String className = resource.substring(0, resource.length() - ".class".length()).replace('/', '.');
                            classes.put(className, entry);
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

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.modules = Map.copyOf(modulesMap);
    }

    /**
     * Gets an unmodifiable map from the {@linkplain Class#forName(String) name} of a class to the
     * name of its enclosing module.
     */
    public Map<String, String> getModules() {
        return modules;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!SubstrateUtil.HOSTED || !classes.containsKey(name)) {
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public Class<?> loadClassOrFail(Class<?> c) {
        if (c.getClassLoader() == this) {
            return c;
        }
        if (c.isArray()) {
            return loadClassOrFail(c.getComponentType()).arrayType();
        }
        return loadClassOrFail(c.getName());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Class<?> loadClassOrFail(String name) {
        try {
            return loadClass(name);
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere("%s unable to load class '%s'", getName(), name);
        }
    }

    @Override
    protected Class<?> findClass(final String name)
                    throws ClassNotFoundException {
        if (SubstrateUtil.HOSTED) {
            String path = name.replace('.', '/').concat(".class");

            String pathInImage = resources.get(path);
            if (pathInImage != null) {
                ImageLocation location = imageReader.findLocation(pathInImage);
                if (location != null) {
                    ByteBuffer bb = Objects.requireNonNull(imageReader.getResourceBuffer(location));
                    ProtectionDomain pd = null;
                    return super.defineClass(name, bb, pd);
                }
            }
        }
        throw new ClassNotFoundException(name);
    }

    /**
     * Name of the protocol for accessing a {@link java.util.ServiceLoader} provider-configuration
     * file named by the {@code "META-INF/services/"} convention.
     */
    private static final String SERVICE_PROTOCOL = "service-config";

    /**
     * Name of the protocol for accessing entries in {@link #resources}.
     */
    private static final String RESOURCE_PROTOCOL = "resource";

    @Platforms(Platform.HOSTED_ONLY.class) //
    private URLStreamHandler serviceHandler;

    @Override
    protected URL findResource(String name) {
        if (SubstrateUtil.HOSTED) {
            URLStreamHandler handler = this.serviceHandler;
            if (handler == null) {
                this.serviceHandler = handler = new ImageURLStreamHandler();
            }
            if (name.startsWith("META-INF/services/")) {
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
                String path = resources.get(name);
                if (path != null) {
                    try {
                        var uri = new URI(RESOURCE_PROTOCOL, name, null);
                        return URL.of(uri, handler);
                    } catch (URISyntaxException | MalformedURLException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        if (!SubstrateUtil.HOSTED) {
            return Collections.emptyEnumeration();
        }
        return new Enumeration<>() {
            private URL next = findResource(name);

            @Override
            public boolean hasMoreElements() {
                return (next != null);
            }

            @Override
            public URL nextElement() {
                if (next == null) {
                    throw new NoSuchElementException();
                }
                URL u = next;
                next = null;
                return u;
            }
        };
    }

    /**
     * A {@link URLStreamHandler} for use with URLs returned by
     * {@link LibGraalClassLoader#findResource(java.lang.String)}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private class ImageURLStreamHandler extends URLStreamHandler {
        @Override
        public URLConnection openConnection(URL u) {
            String protocol = u.getProtocol();
            if (protocol.equalsIgnoreCase(SERVICE_PROTOCOL)) {
                List<String> providers = services.get(u.getPath());
                if (providers != null) {
                    return new ImageURLConnection(u, String.join("\n", providers).getBytes());
                }
            } else if (protocol.equalsIgnoreCase(RESOURCE_PROTOCOL)) {
                String pathInImage = resources.get(u.getPath());
                if (pathInImage != null) {
                    byte[] bytes = Objects.requireNonNull(imageReader.getResource(pathInImage));
                    return new ImageURLConnection(u, bytes);
                }
            }
            throw new IllegalArgumentException(u.toString());
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
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
