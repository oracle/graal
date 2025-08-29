/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

import java.nio.file.Path;
import java.util.function.Predicate;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilerProfiler;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.debug.PathUtilities;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.hotspot.HotSpotBackendFactory;
import jdk.graal.compiler.hotspot.HotSpotDecoratedBackendFactory;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.Platform;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.printer.CanonicalStringGraphPrinter;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Provides support for recording and replaying compilations, acting as an interface between the
 * compiler and the internal implementation of replay compilation.
 *
 * <p>
 * Recording is enabled using {@link DebugOptions#RecordForReplay}. Replay compilations are executed
 * by the {@link ReplayCompilationRunner}. It is not a goal to execute the replayed code.
 *
 * <p>
 * <b>Proxies.</b> Recording and replay require creating proxies for JVMCI objects. During
 * recording, these proxies record the arguments and results of methods to serialize them into a
 * JSON file ({@link RecordedOperationPersistence}). During replay, they look up and return the
 * appropriate results. The behavior of every proxy and method is configured in
 * {@link CompilerInterfaceDeclarations}.
 *
 * <p>
 * <b>Decorators.</b> To enable recording and replay, we create service providers that return the
 * appropriate proxies. This is achieved by decorating the backend factory
 * {@link #decorateBackendFactory}. Moreover, {@link #decorateCompilationRequest},
 * {@link #decorateCompilerProfiler}, {@link #decorateVMConfigAccess}, and
 * {@link #decorateIntrinsificationTrustPredicate} return proxies for additional objects.
 *
 * <p>
 * <b>Compiler Instances and Snippets.</b> Since we hijack the compiler's backend factory, a
 * dedicated compiler instance must be created for recording and replay. During recording, separate
 * compiler instances should be used for the recorded methods
 * ({@link #matchesRecordCompilationFilter}) and the methods that are not recorded. This ensures
 * that the recording overhead is paid for the recorded methods only. Snippets and stubs are
 * considered part of the compiler rather than the application, and they should never be recorded.
 * It is possible to record a libgraal compilation and replay it on jargraal.
 *
 * <p>
 * <b>Local Mirrors.</b> During replay, we search for equivalent JVMCI objects for some of the
 * proxies ({@link #findLocalMirrors}). This is useful when the compiler queries information that
 * was not recorded, including the information used to process snippets. There are also local-only
 * proxies that do not originate from the recorded JSON but are instead created from local JVMCI
 * objects (created using {@link CompilationProxies#proxify}). The exact rules when operations are
 * delegated to local mirrors are dictated by the strategies defined in
 * {@link CompilerInterfaceDeclarations}.
 *
 * <p>
 * <b>Foreign Call Linkages.</b> During recording, we capture the addresses and killed registers of
 * finalized foreign calls ({@link RecordedForeignCallLinkages}). During replay, we restore these
 * linkages ({@link #finalizeForeignCallLinkage}) to ensure that the machine code compiled during
 * replay is close to the code compiled during recording.
 */
public final class ReplayCompilationSupport {
    /**
     * Libgraal build-time system property name for enabling the replay compilation launcher.
     */
    public static final String ENABLE_REPLAY_LAUNCHER_PROP = "debug.jdk.graal.enableReplayLauncher";

    /**
     * Whether the replay compilation launcher is enabled in libgraal.
     */
    public static final boolean ENABLE_REPLAY_LAUNCHER = Boolean.parseBoolean(GraalServices.getSavedProperty(ENABLE_REPLAY_LAUNCHER_PROP));

    /**
     * Checks whether the given method's compilation should be recorded according to the given
     * options.
     *
     * @param options the option values
     * @param method the method to check against the filter
     * @return true if the method's compilation should be recorded
     */
    public static boolean matchesRecordCompilationFilter(OptionValues options, ResolvedJavaMethod method) {
        String filter = DebugOptions.RecordForReplay.getValue(options);
        if (filter == null) {
            return false;
        } else {
            return MethodFilter.parse(filter).matches(method);
        }
    }

    /**
     * The proxies used for recording/replaying the compilations.
     */
    private final CompilationProxies proxies;

    /**
     * The compiler's foreign call provider.
     */
    private HotSpotHostForeignCallsProvider foreignCallsProvider;

    /**
     * Recorded foreign call linkages used to finalize foreign calls.
     */
    private RecordedForeignCallLinkages recordedForeignCallLinkages;

    /**
     * The compilation artifacts of the current compiler thread.
     */
    private final ThreadLocal<CompilationArtifacts> compilationArtifacts = ThreadLocal.withInitial(() -> null);

    /**
     * The compiler's configuration name.
     */
    private final String compilerConfigurationName;

    ReplayCompilationSupport(CompilationProxies proxies, String compilerConfigurationName) {
        this.proxies = proxies;
        this.compilerConfigurationName = compilerConfigurationName;
    }

    /**
     * Creates a replay compilation support for a recording compiler.
     *
     * @param compilerConfigurationName the name of the compiler's configuration
     * @return the support for a recording compiler
     */
    public static ReplayCompilationSupport createRecording(String compilerConfigurationName) {
        return new ReplayCompilationSupport(new RecordingCompilationProxies(CompilerInterfaceDeclarations.build()), compilerConfigurationName);
    }

    /**
     * Sets the foreign calls provider of the compiler bound to this instance.
     *
     * @param newForeignCallsProvider the compiler's foreign calls provider
     */
    public void setForeignCallsProvider(HotSpotHostForeignCallsProvider newForeignCallsProvider) {
        foreignCallsProvider = newForeignCallsProvider;
    }

    /**
     * Sets the recorded foreign calls linkages to be used to finalize foreign calls.
     *
     * @param linkages recorded linkages
     */
    public void setRecordedForeignCallLinkages(RecordedForeignCallLinkages linkages) {
        recordedForeignCallLinkages = linkages;
    }

    /**
     * Filters the given options to remove any that are not allowed during recording.
     *
     * @param options the options to filter
     * @return the filtered options
     */
    public OptionValues filterOptions(OptionValues options) {
        if (proxies instanceof RecordingCompilationProxies) {
            // GraalOptions.UseSnippetGraphCache can remain enabled.
            return new OptionValues(options, SnippetTemplate.Options.UseSnippetTemplateCache, false);
        } else {
            return options;
        }
    }

    /**
     * Enters a snippet context. In a recorded compilation, the proxies should not record JVMCI
     * calls and results related to snippet processing. In a replayed compilation, the proxies can
     * delegate to the local mirrors to handle JVMCI calls.
     *
     * @return a debug closeable that should be closed when exiting the context
     */
    private DebugCloseable enterSnippetContext() {
        return proxies.enterSnippetContext();
    }

    /**
     * Enters a snippet context for the given providers.
     * <p>
     * If the providers have a non-null {@link ReplayCompilationSupport} instance, this method
     * enters a snippet context. Otherwise, it returns {@code null}.
     *
     * @param providers the providers to check for {@link ReplayCompilationSupport}
     * @return a scope representing the snippet context, or {@code null} if not supported
     */
    public static DebugCloseable enterSnippetContext(HotSpotProviders providers) {
        return enterSnippetContext(providers.getReplayCompilationSupport());
    }

    /**
     * Enters a snippet context for the given {@link ReplayCompilationSupport} instance.
     * <p>
     * If the provided {@code support} is not {@code null}, this method enters a snippet context.
     * Otherwise, it returns {@code null}.
     *
     * @param support the instance to check
     * @return a scope representing the snippet context, or {@code null} if not supported
     */
    public static DebugCloseable enterSnippetContext(ReplayCompilationSupport support) {
        if (support == null) {
            return null;
        } else {
            return support.enterSnippetContext();
        }
    }

    /**
     * Enters a scope with a given debug context.
     *
     * @param debug the debug context to use in the scope
     * @return an object that closes the scope
     */
    public DebugCloseable withDebugContext(DebugContext debug) {
        return proxies.withDebugContext(debug);
    }

    /**
     * Enters a compilation context for the specified compilation request.
     *
     * @param originalRequest the original compilation request
     * @param initialOptions the initial options for the compilation
     * @return a debug closeable that should be closed after the compilation
     */
    public DebugCloseable enterCompilationContext(HotSpotCompilationRequest originalRequest, OptionValues initialOptions) {
        DebugCloseable context = proxies.enterCompilationContext();
        return () -> {
            try {
                if (proxies instanceof RecordingCompilationProxies) {
                    serializeRecordedCompilation(originalRequest, initialOptions);
                }
            } finally {
                context.close();
            }
        };
    }

    private void serializeRecordedCompilation(HotSpotCompilationRequest originalRequest, OptionValues initialOptions) {
        RecordedForeignCallLinkages linkages = RecordedForeignCallLinkages.createFrom(foreignCallsProvider);
        try {
            String directory = PathUtilities.getPath(DebugOptions.getDumpDirectory(initialOptions), "replaycomp");
            PathUtilities.createDirectories(directory);
            String requestId = Integer.toString(originalRequest.getId());
            String fileName = requestId + ".json";
            Path path = Path.of(directory, fileName);
            RecordedOperationPersistence persistence = new RecordedOperationPersistence(proxies.getDeclarations(), Platform.ofCurrentHost(),
                            HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget());
            RecordingCompilationProxies recordingCompilationProxies = (RecordingCompilationProxies) proxies;
            CompilationArtifacts artifacts = clearCompilationArtifacts();
            String finalCanonicalGraph = (artifacts == null) ? null : artifacts.finalCanonicalGraph();
            RecordedOperationPersistence.RecordedCompilationUnit compilationUnit = new RecordedOperationPersistence.RecordedCompilationUnit(originalRequest, compilerConfigurationName,
                            LibGraalSupport.inLibGraalRuntime(), recordingCompilationProxies.targetPlatform(), linkages, finalCanonicalGraph,
                            recordingCompilationProxies.collectOperationsForSerialization());
            try (JsonWriter jsonWriter = new JsonWriter(path)) {
                persistence.dump(compilationUnit, jsonWriter);
            }
            TTY.println("Serialized " + originalRequest + " to " + path);
        } catch (Exception exception) {
            TTY.println("Failed to serialize the recorded operations to a file.");
            exception.printStackTrace(TTY.out().out());
        }
    }

    /**
     * Sets the address and temporaries of a foreign call linkage using the recorded linkages if
     * they are available and contain the given foreign call signature.
     *
     * @param signature the foreign call signature
     * @param callTarget the foreign call target to update
     * @return true if the linkage was finalized
     */
    public boolean finalizeForeignCallLinkage(ForeignCallSignature signature, HotSpotForeignCallLinkage callTarget) {
        if (recordedForeignCallLinkages != null) {
            return recordedForeignCallLinkages.finalizeForeignCallLinkage(signature, callTarget);
        }
        return false;
    }

    /**
     * Finds local mirrors for the parsed proxies during replay compilation. This should be invoked
     * just after the core JVMCI providers are created because they are needed to look up the
     * mirrors.
     *
     * @param jvmciRuntime the JVMCI runtime
     */
    public void findLocalMirrors(HotSpotJVMCIRuntime jvmciRuntime) {
        if (proxies instanceof ReplayCompilationProxies replayCompilationProxies) {
            replayCompilationProxies.findLocalMirrors(jvmciRuntime);
        }
    }

    /**
     * Decorates a backend factory.
     *
     * @param factory the backend factory to decorate
     * @param jvmciRuntime the JVMCI runtime
     * @return the decorated backend factory
     */
    public HotSpotBackendFactory decorateBackendFactory(HotSpotBackendFactory factory, HotSpotJVMCIRuntime jvmciRuntime) {
        return new HotSpotDecoratedBackendFactory(factory, new HotSpotProxyBackendFactory(proxies, this, jvmciRuntime));
    }

    /**
     * Decorates a compilation request.
     *
     * @param request the compilation request to decorate
     * @return the decorated compilation request
     */
    public HotSpotCompilationRequest decorateCompilationRequest(HotSpotCompilationRequest request) {
        return new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) proxies.proxify(request.getMethod()), request.getEntryBCI(), request.getJvmciEnv(), request.getId());
    }

    /**
     * Decorates a compiler profiler.
     *
     * @param compilerProfiler the compiler profiler to decorate
     * @return the decorated compiler profiler
     */
    public CompilerProfiler decorateCompilerProfiler(CompilerProfiler compilerProfiler) {
        return (CompilerProfiler) proxies.proxify(compilerProfiler);
    }

    /**
     * Decorates an intrinsification trust predicate.
     *
     * @param predicate the intrinsification trust predicate to decorate
     * @return the decorated intrinsification trust predicate
     */
    @SuppressWarnings("unchecked")
    public Predicate<ResolvedJavaType> decorateIntrinsificationTrustPredicate(Predicate<ResolvedJavaType> predicate) {
        return (Predicate<ResolvedJavaType>) proxies.proxify(predicate);
    }

    /**
     * Decorates a VM config access.
     *
     * @param access the VM config access to decorate
     * @return the decorated VM config access
     */
    public HotSpotVMConfigAccess decorateVMConfigAccess(HotSpotVMConfigAccess access) {
        return (HotSpotVMConfigAccess) proxies.proxify(access);
    }

    /**
     * Returns the target platform.
     */
    public Platform targetPlatform() {
        return proxies.targetPlatform();
    }

    /**
     * The artifacts produced by a compilation.
     *
     * @param graph the final graph
     * @param result the compilation result
     */
    public record CompilationArtifacts(StructuredGraph graph, CompilationResult result) {
        /**
         * Returns the canonical graph string for the final graph.
         */
        public String finalCanonicalGraph() {
            return CanonicalStringGraphPrinter.getCanonicalGraphString(graph, false, true);
        }
    }

    /**
     * Records the artifacts produced by the last compilation of the current compiler thread.
     *
     * @param graph the final graph
     * @param result the compilation result
     */
    public void recordCompilationArtifacts(StructuredGraph graph, CompilationResult result) {
        compilationArtifacts.set(new CompilationArtifacts(graph, result));
    }

    /**
     * Clears and returns the artifacts produced by the last successfully completed compilation of
     * the current compiler thread. May return {@code null} if the last compilation did not complete
     * successfully.
     *
     * @return the cleared compilation artifacts or {@code null}
     */
    public CompilationArtifacts clearCompilationArtifacts() {
        CompilationArtifacts result = compilationArtifacts.get();
        compilationArtifacts.remove();
        return result;
    }

    /**
     * Injects profiles for the given method during recording.
     *
     * @param method the method to inject profiles into
     * @param includeNormal whether to include normal profiles
     * @param includeOSR whether to include OSR profiles
     * @param profilingInfo the profiling information to inject
     * @throws UnsupportedOperationException if called during replay
     */
    public void injectProfiles(ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR, ProfilingInfo profilingInfo) {
        if (proxies instanceof RecordingCompilationProxies recordingProxies) {
            recordingProxies.injectProfiles(method, includeNormal, includeOSR, profilingInfo);
        } else {
            throw new UnsupportedOperationException("injectProfiles during replay");
        }
    }
}
