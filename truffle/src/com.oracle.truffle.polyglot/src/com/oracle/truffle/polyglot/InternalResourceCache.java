/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.TruffleOptions;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.polyglot.io.FileSystem;

import java.io.IOError;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class InternalResourceCache {

    private static final char[] FILE_SYSTEM_SPECIAL_CHARACTERS = {'/', '\\', ':'};
    private static final String OVERRIDDEN_CACHE_ROOT = "polyglot.engine.resourcePath";
    private static final String OVERRIDDEN_COMPONENT_ROOT = "polyglot.engine.resourcePath.%s";
    private static final String OVERRIDDEN_RESOURCE_ROOT = "polyglot.engine.resourcePath.%s.%s";

    private static final Lock unpackLock = new ReentrantLock();
    private static volatile Pair<Path, Boolean> cacheRoot;

    private final String id;
    private final String resourceId;
    private final Supplier<InternalResource> resourceFactory;
    private volatile FileSystem resourceFileSystem;

    InternalResourceCache(String languageId, String resourceId, Supplier<InternalResource> resourceFactory) {
        this.id = Objects.requireNonNull(languageId);
        this.resourceId = Objects.requireNonNull(resourceId);
        this.resourceFactory = Objects.requireNonNull(resourceFactory);
    }

    FileSystem getResourceFileSystem(PolyglotEngineImpl polyglotEngine) throws IOException {
        return getResourceFileSystemImpl((resource) -> EngineAccessor.LANGUAGE.createInternalResourceEnv(resource, () -> polyglotEngine.inEnginePreInitialization));
    }

    /**
     * Installs truffleattach library. Used reflectively by
     * {@code com.oracle.truffle.runtime.ModulesSupport}. The {@code ModulesSupport} is initialized
     * before the Truffle runtime is created and accessor classes are initialized. For this reason,
     * it cannot use {@code EngineSupport} to call this method, nor can this method use any
     * accessor.
     */
    static Path installRuntimeResource(InternalResource resource) throws IOException {
        InternalResourceCache cache = createRuntimeResourceCache(resource);
        return cache.getResourceFileSystemImpl(InternalResourceCache::createInternalResourceEnvReflectively).parsePath("").toAbsolutePath();
    }

    private static InternalResourceCache createRuntimeResourceCache(InternalResource resource) {
        InternalResource.Id id = resource.getClass().getAnnotation(InternalResource.Id.class);
        assert id != null : resource.getClass() + " must be annotated by @InternalResource.Id";
        return new InternalResourceCache(PolyglotEngineImpl.ENGINE_ID, id.value(), () -> resource);
    }

    private static InternalResource.Env createInternalResourceEnvReflectively(InternalResource resource) {
        try {
            Constructor<InternalResource.Env> newEnv = InternalResource.Env.class.getDeclaredConstructor(InternalResource.class, BooleanSupplier.class);
            newEnv.setAccessible(true);
            return newEnv.newInstance(resource, (BooleanSupplier) () -> TruffleOptions.AOT);
        } catch (ReflectiveOperationException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private FileSystem getResourceFileSystemImpl(Function<InternalResource, InternalResource.Env> createEnv) throws IOException {
        FileSystem result = resourceFileSystem;
        if (result == null) {
            synchronized (this) {
                result = resourceFileSystem;
                if (result == null) {
                    Path root = findOverriddenResourceRoot();
                    if (root == null) {
                        if (hasExplicitCacheRoot()) {
                            root = findStandaloneResourceRoot(getExplicitCacheRoot());
                        } else if (ImageInfo.inImageRuntimeCode()) {
                            root = findStandaloneResourceRoot(findCacheRootOnNativeImage());
                        } else {
                            InternalResource resource = resourceFactory.get();
                            InternalResource.Env env = createEnv.apply(resource);
                            String versionHash = resource.versionHash(env);
                            if (versionHash.getBytes().length > 128) {
                                throw new IOException("The version hash length is restricted to a maximum of 128 bytes.");
                            }
                            root = findCacheRootOnHotSpot().resolve(Path.of(sanitize(id), sanitize(resourceId), sanitize(versionHash)));
                            unpackResourceFiles(root, resource, env);
                        }
                    }
                    ResettableCachedRoot rootSupplier = new ResettableCachedRoot(root);
                    result = FileSystems.newInternalResourceFileSystem(rootSupplier);
                    resourceFileSystem = result;
                }
            }
        }
        return result;
    }

    private static void unpackResourceFiles(Path target, InternalResource resource, InternalResource.Env env) throws IOException {
        unpackLock.lock();
        try {
            if (!Files.exists(target)) {
                Path parent = target.getParent();
                if (parent == null) {
                    throw CompilerDirectives.shouldNotReachHere("Target must have a parent directory but was " + target);
                }
                Path owner = Files.createDirectories(Objects.requireNonNull(parent));
                Path tmpDir = Files.createTempDirectory(owner, null);
                resource.unpackFiles(env, tmpDir);
                try {
                    Files.move(tmpDir, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (FileAlreadyExistsException existsException) {
                    // race with other process that already moved the folder just unlink the tmp
                    // directory
                    unlink(tmpDir);
                }
            } else {
                verifyResourceRoot(target);
            }
        } finally {
            unpackLock.unlock();
        }
    }

    private static void verifyResourceRoot(Path resourceRoot) throws IOException {
        if (!Files.isDirectory(resourceRoot)) {
            throw new IOException("Resource cache root " + resourceRoot + " must be a directory.");
        }
        if (!Files.isReadable(resourceRoot)) {
            throw new IOException("Resource cache root " + resourceRoot + " must be readable.");
        }
    }

    private Path findStandaloneResourceRoot(Path root) {
        return root.resolve(Path.of(sanitize(id), sanitize(resourceId)));
    }

    private Path findOverriddenResourceRoot() throws IOException {
        String value = System.getProperty(String.format(OVERRIDDEN_RESOURCE_ROOT, id, resourceId));
        if (value != null) {
            return Paths.get(value).toRealPath();
        }
        value = System.getProperty(String.format(OVERRIDDEN_COMPONENT_ROOT, id));
        if (value != null) {
            return Paths.get(value).resolve(sanitize(resourceId)).toRealPath();
        }
        return null;
    }

    private static String sanitize(String pathElement) {
        String result = pathElement;
        for (char fileSystemsSpecialChar : FILE_SYSTEM_SPECIAL_CHARACTERS) {
            result = result.replace(fileSystemsSpecialChar, '_');
        }
        return result;
    }

    private static boolean hasExplicitCacheRoot() throws IOException {
        Pair<Path, Boolean> res = cacheRoot;
        if (res == null) {
            String resourcesFolder = System.getProperty(OVERRIDDEN_CACHE_ROOT);
            if (resourcesFolder != null) {
                Path cache = Paths.get(resourcesFolder).toRealPath();
                res = Pair.create(cache, true);
                cacheRoot = res;
            }
        }
        return res != null && res.getRight();
    }

    private static Path getExplicitCacheRoot() {
        Pair<Path, Boolean> res = cacheRoot;
        if (res == null || !res.getRight()) {
            throw CompilerDirectives.shouldNotReachHere("Can be only called when hasExplicitCacheRoot() returned true");
        }
        return res.getLeft();
    }

    private static Path findCacheRootOnHotSpot() throws IOException {
        Pair<Path, Boolean> res = cacheRoot;
        if (res == null) {
            String userHomeValue = System.getProperty("user.home");
            if (userHomeValue == null) {
                throw CompilerDirectives.shouldNotReachHere("The 'user.home' system property is not set.");
            }
            Path userHome = Paths.get(userHomeValue);
            Path container = switch (InternalResource.OS.getCurrent()) {
                case DARWIN -> userHome.resolve(Path.of("Library", "Caches"));
                case LINUX -> {
                    Path userCacheDir = null;
                    String xdgCacheValue = System.getenv("XDG_CACHE_HOME");
                    if (xdgCacheValue != null) {
                        try {
                            Path xdgCacheDir = Path.of(xdgCacheValue);
                            // Do not fail when XDG_CACHE_HOME value is invalid. Fall back to
                            // $HOME/.cache.
                            if (xdgCacheDir.isAbsolute()) {
                                userCacheDir = xdgCacheDir;
                            } else {
                                emitWarning("The value of the environment variable 'XDG_CACHE_HOME' is not an absolute path. Using the default cache folder '%s'.", userHome.resolve(".cache"));
                            }
                        } catch (InvalidPathException notPath) {
                            emitWarning("The value of the environment variable 'XDG_CACHE_HOME' is not a valid path. Using the default cache folder '%s'.", userHome.resolve(".cache"));
                        }
                    }
                    if (userCacheDir == null) {
                        userCacheDir = userHome.resolve(".cache");
                    }
                    yield userCacheDir;
                }
                case WINDOWS -> userHome.resolve(Path.of("AppData", "Local"));
            };
            Path cache = container.resolve("org.graalvm.polyglot");
            cache = Files.createDirectories(cache).toRealPath();
            res = Pair.create(cache, false);
            cacheRoot = res;
        }
        return res.getLeft();
    }

    private static void emitWarning(String message, Object... args) {
        PrintStream out = System.err;
        out.printf(message + "%n", args);
    }

    private static Path findCacheRootOnNativeImage() {
        Pair<Path, Boolean> res = cacheRoot;
        if (res == null) {
            assert ImageInfo.inImageRuntimeCode() : "Can be called only in the native-image execution time.";
            Path executable = Path.of(ProcessProperties.getExecutableName());
            Path cache = executable.resolve("resources");
            res = Pair.create(cache, false);
            cacheRoot = res;
        }
        return res.getLeft();
    }

    /**
     * Resets cache roots after closed word analyses. This method is called reflectively by the
     * {@code TruffleBaseFeature#afterAnalysis}.
     */
    static void resetNativeImageState() {
        cacheRoot = null;
        for (LanguageCache language : LanguageCache.languages().values()) {
            for (String resourceId : language.getResourceIds()) {
                InternalResourceCache cache = language.getResourceCache(resourceId);
                cache.resetFileSystemNativeImageState();
            }
        }
        for (InstrumentCache instrument : InstrumentCache.load()) {
            for (String resourceId : instrument.getResourceIds()) {
                InternalResourceCache cache = instrument.getResourceCache(resourceId);
                cache.resetFileSystemNativeImageState();
            }
        }
    }

    private void resetFileSystemNativeImageState() {
        FileSystem fs = resourceFileSystem;
        if (fs != null) {
            ((ResettableCachedRoot) FileSystems.getInternalResourceFileSystemRoot(fs)).resourceCacheRoot = null;
        }
    }

    /**
     * Unpacks internal resources after native-image write. This method is called reflectively by
     * the {@code TruffleBaseFeature#afterAnalysis}.
     */
    static boolean copyResourcesForNativeImage(Path target, String... components) throws IOException {
        boolean result = false;
        Collection<LanguageCache> languages;
        Collection<InstrumentCache> instruments;
        if (components.length == 0) {
            languages = LanguageCache.languages().values();
            instruments = InstrumentCache.load();
        } else {
            Set<String> requiredComponentIds = new HashSet<>();
            Collections.addAll(requiredComponentIds, components);
            Set<String> requiredLanguageIds = new HashSet<>(LanguageCache.languages().keySet());
            requiredLanguageIds.retainAll(requiredComponentIds);
            Set<String> requiredInstrumentIds = InstrumentCache.load().stream().map(InstrumentCache::getId).collect(Collectors.toSet());
            requiredInstrumentIds.retainAll(requiredComponentIds);
            requiredComponentIds.removeAll(requiredLanguageIds);
            requiredComponentIds.removeAll(requiredInstrumentIds);
            if (!requiredComponentIds.isEmpty()) {
                Set<String> installedComponents = new TreeSet<>(LanguageCache.languages().keySet());
                InstrumentCache.load().stream().map(InstrumentCache::getId).forEach(installedComponents::add);
                throw new IllegalArgumentException(String.format("Components with ids %s are not installed. Installed components are: %s.",
                                String.join(", ", requiredComponentIds),
                                String.join(", ", installedComponents)));
            }
            Set<LanguageCache> requiredLanguages = new HashSet<>(LanguageCache.internalLanguages());
            for (String requiredLanguageId : requiredLanguageIds) {
                requiredLanguages.addAll(LanguageCache.computeTransitiveLanguageDependencies(requiredLanguageId));
            }
            languages = requiredLanguages;
            Set<InstrumentCache> requiredInstruments = new HashSet<>(InstrumentCache.internalInstruments());
            InstrumentCache.load().stream().filter((ic) -> requiredInstrumentIds.contains(ic.getId())).forEach(requiredInstruments::add);
            instruments = requiredInstruments;
        }
        for (LanguageCache language : languages) {
            for (String resourceId : language.getResourceIds()) {
                InternalResourceCache cache = language.getResourceCache(resourceId);
                result |= cache.copyResourcesForNativeImage(target);
            }
        }
        for (InstrumentCache instrument : instruments) {
            for (String resourceId : instrument.getResourceIds()) {
                InternalResourceCache cache = instrument.getResourceCache(resourceId);
                result |= cache.copyResourcesForNativeImage(target);
            }
        }
        // Always install Truffle runtime resource caches
        for (InternalResource resource : EngineAccessor.RUNTIME.getInternalResources()) {
            result |= createRuntimeResourceCache(resource).copyResourcesForNativeImage(target);
        }
        return result;
    }

    private boolean copyResourcesForNativeImage(Path target) throws IOException {
        Path resourceRoot = findStandaloneResourceRoot(target);
        unlink(resourceRoot);
        Files.createDirectories(resourceRoot);
        InternalResource resource = resourceFactory.get();
        InternalResource.Env env = EngineAccessor.LANGUAGE.createInternalResourceEnv(resource, () -> false);
        resource.unpackFiles(env, resourceRoot);
        if (isEmpty(resourceRoot)) {
            Files.deleteIfExists(resourceRoot);
            return false;
        } else {
            return true;
        }
    }

    private static boolean isEmpty(Path folder) throws IOException {
        try (Stream<Path> children = Files.list(folder)) {
            return children.findAny().isEmpty();
        }
    }

    private static void unlink(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path child : children) {
                    unlink(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * Sets the {@link #cacheRoot} in unit tests. This method is called reflectively by the
     * {@code InternalResourceTest}.
     */
    @SuppressWarnings("unused")
    private static void setTestCacheRoot(Path root, boolean disposeResourceFileSystem) {
        cacheRoot = root == null ? null : Pair.create(root, false);
        for (LanguageCache language : LanguageCache.languages().values()) {
            for (String resourceId : language.getResourceIds()) {
                InternalResourceCache cache = language.getResourceCache(resourceId);
                if (disposeResourceFileSystem) {
                    cache.resourceFileSystem = null;
                } else {
                    cache.resetFileSystemNativeImageState();
                }
            }
        }
        for (InstrumentCache instrument : InstrumentCache.load()) {
            for (String resourceId : instrument.getResourceIds()) {
                InternalResourceCache cache = instrument.getResourceCache(resourceId);
                if (disposeResourceFileSystem) {
                    cache.resourceFileSystem = null;
                } else {
                    cache.resetFileSystemNativeImageState();
                }
            }
        }
    }

    private final class ResettableCachedRoot implements Supplier<Path> {

        private volatile Path resourceCacheRoot;

        ResettableCachedRoot(Path resourceCacheRoot) {
            Objects.requireNonNull(resourceCacheRoot, "ResourceCacheRoot must be non-null.");
            this.resourceCacheRoot = resourceCacheRoot;
        }

        @Override
        public Path get() {
            Path res = resourceCacheRoot;
            if (res == null) {
                if (ImageInfo.inImageBuildtimeCode()) {
                    throw CompilerDirectives.shouldNotReachHere("Reintroducing internal resource cache path into an image heap.");
                }
                try {
                    res = findOverriddenResourceRoot();
                    if (res == null) {
                        Path cache;
                        if (hasExplicitCacheRoot()) {
                            cache = getExplicitCacheRoot();
                        } else {
                            cache = findCacheRootOnNativeImage();
                        }
                        res = findStandaloneResourceRoot(cache);
                    }
                    resourceCacheRoot = res;
                } catch (IOException ioe) {
                    throw new IOError(ioe);
                }
            }
            return res;
        }
    }
}
