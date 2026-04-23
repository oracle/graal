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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.interop.TruffleObject;
import org.graalvm.nativebridge.Peer;
import org.graalvm.nativebridge.ReferenceHandles;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractPolyglotHostService;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.IOAccessor;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.polyglot.isolate.GuestPolyglotIsolateServices.GuestPolyglotIsolateServicesDirective;

@CContext(value = GuestPolyglotIsolateServicesDirective.class)
final class GuestPolyglotIsolateServices implements PolyglotIsolateServices {

    // Mapping of standard HotSpot X options into SVM options
    private static final Map<String, String> XOPTIONS;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("Xms", "MinHeapSize");
        m.put("Xmx", "MaxHeapSize");
        m.put("Xmn", "MaxNewSize");
        m.put("Xss", "StackSize");
        XOPTIONS = m;
    }
    private static final String HEAP_DUMP_PREFIX = "polyglotisolate-heapdump-";
    private static final String HEAP_DUMP_EXT = ".hprof";

    private static final class SpectrePHTMitigations {

        static final Object GUARD_TARGETS = loadSpectrePHTMitigationsGuardTargets();

        /**
         * RuntimeOptions.listDescriptors() seems to not include graal compiler options, hence the
         * reflective lookup. This should be resolved eventually by GR-48541.
         */
        @SuppressWarnings("rawtypes")
        private static Object loadSpectrePHTMitigationsGuardTargets() {
            try {
                Class<? extends Enum> spectrePHTMitigations = Class.forName("jdk.graal.compiler.core.common.SpectrePHTMitigations").asSubclass(Enum.class);
                for (Enum<?> constant : spectrePHTMitigations.getEnumConstants()) {
                    if ("GuardTargets".equals(constant.name())) {
                        return constant;
                    }
                }
                throw new IllegalStateException("No GuardTargets constant found in the SpectrePHTMitigations enum.");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private final AbstractPolyglotImpl polyglot;

    private GuestPolyglotIsolateServices(AbstractPolyglotImpl polyglot) {
        this.polyglot = polyglot;
    }

    static GuestPolyglotIsolateServices getInstance() {
        if (PolyglotIsolateGuestFeatureEnabled.isEnabled()) {
            return new GuestPolyglotIsolateServices(PolyglotIsolateAccessor.ENGINE.findPolyglot());
        } else {
            // Prevent parsing on host when JNI C directives are not enabled.
            throw new UnsupportedOperationException("Not reachable on host.");
        }
    }

    @Override
    public void initialize(PolyglotHostServices polyglotHostServices, String internalResources) {
        /*
         * Explicitly deactivate signal handling as it is done by Hotspot - required for espresso,
         * which does not use a LanguageLibraryConfig
         */
        RuntimeOptions.set("EnableSignalHandling", false);
        RuntimeOptions.set("InstallSegfaultHandler", false);
        if (internalResources != null) {
            System.setProperty("polyglot.engine.resourcePath", internalResources);
        }
        PolyglotIsolateGuestSupport.Lazy l = new PolyglotIsolateGuestSupport.Lazy(polyglot, (ForeignPolyglotHostServices) polyglotHostServices);
        if (PolyglotIsolateGuestSupport.lazy != null) {
            throw new IllegalStateException("PolyglotIsolate.lazy is already set.");
        }
        PolyglotIsolateGuestSupport.lazy = l;
    }

    @Override
    public long buildEngine(String[] permittedLanguages, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in,
                    Map<String, String> options, Map<String, String> systemPropertiesOptions, boolean useSystemProperties, boolean allowExperimentalOptions,
                    boolean boundEngine, MessageTransport messageInterceptor, LogHandler logHandler, AbstractPolyglotHostService polyglotHostService,
                    Object hostLanguageServicePeer) {
        GuestHostLanguage hostLanguage = new GuestHostLanguage((Peer) hostLanguageServicePeer);
        Engine engine = polyglot.buildEngine(permittedLanguages, sandboxPolicy, out, err, in, options, systemPropertiesOptions, useSystemProperties, allowExperimentalOptions, boundEngine,
                        messageInterceptor, logHandler, hostLanguage, false, false, polyglotHostService, null);
        Object engineReceiver = polyglot.getAPIAccess().getEngineReceiver(engine);
        setVMOptions(sandboxPolicy, engineReceiver);
        GuestEngine guestEngine = new GuestEngine(engine);
        long handle = ReferenceHandles.create(guestEngine);
        guestEngine.setHandle(handle);
        PolyglotIsolateGuestSupport.registerEngine(engineReceiver, guestEngine);
        return handle;
    }

    private static void setVMOptions(SandboxPolicy sandboxPolicy, Object engineReceiver) {
        OptionValues engineOptionValues = PolyglotIsolateAccessor.ENGINE.getEngineOptionValues(engineReceiver);
        Set<Map.Entry<String, String>> vmOptions = engineOptionValues.get(PolyglotIsolateAccessor.ENGINE.getIsolateOptionOption()).entrySet();
        assert SandboxPolicy.CONSTRAINED.isStricterOrEqual(sandboxPolicy) || vmOptions.isEmpty() : "SandboxPolicy ISOLATED or UNTRUSTED must not have vm options.";
        if (!vmOptions.isEmpty()) {
            for (Map.Entry<String, String> vmOption : vmOptions) {
                String givenOptionName = vmOption.getKey();
                String useOptionName = XOPTIONS.getOrDefault(givenOptionName, givenOptionName);

                RuntimeOptions.Descriptor optionDescriptor = RuntimeOptions.getDescriptor(useOptionName);
                if (optionDescriptor == null) {
                    List<String> supportedOptions = new ArrayList<>();
                    RuntimeOptions.listDescriptors().forEach((d) -> supportedOptions.add(d.name()));
                    throw new IllegalArgumentException(String.format("Unknown option %s. Supported options: %s", useOptionName, String.join(", ", supportedOptions)));
                }
                Object optionValue;
                try {
                    optionValue = optionDescriptor.convertValue(vmOption.getValue());
                } catch (IllegalArgumentException ia) {
                    throw new IllegalArgumentException(String.format("Failed to parse %s option. %s", givenOptionName, ia.getMessage()));
                }
                RuntimeOptions.set(useOptionName, optionValue);
            }
        }
        long xmX = engineOptionValues.get(PolyglotIsolateAccessor.ENGINE.getMaxIsolateMemoryOption());
        if (xmX != -1) {
            RuntimeOptions.set("MaxHeapSize", xmX);
        }
        Enum<?> untrustedCodePolicy = engineOptionValues.get(PolyglotIsolateAccessor.ENGINE.getUntrustedCodeMitigationOption());
        if (PolyglotIsolateAccessor.ENGINE.isUntrustedCodeMitigationPolicySoftware(untrustedCodePolicy)) {
            // memory masking is only implemented on AMD64
            if (Platform.includedIn(Platform.AMD64.class)) {
                RuntimeOptions.set("MemoryMaskingAndFencing", true);
            } else {
                RuntimeOptions.set("SpectrePHTBarriers", SpectrePHTMitigations.GUARD_TARGETS);
            }
            RuntimeOptions.set("BlindConstants", true);
            RuntimeOptions.set("MaxRuntimeCodeOffset", 128);
        }
    }

    @Override
    public long createContext(Object receiver, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in, boolean allowHostAccess,
                    Object polyglotAccess, Object ioAccess, FileSystem fileSystem, boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostClassLoading,
                    boolean allowInnerContextOptions, boolean allowExperimentalOptions, boolean allowCreateProcess,
                    Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages, String currentWorkingDirectory, String tmpDir,
                    ProcessHandler processHandler, Object environmentAccess, Map<String, String> environment, ZoneId zone, long hostStackHeadRoom,
                    Object hostLanguageServicePeer, boolean allowValueSharing, boolean useSystemExit, LogHandler logHandler, ReflectionLibraryDispatch guestToHostObjectReceiver) {
        GuestEngine guestEngine = (GuestEngine) receiver;
        Engine engine = guestEngine.engine;
        APIAccess apiAccess = polyglot.getAPIAccess();
        Object engineReceiver = apiAccess.getEngineReceiver(engine);
        AbstractPolyglotImpl.AbstractEngineDispatch engineDispatch = apiAccess.getEngineDispatch(engine);
        if (allowCreateProcess && PolyglotIsolateAccessor.ENGINE.isCreateProcessSupported() && processHandler == null) {
            throw new IllegalArgumentException("ProcessHandler must be no null when allowCreateProcess is enabled.");
        }
        // host access is denied on the isolate side - only access to host object on the host
        // side is granted
        Object hostAccess = apiAccess.getHostAccessNone();
        // Filesystem cannot be serialized by the IOAccessMarshaller, it has to be passed by
        // reference. We need to create a new IOAccess using given ioAccess as a prototype and set
        // filesystem to it.

        Object useIOAccess;
        if (fileSystem != null) {
            IOAccessor ioAccessor = polyglot.getIO();
            useIOAccess = ioAccessor.createIOAccess(null, ioAccessor.hasHostFileAccess(ioAccess), ioAccessor.hasHostSocketAccess(ioAccess), fileSystem);
        } else {
            useIOAccess = ioAccess;
        }
        Context context = engineDispatch.createContext(engineReceiver, engine, sandboxPolicy, out, err, in, allowHostAccess, hostAccess, polyglotAccess,
                        allowNativeAccess, allowCreateThread, allowHostClassLoading, allowInnerContextOptions,
                        allowExperimentalOptions, null, options, arguments, onlyLanguages, useIOAccess,
                        logHandler, allowCreateProcess, processHandler, null, environmentAccess,
                        environment, zone, null, currentWorkingDirectory, tmpDir, null, allowValueSharing, useSystemExit, false);
        ForeignHostLanguageService hostLanguageService = (ForeignHostLanguageService) PolyglotIsolateAccessor.ENGINE.getHostService(engineReceiver);
        hostLanguageService.setPeer((Peer) hostLanguageServicePeer);
        Engine newEngine = context.getEngine();
        Object newEngineReceiver = apiAccess.getEngineReceiver(newEngine);
        if (engineReceiver != newEngineReceiver) {
            PolyglotIsolateGuestSupport.patchEngine(engineReceiver, newEngineReceiver);
            guestEngine.engine = newEngine;
        }
        GuestContext guestContext = new GuestContext(context, apiAccess.getContextReceiver(context), guestToHostObjectReceiver, hostStackHeadRoom);
        long guestContextHandle = ReferenceHandles.create(guestContext);
        guestContext.setHandle(guestContextHandle);
        PolyglotIsolateGuestSupport.registerContext(guestContext);
        return guestContextHandle;
    }

    @Override
    public ReflectionLibraryDispatch getGuestObjectReflection(Object guestContext) {
        return ((GuestContext) guestContext).hostToGuestObjectReferences;
    }

    @Override
    public Object parseEval(Object guestContext, String language, long sourceHandle, boolean eval) {
        APIAccess apiAccess = polyglot.getAPIAccess();
        Context context = ((GuestContext) guestContext).context;
        AbstractPolyglotImpl.AbstractContextDispatch contextDispatch = apiAccess.getContextDispatch(context);
        Object contextReceiver = apiAccess.getContextReceiver(context);
        Object source = PolyglotIsolateGuestSupport.getSource(sourceHandle);
        return eval ? contextDispatch.eval(contextReceiver, language, source) : contextDispatch.parse(contextReceiver, language, source);
    }

    @Override
    public void triggerGC() {
        System.gc();
        CleanableWeakReference.clean();
    }

    @Override
    public String heapDump(String path) throws IOException {
        Path heapDumpFilePath;
        if (path != null) {
            Path heapDumpFolder = Paths.get(path);
            Files.createDirectories(heapDumpFolder);
            heapDumpFilePath = Files.createTempFile(heapDumpFolder, HEAP_DUMP_PREFIX, HEAP_DUMP_EXT);
        } else {
            heapDumpFilePath = Files.createTempFile(HEAP_DUMP_PREFIX, HEAP_DUMP_EXT);
        }
        try {
            VMRuntime.dumpHeap(heapDumpFilePath.toString(), false);
            return heapDumpFilePath.toAbsolutePath().toString();
        } catch (Exception e) {
            try {
                Files.deleteIfExists(heapDumpFilePath);
            } catch (IOException ioe) {
                e.addSuppressed(ioe);
            }
            throw e;
        }
    }

    @Override
    public IsolateSourceCache getSourceCache() {
        return PolyglotIsolateGuestSupport.lazy.sourceCache;
    }

    @Override
    public void onIsolateTearDown() {
        PolyglotIsolateGuestSupport.tearDown();
    }

    @Override
    public boolean isMemoryProtected() {
        return isMemoryProtected(CurrentIsolate.getCurrentThread());
    }

    /**
     * Implemented by
     * {@link PolyglotIsolateCreateSupport#isMemoryProtected(org.graalvm.nativeimage.IsolateThread)}.
     */
    @SuppressWarnings("javadoc")
    @CFunction("truffle_isolate_isMemoryProtected")
    private static native boolean isMemoryProtected(IsolateThread isolateThread);

    @Override
    public void ensureInstrumentCreated(Object guestContext, String instrumentId) {
        Object contextReceiver = polyglot.getAPIAccess().getContextReceiver(((GuestContext) guestContext).context);
        PolyglotIsolateAccessor.ENGINE.ensureInstrumentCreated(contextReceiver, instrumentId);
    }

    @Override
    public long getEmbedderExceptionStackTrace(Object receiver, Throwable exception, boolean inHost) {
        GuestContext guestContext = (GuestContext) receiver;
        Object engineReceiver = polyglot.getAPIAccess().getEngineReceiver(guestContext.context.getEngine());
        Object contextReceiver = guestContext.polyglotContextReceiver;
        boolean enterNeeded = !PolyglotIsolateAccessor.ENGINE.isContextEntered(contextReceiver);
        Object prev = null;
        if (enterNeeded) {
            prev = PolyglotIsolateAccessor.ENGINE.enterInternalContext(null, contextReceiver);
        }
        try {
            TruffleObject stack = (TruffleObject) PolyglotIsolateAccessor.EXCEPTION.getEmbedderStackTrace(exception, engineReceiver, inHost);
            return guestContext.hostToGuestObjectReferences.registerGuestObject(stack);
        } finally {
            if (enterNeeded) {
                PolyglotIsolateAccessor.ENGINE.leaveInternalContext(null, contextReceiver, prev);
            }
        }
    }

    public static class GuestPolyglotIsolateServicesDirective implements CContext.Directives {
        @Override
        public boolean isInConfiguration() {
            return PolyglotIsolateGuestFeatureEnabled.isEnabled();
        }
    }
}
