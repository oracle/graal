/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FilePermission;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import com.oracle.svm.core.util.UserError;

import jdk.internal.access.SharedSecrets;
import jdk.internal.loader.ClassLoaders;
import jdk.internal.loader.Resource;
import jdk.internal.loader.URLClassPath;
import jdk.internal.module.Resources;

/**
 * This custom class loader is used by the image builder to load the application classes that should
 * be built into a native-image. It can load classes from a user-provided application module- and
 * class-path. This is different from the existing classloaders that the JDK provides. While
 * {@code ModuleLayer.defineModulesWith} methods only allow loading modules at runtime,
 * {@code URLClassLoader} only allows loading classes on classpath at runtime. This is insufficient
 * for the image builder as it needs to be able to load from both, module- and class-path, with the
 * same loader so that classes on the given class-path are able to access classes from the given
 * module-path.
 *
 * <p>
 * This loader is heavily inspired by {@code jdk.internal.loader.Loader} and {@code URLClassLoader}.
 * Documentation in this class only mentions where methods diverge from their respective behaviour
 * in {@code jdk.internal.loader.Loader} and {@code URLClassLoader}. More documentation is available
 * in the original classes.
 */
final class NativeImageClassLoader extends SecureClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final ClassLoader parent;

    /* Unmodifiable maps used by this loader */
    private final Map<String, ModuleReference> localNameToModule;
    private final Map<String, LoadedModule> localPackageToModule;
    private final Map<String, ClassLoader> remotePackageToLoader;

    /* Modifiable map used by this loader */
    private final ConcurrentHashMap<ModuleReference, ModuleReader> moduleToReader;

    private final URLClassPath ucp;

    /**
     * See {@code jdk.internal.loader.Loader.LoadedModule}.
     */
    private static class LoadedModule {
        private final ModuleReference mref;
        private final URL url; // may be null
        private final CodeSource cs;

        LoadedModule(ModuleReference mref) {
            URL urlVal = null;
            if (mref.location().isPresent()) {
                try {
                    urlVal = mref.location().get().toURL();
                } catch (MalformedURLException | IllegalArgumentException e) {
                }
            }
            this.mref = mref;
            this.url = urlVal;
            this.cs = new CodeSource(urlVal, (CodeSigner[]) null);
        }

        ModuleReference mref() {
            return mref;
        }

        String name() {
            return mref.descriptor().name();
        }

        @SuppressWarnings("unused")
        URL location() {
            return url;
        }

        CodeSource codeSource() {
            return cs;
        }
    }

    /**
     * See {@code jdk.internal.loader.Loader#Loader} and
     * {@code java.net.URLClassLoader#URLClassLoader}.
     */
    NativeImageClassLoader(List<Path> classpath, Configuration configuration, ClassLoader parent) {
        super(parent);

        Objects.requireNonNull(parent);
        this.parent = parent;

        Map<String, ModuleReference> nameToModule = new HashMap<>();
        Map<String, LoadedModule> packageToModule = new HashMap<>();
        for (ResolvedModule resolvedModule : configuration.modules()) {
            ModuleReference mref = resolvedModule.reference();
            ModuleDescriptor descriptor = mref.descriptor();
            nameToModule.put(descriptor.name(), mref);
            descriptor.packages().forEach(pn -> {
                LoadedModule lm = new LoadedModule(mref);
                if (packageToModule.put(pn, lm) != null) {
                    throw new IllegalArgumentException("Package " + pn + " in more than one module");
                }
            });
        }
        localNameToModule = Collections.unmodifiableMap(nameToModule);
        localPackageToModule = Collections.unmodifiableMap(packageToModule);
        /*
         * Other than in {@code jdk.internal.loader.Loader} we initialize remotePackageToLoader here
         * which allows us to use an unmodifiable map instead of a ConcurrentHashMap.
         */
        remotePackageToLoader = initRemotePackageMap(configuration, List.of(ModuleLayer.boot()));

        /* The only map that gets updated concurrently during the lifetime of this loader. */
        moduleToReader = new ConcurrentHashMap<>();

        /* Initialize URLClassPath that is used to lookup classes from class-path. */
        ucp = new URLClassPath(classpath.stream().map(NativeImageClassLoader::pathToURL).toArray(URL[]::new), null);
    }

    private static URL pathToURL(Path p) {
        try {
            return p.toUri().toURL();
        } catch (MalformedURLException e) {
            throw UserError.abort(e, "Given path element '%s' cannot be expressed as URL.", p);
        }
    }

    /**
     * See {@code jdk.internal.loader.Loader#initRemotePackageMap}.
     */
    private Map<String, ClassLoader> initRemotePackageMap(Configuration cf, List<ModuleLayer> parentModuleLayers) {
        Map<String, ClassLoader> remotePackageMap = new HashMap<>();

        for (String name : localNameToModule.keySet()) {
            ResolvedModule resolvedModule = cf.findModule(name).get();
            assert resolvedModule.configuration() == cf;

            for (ResolvedModule other : resolvedModule.reads()) {
                String mn = other.name();
                ClassLoader loader;

                if (other.configuration() == cf) {
                    assert localNameToModule.containsKey(mn);
                    continue;
                } else {
                    ModuleLayer layer = parentModuleLayers.stream()
                                    .map(parentLayer -> findModuleLayer(parentLayer, other.configuration()))
                                    .flatMap(Optional::stream)
                                    .findAny()
                                    .orElseThrow(() -> new InternalError("Unable to find parent layer"));

                    assert layer.findModule(mn).isPresent();
                    loader = layer.findLoader(mn);
                    if (loader == null) {
                        loader = ClassLoaders.platformClassLoader();
                    }
                }

                ModuleDescriptor descriptor = other.reference().descriptor();
                if (descriptor.isAutomatic()) {
                    ClassLoader l = loader;
                    descriptor.packages().forEach(pn -> remotePackage(remotePackageMap, pn, l));
                } else {
                    String target = resolvedModule.name();
                    for (ModuleDescriptor.Exports e : descriptor.exports()) {
                        boolean delegate;
                        if (e.isQualified()) {
                            delegate = (other.configuration() == cf) && e.targets().contains(target);
                        } else {
                            delegate = true;
                        }

                        if (delegate) {
                            remotePackage(remotePackageMap, e.source(), loader);
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableMap(remotePackageMap);
    }

    /**
     * See {@code jdk.internal.loader.Loader#remotePackage}.
     */
    private static void remotePackage(Map<String, ClassLoader> map, String pn, ClassLoader loader) {
        ClassLoader l = map.putIfAbsent(pn, loader);
        if (l != null && l != loader) {
            throw new IllegalStateException("Package " + pn + " cannot be imported from multiple loaders");
        }
    }

    /**
     * See {@code jdk.internal.loader.Loader#findModuleLayer}.
     */
    private static Optional<ModuleLayer> findModuleLayer(ModuleLayer moduleLayer, Configuration cf) {
        return SharedSecrets.getJavaLangAccess().layers(moduleLayer)
                        .filter(l -> l.configuration() == cf)
                        .findAny();
    }

    /**
     * See {@code jdk.internal.loader.Loader#findResource(String mn, String name)}.
     */
    @Override
    protected URL findResource(String mn, String name) throws IOException {
        /* For unnamed module, search for resource in class-path */
        if (mn == null) {
            return ucp.findResource(name, false);
        }

        /* otherwise search in specific module */
        ModuleReference mref = localNameToModule.get(mn);
        if (mref == null) {
            return null;
        }

        URL url = null;
        Optional<URI> ouri = moduleReaderFor(mref).find(name);
        if (ouri.isPresent()) {
            try {
                url = ouri.get().toURL();
            } catch (MalformedURLException | IllegalArgumentException e) {
            }
        }

        return url;
    }

    /**
     * See {@code jdk.internal.loader.Loader#findResource(String name)}.
     */
    @Override
    public URL findResource(String name) {
        String pn = Resources.toPackageName(name);

        /* Search for resource in class-path ... */
        URL urlOnClasspath = ucp.findResource(name, false);
        if (urlOnClasspath != null) {
            return urlOnClasspath;
        }

        /* ... and in module-path */
        LoadedModule module = localPackageToModule.get(pn);
        if (module != null) {
            try {
                URL url = findResource(module.name(), name);
                if (url != null && (name.endsWith(".class") || url.toString().endsWith("/") || isOpen(module.mref(), pn))) {
                    return url;
                }
            } catch (IOException unused) {
                // ignore
            }

        } else {
            for (ModuleReference mref : localNameToModule.values()) {
                try {
                    URL url = findResource(mref.descriptor().name(), name);
                    if (url != null) {
                        return url;
                    }
                } catch (IOException unused) {
                    // ignore
                }
            }
        }

        return null;
    }

    /**
     * See {@code jdk.internal.loader.Loader#findResources}.
     */
    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        return Collections.enumeration(findResourcesAsList(name));
    }

    /**
     * See {@code jdk.internal.loader.Loader#getResource}.
     */
    @Override
    public URL getResource(String name) {
        Objects.requireNonNull(name);

        URL url = findResource(name);
        if (url == null) {
            url = parent.getResource(name);
        }
        return url;
    }

    /**
     * See {@code jdk.internal.loader.Loader#getResources}.
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Objects.requireNonNull(name);

        List<URL> urls = findResourcesAsList(name);
        Enumeration<URL> e = parent.getResources(name);

        return new Enumeration<>() {
            final Iterator<URL> iterator = urls.iterator();

            @Override
            public boolean hasMoreElements() {
                return (iterator.hasNext() || e.hasMoreElements());
            }

            @Override
            public URL nextElement() {
                if (iterator.hasNext()) {
                    return iterator.next();
                } else {
                    return e.nextElement();
                }
            }
        };
    }

    /**
     * See {@code jdk.internal.loader.Loader#findResourcesAsList}.
     */
    private List<URL> findResourcesAsList(String name) throws IOException {
        String pn = Resources.toPackageName(name);

        List<URL> urls = new ArrayList<>();

        /* Search for resource in class-path ... */
        Enumeration<URL> classPathResources = ucp.findResources(name, false);
        while (classPathResources.hasMoreElements()) {
            urls.add(classPathResources.nextElement());
        }

        /* ... and in module-path */
        LoadedModule module = localPackageToModule.get(pn);
        if (module != null) {
            URL url = findResource(module.name(), name);
            if (url != null && (name.endsWith(".class") || url.toString().endsWith("/") || isOpen(module.mref(), pn))) {
                urls.add(url);
            }
        } else {
            for (ModuleReference mref : localNameToModule.values()) {
                URL url = findResource(mref.descriptor().name(), name);
                if (url != null) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    /**
     * See {@code jdk.internal.loader.Loader#findClass(String cn)}.
     */
    @Override
    protected Class<?> findClass(String cn) throws ClassNotFoundException {
        Class<?> c;
        LoadedModule loadedModule = findLoadedModule(cn);
        if (loadedModule != null) {
            c = findClassInModuleOrNull(loadedModule, cn);
        } else {
            /* Not found in modules of this loader, try class-path instead */
            c = findClassViaClassPath(cn);
        }
        if (c == null) {
            throw new ClassNotFoundException(cn);
        }
        return c;
    }

    /**
     * See {@code java.net.URLClassLoader#findClass(java.lang.String)}.
     */
    private Class<?> findClassViaClassPath(String name) throws ClassNotFoundException {
        Class<?> result;

        String path = name.replace('.', '/').concat(".class");
        Resource res = ucp.getResource(path, false);
        if (res != null) {
            try {
                result = defineClass(name, res);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            } catch (ClassFormatError e2) {
                if (res.getDataError() != null) {
                    e2.addSuppressed(res.getDataError());
                }
                throw e2;
            }
        } else {
            return null;
        }

        return result;
    }

    /**
     * See {@code java.net.URLClassLoader#defineClass}.
     */
    private Class<?> defineClass(String name, Resource res) throws IOException {
        int i = name.lastIndexOf('.');
        URL url = res.getCodeSourceURL();
        if (i != -1) {
            String pkgname = name.substring(0, i);
            Manifest man = res.getManifest();
            if (getAndVerifyPackage(pkgname, man, url) == null) {
                try {
                    if (man != null) {
                        definePackage(pkgname, man, url);
                    } else {
                        definePackage(pkgname, null, null, null, null, null, null, null);
                    }
                } catch (IllegalArgumentException iae) {
                    if (getAndVerifyPackage(pkgname, man, url) == null) {
                        throw new AssertionError("Cannot find package " + pkgname);
                    }
                }
            }
        }
        java.nio.ByteBuffer bb = res.getByteBuffer();
        if (bb != null) {
            CodeSigner[] signers = res.getCodeSigners();
            CodeSource cs = new CodeSource(url, signers);
            return defineClass(name, bb, cs);
        } else {
            byte[] b = res.getBytes();
            CodeSigner[] signers = res.getCodeSigners();
            CodeSource cs = new CodeSource(url, signers);
            return defineClass(name, b, 0, b.length, cs);
        }
    }

    /**
     * See {@code java.net.URLClassLoader#getAndVerifyPackage}.
     */
    private Package getAndVerifyPackage(String pkgname, Manifest man, URL url) {
        Package pkg = getDefinedPackage(pkgname);
        if (pkg != null) {
            if (pkg.isSealed()) {
                if (!pkg.isSealed(url)) {
                    throw new SecurityException("Sealing violation: package " + pkgname + " is sealed");
                }
            } else {
                if ((man != null) && isSealed(pkgname, man)) {
                    throw new SecurityException("Sealing violation: can't seal package " + pkgname + ": already loaded");
                }
            }
        }
        return pkg;
    }

    /**
     * See {@code java.net.URLClassLoader#definePackage}.
     */
    private Package definePackage(String name, Manifest man, URL url) {
        String specTitle = null;
        String specVersion = null;
        String specVendor = null;
        String implTitle = null;
        String implVersion = null;
        String implVendor = null;
        String sealed = null;
        URL sealBase = null;

        Attributes attr = SharedSecrets.javaUtilJarAccess()
                        .getTrustedAttributes(man, name.replace('.', '/').concat("/"));
        if (attr != null) {
            specTitle = attr.getValue(Attributes.Name.SPECIFICATION_TITLE);
            specVersion = attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
            specVendor = attr.getValue(Attributes.Name.SPECIFICATION_VENDOR);
            implTitle = attr.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
            implVersion = attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            implVendor = attr.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
            sealed = attr.getValue(Attributes.Name.SEALED);
        }
        attr = man.getMainAttributes();
        if (attr != null) {
            if (specTitle == null) {
                specTitle = attr.getValue(Attributes.Name.SPECIFICATION_TITLE);
            }
            if (specVersion == null) {
                specVersion = attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
            }
            if (specVendor == null) {
                specVendor = attr.getValue(Attributes.Name.SPECIFICATION_VENDOR);
            }
            if (implTitle == null) {
                implTitle = attr.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
            }
            if (implVersion == null) {
                implVersion = attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            }
            if (implVendor == null) {
                implVendor = attr.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
            }
            if (sealed == null) {
                sealed = attr.getValue(Attributes.Name.SEALED);
            }
        }
        if ("true".equalsIgnoreCase(sealed)) {
            sealBase = url;
        }
        return definePackage(name, specTitle, specVersion, specVendor,
                        implTitle, implVersion, implVendor, sealBase);
    }

    /**
     * See {@code java.net.URLClassLoader#isSealed}.
     */
    private static boolean isSealed(String name, Manifest man) {
        Attributes attr = SharedSecrets.javaUtilJarAccess()
                        .getTrustedAttributes(man, name.replace('.', '/').concat("/"));
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Attributes.Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = man.getMainAttributes()) != null) {
                sealed = attr.getValue(Attributes.Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);
    }

    /**
     * See {@code jdk.internal.loader.Loader#findClass(java.lang.String, java.lang.String)}.
     */
    @Override
    protected Class<?> findClass(String mn, String cn) {
        Class<?> c = null;
        LoadedModule loadedModule = findLoadedModule(cn);
        if (loadedModule != null && loadedModule.name().equals(mn)) {
            c = findClassInModuleOrNull(loadedModule, cn);
        } else {
            /* Not found in modules of this loader, try class-path instead */
            try {
                c = findClassViaClassPath(cn);
            } catch (ClassNotFoundException ex) {
                /* Ignored, return null. */
            }
        }
        return c;
    }

    /**
     * See {@code jdk.internal.loader.Loader#loadClass(java.lang.String, boolean)}.
     */
    @Override
    protected Class<?> loadClass(String cn, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(cn)) {
            Class<?> c = findLoadedClass(cn);

            if (c == null) {
                try {
                    c = parent.loadClass(cn);
                } catch (ClassNotFoundException ignore) {
                    /* Ignore. */
                }
            }

            if (c == null) {
                LoadedModule loadedModule = findLoadedModule(cn);

                if (loadedModule != null) {
                    c = findClassInModuleOrNull(loadedModule, cn);
                } else {
                    /* Not found in modules of this loader, try class-path instead */
                    c = findClassViaClassPath(cn);

                    if (c == null) {
                        String pn = packageName(cn);
                        ClassLoader loader = remotePackageToLoader.get(pn);
                        if (loader == null) {
                            loader = parent;
                        }
                        c = loader.loadClass(cn);
                    }
                }
            }

            if (c == null) {
                throw new ClassNotFoundException(cn);
            }

            if (resolve) {
                resolveClass(c);
            }

            return c;
        }
    }

    /**
     * See {@code jdk.internal.loader.Loader#findClassInModuleOrNull}.
     */
    private Class<?> findClassInModuleOrNull(LoadedModule loadedModule, String cn) {
        return defineClass(cn, loadedModule);
    }

    /**
     * See {@code jdk.internal.loader.Loader#defineClass}.
     */
    private Class<?> defineClass(String cn, LoadedModule loadedModule) {
        ModuleReader reader = moduleReaderFor(loadedModule.mref());

        try {
            String rn = cn.replace('.', '/').concat(".class");
            ByteBuffer bb = reader.read(rn).orElse(null);
            if (bb == null) {
                return null;
            }

            try {
                return defineClass(cn, bb, loadedModule.codeSource());
            } finally {
                reader.release(bb);
            }

        } catch (IOException ioe) {
            return null;
        }
    }

    /**
     * See {@code jdk.internal.loader.Loader#getPermissions}.
     */
    @Override
    protected PermissionCollection getPermissions(CodeSource cs) {
        PermissionCollection perms = super.getPermissions(cs);

        URL url = cs.getLocation();
        if (url == null) {
            return perms;
        }

        try {
            Permission p = url.openConnection().getPermission();
            if (p != null) {
                if (p instanceof FilePermission) {
                    String path = p.getName();
                    if (path.endsWith(File.separator)) {
                        path += "-";
                        p = new FilePermission(path, "read");
                    }
                }
                perms.add(p);
            }
        } catch (IOException ioe) {
        }

        return perms;
    }

    /**
     * See {@code jdk.internal.loader.Loader#findLoadedModule}.
     */
    private LoadedModule findLoadedModule(String cn) {
        String pn = packageName(cn);
        return pn.isEmpty() ? null : localPackageToModule.get(pn);
    }

    /**
     * See {@code jdk.internal.loader.Loader#packageName}.
     */
    private static String packageName(String cn) {
        int pos = cn.lastIndexOf('.');
        return (pos < 0) ? "" : cn.substring(0, pos);
    }

    /**
     * See {@code jdk.internal.loader.Loader#moduleReaderFor}.
     */
    private ModuleReader moduleReaderFor(ModuleReference mref) {
        return moduleToReader.computeIfAbsent(mref, m -> createModuleReader(mref));
    }

    /**
     * See {@code jdk.internal.loader.Loader#createModuleReader}.
     */
    private static ModuleReader createModuleReader(ModuleReference mref) {
        try {
            return mref.open();
        } catch (IOException e) {
            return new NullModuleReader();
        }
    }

    /**
     * See {@code jdk.internal.loader.Loader#NullModuleReader}.
     */
    private static class NullModuleReader implements ModuleReader {
        @Override
        public Optional<URI> find(String name) {
            return Optional.empty();
        }

        @Override
        public Stream<String> list() {
            return Stream.empty();
        }

        @Override
        public void close() {
            throw new InternalError("Should not get here");
        }
    }

    /**
     * See {@code jdk.internal.loader.Loader#isOpen}.
     */
    private static boolean isOpen(ModuleReference mref, String pn) {
        ModuleDescriptor descriptor = mref.descriptor();
        if (descriptor.isOpen() || descriptor.isAutomatic()) {
            return true;
        }
        for (ModuleDescriptor.Opens opens : descriptor.opens()) {
            String source = opens.source();
            if (!opens.isQualified() && source.equals(pn)) {
                return true;
            }
        }
        return false;
    }
}
