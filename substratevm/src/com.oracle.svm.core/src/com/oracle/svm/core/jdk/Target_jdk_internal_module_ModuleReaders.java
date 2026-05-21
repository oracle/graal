/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntryBase;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.internal.misc.Unsafe;
import sun.net.www.ParseUtil;

/**
 * Makes build-time embedded resources compose with runtime jimage-backed resources for system
 * modules. Embedded resources take precedence over runtime jimage or module-path resources.
 * Intercepting the module reader here makes embedded resources appear as part of the system module
 * contents while keeping the rest of the JDK resource lookup code unchanged.
 */
@TargetClass(value = jdk.internal.module.SystemModuleFinders.class, innerClass = "SystemModuleReader")
final class Target_jdk_internal_module_SystemModuleFinders_SystemModuleReader {

    @Alias private String module;
    @Alias private volatile boolean closed;

    @Alias
    private native Target_jdk_internal_jimage_ImageLocation_ModuleReaders findImageLocation(String name) throws IOException;

    @Alias
    private native boolean containsImageLocation(String name) throws IOException;

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/jdk/internal/module/SystemModuleFinders.java#L421-L427")
    Optional<URI> find(String name) throws IOException {
        Objects.requireNonNull(name);
        if (closed) {
            throw new IOException("ModuleReader is closed");
        }
        Module runtimeModule = ModuleLayer.boot().findModule(module).orElse(null);
        Optional<URI> embedded = ResourceBasedModuleReaderSupport.getEmbeddedResourceURI(runtimeModule, name);
        if (embedded.isPresent()) {
            return embedded;
        }
        if (JRTSupport.Options.AllowJRTFileSystem.getValue() && containsImageLocation(name)) {
            return Optional.of(URI.create("jrt:/" + module + "/" + name));
        } else {
            return Optional.empty();
        }
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/jdk/internal/module/SystemModuleFinders.java#L445-L452")
    Optional<ByteBuffer> read(String name) throws IOException {
        Objects.requireNonNull(name);
        if (closed) {
            throw new IOException("ModuleReader is closed");
        }
        Module runtimeModule = ModuleLayer.boot().findModule(module).orElse(null);
        Optional<ByteBuffer> embedded = ResourceBasedModuleReaderSupport.getEmbeddedResourceData(runtimeModule, name, ByteBuffer::wrap);
        if (embedded.isPresent()) {
            return embedded;
        }
        if (JRTSupport.Options.AllowJRTFileSystem.getValue()) {
            Target_jdk_internal_jimage_ImageLocation_ModuleReaders location = findImageLocation(name);
            if (location != null) {
                return Optional.of(Target_jdk_internal_module_SystemModuleFinders_SystemImage_ModuleReaders.reader().getResourceBuffer(location));
            }
        }
        return Optional.empty();
    }
}

@TargetClass(className = "jdk.internal.module.ModuleReferences", innerClass = "SafeCloseModuleReader", onlyWith = ClassRegistries.RespectsClassLoader.class)
final class Target_jdk_internal_module_ModuleReferences_SafeCloseModuleReader {
    @Alias
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    native void constructor();
}

@TargetClass(className = "jdk.internal.module.ModuleReferences", innerClass = "JarModuleReader")
final class Target_jdk_internal_module_ModuleReferences_JarModuleReader {

