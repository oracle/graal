/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyglot.PolyglotContextConfig.PreinitConfig;

/**
 * A sharing layer is a set of language instances that share code within one or more polyglot
 * contexts. In previous versions language instances were shared individually whenever a new
 * language context was created. Instead language instances are reused for a new context if and only
 * if the entire layer can be shared. A layer can be shared if all initialized languages of a layer
 * support the same context policy and their options are compatible.
 * <p>
 * The shared part of a sharing layer is initialized lazily when the first non-host language is
 * initialized. This process is internally referred to as
 * {@link #claimLayerForContext(PolyglotSharingLayer, PolyglotContextImpl, PolyglotLanguage)
 * claiming}. {@link RootNode Root nodes} might be created before a layer is claimed, e.g. for the
 * host language, therefore we need to later patch the sharing layer with a {@link Shared} instance.
 * <p>
 * Read more on sharing in {@link ContextPolicy}.
 */
final class PolyglotSharingLayer {

    private static final AtomicLong LAYER_COUNTER = new AtomicLong();

    final PolyglotEngineImpl engine;

    /**
     * Every context has its own sharing layer instance. Only the internal shared data structure is
     * shared across contexts. Use {@link #equals(Object)} to find out whether layers are equal.
     */
    @CompilationFinal volatile Shared shared;

    /**
     * Temporary place for the host language instance to reside until a layer is claimed.
     */
    PolyglotLanguageInstance hostLanguage;

    static final class Shared {

        final long id;
        final PolyglotSourceCache sourceCache;
        // indexed by engine index, not static index
        @CompilationFinal(dimensions = 1) private final PolyglotLanguageInstance[] instances;
        @CompilationFinal ContextPolicy contextPolicy;
        Map<PolyglotLanguage, OptionValuesImpl> previousLanguageOptions;
        final WeakAssumedValue<PolyglotContextImpl> singleContextValue = new WeakAssumedValue<>("single context");
        private volatile Object[] fastThreadLocalsCache;
        /*
         * Configuration that is common to all contexts created from this layer.
         */
        volatile PreinitConfig preinitConfig;
        volatile PolyglotContextImpl preInitializedContext;

        int claimedCount;

        private Shared(PolyglotEngineImpl engine, ContextPolicy contextPolicy, Map<PolyglotLanguage, OptionValuesImpl> previousLanguageOptions) {
            this.sourceCache = new PolyglotSourceCache(TracingSourceCacheListener.createOrNull(engine));
            this.contextPolicy = contextPolicy;
            this.instances = new PolyglotLanguageInstance[engine.languageCount];
            this.previousLanguageOptions = previousLanguageOptions;
            this.id = LAYER_COUNTER.incrementAndGet();
        }

        void updatePreinitConfig(PolyglotContextConfig config) {
            PreinitConfig newConfig;
            PreinitConfig prev = preinitConfig;
            if (prev == null) {
                newConfig = new PreinitConfig(config);
            } else {
                newConfig = new PreinitConfig(prev, config);
            }
            this.preinitConfig = newConfig;
        }

        Object[] getFastThreadLocals(PolyglotEngineImpl engine) {
            Object[] data = fastThreadLocalsCache;
            if (data == null) {
                data = PolyglotFastThreadLocals.createFastThreadLocals(engine, instances);
                fastThreadLocalsCache = data;
            }
            return data;
        }

        void resetFastThreadLocalsCache() {
            fastThreadLocalsCache = null;
        }
    }

    PolyglotSharingLayer(PolyglotEngineImpl engine) {
        this.engine = engine;
    }

