package com.oracle.truffle.api.vm.contextproto;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;

@SuppressWarnings("all")
public class PolyglotEngine {

    private final ContextStore contextStore;
    private final ContextStoreProfile contextStoreProfile;

    PolyglotEngine() {
        this.contextStore = new ContextStore(Math.max(4, currentLanguageId << 1));
        this.contextStoreProfile = new ContextStoreProfile(contextStore);
    }

    private PolyglotEngine(PolyglotEngine baseEngine) {
        this.contextStore = new ContextStore(Math.max(4, currentLanguageId << 1));

        // we share the contest store lookup for multiple forked engines
        // so all context references are invalidated at the same time
        this.contextStoreProfile = baseEngine.contextStoreProfile;

        // might force invalidation of context lookups
        this.contextStoreProfile.enter(contextStore);
    }

    public Object eval(Source s) {
        contextStoreProfile.enter(contextStore);

        // imagine source valuation here
        return null;
    }

    public PolyglotEngine fork() {
        PolyglotEngine engine = new PolyglotEngine(this);

        return engine;
    }

    // to be used from the language to create context references
    <C> ContextReference<C> createContextReference(TruffleLanguage<C> language) {
        return new ContextReference<>(contextStoreProfile, getLanguageId(language));
    }

    private static int getLanguageId(TruffleLanguage<?> language) {
        Integer value = LANGUAGE_IDS.get(language);
        if (value == null) {
            synchronized (LANGUAGE_IDS) {
                value = LANGUAGE_IDS.get(language);
                if (value == null) {
                    value = currentLanguageId++;
                    LANGUAGE_IDS.put(language, value);
                }
            }
        }
        return value;
    }

    // language ids need to be unique accross the whole system
    private static final Map<TruffleLanguage<?>, Integer> LANGUAGE_IDS = Collections.synchronizedMap(new HashMap<TruffleLanguage<?>, Integer>());
    private static volatile int currentLanguageId;

}
