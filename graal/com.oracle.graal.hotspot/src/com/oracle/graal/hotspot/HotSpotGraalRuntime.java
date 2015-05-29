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

import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.Options.*;
import static com.oracle.jvmci.common.UnsafeAccess.*;
import static com.oracle.jvmci.hotspot.InitTimer.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.collections.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.debug.*;
import com.oracle.graal.hotspot.events.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.runtime.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.code.stack.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.hotspot.*;
import com.oracle.jvmci.hotspot.logging.*;
import com.oracle.jvmci.meta.*;
import com.oracle.jvmci.options.*;
import com.oracle.jvmci.runtime.*;
import com.oracle.jvmci.service.*;

//JaCoCo Exclude

/**
 * Singleton class holding the instance of the {@link GraalRuntime}.
 */
public final class HotSpotGraalRuntime implements HotSpotGraalRuntimeProvider, HotSpotProxified {

    private static final HotSpotGraalRuntime instance;

    static {
        try (InitTimer t0 = timer("HotSpotGraalRuntime.<clinit>")) {
            try (InitTimer t = timer("HotSpotGraalRuntime.<init>")) {
                instance = new HotSpotGraalRuntime();
            }

            try (InitTimer t = timer("HotSpotGraalRuntime.completeInitialization")) {
                // Why deferred initialization? See comment in completeInitialization().
                instance.completeInitialization();
            }
        }
    }

    /**
     * Gets the singleton {@link HotSpotGraalRuntime} object.
     */
    public static HotSpotGraalRuntime runtime() {
        assert instance != null;
        return instance;
    }

    @Override
    public HotSpotJVMCIRuntimeProvider getJVMCIRuntime() {
        return jvmciRuntime;
    }

    private boolean checkArrayIndexScaleInvariants() {
        assert getJVMCIRuntime().getArrayIndexScale(Kind.Byte) == 1;
        assert getJVMCIRuntime().getArrayIndexScale(Kind.Boolean) == 1;
        assert getJVMCIRuntime().getArrayIndexScale(Kind.Char) == 2;
        assert getJVMCIRuntime().getArrayIndexScale(Kind.Short) == 2;
        assert getJVMCIRuntime().getArrayIndexScale(Kind.Int) == 4;
        assert getJVMCIRuntime().getArrayIndexScale(Kind.Long) == 8;
        assert getJVMCIRuntime().getArrayIndexScale(Kind.Float) == 4;
        assert getJVMCIRuntime().getArrayIndexScale(Kind.Double) == 8;
        return true;
    }

    public static class Options {

        // @formatter:off
        @Option(help = "The runtime configuration to use", type = OptionType.Expert)
        static final OptionValue<String> GraalRuntime = new OptionValue<>("");
        // @formatter:on
    }

    private static HotSpotBackendFactory findFactory(String architecture) {
        HotSpotBackendFactory basic = null;
        HotSpotBackendFactory selected = null;
        HotSpotBackendFactory nonBasic = null;
        int nonBasicCount = 0;

        assert GraalRuntime.getValue().equals(HotSpotJVMCIRuntime.Options.JVMCIRuntime.getValue());

        for (HotSpotBackendFactory factory : Services.load(HotSpotBackendFactory.class)) {
            if (factory.getArchitecture().equalsIgnoreCase(architecture)) {
                if (factory.getGraalRuntimeName().equals(GraalRuntime.getValue())) {
                    assert selected == null || checkFactoryOverriding(selected, factory);
                    selected = factory;
                }
                if (factory.getGraalRuntimeName().equals("basic")) {
                    assert basic == null || checkFactoryOverriding(basic, factory);
                    basic = factory;
                } else {
                    nonBasic = factory;
                    nonBasicCount++;
                }
            }
        }

        if (selected != null) {
            return selected;
        } else {
            if (!GraalRuntime.getValue().equals("")) {
                // Fail fast if a non-default value for GraalRuntime was specified
                // and the corresponding factory is not available
                throw new JVMCIError("Specified runtime \"%s\" not available for the %s architecture", GraalRuntime.getValue(), architecture);
            } else if (nonBasicCount == 1) {
                // If there is exactly one non-basic runtime, select this one.
                return nonBasic;
            } else {
                return basic;
            }
        }
    }

