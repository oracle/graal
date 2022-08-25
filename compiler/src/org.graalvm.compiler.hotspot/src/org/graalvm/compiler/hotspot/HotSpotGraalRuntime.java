/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.compiler.core.common.GraalOptions.HotSpotPrintInlining;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigAccess.JDK;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.Instrumentation;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompilationListenerProfiler;
import org.graalvm.compiler.core.common.CompilerProfiler;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.GlobalMetrics;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.CompilationStatistics.Options;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory.BackendMap;
import org.graalvm.compiler.hotspot.debug.BenchmarkCounters;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;

//JaCoCo Exclude

/**
 * Singleton class holding the instance of the {@link GraalRuntime}.
 */
public final class HotSpotGraalRuntime implements HotSpotGraalRuntimeProvider {

    private static boolean checkArrayIndexScaleInvariants(MetaAccessProvider metaAccess) {
        assert metaAccess.getArrayIndexScale(JavaKind.Byte) == 1;
        assert metaAccess.getArrayIndexScale(JavaKind.Boolean) == 1;
        assert metaAccess.getArrayIndexScale(JavaKind.Char) == 2;
        assert metaAccess.getArrayIndexScale(JavaKind.Short) == 2;
        assert metaAccess.getArrayIndexScale(JavaKind.Int) == 4;
        assert metaAccess.getArrayIndexScale(JavaKind.Long) == 8;
        assert metaAccess.getArrayIndexScale(JavaKind.Float) == 4;
        assert metaAccess.getArrayIndexScale(JavaKind.Double) == 8;
        return true;
    }

    private final String runtimeName;
    private final String compilerConfigurationName;
    private final HotSpotBackend hostBackend;

    public GlobalMetrics getMetricValues() {
        return metricValues;
    }

    private final GlobalMetrics metricValues = new GlobalMetrics();
    private final List<SnippetCounter.Group> snippetCounterGroups;
    private final HotSpotGC garbageCollector;

    private final EconomicMap<Class<? extends Architecture>, HotSpotBackend> backends = EconomicMap.create(Equivalence.IDENTITY);

    private final GraalHotSpotVMConfig config;

    private final Instrumentation instrumentation;

    private final OptionValues options;

    private final DiagnosticsOutputDirectory outputDirectory;
    private final Map<ExceptionAction, Integer> compilationProblemsPerAction;

    private final CompilerProfiler compilerProfiler;

