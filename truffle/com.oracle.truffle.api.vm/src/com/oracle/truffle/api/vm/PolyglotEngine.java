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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.debug.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.source.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.logging.*;

/**
 * Gate way into the world of {@link TruffleLanguage Truffle languages}. {@link #createNew()
 * Instantiate} your own portal into the isolated, multi language system with all the registered
 * languages ready for your use. A {@link PolyglotEngine} runs inside of a <em>JVM</em>, there can however
 * be multiple instances (some would say tenants) of {@link PolyglotEngine} running next to each other in a
 * single <em>JVM</em> with a complete mutual isolation. There is 1:N mapping between <em>JVM</em>
 * and {@link PolyglotEngine}.
 * <p>
 * It would not be correct to think of a {@link PolyglotEngine} as a runtime for a single
 * {@link TruffleLanguage Truffle language} (Ruby, Python, R, C, JavaScript, etc.) either.
 * {@link PolyglotEngine} can host as many of Truffle languages as {@link Registration registered on a
 * class path} of your <em>JVM</em> application. {@link PolyglotEngine} orchestrates these languages,
 * manages exchange of objects and calls among them. While it may happen that there is just one
 * activated language inside of a {@link PolyglotEngine}, the greatest strength of {@link PolyglotEngine} is in
 * inter-operability between all Truffle languages. There is 1:N mapping between {@link PolyglotEngine} and
 * {@link TruffleLanguage Truffle language implementations}.
 * <p>
 * Use {@link #createNew()} to create new isolated portal ready for execution of various languages.
 * All the languages in a single portal see each other exported global symbols and can cooperate.
 * Use {@link #createNew()} multiple times to create different, isolated portal environment
 * completely separated from each other.
 * <p>
 * Once instantiated use {@link #eval(java.net.URI)} with a reference to a file or URL or directly
 * pass code snippet into the virtual machine via {@link #eval(java.lang.String, java.lang.String)}.
 * Support for individual languages is initialized on demand - e.g. once a file of certain MIME type
 * is about to be processed, its appropriate engine (if found), is initialized. Once an engine gets
 * initialized, it remains so, until the virtual machine isn't garbage collected.
 * <p>
 * The <code>TruffleVM</code> is single-threaded and tries to enforce that. It records the thread it
 * has been {@link Builder#build() created} by and checks that all subsequent calls are coming from
 * the same thread. There is 1:1 mapping between {@link PolyglotEngine} and a thread that can tell it what
 * to do.
 */
