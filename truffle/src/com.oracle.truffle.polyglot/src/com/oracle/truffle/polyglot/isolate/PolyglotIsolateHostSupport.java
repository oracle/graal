/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot.isolate;

import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.PolyglotIsolateLanguages;
import org.graalvm.collections.Pair;
import org.graalvm.home.HomeFinder;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativebridge.IsolateCreateException;
import org.graalvm.nativebridge.IsolateThread;
import org.graalvm.nativebridge.NativeIsolate;
import org.graalvm.nativebridge.NativeIsolateConfig;
import org.graalvm.nativebridge.Peer;
import org.graalvm.nativebridge.ProcessIsolate;
import org.graalvm.nativebridge.ProcessIsolateConfig;
import org.graalvm.nativebridge.ReferenceHandles;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

final class PolyglotIsolateHostSupport {

    private static final Map<Set<String>, LibraryConfig> libraryCache = new ConcurrentHashMap<>();
    private static volatile Lazy lazy;

    static Engine buildIsolatedEngine(AbstractPolyglotImpl polyglot, Engine localEngine, String[] isolateLanguages, String[] permittedLanguages, SandboxPolicy sandboxPolicy, OutputStream out,
                    OutputStream err, InputStream in, Map<String, String> options, Map<String, String> systemPropertiesOptions, boolean useSystemProperties,
                    boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor, boolean registerInActiveEngines, boolean externalProcess, long stackHeadroom,
                    String isolateLibrary, String isolateLauncher) {
        assert isolateLanguages != null;
        APIAccess apiAccess = polyglot.getAPIAccess();
        LibraryConfig libraryConfig = resolveIsolatePaths(apiAccess.getEngineReceiver(localEngine), isolateLibrary, isolateLauncher, permittedLanguages, isolateLanguages);
        return spawnIsolatedEngine(polyglot, localEngine, permittedLanguages, sandboxPolicy, out, err, in, options, systemPropertiesOptions, useSystemProperties, allowExperimentalOptions, boundEngine,
                        messageInterceptor,
                        libraryConfig, registerInActiveEngines, externalProcess, stackHeadroom);
    }

    private static LibraryConfig resolveIsolatePaths(Object localEngineReceiver, String isolateLibrary, String isolateLauncher, String[] permittedLanguages, String[] spawnedLanguages) {
        Path isolateLauncherPath;
        if (isolateLauncher != null) {
            isolateLauncherPath = Path.of(isolateLauncher);
            if (!Files.isRegularFile(isolateLauncherPath)) {
                throw new IllegalArgumentException(String.format("The external isolate launcher specified at path '%s' does not exist.", isolateLauncher));
            }
        } else {
            isolateLauncherPath = null;
        }
        if (isolateLibrary != null) {
            Path p = Paths.get(isolateLibrary);
            if (Files.isRegularFile(p)) {
                return new LibraryConfig(p, isolateLauncherPath);
            } else {
                throw new IllegalArgumentException(String.format("The isolate library specified at path '%s' does not exist.", isolateLibrary));
            }
        } else {
            HomeFinder hf = HomeFinder.getInstance();
            String[] requestedLanguages;
            if (permittedLanguages.length == 0 && spawnedLanguages.length == 0) {
                List<PolyglotIsolateResource> languages = resolveAvailablePolyglotIsolates(hf);
                throw new IllegalArgumentException(String.format(
                                "A language must be explicitly specified when using the --engine.SpawnIsolate option to determine which polyglot isolate should be created. " + //
                                                "The following language combinations currently support spawning isolates: %s. To resolve this problem either: %n" + //
                                                " - Specify one of the supported languages when constructing the engine or context with Engine.newBuilder(...) or Context.newBuilder(...). %n" + //
                                                " - Specify one of the supported languages using the --engine.SpawnIsolate=<language> option or alternatively using -Dpolyglot.engine.SpawnIsolate=<language> system property. %n" + //
                                                " - Specify the path to the isolate library using --engine.IsolateLibrary=/path/to/lib (use only for testing).",
                                languages.stream().map(PolyglotIsolateResource::getIncludedLanguagesAsString).collect(Collectors.joining(", "))));
            } else {
                // permitted wins over spawned
                if (permittedLanguages.length > 0 && spawnedLanguages.length > 0) {
                    Set<String> permittedSet = new HashSet<>();
                    Collections.addAll(permittedSet, permittedLanguages);
                    Set<String> spawnedSet = new HashSet<>();
                    Collections.addAll(spawnedSet, spawnedLanguages);
                    if (!permittedSet.equals(spawnedSet)) {
                        throw new IllegalArgumentException(String.format("The permitted languages %s do not match the spawned languages %s. They must match in order to resolve an isolate library.",
                                        String.join(", ", permittedLanguages),
                                        String.join(", ", spawnedLanguages)));
                    }
                    requestedLanguages = permittedLanguages;
                } else {
                    if (permittedLanguages.length > 0) {
                        requestedLanguages = permittedLanguages;
                    } else {
                        requestedLanguages = spawnedLanguages;
                    }
                }
            }

            LibraryConfig libraryConfig = resolveLanguageIsolatePaths(localEngineReceiver, isolateLauncherPath, requestedLanguages, hf);
            if (libraryConfig == null) {
                List<PolyglotIsolateResource> languages = resolveAvailablePolyglotIsolates(hf);
                throw new IllegalArgumentException(String.format("No native isolate library is available for the requested language(s) [%s].%n" +
                                "The following language combinations currently support spawning isolates: %s.%n%n" +
                                "To resolve this problem, either:%n" +
                                " - Specify one of the supported languages or language combinations when constructing the engine or context using Engine.newBuilder(...) or Context.newBuilder(...).%n" +
                                " - Specify one of the supported languages or language combinations using the --engine.SpawnIsolate=<language> option or the -Dpolyglot.engine.SpawnIsolate=<language> system property.%n" +
                                " - Specify the path to the isolate library using --engine.IsolateLibrary=/path/to/lib (for testing purposes only).",
                                String.join(", ", requestedLanguages), languages.stream().map(PolyglotIsolateResource::getIncludedLanguagesAsString).collect(Collectors.joining(", "))));
            }
            return libraryConfig;
        }
    }