    /**
     * Checks that a factory overriding is valid. A factory B can only override/replace a factory A
     * if the B.getClass() is a subclass of A.getClass(). This models the assumption that B is
     * extends the behavior of A and has therefore understood the behavior expected of A.
     *
     * @param baseFactory
     * @param overridingFactory
     */
    private static boolean checkFactoryOverriding(HotSpotBackendFactory baseFactory, HotSpotBackendFactory overridingFactory) {
        return baseFactory.getClass().isAssignableFrom(overridingFactory.getClass());
    }

    /**
     * Gets the kind of a word value on the {@linkplain #getHostBackend() host} backend.
     */
    public static Kind getHostWordKind() {
        return instance.getHostBackend().getTarget().wordKind;
    }

    /**
     * Reads a klass pointer from a constant object.
     */
    public static long unsafeReadKlassPointer(Object object) {
        return instance.getCompilerToVM().readUnsafeKlassPointer(object);
    }

    /**
     * Reads a word value from a given object.
     */
    public static long unsafeReadWord(Object object, long offset) {
        if (getHostWordKind() == Kind.Long) {
            return unsafe.getLong(object, offset);
        }
        return unsafe.getInt(object, offset) & 0xFFFFFFFFL;
    }

    private final HotSpotBackend hostBackend;
    private DebugValuesPrinter debugValuesPrinter;

    /**
     * Graal mirrors are stored as a {@link ClassValue} associated with the {@link Class} of the
     * type. This data structure stores both {@link HotSpotResolvedObjectType} and
     * {@link HotSpotResolvedPrimitiveType} types.
     */
    private final ClassValue<ResolvedJavaType> graalMirrors = new ClassValue<ResolvedJavaType>() {
        @Override
        protected ResolvedJavaType computeValue(Class<?> javaClass) {
            if (javaClass.isPrimitive()) {
                Kind kind = Kind.fromJavaClass(javaClass);
                return new HotSpotResolvedPrimitiveType(kind);
            } else {
                return new HotSpotResolvedObjectTypeImpl(javaClass);
            }
        }
    };

    private final Map<Class<? extends Architecture>, HotSpotBackend> backends = new HashMap<>();

    private final HotSpotJVMCIRuntime jvmciRuntime;

    private HotSpotGraalRuntime() {

        jvmciRuntime = (HotSpotJVMCIRuntime) JVMCI.getRuntime();

        HotSpotVMConfig config = getConfig();
        CompileTheWorld.Options.overrideWithNativeOptions(config);

        // Only set HotSpotPrintInlining if it still has its default value (false).
        if (HotSpotPrintInlining.getValue() == false) {
            HotSpotPrintInlining.setValue(config.printInlining);
        }

        if (Boolean.valueOf(System.getProperty("graal.printconfig"))) {
            printConfig(config);
        }

        String hostArchitecture = config.getHostArchitectureName();

        HotSpotBackendFactory factory;
        try (InitTimer t = timer("find factory:", hostArchitecture)) {
            factory = findFactory(hostArchitecture);
        }
        try (InitTimer t = timer("create backend:", hostArchitecture)) {
            hostBackend = registerBackend(factory.createBackend(this, jvmciRuntime.getHostJVMCIBackend(), null));
        }

        String[] gpuArchitectures = getGPUArchitectureNames(getCompilerToVM());
        for (String arch : gpuArchitectures) {
            try (InitTimer t = timer("find factory:", arch)) {
                factory = findFactory(arch);
            }
            if (factory == null) {
                throw new JVMCIError("No backend available for specified GPU architecture \"%s\"", arch);
            }
            try (InitTimer t = timer("create backend:", arch)) {
                registerBackend(factory.createBackend(this, null, hostBackend));
            }
        }

        try (InitTimer t = timer("createEventProvider")) {
            eventProvider = createEventProvider();
        }
    }