@SuppressWarnings("rawtypes")
public class PolyglotEngine {
    static final Logger LOG = Logger.getLogger(PolyglotEngine.class.getName());
    private static final SPIAccessor SPI = new SPIAccessor();
    private final Thread initThread;
    private final Executor executor;
    private final Map<String, Language> langs;
    private final Reader in;
    private final Writer err;
    private final Writer out;
    private final EventConsumer<?>[] handlers;
    private final Map<String, Object> globals;
    private Debugger debugger;

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
    }

    /**
     * Real constructor used from the builder.
     */
    PolyglotEngine(Executor executor, Map<String, Object> globals, Writer out, Writer err, Reader in, EventConsumer<?>[] handlers) {
        this.executor = executor;
        this.out = out;
        this.err = err;
        this.in = in;
        this.handlers = handlers;
        this.initThread = Thread.currentThread();
        this.globals = new HashMap<>(globals);
        Map<String, Language> map = new HashMap<>();
        for (Map.Entry<String, LanguageCache> en : LanguageCache.languages().entrySet()) {
            map.put(en.getKey(), createLanguage(en));
        }
        this.langs = map;
    }

    /**
     * Creation of new Truffle virtual machine. Use the {@link Builder} methods to configure your
     * virtual machine and then create one using {@link Builder#build()}:
     *
     * <pre>
     * {@link PolyglotEngine} vm = {@link PolyglotEngine}.{@link PolyglotEngine#createNew() createNew()}
     *     .{@link Builder#stdOut(java.io.Writer) stdOut}({@link Writer yourWriter})
     *     .{@link Builder#stdErr(java.io.Writer) stdErr}({@link Writer yourWriter})
     *     .{@link Builder#stdIn(java.io.Reader) stdIn}({@link Reader yourReader})
     *     .{@link Builder#build() build()};
     * </pre>
     *
     * It searches for {@link Registration languages registered} in the system class loader and
     * makes them available for later evaluation via
     * {@link #eval(java.lang.String, java.lang.String)} methods.
     *
     * @return new, isolated virtual machine with pre-registered languages
     */
    public static PolyglotEngine.Builder createNew() {
        // making Builder non-static inner class is a
        // nasty trick to avoid the Builder class to appear
        // in Javadoc next to TruffleVM class
        PolyglotEngine vm = new PolyglotEngine();
        return vm.new Builder();
    }

    /**
     * Builder for a new {@link PolyglotEngine}. Call various configuration methods in a chain and at the
     * end create new {@link PolyglotEngine virtual machine}:
     *
     * <pre>
     * {@link PolyglotEngine} vm = {@link PolyglotEngine}.{@link PolyglotEngine#createNew() createNew()}
     *     .{@link Builder#stdOut(java.io.Writer) stdOut}({@link Writer yourWriter})
     *     .{@link Builder#stdErr(java.io.Writer) stdErr}({@link Writer yourWriter})
     *     .{@link Builder#stdIn(java.io.Reader) stdIn}({@link Reader yourReader})
     *     .{@link Builder#build() build()};
     * </pre>
     */
    public class Builder {
        private Writer out;
        private Writer err;
        private Reader in;
        private final List<EventConsumer<?>> handlers = new ArrayList<>();
        private final Map<String, Object> globals = new HashMap<>();
        private Executor executor;

        Builder() {
        }

        /**
         * Changes the default output for languages running in <em>to be created</em>
         * {@link PolyglotEngine virtual machine}. The default is to use {@link System#out}.
         *
         * @param w the writer to use as output
         * @return instance of this builder
         */
        public Builder stdOut(Writer w) {
            out = w;
            return this;
        }

        /**
         * Changes the error output for languages running in <em>to be created</em> {@link PolyglotEngine
         * virtual machine}. The default is to use {@link System#err}.
         *
         * @param w the writer to use as output
         * @return instance of this builder
         */
        public Builder stdErr(Writer w) {
            err = w;
            return this;
        }

        /**
         * Changes the default input for languages running in <em>to be created</em> {@link PolyglotEngine
         * virtual machine}. The default is to use {@link System#out}.
         *
         * @param r the reader to use as input
         * @return instance of this builder
         */
        public Builder stdIn(Reader r) {
            in = r;
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
         * Adds global named symbol into the configuration of to-be-built {@link PolyglotEngine}. This
         * symbol will be accessible to all languages via {@link Env#importSymbol(java.lang.String)}
         * and will take precedence over {@link TruffleLanguage#findExportedSymbol symbols exported
         * by languages itself}. Repeated use of <code>globalSymbol</code> is possible; later
         * definition of the same name overrides the previous one.
         *
         * @param name name of the symbol to register
         * @param obj value of the object - expected to be primitive wrapper, {@link String} or
         *            <code>TruffleObject</code> for mutual inter-operability
         * @return instance of this builder
         * @see PolyglotEngine#findGlobalSymbol(java.lang.String)
         */
        public Builder globalSymbol(String name, Object obj) {
            globals.put(name, obj);
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
         * Creates the {@link PolyglotEngine Truffle virtual machine}. The configuration is taken from
         * values passed into configuration methods in this class.
         *
         * @return new, isolated virtual machine with pre-registered languages
         */
        @SuppressWarnings("deprecation")
        public PolyglotEngine build() {
            if (out == null) {
                out = new OutputStreamWriter(System.out);
            }
            if (err == null) {
                err = new OutputStreamWriter(System.err);
            }
            if (in == null) {
                in = new InputStreamReader(System.in);
            }
            Executor nonNullExecutor = executor != null ? executor : new Executor() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            };
            return new TruffleVM(nonNullExecutor, globals, out, err, in, handlers.toArray(new EventConsumer[0]));
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
            if (file.getName().endsWith(".c")) {
                mimeType = "text/x-c";
            } else if (file.getName().endsWith(".sl")) {
                mimeType = "application/x-sl";
            } else if (file.getName().endsWith(".R") || file.getName().endsWith(".r")) {
                mimeType = "application/x-r";
            } else {
                mimeType = Files.probeContentType(file.toPath());
            }
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

    private Value eval(final Language l, final Source s) throws IOException {
        final Debugger[] fillIn = {debugger};
        final Object[] result = {null, null};
        final CountDownLatch ready = new CountDownLatch(1);
        final TruffleLanguage[] lang = {null};
        executor.execute(new Runnable() {
            @Override
            public void run() {
                evalImpl(fillIn, lang, s, result, l, ready);
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
    private void evalImpl(Debugger[] fillIn, TruffleLanguage<?>[] fillLang, Source s, Object[] result, Language l, CountDownLatch ready) {
        try (Closeable d = SPI.executionStart(this, fillIn, s)) {
            TruffleLanguage<?> langImpl = l.getImpl(true);
            fillLang[0] = langImpl;
            PolyglotEngine.findDebuggerSupport(langImpl);
            if (debugger == null) {
                debugger = fillIn[0];
            }
            result[0] = SPI.eval(langImpl, s);
        } catch (IOException ex) {
            result[1] = ex;
        } finally {
            ready.countDown();
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
            throw new IllegalStateException("TruffleVM created on " + initThread.getName() + " but used on " + Thread.currentThread().getName());
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
     * {@link PolyglotEngine} functions like {@link PolyglotEngine#findGlobalSymbol(java.lang.String)} and
     * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source)} or value returned by
     * {@link #invoke(java.lang.Object, java.lang.Object...) sbbsequent of execution}. In case the
     * {@link PolyglotEngine} has been initialized for
     * {@link Builder#executor(java.util.concurrent.Executor) asynchronous excution}, the
     * {@link Value} represents a future - e.g. it is returned immediately, leaving the execution
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
            return result[0];
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
            final Debugger[] fillIn = {debugger};
            final CountDownLatch done = new CountDownLatch(1);
            final Object[] res = {null, null};
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeImpl(fillIn, thiz, args, res, done);
                }
            });
            exceptionCheck(res);
            return createValue(language, res, done);
        }

        @SuppressWarnings("try")
        private void invokeImpl(Debugger[] fillIn, Object thiz, Object[] args, Object[] res, CountDownLatch done) {
            try (final Closeable c = SPI.executionStart(PolyglotEngine.this, fillIn, null)) {
                if (debugger == null) {
                    debugger = fillIn[0];
                }
                List<Object> arr = new ArrayList<>();
                if (thiz == null && language != null) {
                    Object global = SPI.languageGlobal(SPI.findLanguage(PolyglotEngine.this, language.getClass()));
                    if (global != null) {
                        arr.add(global);
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
                ready.await();
            } catch (InterruptedException ex) {
                throw (InterruptedIOException) new InterruptedIOException(ex.getMessage()).initCause(ex);
            }
        }
    }

    /**
     * Description of a language registered in {@link PolyglotEngine Truffle virtual machine}. Languages
     * are registered by {@link Registration} annotation which stores necessary information into a
     * descriptor inside of the language's JAR file. When a new {@link PolyglotEngine} is created, it reads
     * all available descriptors and creates {@link Language} objects to represent them. One can
     * obtain a {@link #getName() name} or list of supported {@link #getMimeTypes() MIME types} for
     * each language. The actual language implementation is not initialized until
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
         * Human readable string that identifies the language and version.
         *
         * @return string describing the specific language version
         */
        public String getShortName() {
            return getName() + "(" + getVersion() + ")";
        }

        TruffleLanguage<?> getImpl(boolean create) {
            getEnv(create);
            return info.getImpl(false);
        }

        TruffleLanguage.Env getEnv(boolean create) {
            if (env == null && create) {
                env = SPI.attachEnv(PolyglotEngine.this, info.getImpl(true), out, err, in);
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

    TruffleLanguage<?> findLanguage(Probe probe) {
        Class<? extends TruffleLanguage> languageClazz = SPI.findLanguage(probe);
        for (Map.Entry<String, Language> entrySet : langs.entrySet()) {
            Language languageDescription = entrySet.getValue();
            final TruffleLanguage<?> impl = languageDescription.getImpl(false);
            if (languageClazz.isInstance(impl)) {
                return impl;
            }
        }
        throw new IllegalStateException("Cannot find language " + languageClazz + " among " + langs);
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

    static DebugSupportProvider findDebuggerSupport(TruffleLanguage<?> l) {
        return SPI.getDebugSupport(l);
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
        public Env attachEnv(Object obj, TruffleLanguage<?> language, Writer stdOut, Writer stdErr, Reader stdIn) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            return super.attachEnv(vm, language, stdOut, stdErr, stdIn);
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
        public ToolSupportProvider getToolSupport(TruffleLanguage<?> l) {
            return super.getToolSupport(l);
        }

        @Override
        public DebugSupportProvider getDebugSupport(TruffleLanguage<?> l) {
            return super.getDebugSupport(l);
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
        protected Closeable executionStart(Object obj, Debugger[] fillIn, Source s) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            return super.executionStart(vm, fillIn, s);
        }

        @Override
        protected void dispatchEvent(Object obj, Object event) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            vm.dispatch(event);
        }
    } // end of SPIAccessor
}
