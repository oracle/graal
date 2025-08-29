/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.polyglot.InternalResourceRoots.Root.Kind;

final class InternalResourceRoots {

    private static final String PROPERTY_RESOURCE_PATH = "polyglot.engine.resourcePath";
    private static final String PROPERTY_USER_RESOURCE_CACHE = "polyglot.engine.userResourceCache";
    private static final Map<Collection<EngineAccessor.AbstractClassLoaderSupplier>, InternalResourceRoots> runtimeCaches = new ConcurrentHashMap<>();
    private static final boolean TRACE_INTERNAL_RESOURCE_EVENTS = Boolean.getBoolean("polyglotimpl.TraceInternalResources");

    static String overriddenComponentRootProperty(String componentId) {
        StringBuilder builder = new StringBuilder(PROPERTY_RESOURCE_PATH);
        builder.append('.');
        builder.append(componentId);
        return builder.toString();
    }

    static String overriddenResourceRootProperty(String componentId, String resourceId) {
        StringBuilder builder = new StringBuilder(PROPERTY_RESOURCE_PATH);
        builder.append('.');
        builder.append(componentId);
        builder.append('.');
        builder.append(resourceId);
        return builder.toString();
    }

    /**
     * This field is reset to {@code null} by the {@code TruffleBaseFeature} before writing the
     * native image heap. The value is recomputed when the pre-initialized engine is patched.
     */
    private volatile List<Root> roots;

    private InternalResourceRoots() {
    }

    static InternalResourceRoots getInstance() {
        List<EngineAccessor.AbstractClassLoaderSupplier> loaders = TruffleOptions.AOT ? List.of() : EngineAccessor.locatorOrDefaultLoaders();
        InternalResourceRoots instance = runtimeCaches.computeIfAbsent(loaders, (k) -> new InternalResourceRoots());
        /*
         * Calling ensureInitialized in the InternalResourceRoots constructor alone is insufficient
         * due to context pre-initialization. The roots are reset after context pre-initialization
         * and must be recomputed during image execution time. Typically, this occurs during the
         * patch process. However, if the pre-initialized engine is not used, we must reinitialize
         * the roots field before returning InternalResourceRoots from the getInstance call to
         * PolyglotEngineImpl.
         */
        instance.ensureInitialized();
        return instance;
    }

    boolean patch(PolyglotEngineImpl engine) {
        ensureInitialized();
        /*
         * Unpack all resources that are included in the native-image binary and were queried during
         * context pre-initialization.
         */
        boolean[] result = {true};
        InternalResourceCache.walkAllResources((componentId, resources) -> {
            for (InternalResourceCache resource : resources) {
                if (resource.requiresEagerUnpack()) {
                    if (InternalResourceCache.usesInternalResources()) {
                        try {
                            resource.getPath(engine);
                        } catch (IOException ioe) {
                            throw new IllegalStateException(ioe);
                        }
                    } else {
                        /*
                         * Internal resources were utilized during the image build but are disabled
                         * at runtime. Return false to indicate that the pre-initialized engine
                         * should not be used.
                         */
                        result[0] = false;
                    }
                }
            }
        });
        return result[0];
    }

    private synchronized void ensureInitialized() {
        if (roots == null) {
            if (InternalResourceCache.usesInternalResources()) {
                roots = computeRoots(findDefaultRoot());
            } else {
                roots = List.of();
            }
        }
    }

    Root findRoot(Path hostPath) {
        for (Root root : roots) {
            if (hostPath.startsWith(root.path)) {
                return root;
            }
        }
        return null;
    }

    InternalResourceCache findInternalResource(Path hostPath) {
        Root root = findRoot(hostPath);
        if (root != null) {
            for (InternalResourceCache cache : root.resources) {
                Path resourceRoot = cache.getPathOrNull();
                // Used InternalResourceCache instances always have non-null root.
                if (resourceRoot != null && hostPath.startsWith(resourceRoot)) {
                    return cache;
                }
            }
        }
        return null;
    }

    /**
     * The unpacking of the Truffle attach library is called reflectively in a boot time when
     * accessors, LanguageCache and InstrumentCache cannot be used. We are creating a temporary
     * {@link InternalResourceCache} just to unpack the library.
     *
     */
    static void initializeRuntimeResource(InternalResourceCache truffleRuntimeResource) {
        Pair<Path, Root.Kind> defaultRoot = findDefaultRoot();
        Map<Pair<Path, Root.Kind>, List<InternalResourceCache>> collector = new HashMap<>();
        collectRoots(PolyglotEngineImpl.ENGINE_ID, defaultRoot.getLeft(), defaultRoot.getRight(),
                        List.of(truffleRuntimeResource), collector);
        var entry = collector.entrySet().iterator().next();
        var key = entry.getKey();
        truffleRuntimeResource.initializeOwningRoot(new Root(key.getLeft(), key.getRight(), entry.getValue()));
    }

