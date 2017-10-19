/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.io.IOException;
import java.util.List;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;

/**
 * Reusable language for testing that allows wrap all methods.
 */
@TruffleLanguage.Registration(id = ProxyLanguage.ID, name = ProxyLanguage.ID, version = "1.0", mimeType = ProxyLanguage.ID)
public class ProxyLanguage extends TruffleLanguage<LanguageContext> {

    static final String ID = "proxyLanguage";

    static class LanguageContext {
        final Env env;

        LanguageContext(Env env) {
            this.env = env;
        }
    }

    private static volatile ProxyLanguage delegate = new ProxyLanguage();
    static {
        delegate.wrapper = false;
    }
    private boolean wrapper = true;

    public static void setDelegate(ProxyLanguage delegate) {
        delegate.wrapper = false;
        ProxyLanguage.delegate = delegate;
    }

    @Override
    protected LanguageContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        if (wrapper) {
            return delegate.createContext(env);
        } else {
            return null;
        }
    }

    @Override
    protected Object getLanguageGlobal(LanguageContext context) {
        if (wrapper) {
            return delegate.getLanguageGlobal(context);
        } else {
            return null;
        }
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        if (wrapper) {
            return delegate.isObjectOfLanguage(object);
        } else {
            return false;
        }
    }

    @Override
    protected void disposeContext(LanguageContext context) {
        if (wrapper) {
            delegate.disposeContext(context);
        } else {
            super.disposeContext(context);
        }
    }

    @Override
    protected void disposeThread(LanguageContext context, Thread thread) {

        if (wrapper) {
            delegate.disposeThread(context, thread);
        } else {
            super.disposeThread(context, thread);
        }
    }

    @Override
    protected Object findExportedSymbol(LanguageContext context, String globalName, boolean onlyExplicit) {
        if (wrapper) {
            return delegate.findExportedSymbol(context, globalName, onlyExplicit);
        } else {
            return super.findExportedSymbol(context, globalName, onlyExplicit);
        }

    }

    @Override
    protected Object findMetaObject(LanguageContext context, Object value) {
        if (wrapper) {
            return delegate.findMetaObject(context, value);
        } else {
            return super.findMetaObject(context, value);
        }
    }

    @Override
    protected SourceSection findSourceLocation(LanguageContext context, Object value) {
        if (wrapper) {
            return delegate.findSourceLocation(context, value);
        } else {
            return super.findSourceLocation(context, value);
        }
    }

    @Override
    protected void initializeContext(LanguageContext context) throws Exception {
        if (wrapper) {
            delegate.initializeContext(context);
        } else {
            super.initializeContext(context);
        }

    }

    @Override
    protected void initializeMultiThreading(LanguageContext context) {
        if (wrapper) {
            delegate.initializeMultiThreading(context);
        } else {
            super.initializeMultiThreading(context);
        }
    }

    @Override
    protected void initializeThread(LanguageContext context, Thread thread) {
        if (wrapper) {
            delegate.initializeThread(context, thread);
        } else {
            super.initializeThread(context, thread);
        }
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        if (wrapper) {
            return delegate.isThreadAccessAllowed(thread, singleThreaded);
        } else {
            return super.isThreadAccessAllowed(thread, singleThreaded);
        }
    }

    @Override
    protected boolean isVisible(LanguageContext context, Object value) {
        if (wrapper) {
            return delegate.isVisible(context, value);
        } else {
            return super.isVisible(context, value);
        }
    }

    @Override
    protected Object lookupSymbol(LanguageContext context, String symbolName) {
        if (wrapper) {
            return delegate.lookupSymbol(context, symbolName);
        } else {
            return super.lookupSymbol(context, symbolName);
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        if (wrapper) {
            return delegate.getOptionDescriptors();
        } else {
            return super.getOptionDescriptors();
        }
    }

    @Override
    protected String toString(LanguageContext context, Object value) {
        if (wrapper) {
            return delegate.toString(context, value);
        } else {
            return super.toString(context, value);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected List<OptionDescriptor> describeOptions() {
        if (wrapper) {
            return delegate.describeOptions();
        } else {
            return super.describeOptions();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
        if (wrapper) {
            return delegate.evalInContext(source, node, mFrame);
        } else {
            return super.evalInContext(source, node, mFrame);
        }
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        if (wrapper) {
            return delegate.parse(request);
        } else {
            return super.parse(request);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected CallTarget parse(Source code, Node context, String... argumentNames) throws Exception {
        if (wrapper) {
            return delegate.parse(code, context, argumentNames);
        } else {
            return super.parse(code, context, argumentNames);
        }
    }

}