    private static List<PolyglotIsolateResource> resolveAvailablePolyglotIsolates(HomeFinder hf) {
        List<PolyglotIsolateResource> availablePolyglotIsolates = new ArrayList<>();
        for (String language : hf.getLanguageHomes().keySet()) {
            LibraryConfig libraryConfig = resolveLibraryInLanguageHome(null, language, hf.getLanguageHomes().get(language));
            if (libraryConfig != null) {
                availablePolyglotIsolates.add(new PolyglotIsolateResource(null, Set.of(language)));
            }
        }
        availablePolyglotIsolates.addAll(computeAvailablePolyglotIsolateResources());
        return availablePolyglotIsolates;
    }

    private static LibraryConfig resolveLanguageIsolatePaths(Object localEngineReceiver, Path isolateLauncherPath, String[] languages, HomeFinder homeFinder) {
        Set<String> key = Set.of(languages);
        LibraryConfig res = libraryCache.get(key);
        if (res == null) {
            Path languageHome = languages.length == 1 ? homeFinder.getLanguageHomes().get(languages[0]) : null;
            if (languageHome == null) {
                res = resolveLibraryFromResource(localEngineReceiver, isolateLauncherPath, languages);
            } else {
                res = resolveLibraryInLanguageHome(isolateLauncherPath, languages[0], languageHome);
            }
            if (res != null) {
                libraryCache.putIfAbsent(key, res);
            }
        }
        return res;
    }

    private static LibraryConfig resolveLibraryInLanguageHome(Path isolateLauncherPath, String language, Path languageHome) {
        Path libraryPath = languageHome.resolve("lib").resolve(System.mapLibraryName(language + "vm"));
        if (Files.isRegularFile(libraryPath)) {
            return new LibraryConfig(libraryPath, isolateLauncherPath);
        }
        return null;
    }

    private static LibraryConfig resolveLibraryFromResource(Object localEngineReceiver, Path isolateLauncherPath, String[] languages) {
        Set<String> requestedLanguages = Set.of(languages);
        PolyglotIsolateResource selectedPolyglotIsolateResource = null;
        for (var polyglotIsolateResource : computeAvailablePolyglotIsolateResources()) {
            if (polyglotIsolateResource.includedLanguages.containsAll(requestedLanguages)) {
                selectedPolyglotIsolateResource = polyglotIsolateResource;
                break;
            }
        }
        if (selectedPolyglotIsolateResource == null) {
            return null;
        }
        try {
            Path root = PolyglotIsolateAccessor.ENGINE.getEngineResource(localEngineReceiver, selectedPolyglotIsolateResource.resourceId);
            if (root != null) {
                Path isolateLibrary = root.resolve(System.mapLibraryName("polyglotisolate"));
                Path isolateResources = root.resolve("resources");
                Path isolateLauncher;
                if (isolateLauncherPath != null) {
                    isolateLauncher = isolateLauncherPath;
                } else {
                    isolateLauncher = root.resolve("external_isolate").resolve("launcher" + (InternalResource.OS.getCurrent() == InternalResource.OS.WINDOWS ? ".exe" : ""));
                }
                return new LibraryConfig(isolateLibrary, isolateResources, isolateLauncher);
            } else {
                return null;
            }
        } catch (IOException ioe) {
            throw new InternalError(ioe);
        }
    }

