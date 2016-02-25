/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.debug.GraalDebugConfig.areScopedMetricsOrTimersEnabled;
import static com.oracle.graal.debug.GraalDebugConfig.Options.DebugValueSummary;
import static com.oracle.graal.debug.GraalDebugConfig.Options.Dump;
import static com.oracle.graal.debug.GraalDebugConfig.Options.Log;
import static com.oracle.graal.debug.GraalDebugConfig.Options.MethodFilter;
import static com.oracle.graal.debug.GraalDebugConfig.Options.Verify;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayIndexScale;
import static jdk.vm.ci.inittimer.InitTimer.timer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.api.collections.CollectionsProvider;
import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.api.runtime.GraalRuntime;
import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugEnvironment;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.graph.DefaultNodeCollectionsProvider;
import com.oracle.graal.graph.NodeCollectionsProvider;
import com.oracle.graal.hotspot.debug.BenchmarkCounters;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.phases.tiers.CompilerConfiguration;
import com.oracle.graal.replacements.SnippetCounter;
import com.oracle.graal.runtime.RuntimeProvider;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotProxified;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.inittimer.InitTimer;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.runtime.JVMCIBackend;

//JaCoCo Exclude

/**
 * Singleton class holding the instance of the {@link GraalRuntime}.
 */
public final class HotSpotGraalRuntime implements HotSpotGraalRuntimeProvider, HotSpotProxified {

    private static boolean checkArrayIndexScaleInvariants() {
        assert getArrayIndexScale(JavaKind.Byte) == 1;
        assert getArrayIndexScale(JavaKind.Boolean) == 1;
        assert getArrayIndexScale(JavaKind.Char) == 2;
        assert getArrayIndexScale(JavaKind.Short) == 2;
        assert getArrayIndexScale(JavaKind.Int) == 4;
        assert getArrayIndexScale(JavaKind.Long) == 8;
        assert getArrayIndexScale(JavaKind.Float) == 4;
        assert getArrayIndexScale(JavaKind.Double) == 8;
        return true;
    }

    private final HotSpotBackend hostBackend;
    private DebugValuesPrinter debugValuesPrinter;

    private final Map<Class<? extends Architecture>, HotSpotBackend> backends = new HashMap<>();

