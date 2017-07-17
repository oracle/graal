/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractStackFrameImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueImpl;

/**
 * An execution engine for Graal {@linkplain Language guest languages} that allows to inspect the
 * the installed {@link #getLanguages() guest languages}, {@link #getInstruments() instruments} and
 * their available options.
 * <p>
 * By default every context creates its own {@link Engine engine} instance implicitly when
 * {@link Context.Builder#build() instantiated}. Multiple contexts can use an
 * {@link Context.Builder#engine(Engine) explicit engine} when using a context builder. If contexts
 * share the same engine instance then they share instruments and its configuration. Also,
 * {@link Value value} instances can only be exchanged between contexts that are associated with
 * same engine.
 * <p>
 * It can be useful to {@link Engine#create() create} an engine instance without a context to only
 * access meta-data for installed languages, instruments and their available options.
 *
 * @since 1.0
 */
public final class Engine implements AutoCloseable {

    final AbstractEngineImpl impl;

    Engine(AbstractEngineImpl impl) {
        this.impl = impl;
    }

    private static final class ImplHolder {
        private static final AbstractPolyglotImpl IMPL = initEngineImpl();
    }

    /**
     * Gets an installed language by looking up the unique language ID. An example for the language
     * id of JavaScript for example is <code>"js"</code>.
     *
     * @param languageId the unique of the language
     * @throws IllegalArgumentException if an invalid language id was provided
     * @see #getLanguages() To get map of all installed languages.
     * @since 1.0
     * @deprecated use {@link #getLanguage()}.{@link Map#get(Object) get(id)} instead
     */
    @Deprecated
    public Language getLanguage(String languageId) {
        return impl.requirePublicLanguage(languageId);
    }

    /**
     * Gets a map of all installed languages with the language id as key and the language object as
     * value. The returned map is unmodifiable and might be used from multiple threads.
     *
     * @since 1.0
     */
    public Map<String, Language> getLanguages() {
        return impl.getLanguages();
    }

    /**
     * Gets an installed instrument by looking it up using its identifier. Shortcut for
     * <code>engine.getLanguages().get(languageId)</code>.
     * <p>
     * An instrument alters and/or monitors the execution of guest language source code. Common
     * examples for instruments are debuggers, profilers or monitoring tools. Instruments are
     * enabled via {@link Instrument#getOptions() options} passed to the
     * {@link Builder#option(String, String) engine} when the engine or context is constructed.
     *
     * @param instrumentId the unique of the language
     * @throws IllegalArgumentException if an invalid languageId was provided
     * @see #getLanguages() To get map of all installed languages.
     * @since 1.0
     * @deprecated use {@link #getInstruments()}.{@link Map#get(Object) get(id)} instead
     */
    @Deprecated
    public Instrument getInstrument(String instrumentId) {
        return impl.requirePublicInstrument(instrumentId);
    }

    /**
     * Gets all installed instruments of this engine. An instrument alters and/or monitors the
     * execution of guest language source code. Common examples for instruments are debuggers,
     * profilers, or monitoring tools. Instruments are enabled via {@link Instrument#getOptions()
     * options} passed to the {@link Builder#option(String, String) engine} when the engine or
     * context is constructed.
     *
     * @since 1.0
     */
    public Map<String, Instrument> getInstruments() {
        return impl.getInstruments();
    }

    /**
     * Returns all options available for the engine. The engine offers options with the following
     * {@link OptionDescriptor#getKey() groups}:
     * <ul>
     * <li><b>engine</b>: options to configure the behavior of this engine.
     * <li><b>compiler</b>: options to configure the optimizing compiler.
     * </ul>
     * The language and instrument specific options need to be retrieved using
     * {@link Instrument#getOptions()} or {@link Language#getOptions()}.
     *
     * @see Language#getOptions() To get a list of options for a language.
     * @see Instrument#getOptions() To get a list of options for an instrument.
     * @see Builder#option(String, String) To set an option for an engine, language, or instrument.
     * @see Context.Builder#option(String, String) To set an option for a context.
     *
     * @since 1.0
     */
    public OptionDescriptors getOptions() {
        return impl.getOptions();
    }

    /**
     * Gets the version string of the engine in an unspecified format.
     *
     * @since 1.0
     */
    public String getVersion() {
        return impl.getVersion();
    }

