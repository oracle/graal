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
import com.oracle.truffle.api.debug.*;
import com.oracle.truffle.api.source.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * @deprecated
 */
@Deprecated
@SuppressWarnings({"rawtypes", "deprecated"})
public final class TruffleVM extends Portaal {
    private TruffleVM() {
        super();
    }

    TruffleVM(Executor executor, Map<String, Object> globals, Writer out, Writer err, Reader in, EventConsumer<?>[] handlers) {
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

    @Deprecated
    public final class Builder extends Portaal.Builder {
        Builder() {
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
    public Symbol eval(Source source) throws IOException {
        return (Symbol)super.eval(source);
    }

    @Override
    public Symbol findGlobalSymbol(final String globalName) {
        return (Symbol)super.findGlobalSymbol(globalName);
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
            return (Symbol)super.invoke(thiz, args);
        }
    }

    @Deprecated
    public final class Language extends Portaal.Language {
        Language(LanguageCache info) {
            super(info);
        }
    } // end of Language
}