    /**
     * @param nameQualifier a qualifier to be added to this runtime's {@linkplain #getName() name}
     * @param compilerConfigurationFactory factory for the compiler configuration
     *            {@link CompilerConfigurationFactory#selectFactory}
     */
    @SuppressWarnings("try")
    HotSpotGraalRuntime(String nameQualifier, HotSpotJVMCIRuntime jvmciRuntime, CompilerConfigurationFactory compilerConfigurationFactory, OptionValues initialOptions) {
        this.runtimeName = getClass().getSimpleName() + ":" + nameQualifier;
        HotSpotVMConfigStore store = jvmciRuntime.getConfigStore();
        config = new GraalHotSpotVMConfig(store);

        // Only set HotSpotPrintInlining if it still has its default value (false).
        if (GraalOptions.HotSpotPrintInlining.getValue(initialOptions) == false && config.printInlining) {
            options = new OptionValues(initialOptions, HotSpotPrintInlining, true);
        } else {
            options = initialOptions;
        }

        garbageCollector = getSelectedGC();

        outputDirectory = new DiagnosticsOutputDirectory(options);
        compilationProblemsPerAction = new EnumMap<>(ExceptionAction.class);
        snippetCounterGroups = GraalOptions.SnippetCounters.getValue(options) ? new ArrayList<>() : null;
        CompilerConfiguration compilerConfiguration = compilerConfigurationFactory.createCompilerConfiguration();
        compilerConfigurationName = compilerConfigurationFactory.getName();

        this.instrumentation = compilerConfigurationFactory.createInstrumentation(options);

        BackendMap backendMap = compilerConfigurationFactory.createBackendMap();

        JVMCIBackend hostJvmciBackend = jvmciRuntime.getHostJVMCIBackend();
        Architecture hostArchitecture = hostJvmciBackend.getTarget().arch;
        try (InitTimer t = timer("create backend:", hostArchitecture)) {
            HotSpotBackendFactory factory = backendMap.getBackendFactory(hostArchitecture);
            if (factory == null) {
                throw new GraalError("No backend available for host architecture \"%s\"", hostArchitecture);
            }
            hostBackend = registerBackend(factory.createBackend(this, compilerConfiguration, jvmciRuntime, null));
        }

        for (JVMCIBackend jvmciBackend : jvmciRuntime.getJVMCIBackends().values()) {
            if (jvmciBackend == hostJvmciBackend) {
                continue;
            }

            Architecture gpuArchitecture = jvmciBackend.getTarget().arch;
            HotSpotBackendFactory factory = backendMap.getBackendFactory(gpuArchitecture);
            if (factory == null) {
                throw new GraalError("No backend available for specified GPU architecture \"%s\"", gpuArchitecture);
            }
            try (InitTimer t = timer("create backend:", gpuArchitecture)) {
                registerBackend(factory.createBackend(this, compilerConfiguration, null, hostBackend));
            }
        }

        // Complete initialization of backends
        try (InitTimer st = timer(hostBackend.getTarget().arch.getName(), ".completeInitialization")) {
            hostBackend.completeInitialization(jvmciRuntime, options);
        }
        for (HotSpotBackend backend : backends.getValues()) {
            if (backend != hostBackend) {
                try (InitTimer st = timer(backend.getTarget().arch.getName(), ".completeInitialization")) {
                    backend.completeInitialization(jvmciRuntime, options);
                }
            }
        }

        BenchmarkCounters.initialize(jvmciRuntime, options);

        assert checkArrayIndexScaleInvariants(hostJvmciBackend.getMetaAccess());

        runtimeStartTime = System.nanoTime();
        bootstrapJVMCI = config.getFlag("BootstrapJVMCI", Boolean.class);

        this.compilerProfiler = GraalServices.loadSingle(CompilerProfiler.class, false);

        startupLibGraal(this);
    }

    /**
     * Constants denoting the GC algorithms available in HotSpot. The names of the constants match
     * the constants in the {@code CollectedHeap::Name} C++ enum.
     */
    public enum HotSpotGC {
        // Supported GCs
        Serial(true, JDK >= 11, "UseSerialGC", true),
        Parallel(true, JDK >= 11, "UseParallelGC", true, "UseParallelOldGC", JDK < 15, "UseParNewGC", JDK < 10),
        CMS(true, JDK >= 11 && JDK <= 14, "UseConcMarkSweepGC", JDK < 14),
        G1(true, JDK >= 11, "UseG1GC", true),

        // Unsupported GCs
        Epsilon(false, JDK >= 11, "UseEpsilonGC", JDK >= 11),
        Z(false, JDK >= 11, "UseZGC", JDK >= 11),
        Shenandoah(false, JDK >= 12, "UseShenandoahGC", JDK >= 12);

        HotSpotGC(boolean supported, boolean expectNamePresent,
                        String flag1, boolean expectFlagPresent1,
                        String flag2, boolean expectFlagPresent2,
                        String flag3, boolean expectFlagPresent3) {
            this.supported = supported;
            this.expectNamePresent = expectNamePresent;
            this.expectFlagsPresent = new boolean[]{expectFlagPresent1, expectFlagPresent2, expectFlagPresent3};
            this.flags = new String[]{flag1, flag2, flag3};
        }

        HotSpotGC(boolean supported, boolean expectNamePresent, String flag, boolean expectFlagPresent) {
            this.supported = supported;
            this.expectNamePresent = expectNamePresent;
            this.expectFlagsPresent = new boolean[]{expectFlagPresent};
            this.flags = new String[]{flag};
        }

