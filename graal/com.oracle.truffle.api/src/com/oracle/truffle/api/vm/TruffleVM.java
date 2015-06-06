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

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.source.*;

/**
 * Virtual machine for Truffle based languages. Use {@link #newVM()} to create new isolated virtual
 * machine ready for execution of various languages. All the languages in a single virtual machine
 * see each other exported global symbols and can cooperate. Use {@link #newVM()} multiple times to
 * create different, isolated virtual machines completely separated from each other.
 * <p>
 * Once instantiated use {@link #eval(java.net.URI)} with a reference to a file or URL or directly
 * pass code snippet into the virtual machine via {@link #eval(java.lang.String, java.lang.String)}.
 * Support for individual languages is initialized on demand - e.g. once a file of certain MIME type
 * is about to be processed, its appropriate engine (if found), is initialized. Once an engine gets
 * initialized, it remains so, until the virtual machine isn't garbage collected.
 * <p>
 * The <code>TruffleVM</code> is single-threaded and tries to enforce that. It records the thread it
 * has been {@link Builder#build() created} by and checks that all subsequent calls are coming from
 * the same thread.
 */
public final class TruffleVM {
    private static final Logger LOG = Logger.getLogger(TruffleVM.class.getName());
    private static final SPIAccessor SPI = new SPIAccessor();
    private final Thread initThread;
    private final Map<String, Language> langs;
    private final Reader in;
    private final Writer err;
    private final Writer out;

    /**
     * Private & temporary only constructor.
     */
    private TruffleVM() {
        this.initThread = null;
        this.in = null;
        this.err = null;
        this.out = null;
        this.langs = null;
    }

    /**
     * Real constructor used from the builder.
     *
     * @param out stdout
     * @param err stderr
     * @param in stdin
     */
    private TruffleVM(Writer out, Writer err, Reader in) {
        this.out = out;
        this.err = err;
        this.in = in;
        this.initThread = Thread.currentThread();
        this.langs = new HashMap<>();
        Enumeration<URL> en;
        try {
            en = loader().getResources("META-INF/truffle/language");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read list of Truffle languages", ex);
        }
        while (en.hasMoreElements()) {
            URL u = en.nextElement();
            Properties p;
            try {
                p = new Properties();
                try (InputStream is = u.openStream()) {
                    p.load(is);
                }
            } catch (IOException ex) {
                LOG.log(Level.CONFIG, "Cannot process " + u + " as language definition", ex);
                continue;
            }
            for (int cnt = 1;; cnt++) {
                String prefix = "language" + cnt + ".";
                if (p.getProperty(prefix + "name") == null) {
                    break;
                }
                Language l = new Language(prefix, p);
                for (String mimeType : l.getMimeTypes()) {
                    langs.put(mimeType, l);
                }
            }
        }
    }

    static ClassLoader loader() {
        ClassLoader l = TruffleVM.class.getClassLoader();
        if (l == null) {
            l = ClassLoader.getSystemClassLoader();
        }
        return l;
    }

    /**
     * Creation of new Truffle virtual machine. Use the {@link Builder} methods to configure your
     * virtual machine and then create one using {@link Builder#build()}:
     *
     * <pre>
     * {@link TruffleVM} vm = {@link TruffleVM}.{@link TruffleVM#newVM() newVM()}
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
    public static TruffleVM.Builder newVM() {
        // making Builder non-static inner class is a
        // nasty trick to avoid the Builder class to appear
        // in Javadoc next to TruffleVM class
        TruffleVM vm = new TruffleVM();
        return vm.new Builder();
    }

    /**
     * Builder for a new {@link TruffleVM}. Call various configuration methods in a chain and at the
     * end create new {@link TruffleVM virtual machine}:
     *
     * <pre>
     * {@link TruffleVM} vm = {@link TruffleVM}.{@link TruffleVM#newVM() newVM()}
     *     .{@link Builder#stdOut(java.io.Writer) stdOut}({@link Writer yourWriter})
     *     .{@link Builder#stdErr(java.io.Writer) stdErr}({@link Writer yourWriter})
     *     .{@link Builder#stdIn(java.io.Reader) stdIn}({@link Reader yourReader})
     *     .{@link Builder#build() build()};
     * </pre>
     */
    public final class Builder {
        private Writer out;
        private Writer err;
        private Reader in;

        Builder() {
        }

        /**
         * Changes the defaut output for languages running in <em>to be created</em>
         * {@link TruffleVM virtual machine}. The default is to use {@link System#out}.
         *
         * @param w the writer to use as output
         * @return instance of this builder
         */
        public Builder stdOut(Writer w) {
            out = w;
            return this;
        }

