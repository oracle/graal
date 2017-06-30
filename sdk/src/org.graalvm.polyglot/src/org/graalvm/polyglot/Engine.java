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
import java.util.concurrent.TimeUnit;

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
 * Represents an execution engine for polyglot applications. An engine consists of
 * {@link #getLanguages() guest languages} and {@link #getInstruments() instruments}. An engine can
 * be used to {@link #createPolyglotContext() create} {@link Context execution contexts}.
 * <p>
 * An instrument alters and/or monitors the execution of guest language source code. Common examples
 * for instruments are debuggers, profilers or monitoring tools. Instruments are enabled via
 * {@link Instrument#getOptions() options} passed to the {@link Builder#setOption(String, String)
 * engine} when it is constructed.
 *
 * @see Context
 * @see Value
 * @see Language
 * @see Instrument
 *
 * @since 1.0
 */
// TODO document that the current context class loader is captured when the engine is created.
public final class Engine implements AutoCloseable {

    final AbstractEngineImpl impl;

    Engine(AbstractEngineImpl impl) {
        this.impl = impl;
    }

    private static final class ImplHolder {
        private static final AbstractPolyglotImpl IMPL = initEngineImpl();
    }

    /**
     * Returns an installed language by looking it up using its unique id. Shortcut for
     * <code>engine.getLanguages().get(languageId)</code>. Returns <code>null</code> if the language
     * was not found. Examples for language ids are: <code>"js"</code>, <code>"r"</code> or
     * <code>"ruby"</code>. Throws {@link IllegalArgumentException} if an invalid languageId was
     * provided. Use the map returned by {@link #getLanguages()} to find out whether a language is
     * installed.
     *
     * @param languageId the unique of the language
     * @since 1.0
     */
    public Language getLanguage(String languageId) {
        return impl.getLanguage(languageId);
    }

    /**
     * Returns all installed languages mapped using the its unique ids. The returned map is
     * unmodifiable and might be used from multiple threads.
     *
     * @since 1.0
     */
    public Map<String, Language> getLanguages() {
        return impl.getLanguages();
    }

    public Instrument getInstrument(String id) {
        return impl.getInstrument(id);
    }

    public Map<String, Instrument> getInstruments() {
        return impl.getInstruments();
    }

    /**
     * Detects a language for a given source object. Languages implement a way to detect languages
     * based on its file name or its code. Returns <code>null</code> if the language of the given
     * source object could not be detected.
     *
     * @since 1.0
     */
    public Language detectLanguage(Source source) {
        return impl.detectLanguage(source.impl);
    }

    /**
     * Returns all options available for the engine. The engine offers options with the following
     * {@link OptionDescriptor#getGroup() groups}:
     * <ul>
     * <li><b>engine</b>: options to configure the behavior of this engine.
     * <li><b>compiler</b>: options to configure the optimizing compiler.
     * </ul>
     * Language or instrument optionds need to be retrieved using separate methods.
     *
     * @see Language#getOptions() To get a list of options for a language.
     * @see Instrument#getOptions() To get a list of options for an instrument.
     * @see Builder#setOption(String, String) To set an option for an engine, language or
     *      instrument.
     * @see Context.Builder#setOption(String, String) To set an option for a context.
     * @see PolyglotContext.Builder#setOption(String, String) To set an option for a polyglot
     *      context.
     *
     * @since 1.0
     */
    public OptionDescriptors getOptions() {
        return impl.getOptions();
    }

    /**
     * Creates a new <i>polyglot</i> context that allows access to all installed
     * {@link #getLanguages() languages}.
     *
     * @see Context for further information on context instances.
     * @see Language#createContext() to create a context for a single language.
     * @return
     * @since 1.0
     */
    public Context createPolyglotContext() {
        return new Context.Builder(this, null).setPolyglot(true).build();
    }

    public Context.Builder newPolyglotContextBuilder() {
        return new Context.Builder(this, null).setPolyglot(true);
    }

    public String getVersion() {
        return impl.getVersion();
    }

    /**
     * Closes this engine and frees up allocated native resources. If there are still open context
     * instances that were created using this engine and they are currently not beeing executed then
     * all they will be closed automatically. If an an attempt to close the engine was successful
     * then consecutive calls to close have no effect. If a context got cancelled then the cancelled
     * thread will throw a {@link PolyglotException} where the
     * {@link PolyglotException#isCancelled() cancelled} flag is set.
     *
     * @param cancelIfExecuting if <code>true</code> then currently executing contexts will be
     *            cancelled, else an {@link IllegalStateException} is thrown.
     * @since 1.0
     */
    public void close(boolean cancelIfExecuting) {
        impl.ensureClosed(false);
    }

    /**
     * Closes this engine and frees up allocated native resources. If there are still open context
     * instances that were created using this engine and they are currently not beeing executed then
     * all they will be closed automatically. If an an attempt to close the engine was successful
     * then consecutive calls to close have no effect.
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

    public static Engine create() {
        return newBuilder().build();
    }

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

    public static final class Builder {

        private OutputStream out = System.out;
        private OutputStream err = System.err;
        private InputStream in = System.in;
        private Map<String, String> options = new HashMap<>();
        private boolean useSystemProperties = true;

        public Builder setOut(OutputStream out) {
            Objects.requireNonNull(out);
            this.out = out;
            return this;
        }

        public Builder setErr(OutputStream err) {
            Objects.requireNonNull(err);
            this.err = err;
            return this;
        }

        public Builder setIn(InputStream in) {
            Objects.requireNonNull(in);
            this.in = in;
            return this;
        }

        // not implemented yet. planned in the future
        Builder setTimeout(long timeout, TimeUnit unit) {
            Objects.requireNonNull(unit);
            if (timeout <= 0) {
                throw new IllegalArgumentException("Timeout must be greater than zero.");
            }
            return this.setOption("Timeout", Long.toString(unit.toMillis(timeout)));
        }

        // not implemented yet. planned in the future
        Builder setMaximumAllocatedBytes(long bytes) {
            return this.setOption("MaximumAllocatedBytes", Long.toString(bytes));
        }

        /**
         * Specifies whether the engine should use {@link System#getProperty(String) system
         * properties} if no explicit option is {@link #setOption(String, String) set}. The default
         * value is <code>true</code> indicating that the system properties should be used. System
         * properties are looked up with the prefix <i>"polyglot"</i> in order to disambiguate
         * existing system properties. Eg. for the option with the key
         * <code>"js.ECMACompatiblity"</code> the system property
         * <code>"polyglot.js.ECMACompatiblity"</code> is read. Invalid options specified using
         * system properties will cause the {@link #build() build} method to fail using an
         * {@link IllegalArgumentException}. System properties are read once when the engine is
         * built and are never updated after that.
         *
         * @param enabled if <code>true</code> system properties will be used as options.
         * @see #setOption(String, String) To specify option values directly.
         * @see #build() To build the engine instance.
         * @since 1.0
         */
        public Builder setUseSystemProperties(boolean enabled) {
            useSystemProperties = enabled;
            return this;
        }

        // not implemented yet. planned in the future
        Builder setSandbox(@SuppressWarnings("unused") boolean value) {
            return this;
        }

        /**
         * Sets an option for an {@link Engine engine}, {@link Language language} or
         * {@link Instrument instrument}. If one of the set option keys or values is invalid then an
         * {@link IllegalArgumentException} is thrown when the engine is {@link #build() built}. The
         * given key and value must not be <code>null</code>.
         *
         * @see Engine#getOptions() To list all available options.
         * @see Language#getOptions() To list all available options for a {@link Language language}.
         * @see Instrument#getOptions() To list all available options for an {@link Instrument
         *      instrument}.
         * @since 1.0
         */
        public Builder setOption(String key, String value) {
            Objects.requireNonNull(key, "Key must not be null.");
            Objects.requireNonNull(value, "Value must not be null.");
            options.put(key, value);
            return this;
        }

        /**
         * Shortcut for setting multiple {@link #setOption(String, String) options} using a map. All
         * values of the provided map must be non-null.
         *
         * @param options a map options.
         * @see #setOption(String, String) To set a single option.
         * @since 1.0
         */
        public Builder setOptions(Map<String, String> options) {
            for (String key : options.keySet()) {
                Objects.requireNonNull(options.get(key), "All option values must be non-null.");
            }
            this.options.putAll(options);
            return this;
        }

        public Engine build() {
            AbstractPolyglotImpl loadedImpl = getImpl();
            if (loadedImpl == null) {
                throw new IllegalStateException("The Polyglot API implementation failed to load.");
            }
            return loadedImpl.buildEngine(out, err, in, options, 0, null,
                            false, 0, useSystemProperties);
        }

    }

    static class APIAccessImpl extends AbstractPolyglotImpl.APIAccess {

        @Override
        public Engine newEngine(AbstractEngineImpl impl) {
            return new Engine(impl);
        }

        @Override
        public Context newContext(AbstractContextImpl impl, Language languageImpl) {
            return new Context(impl, languageImpl);
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
        public Source newSource(Object impl) {
            return new Source(impl);
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