    public boolean claimLayerForContext(PolyglotSharingLayer sharableLayer, PolyglotContextImpl context, Set<PolyglotLanguage> requestingLanguages) {
        assert Thread.holdsLock(engine.lock);
        assert !isClaimed() : "already claimed";
        assert sharableLayer == null || (sharableLayer.isClaimed() && sharableLayer.getContextPolicy() != ContextPolicy.EXCLUSIVE);
        assert hostLanguage != null || engine.inEnginePreInitialization;

        Shared s = sharableLayer != null ? sharableLayer.shared : null;

        /*
         * Fast-path check for non-reusable layers. Avoids expensive computation of the effective
         * context policy if it can be decided earlier
         */
        if (s != null) {
            switch (s.contextPolicy) {
                case EXCLUSIVE:
                    return false;
                case REUSE:
                    // with policy REUSE a context must be freed before a layer can be reused
                    if (s.claimedCount > 0) {
                        return false;
                    }
                    break;
                case SHARED:
                    break;
                default:
                    CompilerDirectives.shouldNotReachHere();
                    break;
            }
        }

        // try reuse the shared layer by checking all previous options

        Map<PolyglotLanguage, OptionValuesImpl> newLanguageOptions = collectLanguageOptions(context.config, requestingLanguages);

        // determine new requested policy
        ContextPolicy newPolicy;
        if (engine.isSharingEnabled(context.config)) {
            newPolicy = computeMinContextPolicyPolicy(newLanguageOptions.keySet());
        } else {
            if (s != null && !context.config.isCodeSharingDisabled()) {
                // a layer was loaded from persistence => try to use the layer.
                newPolicy = s.contextPolicy;
            } else {
                newPolicy = ContextPolicy.EXCLUSIVE;
            }
        }

        Map<PolyglotLanguage, OptionValuesImpl> previousLanguageOptions = null;
        if (s == null) {
            // exclusive layer or first time usage
            s = new Shared(engine, newPolicy, newLanguageOptions);

            s.instances[PolyglotEngineImpl.HOST_LANGUAGE_INDEX] = this.hostLanguage;

            if (newPolicy != ContextPolicy.EXCLUSIVE && !areLanguageOptionsCompatible(s, newLanguageOptions, newLanguageOptions)) {
                // if any language returns false with the same set of options we need to
                // fallback to EXCLUSIVE. We can still modify the contextPolicy
                // as the sharing layer is not published.
                s.contextPolicy = ContextPolicy.EXCLUSIVE;
            }
        } else {
            // layer was used before and is sharable
            previousLanguageOptions = s.previousLanguageOptions;

            if (!isContextPolicyCompatible(s.contextPolicy, newPolicy)) {
                if (engine.getEngineOptionValues().get(PolyglotEngineOptions.TraceCodeSharing)) {
                    traceClaimLayer(false, s, context, requestingLanguages, previousLanguageOptions);
                }
                return false;
            }

            // fill in options from previous languages that are not configured in this context
            for (PolyglotLanguage language : previousLanguageOptions.keySet()) {
                if (!newLanguageOptions.containsKey(language)) {
                    newLanguageOptions.put(language, context.config.getLanguageOptionValues(language));
                }
            }

            if (!areLanguageOptionsCompatible(s, previousLanguageOptions, newLanguageOptions)) {
                if (engine.getEngineOptionValues().get(PolyglotEngineOptions.TraceCodeSharing)) {
                    traceClaimLayer(false, s, context, requestingLanguages, previousLanguageOptions);
                }
                return false;
            }
            s.previousLanguageOptions = newLanguageOptions;

            /*
             * Host instances are needed early so they are initialized right when the context is
             * created. A layer is claimed when the first non-host language is initialized.
             * Therefore we need to assume EXCLUSIVE sharing until the layer is claimed. After that
             * we want to share the host code in the layer.
             */
            PolyglotLanguageInstance hostInstance = s.instances[PolyglotEngineImpl.HOST_LANGUAGE_INDEX];
            assert hostInstance != null : "host instance must always be initialized before claiming a shared layer";
            context.getHostContext().patchInstance(hostInstance);
        }

        s.updatePreinitConfig(context.config);

        assert this.shared == null || this.shared == s;
        this.shared = s;

        if (isSingleContext()) {
            s.singleContextValue.update(context);
        } else {
            s.singleContextValue.invalidate();
            hostLanguage.singleLanguageContext.invalidate();
        }

        s.claimedCount++;

        if (engine.getEngineOptionValues().get(PolyglotEngineOptions.TraceCodeSharing)) {
            traceClaimLayer(true, s, context, requestingLanguages, previousLanguageOptions);
        }
        return true;
    }

