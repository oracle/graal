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
package com.oracle.truffle.api.impl;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.debug.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.vm.*;

/**
 * Communication between TruffleVM and TruffleLanguage API/SPI.
 */
public abstract class Accessor {
    private static Accessor API;
    private static Accessor SPI;
    private static Accessor NODES;
    private static Accessor INSTRUMENT;
    private static Accessor DEBUG;

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

            @Override
            protected ToolSupportProvider getToolSupport() {
                return null;
            }

            @Override
            protected DebugSupportProvider getDebugSupport() {
                return null;
            }
        };
        lng.hashCode();
        new Node(null) {
        }.getRootNode();

        try {
            Class.forName(Debugger.class.getName(), true, Debugger.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected Accessor() {
        if (this.getClass().getSimpleName().endsWith("API")) {
            if (API != null) {
                throw new IllegalStateException();
            }
            API = this;
        } else if (this.getClass().getSimpleName().endsWith("Nodes")) {
            if (NODES != null) {
                throw new IllegalStateException();
            }
            NODES = this;
        } else if (this.getClass().getSimpleName().endsWith("Instrument")) {
            if (INSTRUMENT != null) {
                throw new IllegalStateException();
            }
            INSTRUMENT = this;
        } else if (this.getClass().getSimpleName().endsWith("Debug")) {
            if (DEBUG != null) {
                throw new IllegalStateException();
            }
            DEBUG = this;
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

    protected ToolSupportProvider getToolSupport(TruffleLanguage l) {
        return API.getToolSupport(l);
    }

    protected DebugSupportProvider getDebugSupport(TruffleLanguage l) {
        return API.getDebugSupport(l);
    }

    protected Object invoke(TruffleLanguage lang, Object obj, Object[] args) throws IOException {
        for (SymbolInvoker si : ServiceLoader.load(SymbolInvoker.class)) {
            return si.invoke(lang, obj, args);
        }
        throw new IOException("No symbol invoker found!");
    }

    protected Class<? extends TruffleLanguage> findLanguage(RootNode n) {
        return NODES.findLanguage(n);
    }

    protected Class<? extends TruffleLanguage> findLanguage(Probe probe) {
        return INSTRUMENT.findLanguage(probe);
    }

    protected TruffleLanguage findLanguage(TruffleVM vm, Class<? extends TruffleLanguage> languageClass) {
        return SPI.findLanguage(vm, languageClass);
    }

    protected Closeable executionStart(TruffleVM vm, Debugger[] fillIn, Source s) {
        return DEBUG.executionStart(vm, fillIn, s);
    }

    protected void dispatchEvent(TruffleVM vm, Object event) {
        SPI.dispatchEvent(vm, event);
    }

    /**
     * Don't call me. I am here only to let NetBeans debug any Truffle project.
     *
     * @param args
     */
    public static void main(String... args) {
        throw new IllegalStateException();
    }
}
