/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.impl.AbstractFastThreadLocal;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.polyglot.EngineAccessor.AbstractClassLoaderSupplier;

// 0: PolyglotThreadInfo
// 1: PolyglotContextImpl
// 2: EncapsulatingNodeReference
// 3 + (languageIndex * 2 + 0): language context impl for fast access
// 3 + (languageIndex * 2 + 1): language spi for fast access
final class PolyglotFastThreadLocals {

    private static final AbstractFastThreadLocal IMPL = EngineAccessor.RUNTIME.getContextThreadLocal();
    private static final ConcurrentHashMap<List<AbstractClassLoaderSupplier>, Map<String, LanguageCache>> CLASS_NAME_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, CachedReferences> CONTEXT_REFERENCE_CACHE = new ConcurrentHashMap<>();
    private static final Object NOT_ENTERED = new Object();

    private static final FinalIntMap LANGUAGE_INDEXES = new FinalIntMap();
    private static final int RESERVED_NULL = -1; // never set
    private static final int THREAD_INDEX = 0;
    static final int CONTEXT_THREAD_LOCALS_INDEX = 1;
    static final int CONTEXT_INDEX = 2;
    private static final int ENCAPSULATING_NODE_REFERENCE_INDEX = 3;
    private static final int LANGUAGE_START = 4;

    static final int LANGUAGE_CONTEXT_OFFSET = 0;
    static final int LANGUAGE_SPI_OFFSET = 1;
    private static final int LANGUAGE_ELEMENTS = 2;

    static void resetNativeImageState() {
        CONTEXT_REFERENCE_CACHE.clear();
        CLASS_NAME_CACHE.clear();
    }

    static Object[] createFastThreadLocals(PolyglotThreadInfo thread) {
        PolyglotContextImpl context = thread.context;
        assert Thread.holdsLock(context);
        Object[] data = createEmptyData(thread.context.engine);
        data[THREAD_INDEX] = thread;
        data[CONTEXT_THREAD_LOCALS_INDEX] = thread.contextThreadLocals;
        data[CONTEXT_INDEX] = thread.context;
        data[ENCAPSULATING_NODE_REFERENCE_INDEX] = EngineAccessor.NODES.createEncapsulatingNodeReference(thread.getThread());
        for (PolyglotLanguageContext languageContext : thread.context.contexts) {
            if (languageContext.isCreated()) {
                updateLanguageObjects(data, languageContext);
            }
        }
        return data;
    }

    private static Object[] createFastThreadLocalsForLanguage(PolyglotLanguageInstance instance) {
        Object[] data = createEmptyData(instance.language.engine);
        data[THREAD_INDEX] = null; // not available if only engine is entered
        data[CONTEXT_THREAD_LOCALS_INDEX] = null; // not available if only engine is entered
        data[CONTEXT_INDEX] = null; // not available if only engine is entered

        // we take the first language we find. should we fail maybe if there is more than one?
        data[getLanguageIndex(instance) + LANGUAGE_SPI_OFFSET] = instance.spi;
        return data;
    }

    static Object[] createFastThreadLocals(PolyglotEngineImpl engine, PolyglotLanguageInstance[] instances) {
        Object[] data = createEmptyData(engine);
        for (PolyglotLanguageInstance instance : instances) {
            if (instance != null) {
                int index = getLanguageIndex(instance);
                data[LANGUAGE_SPI_OFFSET + index] = instance.spi;
            }
        }
        return data;
    }

    private static Object[] createEmptyData(PolyglotEngineImpl engine) {
        return new Object[LANGUAGE_START + (engine.languages.length * LANGUAGE_ELEMENTS)];
    }

    private static int getLanguageIndex(PolyglotLanguageInstance instance) {
        return LANGUAGE_START + (instance.language.cache.getStaticIndex() * LANGUAGE_ELEMENTS);
    }