        /**
         * Changes the error output for languages running in <em>to be created</em>
         * {@link TruffleVM virtual machine}. The default is to use {@link System#err}.
         *
         * @param w the writer to use as output
         * @return instance of this builder
         */
        public Builder stdErr(Writer w) {
            err = w;
            return this;
        }

        /**
         * Changes the default input for languages running in <em>to be created</em>
         * {@link TruffleVM virtual machine}. The default is to use {@link System#out}.
         *
         * @param r the reader to use as input
         * @return instance of this builder
         */
        public Builder stdIn(Reader r) {
            in = r;
            return this;
        }

        /**
         * Creates the {@link TruffleVM Truffle virtual machine}. The configuration is taken from
         * values passed into configuration methods in this class.
         *
         * @return new, isolated virtual machine with pre-registered languages
         */
        public TruffleVM build() {
            if (out == null) {
                out = new OutputStreamWriter(System.out);
            }
            if (err == null) {
                err = new OutputStreamWriter(System.err);
            }
            if (in == null) {
                in = new InputStreamReader(System.in);
            }
            return new TruffleVM(out, err, in);
        }
    }

    /**
     * Descriptions of languages supported in this Truffle virtual machine.
     *
     * @return an immutable map with keys being MIME types and values the {@link Language
     *         descriptions} of associated languages
     */
    public Map<String, Language> getLanguages() {
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
     */
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
            } else {
                mimeType = Files.probeContentType(file.toPath());
            }
        } else {
            URL url = location.toURL();
            s = Source.fromURL(url, location.toString());
            URLConnection conn = url.openConnection();
            mimeType = conn.getContentType();
        }
        TruffleLanguage l = getTruffleLang(mimeType);
        if (l == null) {
            throw new IOException("No language for " + location + " with MIME type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return SPI.eval(l, s);
    }

    /**
     * Evaluates code snippet. Chooses a language registered for a given MIME type (throws
     * {@link IOException} if there is none). And passes the specified code to it for execution.
     *
     * @param mimeType MIME type of the code snippet - chooses the right language
     * @param reader the source of code snippet to execute
     * @return result of an execution, possibly <code>null</code>
     * @throws IOException thrown to signal errors while processing the code
     */
    public Object eval(String mimeType, Reader reader) throws IOException {
        checkThread();
        TruffleLanguage l = getTruffleLang(mimeType);
        if (l == null) {
            throw new IOException("No language for MIME type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return SPI.eval(l, Source.fromReader(reader, mimeType));
    }

    /**
     * Evaluates code snippet. Chooses a language registered for a given MIME type (throws
     * {@link IOException} if there is none). And passes the specified code to it for execution.
     *
     * @param mimeType MIME type of the code snippet - chooses the right language
     * @param code the code snippet to execute
     * @return result of an execution, possibly <code>null</code>
     * @throws IOException thrown to signal errors while processing the code
     */
    public Object eval(String mimeType, String code) throws IOException {
        checkThread();
        TruffleLanguage l = getTruffleLang(mimeType);
        if (l == null) {
            throw new IOException("No language for MIME type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return SPI.eval(l, Source.fromText(code, mimeType));
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
    public Symbol findGlobalSymbol(String globalName) {
        checkThread();
        Object obj = null;
        Object global = null;
        for (Language dl : langs.values()) {
            TruffleLanguage l = dl.getImpl();
            obj = SPI.findExportedSymbol(l, globalName, true);
            if (obj != null) {
                global = SPI.languageGlobal(l);
                break;
            }
        }
        if (obj == null) {
            for (Language dl : langs.values()) {
                TruffleLanguage l = dl.getImpl();
                obj = SPI.findExportedSymbol(l, globalName, false);
                if (obj != null) {
                    global = SPI.languageGlobal(l);
                    break;
                }
            }
        }
        return obj == null ? null : new Symbol(obj, global);
    }

    private void checkThread() {
        if (initThread != Thread.currentThread()) {
            throw new IllegalStateException("TruffleVM created on " + initThread.getName() + " but used on " + Thread.currentThread().getName());
        }
    }

    private TruffleLanguage getTruffleLang(String mimeType) {
        checkThread();
        Language l = langs.get(mimeType);
        return l == null ? null : l.getImpl();
    }

    /**
     * Represents {@link TruffleVM#findGlobalSymbol(java.lang.String) global symbol} provided by one
     * of the initialized languages in {@link TruffleVM Truffle virtual machine}.
     */
    public class Symbol {
        private final Object obj;
        private final Object global;

        Symbol(Object obj, Object global) {
            this.obj = obj;
            this.global = global;
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
         * @return the value returned by invoking the symbol
         * @throws IOException signals problem during execution
         */
        public Object invoke(Object thiz, Object... args) throws IOException {
            List<Object> arr = new ArrayList<>();
            if (thiz == null) {
                if (global != null) {
                    arr.add(global);
                }
            } else {
                arr.add(thiz);
            }
            arr.addAll(Arrays.asList(args));
            return SPI.invoke(obj, arr.toArray());
        }
    }

    /**
     * Description of a language registered in {@link TruffleVM Truffle virtual machine}. Languages
     * are registered by {@link Registration} annotation which stores necessary information into a
     * descriptor inside of the language's JAR file. When a new {@link TruffleVM} is created, it
     * reads all available descriptors and creates {@link Language} objects to represent them. One
     * can obtain a {@link #getName() name} or list of supported {@link #getMimeTypes() MIME types}
     * for each language. The actual language implementation is not initialized until
     * {@link TruffleVM#eval(java.lang.String, java.lang.String) a code is evaluated} in it.
     */
    public final class Language {
        private final Properties props;
        private TruffleLanguage impl;
        private final String prefix;

        Language(String prefix, Properties props) {
            this.prefix = prefix;
            this.props = props;
        }

        /**
         * MIME types recognized by the language.
         *
         * @return returns immutable set of recognized MIME types
         */
        public Set<String> getMimeTypes() {
            TreeSet<String> ts = new TreeSet<>();
            for (int i = 0;; i++) {
                String mt = props.getProperty(prefix + "mimeType." + i);
                if (mt == null) {
                    break;
                }
                ts.add(mt);
            }
            return Collections.unmodifiableSet(ts);
        }

        /**
         * Human readable name of the language. Think of C, Ruby, JS, etc.
         *
         * @return string giving the language a name
         */
        public String getName() {
            return props.getProperty(prefix + "name");
        }

        /**
         * Name of the language version.
         *
         * @return string specifying the language version
         */
        public String getVersion() {
            return props.getProperty(prefix + "version");
        }

        /**
         * Human readable string that identifies the language and version.
         *
         * @return string describing the specific language version
         */
        public String getShortName() {
            return getName() + getVersion();
        }

        TruffleLanguage getImpl() {
            if (impl == null) {
                String n = props.getProperty(prefix + "className");
                try {
                    Class<?> langClazz = Class.forName(n, true, loader());
                    Constructor<?> constructor = langClazz.getConstructor(Env.class);
                    impl = SPI.attachEnv(TruffleVM.this, constructor, out, err, in);
                } catch (Exception ex) {
                    throw new IllegalStateException("Cannot initialize " + getShortName() + " language with implementation " + n, ex);
                }
            }
            return impl;
        }

        @Override
        public String toString() {
            return "[" + getShortName() + " for " + getMimeTypes() + "]";
        }
    } // end of Language

    private static class SPIAccessor extends Accessor {
        @Override
        public Object importSymbol(TruffleVM vm, TruffleLanguage ownLang, String globalName) {
            Set<Language> uniqueLang = new LinkedHashSet<>(vm.langs.values());
            for (Language dl : uniqueLang) {
                TruffleLanguage l = dl.getImpl();
                if (l == ownLang) {
                    continue;
                }
                Object obj = SPI.findExportedSymbol(l, globalName, true);
                if (obj != null) {
                    return obj;
                }
            }
            for (Language dl : uniqueLang) {
                TruffleLanguage l = dl.getImpl();
                if (l == ownLang) {
                    continue;
                }
                Object obj = SPI.findExportedSymbol(l, globalName, false);
                if (obj != null) {
                    return obj;
                }
            }
            return null;
        }

        @Override
        public TruffleLanguage attachEnv(TruffleVM vm, Constructor<?> langClazz, Writer stdOut, Writer stdErr, Reader stdIn) {
            return super.attachEnv(vm, langClazz, stdOut, stdErr, stdIn);
        }

        @Override
        public Object eval(TruffleLanguage l, Source s) throws IOException {
            return super.eval(l, s);
        }

        @Override
        public Object findExportedSymbol(TruffleLanguage l, String globalName, boolean onlyExplicit) {
            return super.findExportedSymbol(l, globalName, onlyExplicit);
        }

        @Override
        public Object languageGlobal(TruffleLanguage l) {
            return super.languageGlobal(l);
        }

        @Override
        public Object invoke(Object obj, Object[] args) throws IOException {
            return super.invoke(obj, args);
        }
    } // end of SPIAccessor
}