    private static List<PolyglotIsolateResource> computeAvailablePolyglotIsolateResources() {
        List<PolyglotIsolateResource> result = new ArrayList<>();
        InternalResource.OS currentOS = InternalResource.OS.getCurrent();
        InternalResource.CPUArchitecture currentArch = InternalResource.CPUArchitecture.getCurrent();
        for (var e : PolyglotIsolateAccessor.ENGINE.getEngineInternalResources().entrySet()) {
            PolyglotIsolateLanguages polyglotIsolateLanguages = e.getValue().getClass().getAnnotation(PolyglotIsolateLanguages.class);
            if (polyglotIsolateLanguages != null && currentOS.equals(polyglotIsolateLanguages.os()) && currentArch.equals(polyglotIsolateLanguages.cpuArchitecture())) {
                result.add(new PolyglotIsolateResource(e.getKey(), Set.of(polyglotIsolateLanguages.value())));
            }
        }
        // Sort by included languages size to prefer images with exact matches
        result.sort(Comparator.comparingInt(polyglotIsolateResource -> polyglotIsolateResource.includedLanguages.size()));
        return result;
    }

    private static Engine spawnIsolatedEngine(AbstractPolyglotImpl polyglot, Engine localEngine, String[] permittedLanguages, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err,
                    InputStream in,
                    Map<String, String> options, Map<String, String> systemPropertiesOptions, boolean useSystemProperties,
                    boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor, LibraryConfig libraryConfig,
                    boolean registerInActiveEngines, boolean externalProcess, long stackHeadroom) {
        if (!ImageInfo.inImageCode()) {
            Accessor.ModulesAccessor moduleAccessor = PolyglotIsolateAccessor.ENGINE.getModulesAccessor();
            // moduleAccessor may be null if the truffleattach library cannot be loaded
            if (moduleAccessor != null) {
                moduleAccessor.addEnableNativeAccess(PolyglotIsolateHostSupport.class.getModule());
            }
        }
        Lazy l = initializeLazy(polyglot);
        APIAccess apiAccess = polyglot.getAPIAccess();
        OptionValues engineOptions = PolyglotIsolateAccessor.ENGINE.getEngineOptionValues(apiAccess.getEngineReceiver(localEngine));
        ForeignPolyglotIsolateServices polyglotIsolateServices;
        if (externalProcess) {
            polyglotIsolateServices = spawnProcessIsolate(libraryConfig, l);
        } else {
            polyglotIsolateServices = spawnNativeIsolate(libraryConfig, l, engineOptions);
        }
        Isolate<?> isolate = polyglotIsolateServices.getPeer().getIsolate();
        boolean success = false;
        try {
            String resourcesPath = libraryConfig.resourcePath != null ? libraryConfig.resourcePath.toAbsolutePath().toString() : null;
            PolyglotHostServicesImpl polyglotHostServices = new PolyglotHostServicesImpl(polyglot, isolate);
            polyglotIsolateServices.initialize(polyglotHostServices, resourcesPath);
            if (PolyglotIsolateAccessor.ENGINE.isIsolateMemoryProtection(engineOptions)) {
                if (!polyglotIsolateServices.isMemoryProtected()) {
                    throw new PolyglotIsolateCreateException("Memory Protection not available");
                }
            }
            Object localEngineReceiver = apiAccess.getEngineReceiver(localEngine);
            AbstractPolyglotImpl.AbstractEngineDispatch localEngineDispatch = apiAccess.getEngineDispatch(localEngine);
            AbstractPolyglotImpl.LogHandler localEngineLogHandler = PolyglotIsolateAccessor.ENGINE.getEngineLogHandler(localEngineReceiver);
            AbstractPolyglotImpl.AbstractHostLanguageService localHostService = PolyglotIsolateAccessor.ENGINE.getHostService(apiAccess.getEngineReceiver(localEngine));
            long engineHandle = polyglotIsolateServices.buildEngine(permittedLanguages, sandboxPolicy, out, err, in, options, systemPropertiesOptions, useSystemProperties,
                            allowExperimentalOptions, boundEngine, messageInterceptor, localEngineLogHandler, l.notificationHostService, localHostService);
            Peer enginePeer = Peer.create(isolate, engineHandle);
            ForeignEngine foreignEngine = new ForeignEngine(enginePeer, localEngine, boundEngine, stackHeadroom, polyglotIsolateServices);
            Engine e = apiAccess.newEngine(getEngineDispatch(foreignEngine), foreignEngine, registerInActiveEngines);
            localEngineDispatch.setEngineAPIReference(localEngineReceiver, foreignEngine.getEngineAPIReference());
            l.registerEngine(foreignEngine);
            if (registerInActiveEngines) {
                apiAccess.processReferenceQueue();
            }
            success = true;
            return e;
        } finally {
            if (!success) {
                isolate.shutdown();
            }
        }
    }

