/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.options.OptionMap;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyOracle;
import com.oracle.truffle.espresso.analysis.hierarchy.DefaultClassHierarchyOracle;
import com.oracle.truffle.espresso.analysis.hierarchy.NoOpClassHierarchyOracle;
import com.oracle.truffle.espresso.classfile.perf.TimerCollection;
import com.oracle.truffle.espresso.jdwp.api.VMEventListenerImpl;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.interop.EspressoForeignProxyGenerator;
import com.oracle.truffle.espresso.nodes.interop.PolyglotTypeMappings;
import com.oracle.truffle.espresso.ref.FinalizationSupport;
import com.oracle.truffle.espresso.threads.EspressoThreadRegistry;

/**
 * Represents an immutable holder class for context-specific fields bound to the
 * {@link com.oracle.truffle.api.TruffleLanguage.Env} (e.g., {@link EspressoOptions} values).
 * <p>
 * When using context pre-initialization, the first {@link EspressoContext} is running in both
 * build-time and runtime environments. This means that the build-time {@link EspressoEnv} needs to
 * be updated in order for the context to be in a valid state at runtime. This is achieved by
 * re-creating the {@link EspressoEnv} at runtime during context patching. This allows the
 * {@link EspressoEnv} to be immutable and effectively final
 * ({@link com.oracle.truffle.api.CompilerDirectives.CompilationFinal}) for every
 * {@link EspressoContext}..
 */
public final class EspressoEnv {
    private final TruffleLanguage.Env env;
    private final String[] vmArguments;

    // region Debug
    private final TimerCollection timers;
    // endregion Debug

    // region Runtime
    private final ClassHierarchyOracle classHierarchyOracle;
    // endregion Runtime

    // region Helpers
    private final EspressoThreadRegistry threadRegistry;
    private final EspressoReferenceDrainer referenceDrainer;
    // endregion Helpers

    // region JDWP
    private final JDWPContextImpl jdwpContext;
    private final boolean shouldReportVMEvents;
    private final VMEventListenerImpl eventListener;
    // endregion JDWP

    // region Options
    // Checkstyle: stop field name check

    // Performance control
    public final boolean bytecodeLevelInlining;
    public final boolean InlineMethodHandle;
    public final boolean SplitMethodHandles;

    // Behavior control
    public final boolean EnableManagement;
    public final boolean SoftExit;
    public final boolean AllowHostExit;
    public final boolean Polyglot;
    public final boolean BuiltInPolyglotCollections;
    public final boolean HotSwapAPI;
    public final boolean UseBindingsLoader;
    public final boolean EnableSignals;
    private final String multiThreadingDisabled;
    public final boolean NativeAccessAllowed;
    public final boolean EnableNativeAgents;
    public final int TrivialMethodSize;
    public final boolean UseHostFinalReference;
    public final EspressoOptions.JImageMode JImageMode;
    private final PolyglotTypeMappings polyglotTypeMappings;
    private final boolean enableGenericTypeHints;
    private final HashMap<String, EspressoForeignProxyGenerator.GeneratedProxyBytes> proxyCache;
    public final boolean AdvancedRedefinition;

    // Debug option
    public final com.oracle.truffle.espresso.jdwp.api.JDWPOptions JDWPOptions;

    // Checkstyle: resume field name check
    // endregion Options

