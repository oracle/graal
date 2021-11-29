/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.StableLocalLocations;

final class PolyglotLocals {

    static <T> ContextLocal<T> createLanguageContextLocal(Object factory) {
        return new LanguageContextLocal<>(factory);
    }

    static <T> ContextLocal<T> createInstrumentContextLocal(Object factory) {
        return new InstrumentContextLocal<>(factory);
    }

    static <T> ContextThreadLocal<T> createLanguageContextThreadLocal(Object factory) {
        return new LanguageContextThreadLocal<>(factory);
    }

    static <T> ContextThreadLocal<T> createInstrumentContextThreadLocal(Object factory) {
        return new InstrumentContextThreadLocal<>(factory);
    }

    static void initializeInstrumentContextLocals(List<InstrumentContextLocal<?>> locals, PolyglotInstrument polyglotInstrument) {
        LocalLocation[] locations;
        if (locals.isEmpty()) {
            locations = PolyglotEngineImpl.EMPTY_LOCATIONS;
        } else {
            for (InstrumentContextLocal<?> local : locals) {
                local.instrument = polyglotInstrument;
            }
            locations = polyglotInstrument.engine.addContextLocals(locals);
        }
        polyglotInstrument.contextLocalLocations = locations;

    }

    static void initializeLanguageContextLocals(List<LanguageContextLocal<?>> locals, PolyglotLanguageInstance polyglotLanguageInstance) {
        LocalLocation[] locations;
        if (locals.isEmpty()) {
            locations = PolyglotEngineImpl.EMPTY_LOCATIONS;
        } else {
            for (LanguageContextLocal<?> local : locals) {
                local.languageInstance = polyglotLanguageInstance;
            }
            locations = polyglotLanguageInstance.language.previousContextLocalLocations;
            if (locations != null) {
                /*
                 * We are reusing local locations as there can be an arbitrary number of language
                 * instances per engine. In order to not let the context local array grow
                 * arbitrarily big, we reuse the locations from the previous language instance. We
                 * therefore require TruffleLanguage instances to always create the same number of
                 * context locals with the exact same types.
                 */
                if (locals.size() != locations.length) {
                    throw new IllegalStateException(String.format("Truffle language %s did not create the same number of context locals. " +
                                    "Expected %s locals but were %s.", polyglotLanguageInstance.spi.getClass().getName(), locations.length, locals.size()));
                }
                for (int i = 0; i < locations.length; i++) {
                    locals.get(i).initializeLocation(locations[i]);
                }
            } else {
                PolyglotLanguage language = polyglotLanguageInstance.language;
                PolyglotEngineImpl engine = language.engine;
                language.previousContextLocalLocations = locations = engine.addContextLocals(locals);
                assert locations.length == locals.size();
            }
        }
        assert polyglotLanguageInstance.contextLocals == null : "current context locals can only be initialized once";
        polyglotLanguageInstance.contextLocals = locals;
        polyglotLanguageInstance.contextLocalLocations = locations;
    }

    static void initializeInstrumentContextThreadLocals(List<InstrumentContextThreadLocal<?>> locals, PolyglotInstrument polyglotInstrument) {
        LocalLocation[] locations;
        if (locals.isEmpty()) {
            locations = PolyglotEngineImpl.EMPTY_LOCATIONS;
        } else {
            for (InstrumentContextThreadLocal<?> local : locals) {
                local.instrument = polyglotInstrument;
            }
            locations = polyglotInstrument.engine.addContextThreadLocals(locals);
        }
        polyglotInstrument.contextThreadLocalLocations = locations;
    }