    private static Lazy initializeLazy(AbstractPolyglotImpl polyglot) {
        Lazy l = lazy;
        if (l == null) {
            synchronized (PolyglotIsolateHostSupport.class) {
                l = lazy;
                if (l == null) {
                    lazy = l = new Lazy(polyglot);
                }
            }
        }
        return l;
    }

    private static ForeignPolyglotIsolateServices spawnProcessIsolate(LibraryConfig libraryConfig, Lazy l) {
        if (InternalResource.OS.getCurrent() == InternalResource.OS.WINDOWS && !ProcessIsolate.isSupported()) {
            throw new IllegalArgumentException(String.format("The option 'engine.IsolateMode=external' requires UNIX domain socket support, " +
                            "which is not available on this operating system (OS version %s). " +
                            "UNIX domain sockets are supported on Windows 10, build 17063 or later.",
                            System.getProperty("os.version")));
        }
        Path launcher = libraryConfig.externalIsolateLauncherPath();
        if (launcher == null) {
            throw new IllegalArgumentException("The option 'engine.IsolateMode=external' requires a path to the isolate launcher executable. " +
                            "Use 'engine.IsolateLauncher' option to specify path to the launcher.");
        }
        Path polyglotIsolateLibrary = libraryConfig.libraryPath();
        Path socketAddress = ProcessIsolateConfig.createDefaultInitiatorSocketAddress(l.getTempFolder());
        ProcessIsolateConfig config = ProcessIsolateConfig.newInitiatorBuilder(launcher.toAbsolutePath(), socketAddress).//
                        threadLocalFactory(() -> PolyglotIsolateAccessor.RUNTIME.createTerminatingThreadLocal(() -> null, (t) -> {
                            if (t != null) {
                                t.getIsolate().detachCurrentThread();
                            }
                        })).//
                        onIsolateTearDown(PolyglotIsolateHostSupport::onIsolateTearDown).//
                        launcherArgument(polyglotIsolateLibrary.toString()).//
                        launcherArgument(ProcessIsolateEntryPoint.class.getName()).//
                        launcherArgument(socketAddress.toString()).//
                        build();
        try {
            return PolyglotIsolateForeignFactoryGen.create(config);
        } catch (IsolateCreateException ice) {
            throw new PolyglotIsolateCreateException(ice);
        }
    }

    private static ForeignPolyglotIsolateServices spawnNativeIsolate(LibraryConfig libraryConfig, Lazy l, OptionValues engineOptions) {
        int isolationDomain = 0;
        if (PolyglotIsolateAccessor.ENGINE.isIsolateMemoryProtection(engineOptions)) {
            // -1 is the auto-assign mode
            isolationDomain = -1;
        }
        l.useNativeIsolateLibrary(libraryConfig.libraryPath());
        NativeIsolateConfig config = NativeIsolateConfig.newBuilder(l.nativeIsolateLibraryPath).//
                        nativeIsolateHandlerOption(PolyglotNativeIsolateHandler.ISOLATION_DOMAIN, isolationDomain).//
                        nativeThreadLocalFactory(() -> PolyglotIsolateAccessor.RUNTIME.createTerminatingThreadLocal(() -> null, (t) -> {
                            if (t != null) {
                                t.getIsolate().detachCurrentThread();
                            }
                        })).//
                        onIsolateTearDown(PolyglotIsolateHostSupport::onIsolateTearDown).//
                        build();
        try {
            return PolyglotIsolateForeignFactoryGen.create(config);
        } catch (IsolateCreateException ice) {
            throw new PolyglotIsolateCreateException(ice);
        }
    }

    private static void onIsolateTearDown(Isolate<?> isolate) {
        Lazy l = lazy;
        ForeignEngine foreignEngine = l.findEngineReferenceByIsolate(isolate);
        /*
         * foreignEngine may be null if the close originated in the #spawnIsolatedEngine as a result
         * of an exception thrown during creating the isolated engine.
         */
        if (foreignEngine != null) {
            foreignEngine.close();
            // Parfait_ALLOW toctou-race-condition-warning (prevented by NativeIsolate#create)
            l.unregisterEngine(foreignEngine.getKey());
            foreignEngine.getPolyglotIsolateServices().onIsolateTearDown();
        }
    }

    static AbstractPolyglotImpl.AbstractEngineDispatch getEngineDispatch(ForeignObject forForeignObject) {
        return lazy.getDispatch(forForeignObject).engineDispatch;
    }

    static AbstractPolyglotImpl.AbstractContextDispatch getContextDispatch(ForeignObject forForeignObject) {
        return lazy.getDispatch(forForeignObject).contextDispatch;
    }

    static ForeignInstrumentDispatch getInstrumentDispatch(ForeignObject forForeignObject) {
        return lazy.getDispatch(forForeignObject).instrumentDispatch;
    }

    static ForeignLanguageDispatch getLanguageDispatch(ForeignObject forForeignObject) {
        return lazy.getDispatch(forForeignObject).languageDispatch;
    }

