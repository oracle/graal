/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.debug.*;
import com.oracle.truffle.api.dsl.test.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.source.*;

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

    @ExpectError("Language must have a public constructor accepting TruffleLanguage.Env as parameter")
    @TruffleLanguage.Registration(name = "myLangNoCnstr", version = "0", mimeType = "text/x-my")
    public static final class MyLangWrongConstr extends TruffleLanguage {
        private MyLangWrongConstr() {
            super(null);
        }

        @Override
        protected Object eval(Source code) throws IOException {
            return null;
        }

        @Override
        protected Object findExportedSymbol(String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal() {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected ToolSupportProvider getToolSupport() {
            return null;
        }

        @Override
        protected DebugSupportProvider getDebugSupport() {
            return null;
        }

    }

    @ExpectError("Language must have a public constructor accepting TruffleLanguage.Env as parameter")
    @TruffleLanguage.Registration(name = "myLangNoCnstr", version = "0", mimeType = "text/x-my")
    public static final class MyLangNoConstr extends TruffleLanguage {
        public MyLangNoConstr() {
            super(null);
        }

        @Override
        protected Object eval(Source code) throws IOException {
            return null;
        }

        @Override
        protected Object findExportedSymbol(String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal() {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected ToolSupportProvider getToolSupport() {
            return null;
        }

        @Override
        protected DebugSupportProvider getDebugSupport() {
            return null;
        }

    }

    @TruffleLanguage.Registration(name = "myLangGood", version = "0", mimeType = "text/x-my")
    public static final class MyLangGood extends TruffleLanguage {
        public MyLangGood(TruffleLanguage.Env env) {
            super(env);
        }

        @Override
        protected Object eval(Source code) throws IOException {
            return null;
        }

        @Override
        protected Object findExportedSymbol(String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal() {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected ToolSupportProvider getToolSupport() {
            return null;
        }

        @Override
        protected DebugSupportProvider getDebugSupport() {
            return null;
        }

    }
}