    static void initializeLanguageContextThreadLocals(List<LanguageContextThreadLocal<?>> locals, PolyglotLanguageInstance polyglotLanguageInstance) {
        LocalLocation[] locations;
        if (locals.isEmpty()) {
            locations = PolyglotEngineImpl.EMPTY_LOCATIONS;
        } else {
            for (LanguageContextThreadLocal<?> local : locals) {
                local.languageInstance = polyglotLanguageInstance;
            }

            locations = polyglotLanguageInstance.language.previousContextThreadLocalLocations;
            if (locations != null) {
                /*
                 * We are reusing local locations as there can be an arbitrary number of language
                 * instances per engine. In order to not let the context local array grow
                 * arbitrarily big, we reuse the locations from the previous language instance. We
                 * therefore require TruffleLanguage instances to always create the same number of
                 * context locals with the exact same types.
                 */
                if (locals.size() != locations.length) {
                    throw new IllegalStateException(String.format("Truffle language %s did not create the same number of context thread locals. " +
                                    "Expected %s locals but were %s.", polyglotLanguageInstance.spi.getClass().getName(), locations.length, locals.size()));
                }
                for (int i = 0; i < locations.length; i++) {
                    locals.get(i).initializeLocation(locations[i]);
                }
            } else {
                PolyglotLanguage language = polyglotLanguageInstance.language;
                PolyglotEngineImpl engine = language.engine;
                language.previousContextThreadLocalLocations = locations = engine.addContextThreadLocals(locals);
                assert locations.length == locals.size();
            }
        }

        assert polyglotLanguageInstance.contextThreadLocals == null : "current context locals can only be initialized once";
        polyglotLanguageInstance.contextThreadLocals = locals;
        polyglotLanguageInstance.contextThreadLocalLocations = locations;
    }

    @TruffleBoundary
    static boolean assertLanguageCreated(PolyglotContextImpl context, PolyglotLanguage language) {
        if (context == null) {
            throw new IllegalStateException("No current context is entered.");
        }
        if (context.localsCleared) {
            throw new IllegalStateException("Locals have already been cleared.");
        }
        if (!context.getContext(language).isCreated()) {
            throw new IllegalStateException(String.format("Language context for language '%s' is not yet created in the context.",
                            language.getId()));
        }
        return true;
    }

    @TruffleBoundary
    static boolean assertInstrumentCreated(PolyglotContextImpl context, PolyglotInstrument instrument) {
        if (context == null) {
            throw new IllegalStateException("No current context is entered.");
        }
        if (context.localsCleared) {
            throw new IllegalStateException("Locals have already been cleared.");
        }
        if (!instrument.isInitialized()) {
            throw new IllegalStateException(String.format("Instrument '%s' is not yet created in the  context.",
                            instrument.getId()));
        }
        return true;
    }

    abstract static class AbstractContextLocal<T> extends ContextLocal<T> {

        @CompilationFinal LocalLocation location;

        protected AbstractContextLocal() {
            super(PolyglotImpl.getInstance());
        }

        final void initializeLocation(LocalLocation l) {
            assert this.location == null;
            this.location = l;
        }

        abstract LocalLocation createLocation(int localIndex);

    }

    static final class InstrumentContextLocal<T> extends AbstractContextLocal<T> {

        private PolyglotInstrument instrument;  // effectively final
        private final Object factory;

