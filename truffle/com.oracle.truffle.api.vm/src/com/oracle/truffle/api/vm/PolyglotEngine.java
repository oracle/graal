/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

/**
 * Gate way into the world of {@link TruffleLanguage Truffle languages}. {@link #buildNew()
 * Instantiate} your own portal into the isolated, multi language system with all the registered
 * languages ready for your use. A {@link PolyglotEngine} runs inside of a <em>JVM</em>, there can
 * however be multiple instances (some would say tenants) of {@link PolyglotEngine} running next to
 * each other in a single <em>JVM</em> with a complete mutual isolation. There is 1:N mapping
 * between <em>JVM</em> and {@link PolyglotEngine}.
 * <p>
 * It would not be correct to think of a {@link PolyglotEngine} as a runtime for a single
 * {@link TruffleLanguage Truffle language} (Ruby, Python, R, C, JavaScript, etc.) either.
 * {@link PolyglotEngine} can host as many of Truffle languages as {@link Registration registered on
 * a class path} of your <em>JVM</em> application. {@link PolyglotEngine} orchestrates these
 * languages, manages exchange of objects and calls among them. While it may happen that there is
 * just one activated language inside of a {@link PolyglotEngine}, the greatest strength of
 * {@link PolyglotEngine} is in inter-operability between all Truffle languages. There is 1:N
 * mapping between {@link PolyglotEngine} and {@link TruffleLanguage Truffle language
 * implementations}.
 * <p>
 * Use {@link #buildNew()} to create new isolated portal ready for execution of various languages.
 * All the languages in a single portal see each other exported global symbols and can cooperate.
 * Use {@link #buildNew()} multiple times to create different, isolated portal environment
 * completely separated from each other.
 * <p>
 * Once instantiated use {@link #eval(java.net.URI)} with a reference to a file or URL or directly
 * pass code snippet into the virtual machine via {@link #eval(java.lang.String, java.lang.String)}.
 * Support for individual languages is initialized on demand - e.g. once a file of certain MIME type
 * is about to be processed, its appropriate engine (if found), is initialized. Once an engine gets
 * initialized, it remains so, until the virtual machine isn't garbage collected.
 * <p>
 * The engine is single-threaded and tries to enforce that. It records the thread it has been
 * {@link Builder#build() created} by and checks that all subsequent calls are coming from the same
 * thread. There is 1:1 mapping between {@link PolyglotEngine} and a thread that can tell it what to
 * do.
 */
@SuppressWarnings("rawtypes")
public class PolyglotEngine {
    static final Logger LOG = Logger.getLogger(PolyglotEngine.class.getName());
    private static final SPIAccessor SPI = new SPIAccessor();
    private final Thread initThread;
    private final Executor executor;
    private final Map<String, Language> langs;
    private final InputStream in;
    private final OutputStream err;
    private final OutputStream out;
    private final EventConsumer<?>[] handlers;
    private final Map<String, Object> globals;
    private final Instrumenter instrumenter;
    private final Debugger debugger;
    private boolean disposed;

    /**
     * Private & temporary only constructor.
     */
    PolyglotEngine() {
        this.initThread = null;
        this.in = null;
        this.err = null;
        this.out = null;
        this.langs = null;
        this.handlers = null;
        this.globals = null;
        this.executor = null;
        this.instrumenter = null;
        this.debugger = null;
    }

    /**
     * Real constructor used from the builder.
     */
    PolyglotEngine(Executor executor, Map<String, Object> globals, OutputStream out, OutputStream err, InputStream in, EventConsumer<?>[] handlers) {
        this.executor = executor;
        this.out = out;
        this.err = err;
        this.in = in;
        this.handlers = handlers;
        this.initThread = Thread.currentThread();
        this.globals = new HashMap<>(globals);
        this.instrumenter = SPI.createInstrumenter(this);
        this.debugger = SPI.createDebugger(this, this.instrumenter);
        Map<String, Language> map = new HashMap<>();
        /* We want to create a language instance but per LanguageCache and not per mime type. */
        Set<LanguageCache> uniqueCaches = new HashSet<>(LanguageCache.languages().values());
        for (LanguageCache languageCache : uniqueCaches) {
            Language newLanguage = new Language(languageCache);
            for (String mimeType : newLanguage.getMimeTypes()) {
                map.put(mimeType, newLanguage);
            }
        }
        this.langs = map;
    }