    @SuppressWarnings({"unchecked"})
    public static <C extends TruffleLanguage<?>> LanguageReference<C> createLanguageReference(Class<? extends TruffleLanguage<?>> language) {
        return (LanguageReference<C>) lookupReferences(language).languageReference;
    }

    @SuppressWarnings("unchecked")
    public static <C> ContextReference<C> createContextReference(Class<? extends TruffleLanguage<C>> language) {
        return (ContextReference<C>) lookupReferences(language).contextReference;
    }

    public static boolean needsEnter(PolyglotContextImpl context) {
        return IMPL.fastGet(CONTEXT_INDEX, PolyglotContextImpl.class, false, false) != context;
    }

    public static Object[] enter(PolyglotThreadInfo threadInfo) {
        Object[] prev = IMPL.get();
        IMPL.set(threadInfo.fastThreadLocals);
        return prev;
    }

    public static void leave(Object[] prev) {
        IMPL.set(prev);
    }

    public static Object enterLanguage(PolyglotLanguageInstance language) {
        Object[] prev = IMPL.get();
        IMPL.set(createFastThreadLocalsForLanguage(language));
        return prev;
    }

    public static void leaveLanguage(PolyglotLanguageInstance instance, Object prev) {
        assert IMPL.get()[getLanguageIndex(instance) + LANGUAGE_SPI_OFFSET] != null : "language not entered";
        IMPL.set((Object[]) prev);
    }

    public static Object enterLayer(RootNode root) {
        PolyglotSharingLayer layer = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(root);
        PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(layer);
        // Enter the layer unless a context with that layer is entered already
        if (context == null || !context.layer.equals(layer)) {
            Object[] prev = IMPL.get();
            IMPL.set(layer.getFastThreadLocals());
            return prev;
        }
        return NOT_ENTERED;
    }

    public static void leaveLayer(Object prev) {
        if (prev != NOT_ENTERED) {
            IMPL.set((Object[]) prev);
        }
    }

    public static void cleanup(Object[] threadLocals) {
        Arrays.fill(threadLocals, null);
    }

    static EncapsulatingNodeReference getEncapsulatingNodeReference(boolean invalidateOnNull) {
        /*
         * It is tempting to constant fold here for single thread contexts using a Node. However, I
         * was unable to measure a speedup from doing this compared to reading the fast thread local
         * instead. So we do not bother here and trade a bit smaller code for fewer deoptimizations
         * and less footprint (no assumptions in use).
         */
        return IMPL.fastGet(ENCAPSULATING_NODE_REFERENCE_INDEX, EncapsulatingNodeReference.class, invalidateOnNull, false);
    }

    static PolyglotThreadInfo getCurrentThread(Node node) {
        if (CompilerDirectives.inCompiledCode() && node != null) {
            PolyglotSharingLayer layer = resolveLayer(node);
            if (layer != null) {
                PolyglotContextImpl singleContext = layer.getSingleConstantContext();
                if (singleContext != null && CompilerDirectives.isPartialEvaluationConstant(singleContext)) {
                    PolyglotThreadInfo constantThread = singleContext.singleThreadValue.getConstant();
                    if (constantThread != null) {
                        return constantThread;
                    }
                }
            }
        }
        return IMPL.fastGet(THREAD_INDEX, PolyglotThreadInfo.class, true, true);
    }

    public static Object[] getCurrentThreadContextThreadLocals(PolyglotSharingLayer layer) {
        if (CompilerDirectives.inCompiledCode() && layer != null) {
            PolyglotContextImpl singleContext = layer.getSingleConstantContext();
            if (singleContext != null && CompilerDirectives.isPartialEvaluationConstant(singleContext)) {
                PolyglotThreadInfo constantThread = singleContext.singleThreadValue.getConstant();
                if (constantThread != null) {
                    return constantThread.getThreadLocals(layer.engine);
                }
            }
        }
        return IMPL.fastGet(CONTEXT_THREAD_LOCALS_INDEX, Object[].class, true, true);
    }