    public EspressoEnv(EspressoContext context, TruffleLanguage.Env env) {
        this.env = env;

        this.threadRegistry = new EspressoThreadRegistry(context);
        this.referenceDrainer = new EspressoReferenceDrainer(context);

        this.SoftExit = env.getOptions().get(EspressoOptions.SoftExit);
        this.AllowHostExit = env.getOptions().get(EspressoOptions.ExitHost);
        this.timers = TimerCollection.create(env.getOptions().get(EspressoOptions.EnableTimers));

        // null if not specified
        this.JDWPOptions = env.getOptions().get(EspressoOptions.JDWPOptions);
        this.shouldReportVMEvents = JDWPOptions != null;
        this.eventListener = new VMEventListenerImpl();

        this.bytecodeLevelInlining = JDWPOptions == null && env.getOptions().get(EspressoOptions.BytecodeLevelInlining);
        this.InlineMethodHandle = JDWPOptions == null && env.getOptions().get(EspressoOptions.InlineMethodHandle);
        this.SplitMethodHandles = JDWPOptions == null && env.getOptions().get(EspressoOptions.SplitMethodHandles);
        this.EnableSignals = env.getOptions().get(EspressoOptions.EnableSignals);
        this.EnableManagement = env.getOptions().get(EspressoOptions.EnableManagement);
        this.EnableNativeAgents = env.getOptions().get(EspressoOptions.EnableNativeAgents);
        this.TrivialMethodSize = env.getOptions().get(EspressoOptions.TrivialMethodSize);
        boolean useHostFinalReferenceOption = env.getOptions().get(EspressoOptions.UseHostFinalReference);
        this.UseHostFinalReference = useHostFinalReferenceOption && FinalizationSupport.canUseHostFinalReference();
        if (useHostFinalReferenceOption && !FinalizationSupport.canUseHostFinalReference()) {
            if (env.getOptions().hasBeenSet(EspressoOptions.UseHostFinalReference)) {
                context.getLogger().warning("--java.UseHostFinalReference is set to 'true' but Espresso cannot access the host java.lang.ref.FinalReference class.\n" +
                                "Ensure that host system properties '-Despresso.finalization.InjectClasses=true' and '-Despresso.finalization.UnsafeOverride=true' are set.\n" +
                                "Espresso's guest FinalReference(s) will fallback to WeakReference semantics.");
            }
        }

        String multiThreadingDisabledReason = null;
        if (!env.getOptions().get(EspressoOptions.MultiThreaded)) {
            multiThreadingDisabledReason = "java.MultiThreaded option is set to false";
        }
        if (!env.isCreateThreadAllowed()) {
            multiThreadingDisabledReason = "polyglot context does not allow thread creation (`allowCreateThread(false)`)";
        }
        if (multiThreadingDisabledReason == null && !env.getOptions().hasBeenSet(EspressoOptions.MultiThreaded)) {
            Set<String> singleThreadedLanguages = knownSingleThreadedLanguages(env);
            if (!singleThreadedLanguages.isEmpty()) {
                multiThreadingDisabledReason = "context seems to contain single-threaded languages: " + singleThreadedLanguages;
                context.getLogger().warning(() -> "Disabling multi-threading since the context seems to contain single-threaded languages: " + singleThreadedLanguages);
            }
        }
        this.multiThreadingDisabled = multiThreadingDisabledReason;
        this.NativeAccessAllowed = env.isNativeAccessAllowed();
        this.Polyglot = env.getOptions().get(EspressoOptions.Polyglot);
        this.HotSwapAPI = env.getOptions().get(EspressoOptions.HotSwapAPI);
        this.BuiltInPolyglotCollections = env.getOptions().get(EspressoOptions.BuiltInPolyglotCollections);
        this.polyglotTypeMappings = new PolyglotTypeMappings(env.getOptions().get(EspressoOptions.PolyglotInterfaceMappings), env.getOptions().get(EspressoOptions.PolyglotTypeConverters),
                        BuiltInPolyglotCollections);
        this.enableGenericTypeHints = env.getOptions().get(EspressoOptions.EnableGenericTypeHints);
        this.proxyCache = polyglotTypeMappings.hasMappings() ? new HashMap<>() : null;
        this.UseBindingsLoader = env.getOptions().get(EspressoOptions.UseBindingsLoader);
        this.AdvancedRedefinition = env.getOptions().get(EspressoOptions.EnableAdvancedRedefinition);

        EspressoOptions.JImageMode requestedJImageMode = env.getOptions().get(EspressoOptions.JImage);
        if (!NativeAccessAllowed && requestedJImageMode == EspressoOptions.JImageMode.NATIVE) {
            throw new IllegalArgumentException("JImage=native can only be set if native access is allowed");
        }
        this.JImageMode = requestedJImageMode;

        this.vmArguments = buildVmArguments(context.getLogger());
        this.jdwpContext = new JDWPContextImpl(context);
        if (env.getOptions().get(EspressoOptions.CHA)) {
            this.classHierarchyOracle = new DefaultClassHierarchyOracle();
        } else {
            this.classHierarchyOracle = new NoOpClassHierarchyOracle();
        }
    }

    public TruffleLanguage.Env env() {
        return env;
    }

    public boolean multiThreadingEnabled() {
        return multiThreadingDisabled == null;
    }

    public String getMultiThreadingDisabledReason() {
        return multiThreadingDisabled;
    }

    public String[] getVmArguments() {
        return vmArguments;
    }

    public JDWPContextImpl getJdwpContext() {
        return jdwpContext;
    }

    public EspressoReferenceDrainer getReferenceDrainer() {
        return referenceDrainer;
    }

    public VMEventListenerImpl getEventListener() {
        return eventListener;
    }

    public TimerCollection getTimers() {
        return timers;
    }

    public EspressoThreadRegistry getThreadRegistry() {
        return threadRegistry;
    }

    public ClassHierarchyOracle getClassHierarchyOracle() {
        return classHierarchyOracle;
    }

    public PolyglotTypeMappings getPolyglotTypeMappings() {
        return polyglotTypeMappings;
    }

    public boolean isGenericTypeHintsEnabled() {
        return enableGenericTypeHints;
    }

    public HashMap<String, EspressoForeignProxyGenerator.GeneratedProxyBytes> getProxyCache() {
        return proxyCache;
    }

    public boolean shouldReportVMEvents() {
        return shouldReportVMEvents;
    }

    private String[] buildVmArguments(TruffleLogger logger) {
        OptionMap<String> argsMap = env().getOptions().get(EspressoOptions.VMArguments);
        if (argsMap == null) {
            return new String[0];
        }
        Set<Map.Entry<String, String>> set = argsMap.entrySet();
        int length = set.size();
        String[] array = new String[length];
        for (Map.Entry<String, String> entry : set) {
            try {
                String key = entry.getKey();
                int idx = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
                if (idx < 0 || idx >= length) {
                    logger.severe("Unsupported use of the 'java.VMArguments' option: " +
                                    "Declared index: " + idx + ", actual number of arguments: " + length + ".\n" +
                                    "Please only declare positive index starting from 0, and growing by 1 each.");
                    throw EspressoError.shouldNotReachHere();
                }
                array[idx] = entry.getValue();
            } catch (NumberFormatException e) {
                logger.warning("Unsupported use of the 'java.VMArguments' option: java.VMArguments." + entry.getKey() + "=" + entry.getValue() + "\n" +
                                "Should be of the form: java.VMArguments.<int>=<value>");
                throw EspressoError.shouldNotReachHere();
            }
        }
        return array;
    }

    private static Set<String> knownSingleThreadedLanguages(TruffleLanguage.Env env) {
        Set<String> singleThreaded = new HashSet<>();
        for (LanguageInfo languageInfo : env.getPublicLanguages().values()) {
            switch (languageInfo.getId()) {
                case "wasm":    // fallthrough
                case "js":      // fallthrough
                case "R":       // fallthrough
                case "python":  // it's configurable for python, be shy
                    singleThreaded.add(languageInfo.getId());
            }
        }
        return singleThreaded;
    }
}