    /*
     * ModuleLayerFeature.ModuleLayerFeatureUtils.ResetModuleReferenceLocation transforms these
     * fields in the image heap by redacting uri and clearing jf. Restoring updates them again when
     * a baked redacted reader finds its matching module jar at image runtime.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.None, isFinal = false) JarFile jf;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.None, isFinal = false) URI uri;

    @Inject String runtimeModuleName;

    @Alias
    @TargetElement(name = "newJarFile")
    static native JarFile newJarFileOriginal(String path);

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/jdk/internal/module/ModuleReferences.java#L246-L255")
    static JarFile newJarFile(String path) {
        /*
         * A baked ModuleReferences.newJarModule() supplier can lazily create its JarModuleReader at
         * image runtime. Its captured fileString is redacted, so avoid opening it and let the other
         * substitutions in this class serve embedded resources or restore from the runtime module
         * path.
         */
        if (ResourceBasedModuleReaderSupport.isRedactedFilePath(path)) {
            return null;
        }
        return newJarFileOriginal(path);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/jdk/internal/module/ModuleReferences.java#L247-L250")
    Target_jdk_internal_module_ModuleReferences_JarModuleReader(String path, URI uri) {
        SubstrateUtil.cast(this, Target_jdk_internal_module_ModuleReferences_SafeCloseModuleReader.class).constructor();
        JarFile jarFile = newJarFile(path);
        this.uri = uri;
        ResourceBasedModuleReaderSupport.putJfFieldVolatile(this, jarFile);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/jdk/internal/module/ModuleReferences.java#L252-L254")
    JarEntry getEntry(String name) {
        JarFile jarFile = ResourceBasedModuleReaderSupport.getJfFieldVolatile(this);
        return jarFile.getJarEntry(Objects.requireNonNull(name));
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/jdk/internal/module/ModuleReferences.java#L258-L270")
    Optional<URI> implFind(String name) {
        Objects.requireNonNull(name);
        String embeddedModuleName = ResourceBasedModuleReaderSupport.getRuntimeModuleName(this);
        if (embeddedModuleName != null) {
            // Prefer resources embedded into the image for baked redacted readers.
            Optional<URI> embedded = ResourceBasedModuleReaderSupport.getEmbeddedResourceURI(embeddedModuleName, name);
            if (embedded.isPresent()) {
                return embedded;
            }
            // Fall back to a runtime module-path jar by restoring this reader's JarFile state.
            ResourceBasedModuleReaderSupport.ensureRuntimeJarFileInitialized(this, embeddedModuleName);
        }
        return ResourceBasedModuleReaderSupport.findInJar(this, name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/jdk/internal/module/ModuleReferences.java#L273-L280")
    Optional<InputStream> implOpen(String name) throws IOException {
        Objects.requireNonNull(name);
        String embeddedModuleName = ResourceBasedModuleReaderSupport.getRuntimeModuleName(this);
        if (embeddedModuleName != null) {
            // Prefer resources embedded into the image for baked redacted readers.
            Optional<InputStream> embedded = ResourceBasedModuleReaderSupport.getEmbeddedResourceData(embeddedModuleName, name, ByteArrayInputStream::new);
            if (embedded.isPresent()) {
                return embedded;
            }
            // Fall back to a runtime module-path jar by restoring this reader's JarFile state.
            ResourceBasedModuleReaderSupport.ensureRuntimeJarFileInitialized(this, embeddedModuleName);
        }
        return ResourceBasedModuleReaderSupport.openFromJar(this, name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/jdk/internal/module/ModuleReferences.java#L283-L288")
    Stream<String> implList() {
        JarFile jarFile = ResourceBasedModuleReaderSupport.getJfFieldVolatile(this);
        if (jarFile == null) {
            String moduleName = ResourceBasedModuleReaderSupport.getRuntimeModuleName(this);
            if (moduleName == null || !ResourceBasedModuleReaderSupport.ensureRuntimeJarFileInitialized(this, moduleName)) {
                // Restoring was unsuccessful, so jf is still null and there are no jar entries to
                // list.
                return Stream.empty();
            }
            jarFile = ResourceBasedModuleReaderSupport.getJfFieldVolatile(this);
        }
        return jarFile.versionedStream().map(JarEntry::getName).toList().stream();
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/jdk/internal/module/ModuleReferences.java#L291-L293")
    void implClose() throws IOException {
        JarFile jarFile = ResourceBasedModuleReaderSupport.getJfFieldVolatile(this);
        if (jarFile != null) {
            jarFile.close();
        }
    }
}

@TargetClass(value = jdk.internal.module.SystemModuleFinders.class, innerClass = "SystemImage")
final class Target_jdk_internal_module_SystemModuleFinders_SystemImage_ModuleReaders {
    @Alias
    static native Target_jdk_internal_jimage_ImageReader_ModuleReaders reader();
}

@TargetClass(className = "jdk.internal.jimage.ImageReader")
final class Target_jdk_internal_jimage_ImageReader_ModuleReaders {
    @Alias
    native ByteBuffer getResourceBuffer(Target_jdk_internal_jimage_ImageLocation_ModuleReaders location);
}

@TargetClass(className = "jdk.internal.jimage.ImageLocation")
final class Target_jdk_internal_jimage_ImageLocation_ModuleReaders {
}

final class ResourceBasedModuleReaderSupport {

    private ResourceBasedModuleReaderSupport() {
    }

    private static final String REDACTED_FILE_PATH_PREFIX = "/REDACTED/";

    private static final ModuleFinder EMPTY_RUNTIME_MODULE_PATH_FINDER = ModuleFinder.of();

    private static volatile ModuleFinder runtimeModulePathFinder;

    private static final Unsafe U = Unsafe.getUnsafe();

    private static final Field JF_FIELD = ReflectionUtil.lookupField(ReflectionUtil.lookupClass("jdk.internal.module.ModuleReferences$JarModuleReader"), "jf");

    private static final long JF_FIELD_OFFSET = U.objectFieldOffset(JF_FIELD);

    static void putJfFieldVolatile(Target_jdk_internal_module_ModuleReferences_JarModuleReader reader, JarFile jarFile) {
        U.putReferenceVolatile(reader, JF_FIELD_OFFSET, jarFile);
    }

    static JarFile getJfFieldVolatile(Target_jdk_internal_module_ModuleReferences_JarModuleReader reader) {
        return (JarFile) U.getReferenceVolatile(reader, JF_FIELD_OFFSET);
    }

    static <T> Optional<T> getEmbeddedResourceData(Module module, String name, Function<byte[], T> mapper) {
        Optional<ResourceStorageEntryBase> resourceEntry = findEmbeddedResourceEntry(module, name);
        if (resourceEntry.isEmpty()) {
            return Optional.empty();
        }
        ResourceStorageEntryBase entry = resourceEntry.get();
        byte[][] dataEntries = entry.getData();
        VMError.guarantee(dataEntries.length <= 1, "Module resource lookup should produce at most one data entry for a module/name pair.");
        return dataEntries.length == 0 ? Optional.empty() : Optional.of(mapper.apply(dataEntries[0]));
    }

    static <T> Optional<T> getEmbeddedResourceData(String moduleName, String name, Function<byte[], T> mapper) {
        Module module = ModuleLayer.boot().findModule(moduleName).orElse(null);
        return module == null ? Optional.empty() : getEmbeddedResourceData(module, name, mapper);
    }

    static Optional<URI> getEmbeddedResourceURI(String moduleName, String name) {
        Module module = ModuleLayer.boot().findModule(moduleName).orElse(null);
        return module == null ? Optional.empty() : getEmbeddedResourceURI(module, name);
    }

    static Optional<URI> getEmbeddedResourceURI(Module module, String name) {
        Optional<ResourceStorageEntryBase> resourceEntry = findEmbeddedResourceEntry(module, name);
        if (resourceEntry.isEmpty()) {
            return Optional.empty();
        }
        java.net.URL resourceURL = Resources.createURL(module, name);
        return resourceURL == null ? Optional.empty() : Optional.of(URI.create(resourceURL.toString()));
    }

    private static Optional<ResourceStorageEntryBase> findEmbeddedResourceEntry(Module module, String name) {
        if (module == null) {
            /*
             * Without a runtime Module object, embedded module-resource lookup must be skipped.
             * Resources.getAtRuntime could otherwise match a classpath resource and treat it as a
             * module resource. The caller may still fall through to JRT lookup for /lib/modules
             * resources.
             */
            return Optional.empty();
        }
        ResourceStorageEntryBase entry = Resources.getAtRuntime(module, name, true);
        if (entry == null || entry == Resources.NEGATIVE_QUERY_MARKER || entry == Resources.MISSING_METADATA_MARKER) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    static Optional<URI> findInJar(Target_jdk_internal_module_ModuleReferences_JarModuleReader reader, String name) {
        JarFile jarFile = getJfFieldVolatile(reader);
        if (jarFile == null) {
            return Optional.empty();
        }
        JarEntry je = reader.getEntry(name);
        if (je == null) {
            return Optional.empty();
        }
        String entryName = name;
        if (jarFile.isMultiRelease()) {
            entryName = je.getRealName();
        }
        if (je.isDirectory() && !entryName.endsWith("/")) {
            entryName += "/";
        }
        String encodedPath = ParseUtil.encodePath(entryName, false);
        String uris = "jar:" + reader.uri + "!/" + encodedPath;
        return Optional.of(URI.create(uris));
    }

    static Optional<InputStream> openFromJar(Target_jdk_internal_module_ModuleReferences_JarModuleReader reader, String name) throws IOException {
        JarFile jarFile = getJfFieldVolatile(reader);
        if (jarFile == null) {
            return Optional.empty();
        }
        JarEntry je = reader.getEntry(name);
        return je == null ? Optional.empty() : Optional.ofNullable(jarFile.getInputStream(je));
    }

    static boolean ensureRuntimeJarFileInitialized(Target_jdk_internal_module_ModuleReferences_JarModuleReader reader, String moduleName) {
        if (getJfFieldVolatile(reader) != null) {
            return true;
        }
        synchronized (reader) {
            if (reader.jf != null) {
                return true;
            }
            // Restore the baked reader with the runtime module jar before first jar access.
            Optional<URI> runtimeUri = getRuntimeModuleLocation(moduleName);
            if (runtimeUri.isPresent()) {
                JarFile runtimeJarFile = openRuntimeModuleJar(runtimeUri.get());
                if (runtimeJarFile != null) {
                    reader.runtimeModuleName = moduleName;
                    reader.uri = runtimeUri.get();
                    putJfFieldVolatile(reader, runtimeJarFile);
                }
            }
            return reader.jf != null;
        }
    }

    static String getRuntimeModuleName(Target_jdk_internal_module_ModuleReferences_JarModuleReader reader) {
        String moduleName = reader.runtimeModuleName;
        if (moduleName == null) {
            synchronized (reader) {
                moduleName = reader.runtimeModuleName;
                if (moduleName == null) {
                    moduleName = getRedactedModuleName(reader.uri);
                    reader.runtimeModuleName = moduleName;
                }
            }
        }
        return moduleName;
    }

    static JarFile openRuntimeModuleJar(URI runtimeUri) {
        if (!"file".equalsIgnoreCase(runtimeUri.getScheme())) {
            return null;
        }
        Path jarPath;
        try {
            jarPath = Path.of(runtimeUri);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!Files.isRegularFile(jarPath)) {
            return null;
        }
        try {
            return Target_jdk_internal_module_ModuleReferences_JarModuleReader.newJarFileOriginal(jarPath.toString());
        } catch (UncheckedIOException e) {
            return null;
        }
    }

    static Optional<URI> getRuntimeModuleLocation(String moduleName) {
        return findRuntimeModuleReference(moduleName).flatMap(ModuleReference::location);
    }

    private static Optional<ModuleReference> findRuntimeModuleReference(String moduleName) {
        if (moduleName == null) {
            return Optional.empty();
        }
        if (runtimeModulePathFinder == null) {
            synchronized (ResourceBasedModuleReaderSupport.class) {
                if (runtimeModulePathFinder == null) {
                    runtimeModulePathFinder = createRuntimeModulePathFinder();
                }
            }
        }
        return runtimeModulePathFinder.find(moduleName);
    }

    private static ModuleFinder createRuntimeModulePathFinder() {
        ModuleFinder finder = Target_jdk_internal_module_ModuleBootstrap.finderFor(RuntimeBootModuleLayerSupport.MODULE_PATH_PROPERTY);
        return finder == null ? EMPTY_RUNTIME_MODULE_PATH_FINDER : finder;
    }

    static String getRedactedModuleName(URI uri) {
        if (uri == null || !"file".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        String path = uri.getPath();
        if (!isRedactedFilePath(path)) {
            return null;
        }
        String moduleName = path.substring(REDACTED_FILE_PATH_PREFIX.length());
        return moduleName.isEmpty() ? null : moduleName;
    }

    static boolean isRedactedFilePath(String path) {
        return path != null && path.startsWith(REDACTED_FILE_PATH_PREFIX);
    }
}

/** Dummy class to have a class with the file's name. */
class Target_jdk_internal_module_ModuleReaders {
}
