/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.RegexLanguage;

@TruffleLanguage.Registration(name = TRegexTestDummyLanguage.NAME, id = TRegexTestDummyLanguage.ID, characterMimeTypes = TRegexTestDummyLanguage.MIME_TYPE, version = "0.1", dependentLanguages = RegexLanguage.ID)
public class TRegexTestDummyLanguage extends TruffleLanguage<TRegexTestDummyLanguage.DummyLanguageContext> {

    public static final String NAME = "REGEXDUMMYLANG";
    public static final String ID = "regexDummyLang";
    public static final String MIME_TYPE = "application/tregexdummy";

    @Override
    protected CallTarget parse(ParsingRequest parsingRequest) {
        return getCurrentContext(TRegexTestDummyLanguage.class).getEnv().parseInternal(Source.newBuilder(RegexLanguage.ID, "", "TRegex Engine Builder Request").internal(true).build());
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    @Override
    protected DummyLanguageContext createContext(Env env) {
        return new DummyLanguageContext(env);
    }

    @Override
    protected boolean patchContext(DummyLanguageContext context, Env newEnv) {
        context.patchContext(newEnv);
        return true;
    }

    public static final class DummyLanguageContext {

        @CompilerDirectives.CompilationFinal private Env env;

        DummyLanguageContext(Env env) {
            this.env = env;
        }

        void patchContext(Env patchedEnv) {
            this.env = patchedEnv;
        }

        public Env getEnv() {
            return env;
        }
    }
}