    boolean isSingleContext() {
        Shared s = this.shared;
        return (s == null || s.contextPolicy == ContextPolicy.EXCLUSIVE) && !engine.isStoreEngine();
    }

    public void preInitialize() {
        assert Thread.holdsLock(engine.lock);
        assert engine.isSharingEnabled(null);
        if (!isClaimed()) {
            // preinitialization does not make sense for layers without initialized languages.
            return;
        }
        Shared s = this.shared;
        PreinitConfig preinitConfig = s.preinitConfig;
        assert preinitConfig != null : "preinit config must be initialized";

        // deterministic iteration order
        Set<PolyglotLanguage> toInitialize = new LinkedHashSet<>();
        for (PolyglotLanguageInstance instance : s.instances) {
            if (instance != null && !instance.language.isHost()) {
                toInitialize.add(instance.language);
            }
        }
        s.preInitializedContext = PolyglotContextImpl.preinitialize(engine, preinitConfig, this, toInitialize, false);
        assert s.preInitializedContext.layer.equals(this) : "invalid resulting layer";
    }

    public PolyglotContextImpl loadPreinitializedContext(PolyglotContextConfig config) {
        assert Thread.holdsLock(engine.lock);
        assert engine.isSharingEnabled(null);

        Shared s = this.shared;
        if (s == null) {
            return null;
        }

        PolyglotContextImpl preinitContext = s.preInitializedContext;
        if (preinitContext == null) {
            return null;
        }

        Set<PolyglotLanguage> usedLanguages = new LinkedHashSet<>();
        for (PolyglotLanguageInstance instance : s.instances) {
            if (instance != null && !instance.language.isHost()) {
                usedLanguages.add(instance.language);
            }
        }

        Map<PolyglotLanguage, OptionValuesImpl> newLanguageOptions = collectLanguageOptions(config, usedLanguages);
        // we eagerly check options to avoid that patching later on fails.
        if (!areLanguageOptionsCompatible(s, s.previousLanguageOptions, newLanguageOptions)) {

            if (engine.getEngineOptionValues().get(PolyglotEngineOptions.TraceCodeSharing)) {
                traceContextPreinit(false, s, preinitContext, s.previousLanguageOptions, newLanguageOptions);
            }

            return null;
        }

        if (engine.getEngineOptionValues().get(PolyglotEngineOptions.TraceCodeSharing)) {
            traceContextPreinit(true, s, preinitContext, s.previousLanguageOptions, newLanguageOptions);
        }

        assert s.preInitializedContext == preinitContext : "must only be mutated while engine lock is held";
        // a context can only be used once.
        s.preInitializedContext = null;
        return preinitContext;
    }

    public void freeSharingLayer(PolyglotContextImpl context) {
        assert Thread.holdsLock(engine.lock);
        assert isClaimed();

        shared.claimedCount--;

        if (engine.getEngineOptionValues().get(PolyglotEngineOptions.TraceCodeSharing)) {
            traceFreeLayer(context);
        }
    }

    public PolyglotLanguageInstance allocateHostLanguage(PolyglotLanguage language) {
        assert !isClaimed();
        assert hostLanguage == null : "host language allocated twice";
        this.hostLanguage = language.createInstance(this);
        return hostLanguage;
    }

    PolyglotLanguageInstance patchHostLanguage(PolyglotLanguage language) {
        this.hostLanguage = language.createInstance(this);
        if (this.shared != null) {
            this.shared.instances[PolyglotEngineImpl.HOST_LANGUAGE_INDEX] = this.hostLanguage;
        }
        return hostLanguage;
    }