        protected InstrumentContextLocal(Object factory) {
            this.factory = factory;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get() {
            assert assertInstrumentCreated(PolyglotFastThreadLocals.getContext(null), instrument);
            PolyglotContextImpl c = PolyglotFastThreadLocals.getContextWithEngine(location.engine);
            return (T) c.getLocal(location);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(TruffleContext context) {
            PolyglotContextImpl c = (PolyglotContextImpl) EngineAccessor.LANGUAGE.getPolyglotContext(context);
            assert assertInstrumentCreated(c, instrument);
            return (T) c.getLocal(location);
        }

        @Override
        LocalLocation createLocation(int localIndex) {
            return new Location(this, localIndex);
        }

        private final class Location extends LocalLocation {

            Location(InstrumentContextLocal<?> local, int index) {
                super(local.instrument.engine, index);
            }

            @Override
            Object invokeFactoryImpl(PolyglotContextImpl context, Thread thread) {
                assert thread == null;
                if (context.engine != instrument.engine) {
                    throw new AssertionError("Invalid sharing of locations.");
                }
                return EngineAccessor.INSTRUMENT.invokeContextLocalFactory(factory, context.creatorTruffleContext);
            }
        }

    }

    static final class LanguageContextLocal<T> extends AbstractContextLocal<T> {

        private final Object factory;

        // effectively final
        private PolyglotLanguageInstance languageInstance;

        protected LanguageContextLocal(Object factory) {
            this.factory = factory;
        }

        @Override
        LocalLocation createLocation(int index) {
            /*
             * It is important to not reference the PolyglotLanguageInstance, but only the
             * PolyglotLanguage in the location to avoid memory leaks for the instance. This is
             * somewhat different to instruments, because there can be an arbitrary number of
             * language instances, but there is at most one instrument per engine.
             */
            return new Location(languageInstance.language, index);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get() {
            LocalLocation l = this.location;
            PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(languageInstance.sharing);
            assert assertLanguageCreated(context, languageInstance.language);
            return (T) context.getLocal(l);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(TruffleContext truffleContext) {
            PolyglotContextImpl context = (PolyglotContextImpl) EngineAccessor.LANGUAGE.getPolyglotContext(truffleContext);
            assert assertLanguageCreated(context, languageInstance.language);
            return (T) context.getLocal(location);
        }

        private static final class Location extends LocalLocation {

            private final PolyglotLanguage language;

            Location(PolyglotLanguage language, int index) {
                super(language.engine, index);
                this.language = language;
            }

            @Override
            Object invokeFactoryImpl(PolyglotContextImpl context, Thread thread) {
                assert thread == null;
                PolyglotLanguageContext languageContext = context.getContext(language);
                List<LanguageContextLocal<?>> locals = languageContext.getLanguageInstance().contextLocals;
                for (LanguageContextLocal<?> local : locals) {
                    if (index == local.location.index) {
                        return EngineAccessor.LANGUAGE.invokeContextLocalFactory(local.factory, context.getContextImpl(language));
                    }
                }
                throw new AssertionError("Local index " + index + " not found in language instance locals.");
            }
        }

    }

    abstract static class AbstractContextThreadLocal<T> extends ContextThreadLocal<T> {

        @CompilationFinal LocalLocation location;

        protected AbstractContextThreadLocal() {
            super(PolyglotImpl.getInstance());
        }

        final void initializeLocation(LocalLocation l) {
            assert this.location == null;
            this.location = l;
        }

        abstract LocalLocation createLocation(int localIndex);

    }

    static final class LanguageContextThreadLocal<T> extends AbstractContextThreadLocal<T> {

        // effectively final
        private PolyglotLanguageInstance languageInstance;

        private final Object factory;

        protected LanguageContextThreadLocal(Object factory) {
            this.factory = factory;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get() {
            assert assertLanguageCreated(PolyglotFastThreadLocals.getContext(null), languageInstance.language);
            return (T) PolyglotFastThreadLocals.getCurrentThread(languageInstance.sharing).getThreadLocal(location);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(Thread t) {
            PolyglotContextImpl c = PolyglotFastThreadLocals.getContext(languageInstance.sharing);
            assert assertLanguageCreated(c, languageInstance.language);
            return (T) c.getThreadLocal(location, t);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(TruffleContext context) {
            PolyglotContextImpl c = (PolyglotContextImpl) EngineAccessor.LANGUAGE.getPolyglotContext(context);
            assert assertLanguageCreated(c, languageInstance.language);
            return (T) c.getThreadLocal(location, Thread.currentThread());
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(TruffleContext context, Thread t) {
            PolyglotContextImpl c = (PolyglotContextImpl) EngineAccessor.LANGUAGE.getPolyglotContext(context);
            assert assertLanguageCreated(c, languageInstance.language);
            return (T) c.getThreadLocal(location, t);
        }

        @Override
        LocalLocation createLocation(int index) {
            /*
             * It is important to not reference the PolyglotLanguageInstance, but only the
             * PolyglotLanguage in the location to avoid memory leaks for the instance. This is
             * somewhat different to instruments, because there can be an arbitrary number of
             * language instances, but there is at most one instrument per engine.
             */
            return new Location(languageInstance.language, index);
        }

        private static final class Location extends LocalLocation {

            private final PolyglotLanguage language;

            Location(PolyglotLanguage language, int index) {
                super(language.engine, index);
                this.language = language;
            }

            @Override
            Object invokeFactoryImpl(PolyglotContextImpl context, Thread thread) {
                PolyglotLanguageContext languageContext = context.getContext(language);
                List<LanguageContextThreadLocal<?>> locals = languageContext.getLanguageInstance().contextThreadLocals;
                for (LanguageContextThreadLocal<?> local : locals) {
                    if (index == local.location.index) {
                        return EngineAccessor.LANGUAGE.invokeContextThreadLocalFactory(local.factory, context.getContextImpl(language), thread);
                    }
                }
                throw new AssertionError("Local index " + index + " not found in language instance locals.");
            }
        }

    }

    static final class InstrumentContextThreadLocal<T> extends AbstractContextThreadLocal<T> {

        private PolyglotInstrument instrument;  // effectively final
        private final Object factory;

        protected InstrumentContextThreadLocal(Object factory) {
            this.factory = factory;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get() {
            assert assertInstrumentCreated(PolyglotFastThreadLocals.getContext(null), instrument);
            return (T) PolyglotFastThreadLocals.getCurrentThreadEngine(location.engine).getThreadLocal(location);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(Thread t) {
            assert assertInstrumentCreated(PolyglotFastThreadLocals.getContext(null), instrument);
            PolyglotContextImpl c = PolyglotFastThreadLocals.getContextWithEngine(location.engine);
            return (T) c.getThreadLocal(location, t);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(TruffleContext context) {
            PolyglotContextImpl c = (PolyglotContextImpl) EngineAccessor.LANGUAGE.getPolyglotContext(context);
            assert assertInstrumentCreated(c, instrument);
            return (T) c.getThreadLocal(location, Thread.currentThread());
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(TruffleContext context, Thread t) {
            PolyglotContextImpl c = (PolyglotContextImpl) EngineAccessor.LANGUAGE.getPolyglotContext(context);
            assert assertInstrumentCreated(c, instrument);
            return (T) c.getThreadLocal(location, t);
        }

        @Override
        LocalLocation createLocation(int localIndex) {
            return new Location(localIndex);
        }

        private final class Location extends LocalLocation {

            Location(int index) {
                super(instrument.engine, index);
            }

            @Override
            Object invokeFactoryImpl(PolyglotContextImpl context, Thread thread) {
                if (context.engine != instrument.engine) {
                    throw new AssertionError("Invalid sharing of locations.");
                }
                return EngineAccessor.INSTRUMENT.invokeContextThreadLocalFactory(factory, context.creatorTruffleContext, thread);
            }
        }

    }

    abstract static class LocalLocation {

        final PolyglotEngineImpl engine;
        final int index;
        @CompilationFinal private volatile Class<?> profiledType;

        private LocalLocation(PolyglotEngineImpl engine, int index) {
            this.engine = engine;
            this.index = index;
        }

        final Object invokeFactory(PolyglotContextImpl context, Thread thread) {
            Object result = invokeFactoryImpl(context, thread);
            Class<?> profileType = this.profiledType;
            assert result != null : "result should already be checked for null";
            if (profileType == null) {
                this.profiledType = result.getClass();
            } else if (profileType != result.getClass()) {
                throw new IllegalStateException(String.format("The return context value type must be stable and exact. Expected %s but got %s for local %s.",
                                profileType, result.getClass(), this));
            }
            return result;
        }

        final Object readLocal(PolyglotContextImpl context, Object[] locals, boolean threadLocal) {
            assert locals != null && index < locals.length && locals[index] != null : invalidLocalMessage(context, locals);
            Object result;
            if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(this)) {
                result = readLocalFast(locals, threadLocal);
            } else {
                result = locals[index];
            }
            assert result.getClass() == profiledType : invalidLocalMessage(context, locals);
            return result;
        }

        private Object readLocalFast(Object[] locals, boolean threadLocal) {
            Object result;
            StableLocalLocations stableLocations = (threadLocal ? this.engine.contextThreadLocalLocations : this.engine.contextLocalLocations);
            LocalLocation[] locations = stableLocations.locations;
            if (!stableLocations.assumption.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                result = locals[index];
            } else {
                result = EngineAccessor.RUNTIME.unsafeCast(EngineAccessor.RUNTIME.castArrayFixedLength(locals, locations.length)[index], profiledType, true, true, true);
            }
            return result;
        }

        abstract Object invokeFactoryImpl(PolyglotContextImpl context, Thread thread);

        private String invalidLocalMessage(PolyglotContextImpl context, Object[] locals) {
            if (locals == null) {
                return "Invalid local state: Locals is null. Current context: " + context.toString();
            } else if (index < 0 || index >= locals.length) {
                return "Invalid local state: Locals index is out of bounds " + index + ". Current context: " + context.toString();
            }

            Object value = locals[index];
            if (value == null) {
                return "Invalid local state: Local is not initialized. Engine closed: " + engine.closed + ". Current context: " + context.toString();
            } else if (locals[index].getClass() != profiledType) {
                return "Invalid local state: Invalid profiled type. Expected " + profiledType.getName() + " but was " + value.getClass().getName();
            }
            return "Invalid local state: Unknown reason. Current context: " + context.toString();
        }

    }

}
