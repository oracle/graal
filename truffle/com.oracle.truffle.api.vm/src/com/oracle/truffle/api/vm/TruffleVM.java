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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * @deprecated
 */
@Deprecated
@SuppressWarnings({"rawtypes", "deprecated"})
public final class TruffleVM extends PolyglotEngine {
    private TruffleVM() {
        super();
    }

    /**
     * Real constructor used from the builder.
     */
    TruffleVM(Executor executor, Map<String, Object> globals, OutputStream out, OutputStream err, InputStream in, EventConsumer<?>[] handlers) {
        super(executor, globals, out, err, in, handlers);
    }

    @Deprecated
    public static TruffleVM.Builder newVM() {
        // making Builder non-static inner class is a
        // nasty trick to avoid the Builder class to appear
        // in Javadoc next to TruffleVM class
        TruffleVM vm = new TruffleVM();
        return vm.new Builder();
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    public final class Builder extends PolyglotEngine.Builder {
        Builder() {
        }

        @Deprecated
        @Override
        public Builder setOut(OutputStream os) {
            super.setOut(os);
            return this;
        }

        @Deprecated
        @Override
        public Builder executor(Executor executor) {
            return (Builder) super.executor(executor);
        }

        @Deprecated
        @Override
        public Builder setErr(OutputStream os) {
            super.setErr(os);
            return this;
        }

        @Deprecated
        @Override
        public Builder globalSymbol(String name, Object obj) {
            return (Builder) super.globalSymbol(name, obj);
        }

        @Deprecated
        @Override
        public Builder setIn(InputStream is) {
            super.setIn(is);
            return this;
        }

        @Deprecated
        @Override
        public Builder onEvent(EventConsumer<?> handler) {
            return (Builder) super.onEvent(handler);
        }

        @Deprecated
        @Override
        public Builder stdIn(Reader r) {
            return (Builder) super.stdIn(r);
        }

        @Deprecated
        @Override
        public Builder stdErr(Writer w) {
            return (Builder) super.stdErr(w);
        }

        @Deprecated
        @Override
        public Builder stdOut(Writer w) {
            return (Builder) super.stdOut(w);
        }

        @Deprecated
        @Override
        public TruffleVM build() {
            return (TruffleVM) super.build();
        }
    }

    @Override
    Value createValue(TruffleLanguage lang, Object[] result, CountDownLatch ready) {
        return new Symbol(lang, result, ready);
    }

    @Override
    Language createLanguage(Map.Entry<String, LanguageCache> en) {
        return new Language(en.getValue());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Language> getLanguages() {
        return (Map<String, Language>) super.getLanguages();
    }

    @Override
    public Symbol eval(Source source) throws IOException {
        return (Symbol) super.eval(source);
    }

    @Override
    public Symbol findGlobalSymbol(final String globalName) {
        return (Symbol) super.findGlobalSymbol(globalName);
    }

    @Override
    void dispatchSuspendedEvent(SuspendedEvent event) {
    }

    @Override
    void dispatchExecutionEvent(ExecutionEvent event) {
    }

    @Deprecated
    public class Symbol extends Value {
        Symbol(TruffleLanguage<?> language, Object[] result, CountDownLatch ready) {
            super(language, result, ready);
        }

        @Override
        public Symbol invoke(final Object thiz, final Object... args) throws IOException {
            return (Symbol) super.invoke(thiz, args);
        }
    }

    @Deprecated
    public final class Language extends PolyglotEngine.Language {
        Language(LanguageCache info) {
            super(info);
        }
    } // end of Language
}