    /**
     * Sets the {@code #roots} in unit tests. This method is called reflectively by the
     * {@code InternalResourceTest}.
     *
     * @param newRoot the new enforced cache root used by unit tests.
     * @param nativeImageRuntime simulates the native image runtime behavior on hotspot. Needed by
     *            the {@code ContextPreInitializationTest}.
     *
     */
    @SuppressWarnings("unused")
    private static void setTestCacheRoot(Path newRoot, boolean nativeImageRuntime) {
        List<EngineAccessor.AbstractClassLoaderSupplier> loaders = TruffleOptions.AOT ? List.of() : EngineAccessor.locatorOrDefaultLoaders();
        InternalResourceRoots resourceRoots = runtimeCaches.computeIfAbsent(loaders, (k) -> new InternalResourceRoots());
        if (resourceRoots.roots != null) {
            for (Root root : resourceRoots.roots) {
                for (InternalResourceCache cache : root.resources()) {
                    cache.clearCache();
                }
            }
        }
        if (newRoot != null) {
            resourceRoots.roots = computeRoots(Pair.create(newRoot, nativeImageRuntime ? Root.Kind.UNVERSIONED : Root.Kind.VERSIONED));
        } else if (nativeImageRuntime) {
            var defaultRoots = findDefaultRoot();
            resourceRoots.roots = computeRoots(Pair.create(defaultRoots.getLeft(), Root.Kind.UNVERSIONED));
        } else {
            resourceRoots.roots = null;
        }
    }

    /**
     * Computes the internal resource roots.
     */
    private static List<Root> computeRoots(Pair<Path, Root.Kind> defaultRoot) {
        Map<Pair<Path, Root.Kind>, List<InternalResourceCache>> collector = new HashMap<>();
        InternalResourceCache.walkAllResources((componentId, resources) -> {
            collectRoots(componentId, defaultRoot.getLeft(), defaultRoot.getRight(), resources, collector);
        });
        // Build a set of immutable Roots.
        List<Root> result = new ArrayList<>();
        for (var entry : collector.entrySet()) {
            Pair<Path, Kind> key = entry.getKey();
            List<InternalResourceCache> resources = entry.getValue();
            Root internalResourceRoot = new Root(key.getLeft(), key.getRight(), resources);
            for (InternalResourceCache resource : resources) {
                resource.initializeOwningRoot(internalResourceRoot);
            }
            result.add(internalResourceRoot);
        }
        return Collections.unmodifiableList(result);
    }

    private static Pair<Path, Root.Kind> findDefaultRoot() {
        ResolvedCacheFolder root;
        Root.Kind kind;
        String overriddenRoot = System.getProperty(PROPERTY_RESOURCE_PATH);
        if (overriddenRoot != null) {
            Path overriddenRootPath = Path.of(overriddenRoot).toAbsolutePath();
            root = new ResolvedCacheFolder(overriddenRootPath, PROPERTY_RESOURCE_PATH + " system property", overriddenRootPath);
            kind = Root.Kind.UNVERSIONED;
        } else if (ImageInfo.inImageRuntimeCode() && InternalResourceCache.usesResourceDirectoryOnNativeImage()) {
            root = findCacheRootOnNativeImage();
            kind = Root.Kind.UNVERSIONED;
        } else {
            root = findCacheRootDefault();
            kind = Root.Kind.VERSIONED;
        }
        if (isTraceInternalResourceEvents()) {
            logInternalResourceEvent("Resolved the root directory for the internal resource cache to: %s, determined by the %s with the value %s.",
                            root.path(), root.hint(), root.hintValue());
        }
        return Pair.create(root.path(), kind);
    }

    private static void collectRoots(String componentId, Path componentRoot, Root.Kind componentKind, Collection<InternalResourceCache> resources,
                    Map<Pair<Path, Root.Kind>, List<InternalResourceCache>> resourcesCollector) {
        Path useRoot = componentRoot;
        Root.Kind useKind = componentKind;
        String overriddenRoot = System.getProperty(overriddenComponentRootProperty(componentId));
        if (overriddenRoot != null) {
            useRoot = Path.of(overriddenRoot).toAbsolutePath();
            useKind = Root.Kind.COMPONENT;
        }
        for (InternalResourceCache resource : resources) {
            Path resourceRoot = useRoot;
            Root.Kind resourceKind = useKind;
            overriddenRoot = System.getProperty(overriddenResourceRootProperty(componentId, resource.getResourceId()));
            if (overriddenRoot != null) {
                resourceRoot = Path.of(overriddenRoot).toAbsolutePath();
                resourceKind = Root.Kind.RESOURCE;
            }
            resourcesCollector.computeIfAbsent(Pair.create(resourceRoot, resourceKind), (k) -> new ArrayList<>()).add(resource);
        }
    }