        /**
         * Specifies if this GC supported by Graal.
         */
        final boolean supported;

        /**
         * Specifies if {@link #name()} is expected to be present in the {@code CollectedHeap::Name}
         * C++ enum.
         */
        final boolean expectNamePresent;

        /**
         * The VM flags that will select this GC.
         */
        private final String[] flags;

        /**
         * Specifies which {@link #flags} are expected to be present in the VM.
         */
        final boolean[] expectFlagsPresent;

        public boolean isSelected(GraalHotSpotVMConfig config) {
            boolean selected = false;
            for (int i = 0; i < flags.length; i++) {
                final boolean notPresent = false;
                if (config.getFlag(flags[i], Boolean.class, notPresent, expectFlagsPresent[i])) {
                    selected = true;
                    if (!Assertions.assertionsEnabled()) {
                        // When asserting, check that isSelected works for all flag names
                        break;
                    }
                }
            }
            return selected;
        }

        /**
         * Gets the GC matching {@code name}.
         *
         * @param name the ordinal of a {@code CollectedHeap::Name} value
         */
        static HotSpotGC forName(int name, GraalHotSpotVMConfig config) {
            for (HotSpotGC gc : HotSpotGC.values()) {
                if (config.getConstant("CollectedHeap::" + gc.name(), Integer.class, -1, gc.expectNamePresent) == name) {
                    return gc;
                }
            }
            return null;
        }
    }

    private HotSpotGC getSelectedGC() throws GraalError {
        HotSpotGC selected = null;
        for (HotSpotGC gc : HotSpotGC.values()) {
            if (gc.isSelected(config)) {
                if (!gc.supported) {
                    throw new GraalError(gc.name() + " garbage collector is not supported by Graal");
                }
                selected = gc;
                if (!Assertions.assertionsEnabled()) {
                    // When asserting, check that isSelected works for all HotSpotGC values
                    break;
                }
            }
        }
        if (selected == null) {
            // Exactly one GC flag is guaranteed to be selected.
            selected = HotSpotGC.Serial;
        }
        return selected;
    }

    private HotSpotBackend registerBackend(HotSpotBackend backend) {
        Class<? extends Architecture> arch = backend.getTarget().arch.getClass();
        HotSpotBackend oldValue = backends.put(arch, backend);
        assert oldValue == null : "cannot overwrite existing backend for architecture " + arch.getSimpleName();
        return backend;
    }

    @Override
    public HotSpotProviders getHostProviders() {
        return getHostBackend().getProviders();
    }

    @Override
    public GraalHotSpotVMConfig getVMConfig() {
        return config;
    }

    @Override
    public DebugContext openDebugContext(OptionValues compilationOptions, CompilationIdentifier compilationId, Object compilable, Iterable<DebugHandlersFactory> factories, PrintStream logStream) {

        Description description = new Description(compilable, compilationId.toString(CompilationIdentifier.Verbosity.ID));
        Builder builder = new Builder(compilationOptions, factories).//
                        globalMetrics(metricValues).//
                        description(description).//
                        logStream(logStream);
        if (compilerProfiler != null) {
            int compileId = ((HotSpotCompilationIdentifier) compilationId).getRequest().getId();
            builder.compilationListener(new CompilationListenerProfiler(compilerProfiler, compileId));
        }
        return builder.build();

    }

    @Override
    public OptionValues getOptions() {
        return options;
    }

    @Override
    public Group createSnippetCounterGroup(String groupName) {
        if (snippetCounterGroups != null) {
            Group group = new Group(groupName);
            snippetCounterGroups.add(group);
            return group;
        }
        return null;
    }