    /**
     * Closes this engine and frees up allocated native resources. If there are still open context
     * instances that were created using this engine and they are currently not being executed then
     * they will be closed automatically. If an attempt to close an engine was successful then
     * consecutive calls to close have no effect. If a context is cancelled then the currently
     * executing thread will throw a {@link PolyglotException}. The exception indicates that it was
     * {@link PolyglotException#isCancelled() cancelled}.
     *
     * @param cancelIfExecuting if <code>true</code> then currently executing contexts will be
     *            cancelled, else an {@link IllegalStateException} is thrown.
     * @since 1.0
     */
    public void close(boolean cancelIfExecuting) {
        impl.ensureClosed(cancelIfExecuting, false);
    }

    /**
     * Closes this engine and frees up allocated native resources. If there are still open context
     * instances that were created using this engine and they are currently not being executed then
     * they will be closed automatically. If an attempt to close the engine was successful then
     * consecutive calls to close have no effect.
     *
     * @throws IllegalStateException if there currently executing open context instances.
     * @see #close(boolean)
     * @see Engine#close()
     * @since 1.0
     */
    @Override
    public void close() {
        close(false);
    }

    /**
     * Creates a new engine instance with default configuration. The engine is constructed with the
     * same configuration as it will be as when constructed implicitly using the context builder.
     *
     * @see Context#create(String...) to create a new execution context.
     * @since 1.0
     */
    public static Engine create() {
        return newBuilder().build();
    }

    /**
     * Creates a new context builder that allows to configure an engine instance.
     *
     * @see Context#newBuilder(String...) to construct a new execution context.
     * @since 1.0
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    static AbstractPolyglotImpl getImpl() {
        return ImplHolder.IMPL;
    }

    /*
     * Used internally to load language specific classes.
     */
    static Class<?> loadLanguageClass(String className) {
        return getImpl().loadLanguageClass(className);
    }

    /**
     *
     * @since 1.0
     */
    @SuppressWarnings("hiding")
    public static final class Builder {

        private OutputStream out = System.out;
        private OutputStream err = System.err;
        private InputStream in = System.in;
        private Map<String, String> options = new HashMap<>();
        private boolean useSystemProperties = true;
        private boolean boundEngine;

        /**
         *
         *
         * @since 1.0
         */
        Builder() {
        }

        Builder setBoundEngine(boolean boundEngine) {
            this.boundEngine = boundEngine;
            return this;
        }

        /**
         *
         *
         * @since 1.0
         */
        public Builder out(OutputStream out) {
            Objects.requireNonNull(out);
            this.out = out;
            return this;
        }

        /**
         *
         *
         * @since 1.0
         */
        public Builder err(OutputStream err) {
            Objects.requireNonNull(err);
            this.err = err;
            return this;
        }

        /**
         *
         *
         * @since 1.0
         */
        public Builder in(InputStream in) {
            Objects.requireNonNull(in);
            this.in = in;
            return this;
        }

        /**
         * Specifies whether the engine should use {@link System#getProperty(String) system
         * properties} if no explicit option is {@link #option(String, String) set}. The default
         * value is <code>true</code> indicating that the system properties should be used. System
         * properties are looked up with the prefix <i>"polyglot"</i> in order to disambiguate
         * existing system properties. For example, for the option with the key
         * <code>"js.ECMACompatiblity"</code>, the system property
         * <code>"polyglot.js.ECMACompatiblity"</code> is read. Invalid options specified using
         * system properties will cause the {@link #build() build} method to fail using an
         * {@link IllegalArgumentException}. System properties are read once when the engine is
         * built and are never updated after that.
         *
         * @param enabled if <code>true</code> system properties will be used as options.
         * @see #option(String, String) To specify option values directly.
         * @see #build() To build the engine instance.
         * @since 1.0
         */
        public Builder useSystemProperties(boolean enabled) {
            useSystemProperties = enabled;
            return this;
        }

        /**
         * Sets an option for an {@link Engine#getOptions() engine}, {@link Language#getOptions()
         * language} or {@link Instrument#getOptions() instrument}.
         * <p>
         * If one of the set option keys or values is invalid then an
         * {@link IllegalArgumentException} is thrown when the engine is {@link #build() built}. The
         * given key and value must not be <code>null</code>.
         *
         * @see Engine#getOptions() To list all available options for engines.
         * @see Language#getOptions() To list all available options for a {@link Language language}.
         * @see Instrument#getOptions() To list all available options for an {@link Instrument
         *      instrument}.
         * @since 1.0
         */
        public Builder option(String key, String value) {
            Objects.requireNonNull(key, "Key must not be null.");
            Objects.requireNonNull(value, "Value must not be null.");
            options.put(key, value);
            return this;
        }