    private static ResolvedCacheFolder findCacheRootOnNativeImage() {
        assert ImageInfo.inImageRuntimeCode() : "Can be called only in the native-image execution time.";
        Path executable = getExecutablePath();
        if (executable == null) {
            // fall back to default if executable or library path is not available
            return findCacheRootDefault();
        }
        return new ResolvedCacheFolder(executable.resolveSibling("resources"), "executable location", executable);
    }

    private static Path getExecutablePath() {
        assert ImageInfo.inImageRuntimeCode();
        String path;
        if (ImageInfo.isExecutable()) {
            path = ProcessProperties.getExecutableName();
        } else if (ImageInfo.isSharedLibrary()) {
            path = ProcessProperties.getObjectFile(InternalResourceCacheSymbol.SYMBOL);
        } else {
            throw new AssertionError("Should only be invoked within native image runtime code.");
        }
        return path == null ? null : Path.of(path);
    }

    private static ResolvedCacheFolder findCacheRootDefault() {
        String enforcedCacheFolder = System.getProperty(PROPERTY_USER_RESOURCE_CACHE);
        if (enforcedCacheFolder != null) {
            Path enforcedCacheFolderPath = Path.of(enforcedCacheFolder);
            return new ResolvedCacheFolder(enforcedCacheFolderPath.toAbsolutePath(), PROPERTY_USER_RESOURCE_CACHE + " system property", enforcedCacheFolderPath);
        }
        String userHomeValue = System.getProperty("user.home");
        if (userHomeValue == null) {
            throw new AssertionError("The 'user.home' system property is not set.");
        }
        Path userHome = Paths.get(userHomeValue);
        ResolvedCacheFolder container = switch (InternalResource.OS.getCurrent()) {
            case DARWIN -> new ResolvedCacheFolder(userHome.resolve(Path.of("Library", "Caches")), "user home", userHome);
            case LINUX -> {
                ResolvedCacheFolder userCacheDir = null;
                String xdgCacheValue = System.getenv("XDG_CACHE_HOME");
                if (xdgCacheValue != null) {
                    try {
                        Path xdgCacheDir = Path.of(xdgCacheValue);
                        // Do not fail when XDG_CACHE_HOME value is invalid. Fall back to
                        // $HOME/.cache.
                        if (xdgCacheDir.isAbsolute()) {
                            userCacheDir = new ResolvedCacheFolder(xdgCacheDir, "XDG_CACHE_HOME env variable", xdgCacheDir);
                        } else {
                            emitWarning("The value of the environment variable 'XDG_CACHE_HOME' is not an absolute path. Using the default cache folder '%s'.", userHome.resolve(".cache"));
                        }
                    } catch (InvalidPathException notPath) {
                        emitWarning("The value of the environment variable 'XDG_CACHE_HOME' is not a valid path. Using the default cache folder '%s'.", userHome.resolve(".cache"));
                    }
                }
                if (userCacheDir == null) {
                    userCacheDir = new ResolvedCacheFolder(userHome.resolve(".cache"), "user home", userHome);
                }
                yield userCacheDir;
            }
            case WINDOWS -> new ResolvedCacheFolder(userHome.resolve(Path.of("AppData", "Local")), "user home", userHome);
            case UNSUPPORTED -> throw new IllegalStateException(String.format("Truffle is running on an unsupported platform. " +
                            "On unsupported platforms, you must explicitly set the default cache directory using the system property " +
                            "'-D%s=<path_to_cache_folder>'.", PROPERTY_USER_RESOURCE_CACHE));
        };
        return container.resolve("org.graalvm.polyglot");
    }

    static boolean isTraceInternalResourceEvents() {
        /*
         * In AOT we want to enable tracing if its enabled in the built image or using the option at
         * runtime.
         */
        return TRACE_INTERNAL_RESOURCE_EVENTS || (TruffleOptions.AOT && Boolean.getBoolean("polyglotimpl.TraceInternalResources"));
    }

    static void logInternalResourceEvent(String message, Object... args) {
        assert isTraceInternalResourceEvents() : "need to check for TRACE_INTERNAL_RESOURCE_EVENTS before use";
        PolyglotEngineImpl.logFallback(String.format("[engine][resource] " + message + "%n", args));
    }

    private static void emitWarning(String message, Object... args) {
        PolyglotEngineImpl.logFallback(String.format(message + "%n", args));
    }

    record Root(Path path, Kind kind, List<InternalResourceCache> resources) {

        enum Kind {
            COMPONENT,
            RESOURCE,
            UNVERSIONED,
            VERSIONED,
        }

    }

    private record ResolvedCacheFolder(Path path, String hint, Path hintValue) {

        ResolvedCacheFolder resolve(String file) {
            return new ResolvedCacheFolder(path.resolve(file), hint, hintValue);
        }
    }
}