    /**
     * Creation of new Truffle virtual machine. Use the {@link Builder} methods to configure your
     * virtual machine and then create one using {@link Builder#build()}:
     *
     * <pre>
     * {@link PolyglotEngine} vm = {@link PolyglotEngine}.{@link PolyglotEngine#buildNew() buildNew()}
     *     .{@link Builder#setOut(java.io.OutputStream) setOut}({@link OutputStream yourOutput})
     *     .{@link Builder#setErr(java.io.OutputStream) setErr}({@link OutputStream yourOutput})
     *     .{@link Builder#setIn(java.io.InputStream) setIn}({@link InputStream yourInput})
     *     .{@link Builder#build() build()};
     * </pre>
     *
     * It searches for {@link Registration languages registered} in the system class loader and
     * makes them available for later evaluation via
     * {@link #eval(java.lang.String, java.lang.String)} methods.
     *
     * @return new, isolated virtual machine with pre-registered languages
     */
    public static PolyglotEngine.Builder buildNew() {
        // making Builder non-static inner class is a
        // nasty trick to avoid the Builder class to appear
        // in Javadoc next to PolyglotEngine class
        PolyglotEngine vm = new PolyglotEngine();
        return vm.new Builder();
    }

    /**
     * Builder for a new {@link PolyglotEngine}. Call various configuration methods in a chain and
     * at the end create new {@link PolyglotEngine virtual machine}:
     *
     * <pre>
     * {@link PolyglotEngine} vm = {@link PolyglotEngine}.{@link PolyglotEngine#buildNew() buildNew()}
     *     .{@link Builder#setOut(java.io.OutputStream) setOut}({@link OutputStream yourOutput})
     *     .{@link Builder#setErr(java.io.OutputStream) setErr}({@link OutputStream yourOutput})
     *     .{@link Builder#setIn(java.io.InputStream) setIn}({@link InputStream yourInput})
     *     .{@link Builder#build() build()};
     * </pre>
     */
    public class Builder {
        private OutputStream out;
        private OutputStream err;
        private InputStream in;
        private final List<EventConsumer<?>> handlers = new ArrayList<>();
        private final Map<String, Object> globals = new HashMap<>();
        private Executor executor;

        Builder() {
        }

        /**
         * Changes the default output for languages running in <em>to be created</em>
         * {@link PolyglotEngine virtual machine}. The default is to use {@link System#out}.
         *
         * @param os the stream to use as output
         * @return instance of this builder
         */
        public Builder setOut(OutputStream os) {
            out = os;
            return this;
        }

        /**
         * @deprecated does nothing
         */
        @Deprecated
        @SuppressWarnings("unused")
        public Builder stdOut(Writer w) {
            return this;
        }

        /**
         * Changes the error output for languages running in <em>to be created</em>
         * {@link PolyglotEngine virtual machine}. The default is to use {@link System#err}.
         *
         * @param os the stream to use as output
         * @return instance of this builder
         */
        public Builder setErr(OutputStream os) {
            err = os;
            return this;
        }

        /**
         * @deprecated does nothing
         */
        @Deprecated
        @SuppressWarnings("unused")
        public Builder stdErr(Writer w) {
            return this;
        }

        /**
         * Changes the default input for languages running in <em>to be created</em>
         * {@link PolyglotEngine virtual machine}. The default is to use {@link System#in}.
         *
         * @param is the stream to use as input
         * @return instance of this builder
         */
        public Builder setIn(InputStream is) {
            in = is;
            return this;
        }

        /**
         * @deprecated does nothing
         */
        @Deprecated
        @SuppressWarnings("unused")
        public Builder stdIn(Reader r) {
            return this;
        }

        /**
         * Registers another instance of {@link EventConsumer} into the to be created
         * {@link PolyglotEngine}.
         *
         * @param handler the handler to register
         * @return instance of this builder
         */
        public Builder onEvent(EventConsumer<?> handler) {
            handler.getClass();
            handlers.add(handler);
            return this;
        }