    public static Object[] getCurrentThreadContextThreadLocalsEngine(PolyglotEngineImpl engine) {
        if (CompilerDirectives.inCompiledCode() && engine != null) {
            PolyglotContextImpl singleContext = engine.singleContextValue.getConstant();
            if (singleContext != null) {
                PolyglotThreadInfo constantThread = singleContext.singleThreadValue.getConstant();
                if (constantThread != null) {
                    return constantThread.getThreadLocals(engine);
                }
            }
        }
        return IMPL.fastGet(CONTEXT_THREAD_LOCALS_INDEX, Object[].class, true, true);
    }

    public static PolyglotContextImpl getContext(PolyglotSharingLayer layer) {
        if (CompilerDirectives.inCompiledCode() && layer != null) {
            PolyglotContextImpl value = layer.getSingleConstantContext();
            if (value != null) {
                return value;
            }
        }
        return IMPL.fastGet(CONTEXT_INDEX, PolyglotContextImpl.class, true, false);
    }

    public static PolyglotContextImpl getContextWithEngine(PolyglotEngineImpl engine) {
        if (CompilerDirectives.inCompiledCode() && engine != null) {
            PolyglotContextImpl context = engine.singleContextValue.getConstant();
            if (context != null) {
                return context;
            }
        }
        return IMPL.fastGet(CONTEXT_INDEX, PolyglotContextImpl.class, true, false);
    }

    public static PolyglotContextImpl getContextWithNode(Node node) {
        if (CompilerDirectives.inCompiledCode()) {
            PolyglotSharingLayer layer = resolveLayer(node);
            if (layer != null) {
                return layer.getSingleConstantContext();
            }
        }
        return IMPL.fastGet(CONTEXT_INDEX, PolyglotContextImpl.class, true, false);
    }

    @SuppressWarnings("unchecked")
    public static TruffleLanguage<Object> getLanguage(Node node, int index, Class<?> languageClass) {
        assert validSharing(node);
        if (CompilerDirectives.inCompiledCode()) {
            PolyglotLanguageInstance instance = resolveLanguageInstance(node, index);
            if (instance != null) {
                return instance.spi;
            }
        }
        return (TruffleLanguage<Object>) IMPL.fastGet(index, languageClass, true, false);
    }

    public static Object getLanguageContext(Node node, int index) {
        assert validSharing(node);
        Class<?> contextClass = null;
        if (CompilerDirectives.inCompiledCode()) {
            PolyglotLanguageInstance instance = resolveLanguageInstance(node, index);
            if (instance != null) {
                PolyglotLanguageContext languageContext = instance.singleLanguageContext.getConstant();
                if (languageContext != null) {
                    return languageContext.getContextImpl();
                }
            }
            contextClass = findContextClass(node, index);
        }
        return IMPL.fastGet(index, contextClass, true, false);
    }

    private static boolean validSharing(Node node) {
        PolyglotContextImpl currentContext = getContext(null);
        if (currentContext == null) {
            // no current context, let us be optimistic that this is fine
            return true;
        }

        PolyglotSharingLayer astLayer = resolveLayer(node);
        if (astLayer == null) {
            // cannot validate with null layer
            return true;
        }

        PolyglotSharingLayer currentLayer = currentContext.layer;
        if (!Objects.equals(astLayer, currentLayer)) {
            throw PolyglotSharingLayer.invalidSharingError(node, astLayer, currentLayer);
        }
        return true;
    }

    private static CachedReferences lookupReferences(Class<? extends TruffleLanguage<?>> language) {
        return CONTEXT_REFERENCE_CACHE.computeIfAbsent(language, (c) -> new CachedReferences(c));
    }

    static void notifyLanguageCreated(PolyglotLanguageContext languageContext) {
        assert Thread.holdsLock(languageContext.context);
        for (PolyglotThreadInfo threadInfo : languageContext.context.getSeenThreads().values()) {
            updateLanguageObjects(threadInfo.fastThreadLocals, languageContext);
        }
    }