    static AbstractPolyglotImpl.AbstractExecutionListenerDispatch getExecutionListenerDispatch(ForeignObject forForeignObject) {
        return lazy.getDispatch(forForeignObject).executionListenerDispatch;
    }

    static AbstractPolyglotImpl.AbstractExecutionEventDispatch getExecutionEventDispatch(ForeignObject forForeignObject) {
        return lazy.getDispatch(forForeignObject).executionEventDispatch;
    }

    static AbstractPolyglotImpl.AbstractSourceDispatch getSourceDispatch(ForeignObject forForeignObject) {
        return lazy.getDispatch(forForeignObject).sourceDispatch;
    }

    static AbstractPolyglotImpl.AbstractSourceSectionDispatch getSourceSectionDispatch(ForeignObject forForeignObject) {
        return lazy.getDispatch(forForeignObject).sourceSectionDispatch;
    }

    static void registerContext(ForeignContext foreignContext) {
        lazy.registerContext(foreignContext);
    }

    static void releaseContextRegisteredRequirement(ForeignContext foreignContext, boolean releaseContextIfNotRequired) {
        lazy.releaseContextRegisteredRequirement(foreignContext, releaseContextIfNotRequired);
    }

    static boolean requireContextRegistered(ForeignContext foreignContext) {
        return lazy.requireContextRegistered(foreignContext);
    }

    static Object findContextByHandle(Isolate<?> isolate, long contextHandle) {
        PolyglotIsolateGuestSupport.Lazy l = PolyglotIsolateGuestSupport.lazy;
        if (l != null) {
            GuestContext guestContext = ReferenceHandles.resolve(contextHandle, GuestContext.class);
            return guestContext != null ? guestContext.polyglotContextReceiver : null;
        } else {
            return lazy.findContextReceiverByHandle(isolate, contextHandle);
        }
    }

    static Object findEngineReceiverByHandle(Isolate<?> isolate, long engineHandle) {
        PolyglotIsolateGuestSupport.Lazy l = PolyglotIsolateGuestSupport.lazy;
        if (l != null) {
            GuestEngine guestEngine = ReferenceHandles.resolve(engineHandle, GuestEngine.class);
            return guestEngine != null ? l.polyglot.getAPIAccess().getEngineReceiver(guestEngine.engine) : null;
        } else {
            return lazy.findEngineReceiverByHandle(isolate, engineHandle);
        }
    }

    static long findEngineHandleByEngineReceiver(Object engineReceiver) {
        PolyglotIsolateGuestSupport.Lazy l = PolyglotIsolateGuestSupport.lazy;
        if (l != null) {
            GuestEngine guestEngine = l.guestEngineByEngineReceiver.get(engineReceiver);
            return guestEngine != null ? guestEngine.getHandle() : 0L;
        } else {
            if (engineReceiver instanceof ForeignEngine) {
                return ((ForeignEngine) engineReceiver).getPeer().getHandle();
            } else {
                throw new IllegalArgumentException(String.valueOf(engineReceiver));
            }
        }
    }

    static long findContextHandleByContextReceiver(Object contextReceiver) {
        PolyglotIsolateGuestSupport.Lazy l = PolyglotIsolateGuestSupport.lazy;
        if (l != null) {
            GuestContext guestContext = l.guestContextByContextReceiver.get(contextReceiver);
            return guestContext != null ? guestContext.getHandle() : 0L;
        } else {
            if (contextReceiver instanceof ForeignContext) {
                return ((ForeignContext) contextReceiver).getPeer().getHandle();
            } else {
                throw new IllegalArgumentException(String.valueOf(contextReceiver));
            }
        }
    }

    static AbstractPolyglotImpl getPolyglot() {
        if (PolyglotIsolateGuestSupport.isHost()) {
            return lazy.polyglot;
        } else {
            return PolyglotIsolateGuestSupport.lazy.polyglot;
        }
    }

    private record PolyglotIsolateResource(String resourceId, Set<String> includedLanguages) {

        String getIncludedLanguagesAsString() {
            List<String> languages = new ArrayList<>(includedLanguages);
            Collections.sort(languages);
            return "[" + String.join(", ", languages) + "]";
        }
    }

    private record LibraryConfig(Path libraryPath, Path resourcePath, Path externalIsolateLauncherPath) {

        private LibraryConfig {
            Objects.requireNonNull(libraryPath, "LibraryPath must be non-null.");
        }

        LibraryConfig(Path libraryPath, Path externalIsolateLauncherPath) {
            this(libraryPath, null, externalIsolateLauncherPath);
        }
    }