        /**
         * Adds global named symbol into the configuration of to-be-built {@link PolyglotEngine}.
         * This symbol will be accessible to all languages via
         * {@link Env#importSymbol(java.lang.String)} and will take precedence over
         * {@link TruffleLanguage#findExportedSymbol symbols exported by languages itself}. Repeated
         * use of <code>globalSymbol</code> is possible; later definition of the same name overrides
         * the previous one.
         *
         * @param name name of the symbol to register
         * @param obj value of the object - expected to be primitive wrapper, {@link String} or
         *            <code>TruffleObject</code> for mutual inter-operability
         * @return instance of this builder
         * @see PolyglotEngine#findGlobalSymbol(java.lang.String)
         */
        public Builder globalSymbol(String name, Object obj) {
            final Object truffleReady;
            if (obj instanceof Number || obj instanceof String || obj instanceof Character || obj instanceof Boolean) {
                truffleReady = obj;
            } else {
                truffleReady = JavaInterop.asTruffleObject(obj);
            }
            globals.put(name, truffleReady);
            return this;
        }

        /**
         * Provides own executor for running {@link PolyglotEngine} scripts. By default
         * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source)} and
         * {@link Value#invoke(java.lang.Object, java.lang.Object[])} are executed synchronously in
         * the calling thread. Sometimes, however it is more beneficial to run them asynchronously -
         * the easiest way to do so is to provide own executor when configuring the {
         * {@link #executor(java.util.concurrent.Executor) the builder}. The executor is expected to
         * execute all {@link Runnable runnables} passed into its
         * {@link Executor#execute(java.lang.Runnable)} method in the order they arrive and in a
         * single (yet arbitrary) thread.
         *
         * @param executor the executor to use for internal execution inside the {@link #build() to
         *            be created} {@link PolyglotEngine}
         * @return instance of this builder
         */
        @SuppressWarnings("hiding")
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Creates the {@link PolyglotEngine Truffle virtual machine}. The configuration is taken
         * from values passed into configuration methods in this class.
         *
         * @return new, isolated virtual machine with pre-registered languages
         */
        public PolyglotEngine build() {
            if (out == null) {
                out = System.out;
            }
            if (err == null) {
                err = System.err;
            }
            if (in == null) {
                in = System.in;
            }
            Executor nonNullExecutor = executor != null ? executor : new Executor() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            };
            return new PolyglotEngine(nonNullExecutor, globals, out, err, in, handlers.toArray(new EventConsumer[0]));
        }
    }

    /**
     * Descriptions of languages supported in this Truffle virtual machine.
     *
     * @return an immutable map with keys being MIME types and values the {@link Language
     *         descriptions} of associated languages
     */
    public Map<String, ? extends Language> getLanguages() {
        return Collections.unmodifiableMap(langs);
    }

    /**
     * Evaluates file located on a given URL. Is equivalent to loading the content of a file and
     * executing it via {@link #eval(java.lang.String, java.lang.String)} with a MIME type guess
     * based on the file's extension and/or content.
     *
     * @param location the location of a file to execute
     * @return result of a processing the file, possibly <code>null</code>
     * @throws IOException exception to signal I/O problems or problems with processing the file's
     *             content
     * @deprecated use {@link #eval(com.oracle.truffle.api.source.Source)}
     */
    @Deprecated
    public Object eval(URI location) throws IOException {
        checkThread();
        Source s;
        String mimeType;
        if (location.getScheme().equals("file")) {
            File file = new File(location);
            s = Source.fromFileName(file.getPath(), true);
            mimeType = Files.probeContentType(file.toPath());
        } else {
            URL url = location.toURL();
            s = Source.fromURL(url, location.toString());
            URLConnection conn = url.openConnection();
            mimeType = conn.getContentType();
        }
        Language l = langs.get(mimeType);
        if (l == null) {
            throw new IOException("No language for " + location + " with MIME type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return eval(l, s).get();
    }

    /**
     * Evaluates code snippet. Chooses a language registered for a given MIME type (throws
     * {@link IOException} if there is none). And passes the specified code to it for execution.
     *
     * @param mimeType MIME type of the code snippet - chooses the right language
     * @param reader the source of code snippet to execute
     * @return result of an execution, possibly <code>null</code>
     * @throws IOException thrown to signal errors while processing the code
     * @deprecated use {@link #eval(com.oracle.truffle.api.source.Source)}
     */
    @Deprecated
    public Object eval(String mimeType, Reader reader) throws IOException {
        checkThread();
        Language l = langs.get(mimeType);
        if (l == null) {
            throw new IOException("No language for MIME type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return eval(l, Source.fromReader(reader, mimeType)).get();
    }

    /**
     * Evaluates code snippet. Chooses a language registered for a given MIME type (throws
     * {@link IOException} if there is none). And passes the specified code to it for execution.
     *
     * @param mimeType MIME type of the code snippet - chooses the right language
     * @param code the code snippet to execute
     * @return result of an execution, possibly <code>null</code>
     * @throws IOException thrown to signal errors while processing the code
     * @deprecated use {@link #eval(com.oracle.truffle.api.source.Source)}
     */
    @Deprecated
    public Object eval(String mimeType, String code) throws IOException {
        checkThread();
        Language l = langs.get(mimeType);
        if (l == null) {
            throw new IOException("No language for MIME type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return eval(l, Source.fromText(code, mimeType)).get();
    }

    /**
     * Evaluates provided source. Chooses language registered for a particular
     * {@link Source#getMimeType() MIME type} (throws {@link IOException} if there is none). The
     * language is then allowed to parse and execute the source.
     *
     * @param source code snippet to execute
     * @return a {@link Value} object that holds result of an execution, never <code>null</code>
     * @throws IOException thrown to signal errors while processing the code
     */
    public Value eval(Source source) throws IOException {
        String mimeType = source.getMimeType();
        checkThread();
        Language l = langs.get(mimeType);
        if (l == null) {
            throw new IOException("No language for MIME type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return eval(l, source);
    }

    /**
     * Dispose instance of this engine. A user can explicitly
     * {@link TruffleLanguage#disposeContext(java.lang.Object) dispose all resources} allocated by
     * the languages active in this engine, when it is known the system is not going to be used in
     * the future.
     * <p>
     * Calling any other method of this class after the dispose has been done yields an
     * {@link IllegalStateException}.
     */
    public void dispose() {
        checkThread();
        disposed = true;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (Language language : getLanguages().values()) {
                    TruffleLanguage<?> impl = language.getImpl(false);
                    if (impl != null) {
                        try {
                            SPI.dispose(impl, language.getEnv(true));
                        } catch (Exception | Error ex) {
                            LOG.log(Level.SEVERE, "Error disposing " + impl, ex);
                        }
                    }
                }
            }
        });
    }

    private Value eval(final Language l, final Source s) throws IOException {
        final Object[] result = {null, null};
        final CountDownLatch ready = new CountDownLatch(1);
        final TruffleLanguage[] lang = {null};
        executor.execute(new Runnable() {
            @Override
            public void run() {
                evalImpl(lang, s, result, l, ready);
            }
        });
        exceptionCheck(result);
        return createValue(lang[0], result, ready);
    }

    Value createValue(TruffleLanguage lang, Object[] result, CountDownLatch ready) {
        return new Value(lang, result, ready);
    }

    Language createLanguage(Map.Entry<String, LanguageCache> en) {
        return new Language(en.getValue());
    }

    @SuppressWarnings("try")
    private void evalImpl(TruffleLanguage<?>[] fillLang, Source s, Object[] result, Language l, CountDownLatch ready) {
        try (Closeable d = SPI.executionStart(this, -1, debugger, s)) {
            TruffleLanguage<?> langImpl = l.getImpl(true);
            fillLang[0] = langImpl;
            result[0] = SPI.eval(langImpl, s);
        } catch (IOException ex) {
            result[1] = ex;
        } finally {
            ready.countDown();
        }
    }

    @SuppressWarnings("try")
    final Object invokeForeign(final Node foreignNode, final VirtualFrame frame, final TruffleObject receiver) throws IOException {
        final Object[] res = {null, null};
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try (final Closeable c = SPI.executionStart(PolyglotEngine.this, -1, debugger, null)) {
                    res[0] = ForeignAccess.execute(foreignNode, frame, receiver, ForeignAccess.getArguments(frame).toArray());
                } catch (IOException ex) {
                    res[1] = ex;
                } catch (Throwable ex) {
                    res[1] = ex;
                }
            }
        });
        exceptionCheck(res);
        if (res[0] instanceof TruffleObject) {
            return new EngineTruffleObject(this, (TruffleObject) res[0]);
        } else {
            return res[0];
        }
    }

    /**
     * Looks global symbol provided by one of initialized languages up. First of all execute your
     * program via one of your {@link #eval(java.lang.String, java.lang.String)} and then look
     * expected symbol up using this method.
     * <p>
     * The names of the symbols are language dependent, but for example the Java language bindings
     * follow the specification for method references:
     * <ul>
     * <li>"java.lang.Exception::new" is a reference to constructor of {@link Exception}
     * <li>"java.lang.Integer::valueOf" is a reference to static method in {@link Integer} class
     * </ul>
     * Once an symbol is obtained, it remembers values for fast access and is ready for being
     * invoked.
     *
     * @param globalName the name of the symbol to find
     * @return found symbol or <code>null</code> if it has not been found
     */
    public Value findGlobalSymbol(final String globalName) {
        checkThread();
        final TruffleLanguage<?>[] lang = {null};
        final Object[] obj = {globals.get(globalName), null};
        final CountDownLatch ready = new CountDownLatch(1);
        if (obj[0] == null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    findGlobalSymbolImpl(obj, globalName, lang, ready);
                }
            });
            try {
                ready.await();
            } catch (InterruptedException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        } else {
            ready.countDown();
        }
        return obj[0] == null ? null : createValue(lang[0], obj, ready);
    }

    private void findGlobalSymbolImpl(Object[] obj, String globalName, TruffleLanguage<?>[] lang, CountDownLatch ready) {
        if (obj[0] == null) {
            for (Language dl : langs.values()) {
                TruffleLanguage.Env env = dl.getEnv(false);
                if (env == null) {
                    continue;
                }
                obj[0] = SPI.findExportedSymbol(env, globalName, true);
                if (obj[0] != null) {
                    lang[0] = dl.getImpl(true);
                    break;
                }
            }
        }
        if (obj[0] == null) {
            for (Language dl : langs.values()) {
                TruffleLanguage.Env env = dl.getEnv(false);
                if (env == null) {
                    continue;
                }
                obj[0] = SPI.findExportedSymbol(env, globalName, true);
                if (obj[0] != null) {
                    lang[0] = dl.getImpl(true);
                    break;
                }
            }
        }
        ready.countDown();
    }

    private void checkThread() {
        if (initThread != Thread.currentThread()) {
            throw new IllegalStateException("PolyglotEngine created on " + initThread.getName() + " but used on " + Thread.currentThread().getName());
        }
        if (disposed) {
            throw new IllegalStateException("Engine has already been disposed");
        }
    }

    @SuppressWarnings("unchecked")
    void dispatch(Object ev) {
        Class type = ev.getClass();
        if (type == SuspendedEvent.class) {
            dispatchSuspendedEvent((SuspendedEvent) ev);
        }
        if (type == ExecutionEvent.class) {
            dispatchExecutionEvent((ExecutionEvent) ev);
        }
        dispatch(type, ev);
    }

    @SuppressWarnings("unused")
    void dispatchSuspendedEvent(SuspendedEvent event) {
    }

    @SuppressWarnings("unused")
    void dispatchExecutionEvent(ExecutionEvent event) {
    }

    @SuppressWarnings("unchecked")
    <Event> void dispatch(Class<Event> type, Event event) {
        for (EventConsumer handler : handlers) {
            if (handler.type == type) {
                handler.on(event);
            }
        }
    }

    static void exceptionCheck(Object[] result) throws RuntimeException, IOException {
        if (result[1] instanceof IOException) {
            throw (IOException) result[1];
        }
        if (result[1] instanceof RuntimeException) {
            throw (RuntimeException) result[1];
        }
    }

    /**
     * A future value wrapper. A user level wrapper around values returned by evaluation of various
     * {@link PolyglotEngine} functions like
     * {@link PolyglotEngine#findGlobalSymbol(java.lang.String)} and
     * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source)} or a value returned by
     * {@link #invoke(java.lang.Object, java.lang.Object...) a subsequent execution}. In case the
     * {@link PolyglotEngine} has been initialized for
     * {@link Builder#executor(java.util.concurrent.Executor) asynchronous execution}, the
     * {@link Value} represents a future - i.e., it is returned immediately, leaving the execution
     * running on behind.
     */
    public class Value {
        private final TruffleLanguage<?> language;
        private final Object[] result;
        private final CountDownLatch ready;
        private CallTarget target;

        Value(TruffleLanguage<?> language, Object[] result, CountDownLatch ready) {
            this.language = language;
            this.result = result;
            this.ready = ready;
        }

        /**
         * Obtains the object represented by this symbol. The <em>raw</em> object can either be a
         * wrapper about primitive type (e.g. {@link Number}, {@link String}, {@link Character},
         * {@link Boolean}) or a <em>TruffleObject</em> representing more complex object from a
         * language. The method can return <code>null</code>.
         *
         * @return the object or <code>null</code>
         * @throws IOException in case it is not possible to obtain the value of the object
         */
        public Object get() throws IOException {
            waitForSymbol();
            exceptionCheck(result);
            if (result[0] instanceof TruffleObject) {
                return new EngineTruffleObject(PolyglotEngine.this, (TruffleObject) result[0]);
            } else {
                return result[0];
            }
        }

        /**
         * Obtains Java view of the object represented by this symbol. The method basically
         * delegates to
         * {@link JavaInterop#asJavaObject(java.lang.Class, com.oracle.truffle.api.interop.TruffleObject)}
         * just handles primitive types as well.
         *
         * @param <T> the type of the view one wants to obtain
         * @param representation the class of the view interface (it has to be an interface)
         * @return instance of the view wrapping the object of this symbol
         * @throws IOException in case it is not possible to obtain the value of the object
         * @throws ClassCastException if the value cannot be converted to desired view
         */
        public <T> T as(Class<T> representation) throws IOException {
            Object obj = get();
            if (obj instanceof EngineTruffleObject) {
                EngineTruffleObject eto = (EngineTruffleObject) obj;
                if (representation.isInstance(eto.getDelegate())) {
                    return representation.cast(eto.getDelegate());
                }
            }
            if (representation.isInstance(obj)) {
                return representation.cast(obj);
            }
            return JavaInterop.asJavaObject(representation, (TruffleObject) obj);
        }

        /**
         * Invokes the symbol. If the symbol represents a function, then it should be invoked with
         * provided arguments. If the symbol represents a field, then first argument (if provided)
         * should set the value to the field; the return value should be the actual value of the
         * field when the <code>invoke</code> method returns.
         *
         * @param thiz this/self in language that support such concept; use <code>null</code> to let
         *            the language use default this/self or ignore the value
         * @param args arguments to pass when invoking the symbol
         * @return symbol wrapper around the value returned by invoking the symbol, never
         *         <code>null</code>
         * @throws IOException signals problem during execution
         */
        public Value invoke(final Object thiz, final Object... args) throws IOException {
            get();
            final CountDownLatch done = new CountDownLatch(1);
            final Object[] res = {null, null};
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeImpl(thiz, args, res, done);
                }
            });
            exceptionCheck(res);
            return createValue(language, res, done);
        }

        @SuppressWarnings("try")
        private void invokeImpl(Object thiz, Object[] args, Object[] res, CountDownLatch done) {
            try (final Closeable c = SPI.executionStart(PolyglotEngine.this, -1, debugger, null)) {
                List<Object> arr = new ArrayList<>();
                if (thiz == null) {
                    if (language != null) {
                        Object global = SPI.languageGlobal(SPI.findLanguage(PolyglotEngine.this, language.getClass()));
                        if (global != null) {
                            arr.add(global);
                        }
                    }
                } else {
                    arr.add(thiz);
                }
                arr.addAll(Arrays.asList(args));
                for (;;) {
                    try {
                        if (target == null) {
                            target = SymbolInvokerImpl.createCallTarget(language, result[0], arr.toArray());
                        }
                        res[0] = target.call(arr.toArray());
                        break;
                    } catch (ArgumentsMishmashException ex) {
                        target = null;
                    }
                }
            } catch (IOException ex) {
                res[1] = ex;
            } catch (RuntimeException ex) {
                res[1] = ex;
            } finally {
                done.countDown();
            }
        }

        private void waitForSymbol() throws InterruptedIOException {
            checkThread();
            try {
                if (ready != null) {
                    ready.await();
                }
            } catch (InterruptedException ex) {
                throw (InterruptedIOException) new InterruptedIOException(ex.getMessage()).initCause(ex);
            }
        }
    }

    /**
     * Description of a language registered in {@link PolyglotEngine Truffle virtual machine}.
     * Languages are registered by {@link Registration} annotation which stores necessary
     * information into a descriptor inside of the language's JAR file. When a new
     * {@link PolyglotEngine} is created, it reads all available descriptors and creates
     * {@link Language} objects to represent them. One can obtain a {@link #getName() name} or list
     * of supported {@link #getMimeTypes() MIME types} for each language. The actual language
     * implementation is not initialized until
     * {@link PolyglotEngine#eval(java.lang.String, java.lang.String) a code is evaluated} in it.
     */
    public class Language {
        private final LanguageCache info;
        private TruffleLanguage.Env env;

        Language(LanguageCache info) {
            this.info = info;
        }

        /**
         * MIME types recognized by the language.
         *
         * @return returns immutable set of recognized MIME types
         */
        public Set<String> getMimeTypes() {
            return info.getMimeTypes();
        }

        /**
         * Human readable name of the language. Think of C, Ruby, JS, etc.
         *
         * @return string giving the language a name
         */
        public String getName() {
            return info.getName();
        }

        /**
         * Name of the language version.
         *
         * @return string specifying the language version
         */
        public String getVersion() {
            return info.getVersion();
        }

        /**
         * Evaluates provided source. Ignores the particular {@link Source#getMimeType() MIME type}
         * and forces evaluation in the context of <code>this</code> language.
         * 
         * @param source code snippet to execute
         * @return a {@link Value} object that holds result of an execution, never <code>null</code>
         * @throws IOException thrown to signal errors while processing the code
         */
        public Value eval(Source source) throws IOException {
            checkThread();
            return PolyglotEngine.this.eval(this, source);
        }

        /**
         * Returns value representing global object of the language.
         * <p>
         * The object is expected to be <code>TruffleObject</code> (e.g. a native object from the
         * other language) but technically it can be one of Java primitive wrappers ({@link Integer}, {@link Double}, {@link Short}, etc.).
         *
         * @return the global object or <code>null</code> if the language does not support such
         *         concept
         */
        public Value getGlobalObject() {
            checkThread();

            Object[] res = {SPI.languageGlobal(getEnv(true)), null};
            return res[0] == null ? null : new Value(info.getImpl(true), res, null);
        }

        /**
         * @deprecated concatenate {@link #getName()} and {@link #getVersion()} the way you want.
         */
        @Deprecated
        public String getShortName() {
            return getName() + "(" + getVersion() + ")";
        }

        TruffleLanguage<?> getImpl(boolean create) {
            getEnv(create);
            TruffleLanguage<?> impl = info.getImpl(false);
            return impl;
        }

        TruffleLanguage.Env getEnv(boolean create) {
            if (env == null && create) {
                env = SPI.attachEnv(PolyglotEngine.this, info.getImpl(true), out, err, in, instrumenter);
            }
            return env;
        }

        @Override
        public String toString() {
            return "[" + getShortName() + " for " + getMimeTypes() + "]";
        }
    } // end of Language

    //
    // Accessor helper methods
    //

    TruffleLanguage<?> findLanguage(Class<? extends TruffleLanguage> languageClazz) {
        for (Map.Entry<String, Language> entrySet : langs.entrySet()) {
            Language languageDescription = entrySet.getValue();
            final TruffleLanguage<?> impl = languageDescription.getImpl(false);
            if (languageClazz.isInstance(impl)) {
                return impl;
            }
        }
        throw new IllegalStateException("Cannot find language " + languageClazz + " among " + langs);
    }

    TruffleLanguage<?> findLanguage(Probe probe) {
        return findLanguage(SPI.findLanguage(probe));
    }

    Env findEnv(Class<? extends TruffleLanguage> languageClazz) {
        for (Map.Entry<String, Language> entrySet : langs.entrySet()) {
            Language languageDescription = entrySet.getValue();
            Env env = languageDescription.getEnv(false);
            if (env != null && languageClazz.isInstance(languageDescription.getImpl(false))) {
                return env;
            }
        }
        throw new IllegalStateException("Cannot find language " + languageClazz + " among " + langs);
    }

    private static class SPIAccessor extends Accessor {
        @Override
        public Object importSymbol(Object vmObj, TruffleLanguage<?> ownLang, String globalName) {
            PolyglotEngine vm = (PolyglotEngine) vmObj;
            Object g = vm.globals.get(globalName);
            if (g != null) {
                return g;
            }
            Set<Language> uniqueLang = new LinkedHashSet<>(vm.langs.values());
            for (Language dl : uniqueLang) {
                TruffleLanguage<?> l = dl.getImpl(false);
                TruffleLanguage.Env env = dl.getEnv(false);
                if (l == ownLang || l == null || env == null) {
                    continue;
                }
                Object obj = SPI.findExportedSymbol(env, globalName, true);
                if (obj != null) {
                    return obj;
                }
            }
            for (Language dl : uniqueLang) {
                TruffleLanguage<?> l = dl.getImpl(false);
                TruffleLanguage.Env env = dl.getEnv(false);
                if (l == ownLang || l == null || env == null) {
                    continue;
                }
                Object obj = SPI.findExportedSymbol(env, globalName, false);
                if (obj != null) {
                    return obj;
                }
            }
            return null;
        }

        @Override
        protected Env attachEnv(Object obj, TruffleLanguage<?> language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Instrumenter instrumenter) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            return super.attachEnv(vm, language, stdOut, stdErr, stdIn, instrumenter);
        }

        @Override
        public Object eval(TruffleLanguage<?> l, Source s) throws IOException {
            return super.eval(l, s);
        }

        @Override
        public Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit) {
            return super.findExportedSymbol(env, globalName, onlyExplicit);
        }

        @Override
        protected Object languageGlobal(TruffleLanguage.Env env) {
            return super.languageGlobal(env);
        }

        @Override
        protected Instrumenter createInstrumenter(Object vm) {
            return super.createInstrumenter(vm);
        }

        @Override
        protected Debugger createDebugger(Object vm, Instrumenter instrumenter) {
            return super.createDebugger(vm, instrumenter);
        }

        @Override
        protected Instrumenter getInstrumenter(Object obj) {
            final PolyglotEngine vm = (PolyglotEngine) obj;
            return vm.instrumenter;
        }

        @Override
        protected Class<? extends TruffleLanguage> findLanguage(Probe probe) {
            return super.findLanguage(probe);
        }

        @Override
        protected Env findLanguage(Object obj, Class<? extends TruffleLanguage> languageClass) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            return vm.findEnv(languageClass);
        }

        @Override
        protected TruffleLanguage<?> findLanguageImpl(Object obj, Class<? extends TruffleLanguage> languageClazz) {
            final PolyglotEngine vm = (PolyglotEngine) obj;
            return vm.findLanguage(languageClazz);
        }

        @Override
        protected Closeable executionStart(Object obj, int currentDepth, Debugger debugger, Source s) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            return super.executionStart(vm, -1, debugger, s);
        }

        @Override
        protected void dispatchEvent(Object obj, Object event) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            vm.dispatch(event);
        }

        @Override
        protected void dispose(TruffleLanguage<?> impl, TruffleLanguage.Env env) {
            super.dispose(impl, env);
        }
    } // end of SPIAccessor
}
