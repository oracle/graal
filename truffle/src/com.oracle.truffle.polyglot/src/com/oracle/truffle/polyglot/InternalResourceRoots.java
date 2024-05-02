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

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InternalResourceRoots {

    private static final String OVERRIDDEN_CACHE_ROOT = "polyglot.engine.resourcePath";
    private static final String OVERRIDDEN_COMPONENT_ROOT = "polyglot.engine.resourcePath.";
    private static final String OVERRIDDEN_RESOURCE_ROOT = "polyglot.engine.resourcePath.";

    /**
     * This field is reset to {@code null} by the {@code TruffleBaseFeature} before writing the
     * native image heap.
     */
    private static volatile Map<Collection<EngineAccessor.AbstractClassLoaderSupplier>, Set<Root>> roots;

    private InternalResourceRoots() {
    }

    /**
     * Initializes the internal resource roots. This method is called from entry-points in the
     * polyglot during engine construction to ensure that internal resource roots are initialized
     * before the engine is used.
     */
    static synchronized void ensureInitialized() {
        if (roots == null) {
            roots = new HashMap<>();
        }
        List<EngineAccessor.AbstractClassLoaderSupplier> loaders = loaders();
        if (!roots.containsKey(loaders)) {
            Set<Root> res;
            if (InternalResourceCache.usesInternalResources()) {
                res = computeRoots(findDefaultRoot());
            } else {
                res = Set.of();
            }
            roots.put(loaders, res);
        }
    }

    private static List<EngineAccessor.AbstractClassLoaderSupplier> loaders() {
        return TruffleOptions.AOT ? List.of() : EngineAccessor.locatorOrDefaultLoaders();
    }

    static Root findRoot(Path hostPath) {
        Set<Root> rootsSet = roots.get(loaders());
        for (Root root : rootsSet) {
            if (hostPath.startsWith(root.path)) {
                return root;
            }
        }
        return null;
    }

    static InternalResourceCache findInternalResource(Path hostPath) {
        Root root = findRoot(hostPath);
        if (root != null) {
            for (InternalResourceCache cache : root.caches) {
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
    private static synchronized void setTestCacheRoot(Path newRoot, boolean nativeImageRuntime) {
        if (roots == null) {
            roots = new HashMap<>();
        }
        List<EngineAccessor.AbstractClassLoaderSupplier> loaders = loaders();
        if (roots.containsKey(loaders)) {
            for (Root root : roots.remove(loaders)) {
                for (InternalResourceCache cache : root.caches()) {
                    cache.clearCache();
                }
            }
        }
        if (newRoot != null) {
            roots.put(loaders, computeRoots(Pair.create(newRoot, nativeImageRuntime ? Root.Kind.UNVERSIONED : Root.Kind.VERSIONED)));
        } else if (nativeImageRuntime) {
            var defaultRoots = findDefaultRoot();
            roots.put(loaders, computeRoots(Pair.create(defaultRoots.getLeft(), Root.Kind.UNVERSIONED)));
        }
    }

    /**
     * Computes the internal resource roots.
     */
    private static Set<Root> computeRoots(Pair<Path, Root.Kind> defaultRoot) {
        Map<Pair<Path, Root.Kind>, List<InternalResourceCache>> collector = new HashMap<>();
        for (LanguageCache language : LanguageCache.languages().values()) {
            Collection<InternalResourceCache> resources = language.getResources();
            if (!resources.isEmpty()) {
                collectRoots(language.getId(), defaultRoot.getLeft(), defaultRoot.getRight(), resources, collector);
            }
        }
        for (InstrumentCache instrument : InstrumentCache.load()) {
            Collection<InternalResourceCache> resources = instrument.getResources();
            if (!resources.isEmpty()) {
                collectRoots(instrument.getId(), defaultRoot.getLeft(), defaultRoot.getRight(), resources, collector);
            }
        }
        Collection<InternalResourceCache> engineResources = InternalResourceCache.getEngineResources();
        if (!engineResources.isEmpty()) {
            collectRoots(PolyglotEngineImpl.ENGINE_ID, defaultRoot.getLeft(), defaultRoot.getRight(), engineResources, collector);
        }
        // Build a set of immutable Roots.
        Set<Root> result = new HashSet<>();
        for (var entry : collector.entrySet()) {
            var key = entry.getKey();
            var resources = entry.getValue();
            Root internalResourceRoot = new Root(key.getLeft(), key.getRight(), resources);
            for (InternalResourceCache resource : resources) {
                resource.initializeOwningRoot(internalResourceRoot);
            }
            result.add(internalResourceRoot);
        }
        return Collections.unmodifiableSet(result);
    }

    private static Pair<Path, Root.Kind> findDefaultRoot() {
        Path root;
        Root.Kind kind;
        String overriddenRoot = System.getProperty(OVERRIDDEN_CACHE_ROOT);
        if (overriddenRoot != null) {
            root = Path.of(overriddenRoot);
            kind = Root.Kind.UNVERSIONED;
        } else if (ImageInfo.inImageRuntimeCode()) {
            root = findCacheRootOnNativeImage();
            kind = Root.Kind.UNVERSIONED;
        } else {
            root = findCacheRootOnHotSpot();
            kind = Root.Kind.VERSIONED;
        }
        return Pair.create(root, kind);
    }

    private static void collectRoots(String componentId, Path componentRoot, Root.Kind componentKind, Collection<InternalResourceCache> resources,
                    Map<Pair<Path, Root.Kind>, List<InternalResourceCache>> collector) {
        Path useRoot = componentRoot;
        Root.Kind useKind = componentKind;
        StringBuilder builder = new StringBuilder(OVERRIDDEN_COMPONENT_ROOT);
        builder.append(componentId);
        String overriddenRoot = System.getProperty(builder.toString());
        if (overriddenRoot != null) {
            useRoot = Path.of(overriddenRoot);
            useKind = Root.Kind.COMPONENT;
        }
        for (InternalResourceCache resource : resources) {
            Path resourceRoot = useRoot;
            Root.Kind resourceKind = useKind;
            builder = new StringBuilder(OVERRIDDEN_RESOURCE_ROOT);
            builder.append(componentId);
            builder.append('.');
            builder.append(resource.getResourceId());
            overriddenRoot = System.getProperty(builder.toString());
            if (overriddenRoot != null) {
                resourceRoot = Path.of(overriddenRoot);
                resourceKind = Root.Kind.RESOURCE;
            }
            collector.computeIfAbsent(Pair.create(resourceRoot, resourceKind), (k) -> new ArrayList<>()).add(resource);
        }
    }

    private static Path findCacheRootOnNativeImage() {
        assert ImageInfo.inImageRuntimeCode() : "Can be called only in the native-image execution time.";
        Path executable = getExecutablePath();
        return executable.resolveSibling("resources");
    }

    private static Path getExecutablePath() {
        assert ImageInfo.inImageRuntimeCode();
        if (ImageInfo.isExecutable()) {
            return Path.of(ProcessProperties.getExecutableName());
        } else if (ImageInfo.isSharedLibrary()) {
            return Path.of(ProcessProperties.getObjectFile(InternalResourceCacheSymbol.SYMBOL));
        } else {
            throw CompilerDirectives.shouldNotReachHere("Should only be invoked within native image runtime code.");
        }
    }

    private static Path findCacheRootOnHotSpot() {
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
        return container.resolve("org.graalvm.polyglot");
    }

    private static void emitWarning(String message, Object... args) {
        PrintStream out = System.err;
        out.printf(message + "%n", args);
    }

    record Root(Path path, Kind kind, List<InternalResourceCache> caches) {

        enum Kind {
            COMPONENT,
            RESOURCE,
            UNVERSIONED,
            VERSIONED,
        }
    }
}