    /**
     * Do deferred initialization.
     */
    private void completeInitialization() {

        if (Log.getValue() == null && !areScopedMetricsOrTimersEnabled() && Dump.getValue() == null && Verify.getValue() == null) {
            if (MethodFilter.getValue() != null) {
                TTY.println("WARNING: Ignoring MethodFilter option since Log, Meter, Time, TrackMemUse, Dump and Verify options are all null");
            }
        }

        if (Debug.isEnabled()) {
            DebugEnvironment.initialize(TTY.cachedOut);

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
            hostBackend.completeInitialization();
        }
        for (HotSpotBackend backend : backends.values()) {
            if (backend != hostBackend) {
                try (InitTimer st = timer(backend.getTarget().arch.getName(), ".completeInitialization")) {
                    backend.completeInitialization();
                }
            }
        }

        BenchmarkCounters.initialize(getCompilerToVM());

        assert checkArrayIndexScaleInvariants();

        runtimeStartTime = System.nanoTime();
    }

    private HotSpotBackend registerBackend(HotSpotBackend backend) {
        Class<? extends Architecture> arch = backend.getTarget().arch.getClass();
        HotSpotBackend oldValue = backends.put(arch, backend);
        assert oldValue == null : "cannot overwrite existing backend for architecture " + arch.getSimpleName();
        return backend;
    }

    public ResolvedJavaType fromClass(Class<?> javaClass) {
        return graalMirrors.get(javaClass);
    }

    /**
     * Gets the names of the supported GPU architectures for the purpose of finding the
     * corresponding {@linkplain HotSpotBackendFactory backend} objects.
     */
    private static String[] getGPUArchitectureNames(CompilerToVM c2vm) {
        String gpuList = c2vm.getGPUs();
        if (!gpuList.isEmpty()) {
            String[] gpus = gpuList.split(",");
            return gpus;
        }
        return new String[0];
    }

    private static void printConfig(HotSpotVMConfig config) {
        Field[] fields = config.getClass().getDeclaredFields();
        Map<String, Field> sortedFields = new TreeMap<>();
        for (Field f : fields) {
            f.setAccessible(true);
            sortedFields.put(f.getName(), f);
        }
        for (Field f : sortedFields.values()) {
            try {
                Logger.info(String.format("%9s %-40s = %s", f.getType().getSimpleName(), f.getName(), Logger.pretty(f.get(config))));
            } catch (Exception e) {
            }
        }
    }

    public HotSpotProviders getHostProviders() {
        return getHostBackend().getProviders();
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    private final NodeCollectionsProvider nodeCollectionsProvider = new DefaultNodeCollectionsProvider();

    private final EventProvider eventProvider;

    private EventProvider createEventProvider() {
        if (getConfig().flightRecorder) {
            Iterable<EventProvider> sl = Services.load(EventProvider.class);
            EventProvider singleProvider = null;
            for (EventProvider ep : sl) {
                assert singleProvider == null : String.format("multiple %s service implementations found: %s and %s", EventProvider.class.getName(), singleProvider.getClass().getName(),
                                ep.getClass().getName());
                singleProvider = ep;
            }
            return singleProvider;
        }
        return new EmptyEventProvider();
    }

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
        } else if (clazz == EventProvider.class) {
            return (T) eventProvider;
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

    @Override
    public <T> T iterateFrames(ResolvedJavaMethod[] initialMethods, ResolvedJavaMethod[] matchingMethods, int initialSkip, InspectedFrameVisitor<T> visitor) {
        final long[] initialMetaMethods = toMeta(initialMethods);
        final long[] matchingMetaMethods = toMeta(matchingMethods);

        CompilerToVM compilerToVM = getCompilerToVM();
        HotSpotStackFrameReference current = compilerToVM.getNextStackFrame(null, initialMetaMethods, initialSkip);
        while (current != null) {
            T result = visitor.visitFrame(current);
            if (result != null) {
                return result;
            }
            current = compilerToVM.getNextStackFrame(current, matchingMetaMethods, 0);
        }
        return null;
    }

    private static long[] toMeta(ResolvedJavaMethod[] methods) {
        if (methods == null) {
            return null;
        } else {
            long[] result = new long[methods.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = ((HotSpotResolvedJavaMethodImpl) methods[i]).getMetaspaceMethod();
            }
            return result;
        }
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
        BenchmarkCounters.shutdown(getCompilerToVM(), runtimeStartTime);
    }
}