    @SuppressWarnings("try")
    HotSpotGraalRuntime(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalCompilerFactory compilerFactory) {

        HotSpotVMConfig config = jvmciRuntime.getConfig();
        CompileTheWorldOptions.overrideWithNativeOptions(config);

        // Only set HotSpotPrintInlining if it still has its default value (false).
        if (GraalOptions.HotSpotPrintInlining.getValue() == false) {
            GraalOptions.HotSpotPrintInlining.setValue(config.printInlining);
        }

        CompilerConfiguration compilerConfiguration = compilerFactory.createCompilerConfiguration();

        JVMCIBackend hostJvmciBackend = jvmciRuntime.getHostJVMCIBackend();
        Architecture hostArchitecture = hostJvmciBackend.getTarget().arch;
        try (InitTimer t = timer("create backend:", hostArchitecture)) {
            HotSpotBackendFactory factory = compilerFactory.getBackendFactory(hostArchitecture);
            if (factory == null) {
                throw new JVMCIError("No backend available for host architecture \"%s\"", hostArchitecture);
            }
            hostBackend = registerBackend(factory.createBackend(this, compilerConfiguration, jvmciRuntime, null));
        }

        for (JVMCIBackend jvmciBackend : jvmciRuntime.getJVMCIBackends().values()) {
            if (jvmciBackend == hostJvmciBackend) {
                continue;
            }

            Architecture gpuArchitecture = jvmciBackend.getTarget().arch;
            HotSpotBackendFactory factory = compilerFactory.getBackendFactory(gpuArchitecture);
            if (factory == null) {
                throw new JVMCIError("No backend available for specified GPU architecture \"%s\"", gpuArchitecture);
            }
            try (InitTimer t = timer("create backend:", gpuArchitecture)) {
                registerBackend(factory.createBackend(this, compilerConfiguration, null, hostBackend));
            }
        }

        if (Log.getValue() == null && !areScopedMetricsOrTimersEnabled() && Dump.getValue() == null && Verify.getValue() == null) {
            if (MethodFilter.getValue() != null) {
                TTY.println("WARNING: Ignoring MethodFilter option since Log, Meter, Time, TrackMemUse, Dump and Verify options are all null");
            }
        }

        if (Debug.isEnabled()) {
            DebugEnvironment.initialize(TTY.out);

            String summary = DebugValueSummary.getValue();
            if (summary != null) {
                switch (summary) {
                    case "Name":
                    case "Partial":
                    case "Complete":
                    case "Thread":
                        break;
                    default:
                        throw new JVMCIError("Unsupported value for DebugSummaryValue: %s", summary);
                }
            }
        }

        if (Debug.areUnconditionalMetricsEnabled() || Debug.areUnconditionalTimersEnabled() || (Debug.isEnabled() && areScopedMetricsOrTimersEnabled())) {
            // This must be created here to avoid loading the DebugValuesPrinter class
            // during shutdown() which in turn can cause a deadlock
            debugValuesPrinter = new DebugValuesPrinter();
        }

        // Complete initialization of backends
        try (InitTimer st = timer(hostBackend.getTarget().arch.getName(), ".completeInitialization")) {
            hostBackend.completeInitialization(jvmciRuntime);
        }
        for (HotSpotBackend backend : backends.values()) {
            if (backend != hostBackend) {
                try (InitTimer st = timer(backend.getTarget().arch.getName(), ".completeInitialization")) {
                    backend.completeInitialization(jvmciRuntime);
                }
            }
        }

        BenchmarkCounters.initialize(jvmciRuntime);

        assert checkArrayIndexScaleInvariants();

        runtimeStartTime = System.nanoTime();
    }

    private HotSpotBackend registerBackend(HotSpotBackend backend) {
        Class<? extends Architecture> arch = backend.getTarget().arch.getClass();
        HotSpotBackend oldValue = backends.put(arch, backend);
        assert oldValue == null : "cannot overwrite existing backend for architecture " + arch.getSimpleName();
        return backend;
    }

    public HotSpotProviders getHostProviders() {
        return getHostBackend().getProviders();
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    private final NodeCollectionsProvider nodeCollectionsProvider = new DefaultNodeCollectionsProvider();

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == RuntimeProvider.class) {
            return (T) this;
        } else if (clazz == CollectionsProvider.class || clazz == NodeCollectionsProvider.class) {
            return (T) nodeCollectionsProvider;
        } else if (clazz == StackIntrospection.class) {
            return (T) this;
        } else if (clazz == SnippetReflectionProvider.class) {
            return (T) getHostProviders().getSnippetReflection();
        } else if (clazz == StampProvider.class) {
            return (T) getHostProviders().getStampProvider();
        }
        return null;
    }

    public HotSpotBackend getHostBackend() {
        return hostBackend;
    }

    public <T extends Architecture> Backend getBackend(Class<T> arch) {
        assert arch != Architecture.class;
        return backends.get(arch);
    }

    public Map<Class<? extends Architecture>, HotSpotBackend> getBackends() {
        return Collections.unmodifiableMap(backends);
    }

    private long runtimeStartTime;

    /**
     * Take action related to entering a new execution phase.
     *
     * @param phase the execution phase being entered
     */
    static void phaseTransition(String phase) {
        CompilationStatistics.clear(phase);
    }

    void shutdown() {
        if (debugValuesPrinter != null) {
            debugValuesPrinter.printDebugValues();
        }
        phaseTransition("final");

        SnippetCounter.printGroups(TTY.out().out());
        BenchmarkCounters.shutdown(runtime(), runtimeStartTime);
    }
}