    private static void updateLanguageObjects(Object[] data, PolyglotLanguageContext languageContext) {
        PolyglotLanguageInstance instance = languageContext.getLanguageInstance();
        int languageIndex = getLanguageIndex(instance);
        assert languageIndex + LANGUAGE_ELEMENTS - 1 < data.length : "unexpected fast thread local state";

        data[languageIndex + LANGUAGE_CONTEXT_OFFSET] = languageContext.getContextImpl();
        data[languageIndex + LANGUAGE_SPI_OFFSET] = instance.spi;
    }

    /*
     * This method is intended to be invoked in the compiler only and must always compile to a
     * constant result.
     */
    private static PolyglotLanguageInstance resolveLanguageInstance(Node node, int index) {
        CompilerAsserts.partialEvaluationConstant(index);

        if (!CompilerDirectives.isPartialEvaluationConstant(node)) {
            // no constant folding without PE constant node
            return null;
        }

        if (node == null) {
            return null;
        }

        RootNode root = node.getRootNode();
        if (root == null) {
            return null;
        }

        PolyglotSharingLayer layer = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(root);
        if (layer == null) {
            return null;
        }
        int languageIndex = resolveLanguageIndex(index);
        return layer.getInstance(layer.engine.languages[languageIndex]);
    }

    private static int computeLanguageIndex(Class<?> languageClass, int offset) {
        List<AbstractClassLoaderSupplier> loaders = EngineAccessor.locatorOrDefaultLoaders();
        int staticIndex;
        if (EngineAccessor.HOST.isHostLanguage(languageClass)) {
            staticIndex = PolyglotEngineImpl.HOST_LANGUAGE_INDEX;
        } else {
            Map<String, LanguageCache> classNames = CLASS_NAME_CACHE.get(loaders);
            if (classNames == null) {
                classNames = new HashMap<>();
                Map<String, LanguageCache> idToLanguage = LanguageCache.loadLanguages(loaders);
                for (LanguageCache cache : idToLanguage.values()) {
                    classNames.put(cache.getClassName(), cache);
                }
                Map<String, LanguageCache> finalClassNames = classNames;
                classNames = CLASS_NAME_CACHE.computeIfAbsent(loaders, (k) -> Collections.unmodifiableMap(finalClassNames));
            }
            LanguageCache cache = classNames.get(languageClass.getName());
            if (cache == null) {
                return RESERVED_NULL;
            }
            staticIndex = cache.getStaticIndex();
            assert staticIndex <= LanguageCache.getMaxStaticIndex() : "invalid sharing between class loaders";
        }
        return computeLanguageIndexFromStaticIndex(staticIndex, offset);
    }

    static int computeLanguageIndexFromStaticIndex(int staticIndex, int offset) {
        return LANGUAGE_START + (staticIndex * LANGUAGE_ELEMENTS) + offset;
    }

    private static int resolveLanguageIndex(int index) {
        if (index < LANGUAGE_START || index >= LANGUAGE_START + ((LanguageCache.getMaxStaticIndex() + 1) * LANGUAGE_ELEMENTS)) {
            throw CompilerDirectives.shouldNotReachHere("invalid fast thread local index");
        }
        return Math.floorDiv(index - LANGUAGE_START, LANGUAGE_ELEMENTS);
    }

    static int computePELanguageIndex(Class<? extends TruffleLanguage<?>> languageClass, int offset) {
        int indexValue = LANGUAGE_INDEXES.get(languageClass);
        if (indexValue == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (LANGUAGE_INDEXES) {
                indexValue = LANGUAGE_INDEXES.get(languageClass);
                if (indexValue == -1) {
                    indexValue = computeLanguageIndex(languageClass, 0);
                    LANGUAGE_INDEXES.put(languageClass, indexValue);
                }
            }
        }
        return indexValue + offset;
    }

