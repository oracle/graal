/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.dsl.test.processor;

import java.io.IOException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.test.ExpectError;

public class LanguageRegistrationTest {

    @ExpectError("Registered language class must be public")
    @TruffleLanguage.Registration(name = "myLang", version = "0", mimeType = "text/x-my")
    private static final class MyLang {
    }

    @ExpectError("Registered language inner-class must be static")
    @TruffleLanguage.Registration(name = "myLangNonStatic", version = "0", mimeType = "text/x-my")
    public final class MyLangNonStatic {
    }

    @ExpectError("Registered language class must subclass TruffleLanguage")
    @TruffleLanguage.Registration(name = "myLang", version = "0", mimeType = "text/x-my")
    public static final class MyLangNoSubclass {
    }

    @ExpectError("Language class must have public static final singleton field called INSTANCE")
    @TruffleLanguage.Registration(name = "myLangNoCnstr", version = "0", mimeType = "text/x-my")
    public static final class MyLangWrongConstr extends TruffleLanguage<Object> {
        private MyLangWrongConstr() {
            super();
        }

        @Override
        protected CallTarget parse(ParsingRequest env) throws IOException {
            throw new IOException();
        }

        @Override
        protected Object findExportedSymbol(Object context, String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal(Object context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected Object createContext(Env env) {
            throw new UnsupportedOperationException();
        }

    }

    @ExpectError("Language class must have public static final singleton field called INSTANCE")
    @TruffleLanguage.Registration(name = "myLangNoField", version = "0", mimeType = "text/x-my")
    public static final class MyLangNoField extends TruffleLanguage<Object> {
        public MyLangNoField() {
            super();
        }

        @Override
        protected CallTarget parse(ParsingRequest env) throws IOException {
            throw new IOException();
        }

        @Override
        protected Object findExportedSymbol(Object context, String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal(Object context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected Object createContext(Env env) {
            throw new UnsupportedOperationException();
        }

    }

    @TruffleLanguage.Registration(name = "myLangGood", version = "0", mimeType = "text/x-my")
    public static final class MyLangGood extends TruffleLanguage<Object> {
        private MyLangGood() {
            super();
        }

        public static final MyLangGood INSTANCE = new MyLangGood();

        @Override
        protected Object findExportedSymbol(Object context, String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal(Object context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected CallTarget parse(ParsingRequest env) throws IOException {
            throw new IOException();
        }

        @Override
        protected Object createContext(Env env) {
            return env;
        }

    }
}