    private record DispatchClasses(ForeignEngineDispatch engineDispatch,
                    ForeignContextDispatch contextDispatch,
                    ForeignInstrumentDispatch instrumentDispatch,
                    ForeignLanguageDispatch languageDispatch,
                    ForeignExecutionEventDispatch executionEventDispatch,
                    ForeignExecutionListenerDispatch executionListenerDispatch,
                    ForeignSourceDispatch sourceDispatch,
                    ForeignSourceSectionDispatch sourceSectionDispatch) {

        static DispatchClasses create(AbstractPolyglotImpl polyglot, Class<? extends Peer> type) {
            return new DispatchClasses(ForeignEngineDispatchGen.create(polyglot, type),
                            ForeignContextDispatchGen.create(polyglot, type),
                            ForeignInstrumentDispatchGen.create(polyglot, type),
                            ForeignLanguageDispatchGen.create(polyglot, type),
                            ForeignExecutionEventDispatchGen.create(polyglot, type),
                            ForeignExecutionListenerDispatchGen.create(polyglot, type),
                            ForeignSourceDispatchGen.create(polyglot, type),
                            ForeignSourceSectionDispatchGen.create(polyglot, type));
        }
    }

    private static final class Lazy {

        private final AbstractPolyglotImpl polyglot;
        private final Map<Pair<Isolate<?>, Long>, ForeignEngine> isolatedEnginesByHandle;
        private final Map<Isolate<?>, ForeignEngine> isolatedEnginesByIsolate;
        private final Map<Pair<Isolate<?>, Long>, ForeignContext> isolatedContextsByHandle;
        private final Map<Pair<Isolate<?>, Long>, Context> anchorForStartingThread;
        private final AbstractPolyglotImpl.AbstractPolyglotHostService notificationHostService;
        private final ClassValue<DispatchClasses> dispatchClasses;
        private volatile Path tmpFolder;

        /*
         * For JNI we are limited to a single polyglot isolate library in a process. This field
         * holds a reference to the used to prevent multiple loading. This limitation does not exist
         * in the process isolate.
         */
        private Path nativeIsolateLibraryPath;

        private Lazy(AbstractPolyglotImpl polyglot) {
            this.polyglot = polyglot;
            this.isolatedEnginesByHandle = new HashMap<>();
            this.isolatedEnginesByIsolate = new HashMap<>();
            this.isolatedContextsByHandle = new HashMap<>();
            this.anchorForStartingThread = new ConcurrentHashMap<>();
            this.notificationHostService = new PolyglotHostServiceImpl(polyglot);
            this.dispatchClasses = new ClassValue<>() {
                @Override
                @SuppressWarnings("unchecked")
                protected DispatchClasses computeValue(Class<?> type) {
                    if (Peer.class.isAssignableFrom(type)) {
                        return DispatchClasses.create(polyglot, (Class<? extends Peer>) type);
                    } else {
                        throw new IllegalArgumentException(type.getName());
                    }
                }
            };
        }

        private Path getTempFolder() {
            Path result = tmpFolder;
            if (result == null) {
                String tmpDir = System.getProperty("java.io.tmpdir", "");
                result = Path.of(tmpDir);
                tmpFolder = result;
            }
            return result;
        }

        private DispatchClasses getDispatch(ForeignObject foreignObject) {
            return dispatchClasses.get(foreignObject.getPeer().getClass());
        }

        synchronized void useNativeIsolateLibrary(Path libraryPath) {
            try {
                if (nativeIsolateLibraryPath == null) {
                    nativeIsolateLibraryPath = libraryPath;
                } else if (!nativeIsolateLibraryPath.equals(libraryPath) && !Files.isSameFile(nativeIsolateLibraryPath, libraryPath)) {
                    throw new IllegalStateException(String.format("A native library for engine.SpawnIsolate at location '%s' was already loaded. " + //
                                    "A new native library was attempted to be loaded at path '%s'. " + //
                                    "Only one native library can be used per process at a time. " + //
                                    "Explicitly specify the set loaded library with the --engine.IsolateLibrary=/path/to/lib option or use --engine.SpawnIsolate=false to disable this feature to resolve this problem.",
                                    nativeIsolateLibraryPath, libraryPath));
                }
            } catch (IOException ioe) {
                throw new IllegalArgumentException("Cannot resolve isolate library path.", ioe);
            }
        }

        synchronized void registerEngine(ForeignEngine foreignEngine) {
            Pair<Isolate<?>, Long> key = foreignEngine.getKey();
            assert isolatedEnginesByHandle.get(key) == null;
            isolatedEnginesByHandle.put(key, foreignEngine);
            isolatedEnginesByIsolate.put(key.getLeft(), foreignEngine);
        }

        synchronized void unregisterEngine(Pair<Isolate<?>, Long> key) {
            isolatedEnginesByHandle.remove(key);
            isolatedEnginesByIsolate.remove(key.getLeft());
        }