    protected static PolyglotSharingLayer resolveLayer(Node node) {
        if (!CompilerDirectives.isPartialEvaluationConstant(node)) {
            // no constant folding without node
            return null;
        }
        if (node == null) {
            return null;
        }
        RootNode root = node.getRootNode();
        if (root == null) {
            return null;
        }
        return (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(root);
    }

    private static PolyglotEngineImpl resolveEngine(Node node) {
        PolyglotSharingLayer layer = resolveLayer(node);
        if (layer != null) {
            return layer.engine;
        } else {
            return null;
        }
    }

    private static PolyglotLanguage findLanguage(Node node, int index) {
        PolyglotEngineImpl engine = resolveEngine(node);
        if (engine == null) {
            return null;
        }
        int languageIndex = resolveLanguageIndex(index);
        if (languageIndex > engine.languages.length) {
            // out of bounds language. might happen for invalid language accesses
            return null;
        }
        return engine.languages[languageIndex];
    }

    @SuppressWarnings("unchecked")
    private static <C> Class<C> findContextClass(Node node, int index) {
        if (index == RESERVED_NULL) {
            return null;
        }
        PolyglotLanguage language = findLanguage(node, index);
        CompilerAsserts.partialEvaluationConstant(language);
        Class<C> targetClass = null;
        if (language != null) {
            targetClass = (Class<C>) language.contextClass;
        }
        return targetClass;
    }

    static boolean assertValidGet(int index, int expectedOffset, Class<?> expectedType, Class<?> languageClass) {
        if (index == RESERVED_NULL) {
            throw new IllegalArgumentException("Language " + languageClass + " not installed but used.");
        }
        Object[] data = IMPL.get();
        assert data != null : "No polyglot context is entered. A language or context reference must not be used if there is no polyglot context entered.";
        assert index >= LANGUAGE_START && index < LANGUAGE_START + ((LanguageCache.getMaxStaticIndex() + 1) * LANGUAGE_ELEMENTS) : "Invalid internal language index range";
        assert ((index - LANGUAGE_START) % LANGUAGE_ELEMENTS) == expectedOffset : "Invalid internal language index offset";
        Object value = data[index];
        assert value == null || expectedType == null || expectedType.isInstance(value) : "Invalid type in internal state.";
        return true;
    }

    static final class CachedReferences {

        final ContextReferenceImpl contextReference;
        final LanguageReferenceImpl languageReference;

        CachedReferences(Class<?> languageClass) {
            this.contextReference = new ContextReferenceImpl(languageClass);
            this.languageReference = new LanguageReferenceImpl(languageClass);
        }
    }

    static final class ContextReferenceImpl extends ContextReference<Object> {

        private final Class<?> languageClass;
        private final int index;

        ContextReferenceImpl(Class<?> languageClass) {
            this.languageClass = languageClass;
            this.index = computeLanguageIndex(languageClass, LANGUAGE_CONTEXT_OFFSET);

        }

        @Override
        public Object get(Node node) {
            assert assertValidGet(index, LANGUAGE_CONTEXT_OFFSET, findContextClass(node, index), languageClass);
            return getLanguageContext(node, index);
        }

        @Override
        public String toString() {
            return "ContextReference[language=" + languageClass + ", index = " + index + "]";
        }
    }

    static final class LanguageReferenceImpl extends LanguageReference<TruffleLanguage<Object>> {

        private final Class<TruffleLanguage<Object>> languageClass;
        private final int index;

        @SuppressWarnings("unchecked")
        LanguageReferenceImpl(Class<?> languageClass) {
            this.languageClass = (Class<TruffleLanguage<Object>>) languageClass;
            this.index = computeLanguageIndex(languageClass, LANGUAGE_SPI_OFFSET);
        }

        @Override
        public TruffleLanguage<Object> get(Node node) {
            assert assertValidGet(index, LANGUAGE_SPI_OFFSET, languageClass, languageClass);
            return getLanguage(node, index, languageClass);
        }

        @Override
        public String toString() {
            return "LanguageReference[language=" + languageClass + ", index = " + index + "]";
        }
    }

}