    public PolyglotLanguageInstance allocateInstance(PolyglotContextImpl context, PolyglotLanguage language) {
        assert Thread.holdsLock(engine.lock);
        assert isClaimed() : "allocateInstance before claim";
        assert !language.isHost() : "not host language";

        PolyglotContextConfig config = context.config;
        Shared s = this.shared;

        ContextPolicy languagePolicy;
        ContextPolicy layerPolicy = s.contextPolicy;

        if (layerPolicy == ContextPolicy.EXCLUSIVE) {
            assert s.claimedCount <= 1;
            languagePolicy = ContextPolicy.EXCLUSIVE;
        } else {
            languagePolicy = language.cache.getPolicy();
            if (languagePolicy != ContextPolicy.EXCLUSIVE && config != null) {
                OptionValuesImpl values = config.getLanguageOptionValues(language);
                if (!areOptionsCompatible(s, language, values, values)) {
                    languagePolicy = ContextPolicy.EXCLUSIVE;
                }
            }
        }

        if (!isContextPolicyCompatible(layerPolicy, languagePolicy)) {
            /*
             * We cannot continue here as the language does not support being allocated as part of a
             * sharable layer and we can also not create a new layer for an existing context for
             * this language as other languages were already created. If we continue here nodes of
             * languages that do not support sharing might be used shared.
             */
            String resolution;
            String reason;
            String id = language.getId();
            if (!engine.boundEngine) {
                reason = String.format("The context was configured with a shared engine but lazily initialized language '%s' does not support sharing. ", id);
                resolution = String.format(" To resolve this either: %n" + //
                                " - Ensure all languages are known when the context is constructed, by providing all required languages in the Context.newBuilder(\"%s\") method. %n" + //
                                " - Avoid lazy initialization of language '%s' by initializing as the first language using Context.initialize(\"%s\"). %n" +
                                " - Disable sharing for the polyglot context by removing the explicit engine configuration with Context.Builder.engine(...).", id, id, id, id);
            } else if (engine.storeEngine) {
                reason = String.format("The engine was configured to be stored but lazily initialized language '%s' does not support storing sharing data. ", id);
                resolution = ""; // there is no real solution when a context is stored
            } else {
                reason = String.format("The engine was forced to use code sharing but lazily initialized language '%s' does not support sharing. ", id);
                resolution = "";
            }
            throw new SharingLazyInitializationError(String.format(
                            "%sNon sharable languages cannot be initialized lazily and must be known ahead of time when the context is created. " +
                                            "Use the --engine.TraceCodeCache option to print debug details on the sharing decisions.%s",
                            reason, resolution));
        }

        PolyglotLanguageInstance instance = s.instances[language.engineIndex];
        if (instance == null) {
            instance = language.createInstance(this);
            s.instances[language.engineIndex] = instance;
            s.resetFastThreadLocalsCache();

            if (!isSingleContext()) {
                EngineAccessor.LANGUAGE.initializeMultiContext(instance.spi);
            }

            if (engine.getEngineOptionValues().get(PolyglotEngineOptions.TraceCodeSharing)) {
                traceAllocateLanguageInstance(context, language);
            }
        }

        return instance;
    }

    public PolyglotSourceCache getSourceCache() {
        assert isClaimed() : "source cache access before claim";
        return shared.sourceCache;
    }

    public PolyglotLanguageInstance getInstance(PolyglotLanguage language) {
        Shared s = this.shared;
        if (s == null) {
            return null;
        } else {
            return getInstance(s, language);
        }
    }

    private static PolyglotLanguageInstance getInstance(Shared s, PolyglotLanguage language) {
        return s.instances[language.engineIndex];
    }

    public PolyglotContextImpl getSingleConstantContext() {
        if (CompilerDirectives.inInterpreter() || !CompilerDirectives.isPartialEvaluationConstant(this)) {
            return null;
        }
        Shared s = this.shared;
        if (s == null) {
            // we need to be conservative here and not do any speculation
            // as long as the sharing layer is not claimed.
            return null;
        } else {
            return s.singleContextValue.getConstant();
        }
    }

    public PolyglotLanguageContext getSingleConstantLanguageContext(PolyglotLanguage language) {
        if (CompilerDirectives.inInterpreter() || !CompilerDirectives.isPartialEvaluationConstant(this)) {
            return null;
        }
        CompilerAsserts.partialEvaluationConstant(language);

        Shared s = this.shared;
        if (s == null) {
            // we need to be conservative here and not do any speculation
            // as long as the sharing layer is not claimed.
            return null;
        } else {
            PolyglotLanguageInstance instance = s.instances[language.engineIndex];
            CompilerAsserts.partialEvaluationConstant(instance);
            if (instance == null) {
                return null;
            }
            return instance.singleLanguageContext.getConstant();
        }
    }

