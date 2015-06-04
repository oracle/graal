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
package com.oracle.truffle.api.impl;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.vm.*;

/**
 * Communication between TruffleVM and TruffleLanguage API/SPI.
 */
public abstract class Accessor {
    private static Accessor API;
    private static Accessor SPI;
    static {
        TruffleLanguage lng = new TruffleLanguage(null) {
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
        };
        lng.hashCode();
    }

    protected Accessor() {
        if (this.getClass().getSimpleName().endsWith("API")) {
            if (API != null) {
                throw new IllegalStateException();
            }
            API = this;
        } else {
            if (SPI != null) {
                throw new IllegalStateException();
            }
            SPI = this;
        }
    }

    protected TruffleLanguage attachEnv(TruffleVM vm, Constructor<?> langClazz, Writer stdOut, Writer stdErr, Reader stdIn) {
        return API.attachEnv(vm, langClazz, stdOut, stdErr, stdIn);
    }

    protected Object eval(TruffleLanguage l, Source s) throws IOException {
        return API.eval(l, s);
    }

    protected Object importSymbol(TruffleVM vm, TruffleLanguage queryingLang, String globalName) {
        return SPI.importSymbol(vm, queryingLang, globalName);
    }

    protected Object findExportedSymbol(TruffleLanguage l, String globalName, boolean onlyExplicit) {
        return API.findExportedSymbol(l, globalName, onlyExplicit);
    }

    protected Object languageGlobal(TruffleLanguage l) {
        return API.languageGlobal(l);
    }

    protected Object invoke(Object obj, Object[] args) throws IOException {
        for (SymbolInvoker si : ServiceLoader.load(SymbolInvoker.class)) {
            return si.invoke(obj, args);
        }
        throw new IOException("No symbol invoker found!");
    }

}