    @Override
    public String getName() {
        return runtimeName;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == RuntimeProvider.class) {
            return (T) this;
        } else if (clazz == OptionValues.class) {
            return (T) options;
        } else if (clazz == StackIntrospection.class) {
            return (T) this;
        } else if (clazz == SnippetReflectionProvider.class) {
            return (T) getHostProviders().getSnippetReflection();
        } else if (clazz == GraalHotSpotVMConfig.class) {
            return (T) getVMConfig();
        } else if (clazz == StampProvider.class) {
            return (T) getHostProviders().getStampProvider();
        } else if (ForeignCallsProvider.class.isAssignableFrom(clazz)) {
            return (T) getHostProviders().getForeignCalls();
        }
        return null;
    }

    @Override
    public HotSpotGC getGarbageCollector() {
        return garbageCollector;
    }

    @Override
    public HotSpotBackend getHostBackend() {
        return hostBackend;
    }

    @Override
    public <T extends Architecture> Backend getBackend(Class<T> arch) {
        assert arch != Architecture.class;
        return backends.get(arch);
    }

    @Override
    public String getCompilerConfigurationName() {
        return compilerConfigurationName;
    }

    @Override
    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    private long runtimeStartTime;

    /**
     * Called from compiler threads to check whether to bail out of a compilation.
     */
    private volatile boolean shutdown;

    /**
     * Shutdown hooks that should be run on the same thread doing the shutdown.
     */
    private List<Runnable> shutdownHooks = new ArrayList<>();

    /**
     * Take action related to entering a new execution phase.
     *
     * @param phase the execution phase being entered
     */
    void phaseTransition(String phase) {
        if (Options.UseCompilationStatistics.getValue(options)) {
            CompilationStatistics.clear(phase);
        }
    }

    /**
     * Adds a {@link Runnable} that will be run when this runtime is {@link #shutdown()}. The
     * runnable will be run on the same thread doing the shutdown. All the advice for regular
     * {@linkplain Runtime#addShutdownHook(Thread) shutdown hooks} also applies here but even more
     * so since the hook runs on the shutdown thread.
     */
    public synchronized void addShutdownHook(Runnable hook) {
        if (!shutdown) {
            shutdownHooks.add(hook);
        }
    }

    synchronized void shutdown() {
        shutdown = true;

        for (Runnable r : shutdownHooks) {
            try {
                r.run();
            } catch (Throwable e) {
                e.printStackTrace(TTY.out);
            }
        }

        metricValues.print(options);

        phaseTransition("final");

        if (snippetCounterGroups != null) {
            for (Group group : snippetCounterGroups) {
                TTY.out().out().println(group);
            }
        }
        BenchmarkCounters.shutdown(runtime(), options, runtimeStartTime);

        outputDirectory.close();

        shutdownLibGraal(this);
    }

    /**
     * Substituted by
     * {@code com.oracle.svm.graal.hotspot.libgraal.Target_org_graalvm_compiler_hotspot_HotSpotGraalRuntime}
     * to notify {@code org.graalvm.libgraal.LibGraalIsolate} and call
     * {@code org.graalvm.nativeimage.VMRuntime.initialize()}.
     */
    @SuppressWarnings("unused")
    private static void startupLibGraal(HotSpotGraalRuntime runtime) {
    }

    /**
     * Substituted by
     * {@code com.oracle.svm.graal.hotspot.libgraal.Target_org_graalvm_compiler_hotspot_HotSpotGraalRuntime}
     * to notify {@code org.graalvm.libgraal.LibGraalIsolate} and call
     * {@code org.graalvm.nativeimage.VMRuntime.shutdown()}.
     */
    @SuppressWarnings("unused")
    private static void shutdownLibGraal(HotSpotGraalRuntime runtime) {
    }

    void clearMetrics() {
        metricValues.clear();
    }

    private final boolean bootstrapJVMCI;
    private boolean bootstrapFinished;

    public void notifyBootstrapFinished() {
        bootstrapFinished = true;
    }

    @Override
    public boolean isBootstrapping() {
        return bootstrapJVMCI && !bootstrapFinished;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public DiagnosticsOutputDirectory getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    public Map<ExceptionAction, Integer> getCompilationProblemsPerAction() {
        return compilationProblemsPerAction;
    }
}
