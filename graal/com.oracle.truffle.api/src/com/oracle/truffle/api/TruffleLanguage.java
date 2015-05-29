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
package com.oracle.truffle.api;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;

import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.vm.*;
import com.oracle.truffle.api.vm.TruffleVM.Language;

/**
 * An entry point for everyone who wants to implement a Truffle based language. By providing
 * implementation of this type and registering it using {@link Registration} annotation, your
 * language becomes accessible to users of the {@link TruffleVM Truffle virtual machine} - all they
 * will need to do is to include your JAR into their application and all the Truffle goodies (multi
 * language support, multi tennat hosting, debugging, etc.) will be made available to them.
 */
public abstract class TruffleLanguage {
    private final Env env;

    /**
     * Constructor to be called by subclasses.
     *
     * @param env language environment that will be available via {@link #env()} method to
     *            subclasses.
     */
    protected TruffleLanguage(Env env) {
        this.env = env;
    }

    /**
     * The annotation to use to register your language to the {@link TruffleVM Truffle} system. By
     * annotating your implementation of {@link TruffleLanguage} by this annotation you are just a
     * <em>one JAR drop to the classpath</em> away from your users. Once they include your JAR in
     * their application, your language will be available to the {@link TruffleVM Truffle virtual
     * machine}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Registration {
        /**
         * Unique name of your language. This name will be exposed to users via the
         * {@link Language#getName()} getter.
         *
         * @return identifier of your language
         */
        String name();

        /**
         * List of mimetypes associated with your language. Users will use them (directly or
         * inderectly) when {@link TruffleVM#eval(java.lang.String, java.lang.String) executing}
         * their code snippets or their {@link TruffleVM#eval(java.net.URI) files}.
         *
         * @return array of mime types assigned to your language files
         */
        String[] mimeType();
    }

    protected final Env env() {
        if (this.env == null) {
            throw new NullPointerException("Accessing env before initialization is finished");
        }
        return this.env;
    }

    protected abstract Object eval(Source code) throws IOException;

    /**
     * Called when some other language is seeking for a global symbol. This method is supposed to do
     * lazy binding, e.g. there is no need to export symbols in advance, it is fine to wait until
     * somebody asks for it (by calling this method).
     * <p>
     * The exported object can either be <code>TruffleObject</code> (e.g. a native object from the
     * other language) to support interoperability between languages or one of Java primitive
     * wrappers ( {@link Integer}, {@link Double}, {@link Short}, etc.).
     *
     * @param globalName the name of the global symbol to find
     * @return an exported object or <code>null</code>, if the symbol does not represent anything
     *         meaningful in this language
     */
    protected abstract Object findExportedSymbol(String globalName);

    /**
     * Returns global object for the language.
     * <p>
     * The object is expected to be <code>TruffleObject</code> (e.g. a native object from the other
     * language) but technically it can be one of Java primitive wrappers ({@link Integer},
     * {@link Double}, {@link Short}, etc.).
     *
     * @return the global object or <code>null</code> if the language does not support such concept
     */
    protected abstract Object getLanguageGlobal();

    /**
     * Checks whether the object is provided by this language.
     *
     * @param object the object to check
     * @return <code>true</code> if this language can deal with such object in native way
     */
    protected abstract boolean isObjectOfLanguage(Object object);

    /**
     * Represents execution environment of the {@link TruffleLanguage}. Each active
     * {@link TruffleLanguage} receives instance of the environment before any code is executed upon
     * it. The environment has knowledge of all active languages and can exchange symbols between
     * them.
     */
    public static final class Env {
        private final TruffleVM vm;
        private final TruffleLanguage lang;
        private final Reader in;
        private final Writer err;
        private final Writer out;

        Env(TruffleVM vm, Constructor<?> langConstructor, Writer out, Writer err, Reader in) {
            this.vm = vm;
            this.in = in;
            this.err = err;
            this.out = out;
            try {
                this.lang = (TruffleLanguage) langConstructor.newInstance(this);
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot construct language " + langConstructor.getDeclaringClass().getName(), ex);
            }
        }

        /**
         * Asks the environment to go through other registered languages and find whether they
         * export global symbol of specified name. The expected return type is either
         * <code>TruffleObject</code>, or one of wrappers of Java primitive types ({@link Integer},
         * {@link Double}).
         *
         * @param globalName the name of the symbol to search for
         * @return object representing the symbol or <code>null</code>
         */
        public Object importSymbol(String globalName) {
            return API.importSymbol(vm, lang, globalName);
        }

        /**
         * Input associated with this {@link TruffleVM}.
         *
         * @return reader, never <code>null</code>
         */
        public Reader stdIn() {
            return in;
        }

        /**
         * Standard output writer for this {@link TruffleVM}.
         *
         * @return writer, never <code>null</code>
         */
        public Writer stdOut() {
            return out;
        }

        /**
         * Standard error writer for this {@link TruffleVM}.
         *
         * @return writer, never <code>null</code>
         */
        public Writer stdErr() {
            return err;
        }
    }

    private static final AccessAPI API = new AccessAPI();

    private static final class AccessAPI extends Accessor {
        @Override
        protected TruffleLanguage attachEnv(TruffleVM vm, Constructor<?> langClazz, Writer stdOut, Writer stdErr, Reader stdIn) {
            Env env = new Env(vm, langClazz, stdOut, stdErr, stdIn);
            return env.lang;
        }

        @Override
        public Object importSymbol(TruffleVM vm, TruffleLanguage queryingLang, String globalName) {
            return super.importSymbol(vm, queryingLang, globalName);
        }

        @Override
        protected Object eval(TruffleLanguage l, Source s) throws IOException {
            return l.eval(s);
        }

        @Override
        protected Object findExportedSymbol(TruffleLanguage l, String globalName) {
            return l.findExportedSymbol(globalName);
        }

        @Override
        protected Object languageGlobal(TruffleLanguage l) {
            return l.getLanguageGlobal();
        }
    }
}