        synchronized void registerContext(ForeignContext foreignContext) {
            Pair<Isolate<?>, Long> key = foreignContext.getKey();
            ForeignContext oldContext = isolatedContextsByHandle.get(key);
            /*
             * When oldContext != null, oldContext represents a context from an already disposed
             * isolate (thus the isolate id was made available for use by the current isolate).
             */
            if (oldContext != null && !oldContext.getPeer().getIsolate().isDisposed()) {
                throw new AssertionError("Replacing an active context reference.");
            }
            isolatedContextsByHandle.put(key, foreignContext);
        }

        synchronized void releaseContextRegisteredRequirement(ForeignContext foreignContext, boolean releaseContextIfNotRequired) {
            assert foreignContext.registrationRequired > 0;
            boolean releaseContext = false;
            foreignContext.registrationRequired--;
            if (foreignContext.registrationRequired == 0 && releaseContextIfNotRequired) {
                releaseContext = true;
                IsolateThread isolateThread = foreignContext.getPeer().getIsolate().tryEnter();
                // Check for null, other thread may be closing or just closed engine and isolate.
                if (isolateThread != null) {
                    try {
                        foreignContext.dispose();
                    } finally {
                        isolateThread.leave();
                    }
                }
            }

            // The isolate that foreignContext belongs to might have already been torn down, and a
            // new
            // isolate with the same id with a new NativeContext with the same handle might have
            // already been created since then. Therefore, we have to make sure we are removing the
            // right thing.
            ForeignContext registeredContext = isolatedContextsByHandle.get(foreignContext.getKey());
            if (registeredContext != null && registeredContext == foreignContext && releaseContext) {
                isolatedContextsByHandle.remove(foreignContext.getKey());
            }
        }

        synchronized boolean requireContextRegistered(ForeignContext foreignContext) {
            ForeignContext registeredContext = isolatedContextsByHandle.get(foreignContext.getKey());
            if (registeredContext != null && registeredContext == foreignContext) {
                foreignContext.registrationRequired++;
                return true;
            }
            return false;
        }

        synchronized ForeignEngine findEngineReceiverByHandle(Isolate<?> isolate, long engineHandle) {
            return isolatedEnginesByHandle.get(Pair.create(isolate, engineHandle));
        }

        synchronized ForeignEngine findEngineReferenceByIsolate(Isolate<?> isolate) {
            return isolatedEnginesByIsolate.get(isolate);
        }

        synchronized Object findContextReceiverByHandle(Isolate<?> isolate, long contextHandle) {
            return isolatedContextsByHandle.get(Pair.create(isolate, contextHandle));
        }

        private synchronized void unregisterContext(ForeignContext context) {
            isolatedContextsByHandle.remove(context.getKey());
        }

        void captureContextAPIForStartingThread(Isolate<?> isolate, long foreignThreadId, Context contextAPI) {
            anchorForStartingThread.put(Pair.create(isolate, foreignThreadId), contextAPI);
        }

        Context getAndClearContextAPIForStartingThread(Isolate<?> isolate, long foreignThreadId) {
            Context contextAPI = anchorForStartingThread.remove(Pair.create(isolate, foreignThreadId));
            /*
             * For virtual threads, the context API is not captured in the Thread#start() method and
             * contextAPI is null. But polyglot isolate does not currently support them. Once
             * support is added, we will need to call getContextAPIOrNull() to obtain the anchor for
             * virtual threads.
             */
            assert contextAPI != null : "Context API must be non-null";
            return contextAPI;
        }
    }

    private static final class PolyglotHostServicesImpl implements PolyglotHostServices {

        private final AbstractPolyglotImpl polyglot;
        private final Isolate<?> isolate;

        PolyglotHostServicesImpl(AbstractPolyglotImpl polyglot, Isolate<?> isolate) {
            this.polyglot = Objects.requireNonNull(polyglot, "Polyglot must be non-null");
            this.isolate = Objects.requireNonNull(isolate, "Isolate must be non-null");
        }

        @Override
        public void attachPolyglotThread(Object contextReceiver, long isolateThreadId, boolean enterContext, long polyglotThreadId) {
            // 1) Pre-enter isolate
            if (isolate instanceof NativeIsolate nativeIsolate) {
                nativeIsolate.registerNativeThread(isolateThreadId);
            }
            Context contextAPI = lazy.getAndClearContextAPIForStartingThread(isolate, polyglotThreadId);
            if (enterContext) {
                // 2) Enter local context
                ForeignContext foreignContext = (ForeignContext) contextReceiver;
                if (foreignContext == null) {
                    // The nativeContext may be null when the context on the host is freed between
                    // polyglot thread creation on guest and the call to attachPolyglotThread.
                    throw PolyglotIsolateAccessor.ENGINE.createCancelExecution(null, "Context disposed.", false);
                }
                Object localContextReceiver = polyglot.getAPIAccess().getContextReceiver(foreignContext.getLocalContext());
                Object[] prev = PolyglotIsolateAccessor.ENGINE.enterContextAsPolyglotThread(localContextReceiver, contextAPI);
                assert prev == null;
            }
        }

