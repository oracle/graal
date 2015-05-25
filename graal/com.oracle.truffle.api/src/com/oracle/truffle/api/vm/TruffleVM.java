/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.source.Source;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Virtual machine for Truffle based languages. Use {@link #create()} to instantiate new isolated
 * virtual machine ready for execution of various languages. All the languages in a single virtual
 * machine see each other exported global symbols and can co-operate. Use {@link #create()} multiple
 * times to create different, isolated virtual machines completely separated from each other.
 * <p>
 * Once instantiated use {@link #eval(java.net.URI)} with a reference to a file or URL or directly
 * pass code snippet into the virtual machine via {@link #eval(java.lang.String, java.lang.String)}.
 * Support for individual languages is initialized on demand - e.g. once a file of certain mime type
 * is about to be processed, its appropriate engine (if found), is initialized. Once an engine gets
 * initialized, it remains so, until the virtual machine isn't garbage collected.
 * <p>
 * The <code>TruffleVM</code> is single-threaded and tries to enforce that. It records the thread it
 * has been {@link #create() created} by and checks that all subsequent calls are coming from the
 * same thread.
 */
public final class TruffleVM {
    private static final Logger LOG = Logger.getLogger(TruffleVM.class.getName());
    private static final SPIAccessor SPI = new SPIAccessor();
    private final Thread initThread;
    private final Map<String, Language> langs;

    private TruffleVM() {
        initThread = Thread.currentThread();
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
            Language l = new Language(p);
            for (String mimeType : l.getMimeTypes()) {
                langs.put(mimeType, l);
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
     * Creates new Truffle virtual machine. It searches for {@link Registration languages
     * registered} in the system class loader and makes them available for later evaluation via
     * {@link #eval(java.lang.String, java.lang.String)} methods.
     *
     * @return new, isolated virtual machine with pre-registered languages
     */
    public static TruffleVM create() {
        return new TruffleVM();
    }

    /**
     * Descriptions of languages supported in this Truffle virtual machine.
     *
     * @return an immutable map with keys being mimetypes and values the {@link Language
     *         descriptions} of associated languages
     */
    public Map<String, Language> getLanguages() {
        return Collections.unmodifiableMap(langs);
    }

    /**
     * Evaluates file located on a given URL. Is equivalent to loading the content of a file and
     * executing it via {@link #eval(java.lang.String, java.lang.String)} with a mime type guess
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
            throw new IOException("No language for " + location + " with mime type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return SPI.eval(l, s);
    }

    /**
     * Evaluates code snippet. Chooses a language registered for a given mime type (throws
     * {@link IOException} if there is none). And passes the specified code to it for execution.
     *
     * @param mimeType mime type of the code snippet - chooses the right language
     * @param reader the source of code snippet to execute
     * @return result of an exceution, possibly <code>null</code>
     * @throws IOException thrown to signal errors while processing the code
     */
    public Object eval(String mimeType, Reader reader) throws IOException {
        checkThread();
        TruffleLanguage l = getTruffleLang(mimeType);
        if (l == null) {
            throw new IOException("No language for mime type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return SPI.eval(l, Source.fromReader(reader, mimeType));
    }

    /**
     * Evaluates code snippet. Chooses a language registered for a given mime type (throws
     * {@link IOException} if there is none). And passes the specified code to it for execution.
     *
     * @param mimeType mime type of the code snippet - chooses the right language
     * @param code the code snippet to execute
     * @return result of an exceution, possibly <code>null</code>
     * @throws IOException thrown to signal errors while processing the code
     */
    public Object eval(String mimeType, String code) throws IOException {
        checkThread();
        TruffleLanguage l = getTruffleLang(mimeType);
        if (l == null) {
            throw new IOException("No language for mime type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return SPI.eval(l, Source.fromText(code, mimeType));
    }

    /**
     * Looks global symbol provided by one of initialized languages up. First of all execute your
     * program via one of your {@link #eval(java.lang.String, java.lang.String)} and then look
     * expected symbol up using this method.
     * <p>
     * The names of the symbols are language dependant, but for example the Java language bindings
     * follow the specification for method references:
     * <ul>
     * <li>"java.lang.Exception::new" is a reference to constructor of {@link Exception}
     * <li>"java.lang.Integer::valueOf" is a reference to static method in {@link Integer} class
     * </ul>
     * Once an symbol is obtained, it remembers values for fast acces and is ready for being
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
            obj = SPI.findExportedSymbol(l, globalName);
            if (obj != null) {
                global = SPI.languageGlobal(l);
                break;
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
     * reads all available descritors and creates {@link Language} objects to represent them. One
     * can obtain a {@link #getName() name} or list of supported {@link #getMimeTypes() mimetypes}
     * for each language. The actual language implementation is not initialized until
     * {@link TruffleVM#eval(java.lang.String, java.lang.String) a code is evaluated} in it.
     */
    public final class Language {
        private final Properties props;
        private TruffleLanguage impl;

        Language(Properties props) {
            this.props = props;
        }

        /**
         * Mimetypes recognized by the language.
         *
         * @return returns immutable set of recognized mimetypes
         */
        public Set<String> getMimeTypes() {
            TreeSet<String> ts = new TreeSet<>();
            for (int i = 0;; i++) {
                String mt = props.getProperty("mimeType." + i);
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
            return props.getProperty("name");
        }

        TruffleLanguage getImpl() {
            if (impl == null) {
                String n = props.getProperty("className");
                try {
                    TruffleLanguage lang = (TruffleLanguage) Class.forName(n, true, loader()).newInstance();
                    SPI.attachEnv(TruffleVM.this, lang);
                    impl = lang;
                } catch (Exception ex) {
                    throw new IllegalStateException("Cannot initialize " + getName() + " language with implementation " + n, ex);
                }
            }
            return impl;
        }

        @Override
        public String toString() {
            return "[" + getName() + " for " + getMimeTypes() + "]";
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
                Object obj = SPI.findExportedSymbol(l, globalName);
                if (obj != null) {
                    return obj;
                }
            }
            return null;
        }

        @Override
        public Env attachEnv(TruffleVM vm, TruffleLanguage l) {
            return super.attachEnv(vm, l);
        }

        @Override
        public Object eval(TruffleLanguage l, Source s) throws IOException {
            return super.eval(l, s);
        }

        @Override
        public Object findExportedSymbol(TruffleLanguage l, String globalName) {
            return super.findExportedSymbol(l, globalName);
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