    Object[] getFastThreadLocals() {
        Shared s = this.shared;
        if (s == null) {
            return null;
        }
        return s.getFastThreadLocals(engine);
    }

    public ContextPolicy getContextPolicy() {
        assert isClaimed() : "context policy lookup before claim";
        return shared.contextPolicy;
    }

    public boolean isClaimed() {
        return shared != null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PolyglotSharingLayer)) {
            return false;
        }
        PolyglotSharingLayer other = (PolyglotSharingLayer) obj;
        return this.engine == other.engine && shared == other.shared;
    }

    @Override
    public int hashCode() {
        return Objects.hash(engine, shared);
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();
        Shared s = this.shared;
        if (s == null) {
            string.append("state=unclaimed");
        } else {
            string.append("state=claimed layer-policy=");
            string.append(s.contextPolicy);
            string.append(" languages=[");
            String sep = "";
            for (PolyglotLanguageInstance instance : s.instances) {
                if (instance != null) {
                    string.append(sep);
                    string.append(instance.language.getId());
                    sep = ", ";
                }
            }
            string.append("]");
        }
        return "PolyglotSharingLayer[" + string + "]";
    }

    private static boolean isContextPolicyCompatible(ContextPolicy prevPolicy, ContextPolicy newPolicy) {
        if (prevPolicy.ordinal() <= newPolicy.ordinal()) {
            // policy is either the same or upgraded from REUSE to SHARED.
            // we just keep the previous contextPolicy we cannot go SHARED with a REUSE layer
            return true;
        } else {
            // layer is currently SHARED but effective policy is EXCLUSIVE
            // => we cannot reuse the layer
            return false;
        }
    }

    private static ContextPolicy computeMinContextPolicyPolicy(Set<PolyglotLanguage> languages) {
        assert !languages.isEmpty() : "cannot compute sharing for empty set of languages";

        ContextPolicy newPolicy = ContextPolicy.SHARED;
        for (PolyglotLanguage language : languages) {
            ContextPolicy policy = language.cache.getPolicy();
            if (policy.ordinal() < newPolicy.ordinal()) {
                newPolicy = policy;
                if (newPolicy == ContextPolicy.EXCLUSIVE) {
                    break;
                }
            }
        }
        return newPolicy;
    }

    private static boolean areLanguageOptionsCompatible(Shared s, Map<PolyglotLanguage, OptionValuesImpl> oldLanguageOptions, Map<PolyglotLanguage, OptionValuesImpl> newLanguageOptions) {
        for (Entry<PolyglotLanguage, OptionValuesImpl> entry : newLanguageOptions.entrySet()) {
            PolyglotLanguage language = entry.getKey();
            OptionValuesImpl newOptions = entry.getValue();
            assert newOptions != null;
            OptionValuesImpl prevOptions = oldLanguageOptions.get(language);
            if (prevOptions == null) {
                prevOptions = language.getOptionValues();
            }
            if (!areOptionsCompatible(s, language, prevOptions, newOptions)) {
                return false;
            }
        }
        return true;
    }

    private static boolean areOptionsCompatible(Shared s, PolyglotLanguage language, OptionValuesImpl previousOptions, OptionValuesImpl newOptions) {
        PolyglotLanguageInstance instance = resolveInstance(s, language);
        return EngineAccessor.LANGUAGE.areOptionsCompatible(instance.spi, previousOptions, newOptions);
    }

    private static PolyglotLanguageInstance resolveInstance(Shared s, PolyglotLanguage language) {
        PolyglotLanguageInstance instance = getInstance(s, language);
        if (instance == null) {
            instance = language.getInitLanguage();
        }
        return instance;
    }

    private Map<PolyglotLanguage, OptionValuesImpl> collectLanguageOptions(PolyglotContextConfig config, Set<PolyglotLanguage> forcedLanguages) {
        Map<PolyglotLanguage, OptionValuesImpl> newOptions = new HashMap<>();
        Set<PolyglotLanguage> languages = config.getConfiguredLanguages();
        if (!languages.containsAll(forcedLanguages)) {
            // we need to resolve dependencies of not yet configured languages
            // the set of configured languages is already resolved
            languages = new HashSet<>(languages);
            for (PolyglotLanguage language : forcedLanguages) {
                config.addConfiguredLanguage(this.engine, languages, language);
            }
        }

        for (PolyglotLanguage language : languages) {
            newOptions.put(language, config.getLanguageOptionValues(language));
        }

        return newOptions;
    }

    public void listCachedSources(Set<Object> sources) {
        Shared s = this.shared;
        if (s != null) {
            s.sourceCache.listCachedSources(engine.getImpl(), sources);
        }
    }

    /*
     * Layer validation and error code.
     */
    public static AssertionError invalidSharingError(Node node,
                    PolyglotSharingLayer previousLayer,
                    PolyglotSharingLayer currentLayer) throws AssertionError {

        PolyglotSharingLayer prev = previousLayer;
        PolyglotSharingLayer current = currentLayer;

        Exception e = new Exception();
        StringBuilder stack = new StringBuilder();
        Exception exceptionCreating = null;
        try {
            TruffleStackTrace.fillIn(e);

            stack.append(String.format("%n  <<current-context>>"));

            printLayerChange(stack, prev, current);

            if (node != null) {
                RootNode root = node.getRootNode();
                if (root != null) {
                    stack.append(String.format("%n  %s(%s)", createJavaStackFrame(root, node.getEncapsulatingSourceSection()), node));
                }
            }

            for (TruffleStackTraceElement stackTrace : TruffleStackTrace.getStackTrace(e)) {
                RootNode root = stackTrace.getTarget().getRootNode();

                current = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(root);

                printLayerChange(stack, prev, current);

                SourceSection sourceSection = null;
                Node location = stackTrace.getLocation();
                if (location != null) {
                    sourceSection = location.getEncapsulatingSourceSection();
                }
                stack.append(String.format("%n  %s", createJavaStackFrame(root, sourceSection)));

                if (current != null) {
                    prev = current;
                }
            }

        } catch (Exception ex) {
            exceptionCreating = ex;
        }

        AssertionError error = new AssertionError(
                        String.format("Invalid sharing of AST nodes detected. " + //
                                        "The current context uses a different sharing layer than the executed node. " + //
                                        "A common cause of this are CallTargets that are reused across different contexts in an invalid way." + //
                                        "Stack trace: %s",
                                        stack.toString()));
        if (exceptionCreating != null) {
            error.addSuppressed(exceptionCreating);
        }
        throw error;
    }

    private static void printLayerChange(StringBuilder stack, PolyglotSharingLayer previousLayer, PolyglotSharingLayer newLayer) {
        if (newLayer != null && !Objects.equals(previousLayer, newLayer)) {
            stack.append(String.format("%n    <-- Sharing Layer Change: 0x%H => 0x%H -->",
                            System.identityHashCode(previousLayer.shared),
                            System.identityHashCode(newLayer.shared)));
        }
    }

    private static StackTraceElement createJavaStackFrame(RootNode root, SourceSection sourceSection) {
        SourceSection sc = sourceSection;
        if (sc == null) {
            sc = root.getSourceSection();
        }
        PolyglotLanguageInstance instance = lookupLanguageInstance(root);
        String language = instance != null ? instance.language.getId() : "Unknown";
        String rootName = root.getName();
        String declaringClass = "<" + language + ">";
        String methodName = rootName == null ? "" : rootName;
        String fileName = sc != null ? sc.getSource().getName() : "Unknown";
        int startLine = sc != null ? sc.getStartLine() : -1;
        return new StackTraceElement(declaringClass, methodName, fileName, startLine);
    }

    private static PolyglotLanguageInstance lookupLanguageInstance(RootNode root) {
        TruffleLanguage<?> spi = EngineAccessor.NODES.getLanguage(root);
        if (spi != null) {
            return (PolyglotLanguageInstance) EngineAccessor.LANGUAGE.getPolyglotLanguageInstance(spi);
        }
        return null;
    }

    /*
     * Tracing code.
     */

    private void traceContextPreinit(boolean success, Shared s, PolyglotContextImpl context, Map<PolyglotLanguage, OptionValuesImpl> previousOptions,
                    Map<PolyglotLanguage, OptionValuesImpl> newLanguageOptions) {
        trace(context, s, "loading pre-init", String.format("claimedCount:%s sharingEnabled:%s ",
                        success ? (s.claimedCount - 1) : s.claimedCount,
                        engine.isSharingEnabled(context.config)));
        for (Entry<PolyglotLanguage, OptionValuesImpl> entry : newLanguageOptions.entrySet()) {
            traceCompatibility(s, context, previousOptions, entry);
        }
        trace(context, s, success ? "loaded" : "failed to load pre-init", "");
    }

    private void traceClaimLayer(boolean success, Shared s, PolyglotContextImpl context, Set<PolyglotLanguage> requestingLangauges, Map<PolyglotLanguage, OptionValuesImpl> previousOptions) {
        trace(context, s, "claiming", String.format("claimedCount:%s sharingEnabled:%s ",
                        success ? (s.claimedCount - 1) : s.claimedCount,
                        engine.isSharingEnabled(context.config)));

        Map<PolyglotLanguage, OptionValuesImpl> newLanguageOptions = collectLanguageOptions(context.config, requestingLangauges);
        for (Entry<PolyglotLanguage, OptionValuesImpl> entry : newLanguageOptions.entrySet()) {
            traceCompatibility(s, context, previousOptions, entry);
        }
        trace(context, s, success ? "claimed" : "failed to claim", String.format("claimedCount:%s layer-policy:%s", s.claimedCount, s.contextPolicy));
    }

    private void traceCompatibility(Shared s, PolyglotContextImpl context, Map<PolyglotLanguage, OptionValuesImpl> previousOptions, Entry<PolyglotLanguage, OptionValuesImpl> entry) {
        StringBuilder languageInfos = new StringBuilder();
        PolyglotLanguage language = entry.getKey();
        ContextPolicy policy = language.cache.getPolicy();
        languageInfos.append(String.format("%s registration-policy:%s  ", language.getId(), policy));
        boolean optionsCompatible = isContextPolicyCompatible(s.contextPolicy, policy);
        if (optionsCompatible && engine.isSharingEnabled(context.config)) {
            OptionValuesImpl newOptions = entry.getValue();
            OptionValuesImpl prevOptions = previousOptions != null ? previousOptions.get(language) : newOptions;
            if (prevOptions == null) {
                prevOptions = language.getOptionValues();
            }
            optionsCompatible = areOptionsCompatible(s, language, prevOptions, newOptions);
            languageInfos.append(
                            String.format("%s.areOptionsCompatibleWith(%s, %s) == %s", resolveInstance(s, language).spi.getClass().getSimpleName(), prevOptions, newOptions, optionsCompatible));
        }
        trace(context, s, optionsCompatible ? "  compatible" : "  incompatible", languageInfos.toString());
    }

    private void traceFreeLayer(PolyglotContextImpl context) {
        trace(context, shared, "freed", String.format("claimedCount:%s", context.layer.shared.claimedCount));
    }

    private void traceAllocateLanguageInstance(PolyglotContextImpl context, PolyglotLanguage language) {
        trace(context, this.shared, "created language", String.format("%s for policy %s", language.getId(), shared.contextPolicy));
    }

    private void trace(PolyglotContextImpl context, Shared s, String label, String message) {
        engine.getEngineLogger().info(
                        String.format("[sharing] engine 0x%8H context 0x%8H layer 0x%8H: %-20s %s", engine.hashCode(), Objects.hash(context), s.hashCode(), label, message));
    }

    @SuppressWarnings("serial")
    static final class SharingLazyInitializationError extends AbstractTruffleException {

        SharingLazyInitializationError(String message) {
            super(message);
        }

    }

}