        /**
         * Shortcut for setting multiple {@link #option(String, String) options} using a map. All
         * values of the provided map must be non-null.
         *
         * @param options a map options.
         * @see #option(String, String) To set a single option.
         * @since 1.0
         */
        public Builder options(Map<String, String> options) {
            for (String key : options.keySet()) {
                Objects.requireNonNull(options.get(key), "All option values must be non-null.");
            }
            this.options.putAll(options);
            return this;
        }

        /**
         *
         *
         * @since 1.0
         */
        public Engine build() {
            AbstractPolyglotImpl loadedImpl = getImpl();
            if (loadedImpl == null) {
                throw new IllegalStateException("The Polyglot API implementation failed to load.");
            }
            return loadedImpl.buildEngine(out, err, in, options, 0, null,
                            false, 0, useSystemProperties, boundEngine);
        }

    }

    static class APIAccessImpl extends AbstractPolyglotImpl.APIAccess {

        @Override
        public Engine newEngine(AbstractEngineImpl impl) {
            return new Engine(impl);
        }

        @Override
        public Context newContext(AbstractContextImpl impl) {
            return new Context(impl);
        }

        @Override
        public PolyglotException newLanguageException(String message, AbstractExceptionImpl impl) {
            return new PolyglotException(message, impl);
        }

        @Override
        public Language newLanguage(AbstractLanguageImpl impl) {
            return new Language(impl);
        }

        @Override
        public Instrument newInstrument(AbstractInstrumentImpl impl) {
            return new Instrument(impl);
        }

        @Override
        public Value newValue(Object value, AbstractValueImpl impl) {
            return new Value(impl, value);
        }

        @Override
        public Source newSource(String language, Object impl) {
            return new Source(language, impl);
        }

        @Override
        public SourceSection newSourceSection(Source source, Object impl) {
            return new SourceSection(source, impl);
        }

        @Override
        public AbstractValueImpl getImpl(Value value) {
            return value.impl;
        }

        @Override
        public AbstractInstrumentImpl getImpl(Instrument value) {
            return value.impl;
        }

        @Override
        public AbstractLanguageImpl getImpl(Language value) {
            return value.impl;
        }

        @Override
        public AbstractStackFrameImpl getImpl(StackFrame value) {
            return value.impl;
        }

        @Override
        public Object getReceiver(Value value) {
            return value.receiver;
        }

        @Override
        public StackFrame newPolyglotStackTraceElement(AbstractStackFrameImpl impl) {
            return new StackFrame(impl);
        }

    }

    private static final boolean JDK8_OR_EARLIER = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    private static AbstractPolyglotImpl initEngineImpl() {
        return AccessController.doPrivileged(new PrivilegedAction<AbstractPolyglotImpl>() {
            public AbstractPolyglotImpl run() {
                AbstractPolyglotImpl engine = null;
                Class<?> servicesClass = null;

                // TODO remove temporary hack to load polyglot engine impl, until we can load it
                // using the jvmci/truffle service loader

                if (engine == null) {
                    if (JDK8_OR_EARLIER) {
                        try {
                            servicesClass = Class.forName("jdk.vm.ci.services.Services");
                        } catch (ClassNotFoundException e) {
                        }
                        if (servicesClass != null) {
                            try {
                                Method m = servicesClass.getDeclaredMethod("loadSingle", Class.class, boolean.class);
                                engine = (AbstractPolyglotImpl) m.invoke(null, AbstractPolyglotImpl.class, false);
                            } catch (Throwable e) {
                                // Fail fast for other errors
                                throw new InternalError(e);
                            }
                        }
                    } else {
                        // As of JDK9, the JVMCI Services class should only be used for service
                        // types
                        // defined by JVMCI. Other services types should use ServiceLoader directly.
                        Iterator<AbstractPolyglotImpl> providers = ServiceLoader.load(AbstractPolyglotImpl.class).iterator();
                        if (providers.hasNext()) {
                            engine = providers.next();
                            if (providers.hasNext()) {

                                throw new InternalError(String.format("Multiple %s providers found", AbstractPolyglotImpl.class.getName()));
                            }
                        }
                    }
                }

                if (engine == null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Class<? extends AbstractPolyglotImpl> polyglotClass = (Class<? extends AbstractPolyglotImpl>) Class.forName("com.oracle.truffle.api.vm.PolyglotImpl");
                        Constructor<? extends AbstractPolyglotImpl> constructor = polyglotClass.getDeclaredConstructor();
                        constructor.setAccessible(true);
                        engine = constructor.newInstance();
                    } catch (Exception e1) {
                        throw new InternalError(e1);
                    }
                }

                if (engine != null) {
                    engine.setConstructors(new APIAccessImpl());
                }

                return engine;
            }
        });
    }

}