        @Override
        public void detachPolyglotThread(Object contextReceiver, boolean leaveContext) {
            if (leaveContext) {
                ForeignContext foreignContext = (ForeignContext) contextReceiver;
                // Leave local context
                Object localContextReceiver = polyglot.getAPIAccess().getContextReceiver(foreignContext.getLocalContext());
                PolyglotIsolateAccessor.ENGINE.leaveContextAsPolyglotThread(localContextReceiver, null);
            }
        }

        @Override
        public long retrieveHostStackOverflowLimit() {
            return PolyglotIsolateAccessor.RUNTIME.getStackOverflowLimit();
        }

        @Override
        public boolean isDefaultProcessHandler(ProcessHandler processHandler) {
            return PolyglotIsolateAccessor.ENGINE.isDefaultProcessHandler(processHandler);
        }

        @Override
        public boolean isInternalFileSystem(FileSystem fileSystem) {
            return PolyglotIsolateAccessor.ENGINE.isInternalFileSystem(fileSystem);
        }

        @Override
        public boolean isInCurrentEngineHostCallback(Object engineReceiver) {
            ForeignEngine foreignEngine = (ForeignEngine) engineReceiver;
            Object localEngineReceiver = polyglot.getAPIAccess().getEngineReceiver(foreignEngine.getLocalEngine());
            return PolyglotIsolateAccessor.ENGINE.isInCurrentEngineHostCallback(localEngineReceiver);
        }

        @Override
        public void notifyPolyglotThreadStart(Object contextReceiver, long foreignThreadId) {
            ForeignContext foreignContext = (ForeignContext) contextReceiver;
            lazy.captureContextAPIForStartingThread(foreignContext.getPeer().getIsolate(), foreignThreadId, foreignContext.getContextAPI());
        }

        @Override
        public Throwable getStackTrace() {
            return new Exception();
        }
    }

    static class PolyglotHostServiceImpl extends AbstractPolyglotImpl.AbstractPolyglotHostService {

        final AbstractPolyglotImpl polyglot;

        PolyglotHostServiceImpl(AbstractPolyglotImpl polyglot) {
            super(polyglot);
            this.polyglot = polyglot;
        }

        @Override
        public void notifyClearExplicitContextStack(Object contextReceiver) {
            if (contextReceiver != null) {
                ForeignContext foreignContext = (ForeignContext) contextReceiver;
                Object localContextReceiver = polyglot.getAPIAccess().getContextReceiver(foreignContext.getLocalContext());
                PolyglotIsolateAccessor.ENGINE.clearExplicitContextStack(localContextReceiver);
            }
        }

        @Override
        public void notifyContextCancellingOrExiting(Object contextReceiver, boolean exit, int exitCode, boolean resourceLimit, String message) {
            if (contextReceiver != null) {
                ForeignContext foreignContext = (ForeignContext) contextReceiver;
                Object localContextReceiver = polyglot.getAPIAccess().getContextReceiver(foreignContext.getLocalContext());
                PolyglotIsolateAccessor.ENGINE.initiateCancelOrExit(localContextReceiver, exit, exitCode, resourceLimit, message);
            }
        }

        @Override
        public void notifyContextClosed(Object contextReceiver, boolean cancelIfExecuting, boolean resourceLimit, String message) {
            if (contextReceiver != null) {
                ForeignContext foreignContext = (ForeignContext) contextReceiver;
                Object localContextReceiver = polyglot.getAPIAccess().getContextReceiver(foreignContext.getLocalContext());
                PolyglotIsolateAccessor.ENGINE.closeContext(localContextReceiver, cancelIfExecuting, resourceLimit, message);
                if (!PolyglotIsolateAccessor.ENGINE.isContextActive(localContextReceiver)) {
                    lazy.unregisterContext(foreignContext);
                }
            }
        }

        @Override
        public void notifyEngineClosed(Object engineReceiver, boolean cancelIfExecuting) {
            if (engineReceiver != null) {
                ForeignEngine foreignEngine = (ForeignEngine) engineReceiver;
                Object localEngineReceiver = polyglot.getAPIAccess().getEngineReceiver(foreignEngine.getLocalEngine());
                PolyglotIsolateAccessor.ENGINE.closeEngine(localEngineReceiver, cancelIfExecuting);
                foreignEngine.getPeer().getIsolate().shutdown();
            }
        }

        @Override
        public RuntimeException hostToGuestException(AbstractPolyglotImpl.AbstractHostLanguageService host, Throwable throwable) {
            throw new AssertionError("Should not reach here.");
        }

        @Override
        public void notifyPolyglotThreadStart(Object contextReceiver, Thread threadToStart) {
            throw new AssertionError("Should not reach here.");
        }
    }
}
