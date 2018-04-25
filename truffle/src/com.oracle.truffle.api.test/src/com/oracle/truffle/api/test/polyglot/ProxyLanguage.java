/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.util.function.Consumer;

import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;

/**
 * Reusable language for testing that allows wrap all methods.
 */
@TruffleLanguage.Registration(id = ProxyLanguage.ID, name = ProxyLanguage.ID, version = "1.0", contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
@ProvidedTags({ExpressionTag.class, StatementTag.class, RootTag.class})
public class ProxyLanguage extends TruffleLanguage<LanguageContext> {

    public static final String ID = "proxyLanguage";

    public static class LanguageContext {
        final Env env;

        LanguageContext(Env env) {
            this.env = env;
        }

        public Env getEnv() {
            return env;
        }
    }

    private static volatile ProxyLanguage delegate = new ProxyLanguage();
    static {
        delegate.wrapper = false;
    }
    protected boolean wrapper = true;
    protected ProxyLanguage languageInstance;

    private Consumer<LanguageContext> onCreate;

    public static <T extends ProxyLanguage> T setDelegate(T delegate) {
        ((ProxyLanguage) delegate).wrapper = false;
        ProxyLanguage.delegate = delegate;
        return delegate;
    }

    public void setOnCreate(Consumer<LanguageContext> onCreate) {
        this.onCreate = onCreate;
    }

    public static LanguageContext getCurrentContext() {
        return getCurrentContext(ProxyLanguage.class);
    }

    public static LanguageContext getCurrentLanguageContext(Class<? extends ProxyLanguage> languageClass) {
        return getCurrentContext(languageClass);
    }

    public static ProxyLanguage getCurrentLanguage() {
        return getCurrentLanguage(ProxyLanguage.class);
    }

    public static ContextReference<LanguageContext> getCurrentContextReference() {
        return getCurrentLanguage(ProxyLanguage.class).getContextReference();
    }

    @Override
    protected LanguageContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.createContext(env);
        } else {
            LanguageContext c = new LanguageContext(env);
            if (onCreate != null) {
                onCreate.accept(c);
            }
            return c;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Object getLanguageGlobal(LanguageContext context) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.getLanguageGlobal(context);
        } else {
            return null;
        }
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.isObjectOfLanguage(object);
        } else {
            return false;
        }
    }

    @Override
    protected void finalizeContext(LanguageContext context) {
        if (wrapper) {
            delegate.finalizeContext(context);
        } else {
            super.finalizeContext(context);
        }
    }

    @Override
    protected void disposeContext(LanguageContext context) {
        if (wrapper) {
            delegate.languageInstance = this;
            delegate.disposeContext(context);
        } else {
            super.disposeContext(context);
        }
    }

    @Override
    protected void disposeThread(LanguageContext context, Thread thread) {

        if (wrapper) {
            delegate.languageInstance = this;
            delegate.disposeThread(context, thread);
        } else {
            super.disposeThread(context, thread);
        }
    }

    @Override
    protected Object findMetaObject(LanguageContext context, Object value) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.findMetaObject(context, value);
        } else {
            return value.toString();
        }
    }

    @Override
    protected SourceSection findSourceLocation(LanguageContext context, Object value) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.findSourceLocation(context, value);
        } else {
            return super.findSourceLocation(context, value);
        }
    }

    @Override
    protected void initializeContext(LanguageContext context) throws Exception {
        if (wrapper) {
            delegate.languageInstance = this;
            delegate.initializeContext(context);
        } else {
            super.initializeContext(context);
        }

    }

    @SuppressWarnings("deprecation")
    @Override
    protected boolean initializeMultiContext() {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.initializeMultiContext();
        } else {
            return super.initializeMultiContext();
        }
    }

    @Override
    protected void initializeMultipleContexts() {
        if (wrapper) {
            delegate.languageInstance = this;
            delegate.initializeMultipleContexts();
        } else {
            super.initializeMultipleContexts();
        }
    }

    @Override
    protected void initializeMultiThreading(LanguageContext context) {
        if (wrapper) {
            delegate.languageInstance = this;
            delegate.initializeMultiThreading(context);
        } else {
            super.initializeMultiThreading(context);
        }
    }

    @Override
    protected void initializeThread(LanguageContext context, Thread thread) {
        if (wrapper) {
            delegate.languageInstance = this;
            delegate.initializeThread(context, thread);
        } else {
            super.initializeThread(context, thread);
        }
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.isThreadAccessAllowed(thread, singleThreaded);
        } else {
            return super.isThreadAccessAllowed(thread, singleThreaded);
        }
    }

    @Override
    protected boolean isVisible(LanguageContext context, Object value) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.isVisible(context, value);
        } else {
            return super.isVisible(context, value);
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.getOptionDescriptors();
        } else {
            return super.getOptionDescriptors();
        }
    }

    @Override
    protected String toString(LanguageContext context, Object value) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.toString(context, value);
        } else {
            return value.toString();
        }
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.parse(request);
        } else {
            return super.parse(request);
        }
    }

    @Override
    protected Iterable<Scope> findTopScopes(LanguageContext context) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.findTopScopes(context);
        } else {
            return super.findTopScopes(context);
        }
    }

    @Override
    protected Iterable<Scope> findLocalScopes(LanguageContext context, Node node, Frame frame) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.findLocalScopes(context, node, frame);
        } else {
            return super.findLocalScopes(context, node, frame);
        }
    }

    @Override
    protected ExecutableNode parse(InlineParsingRequest request) throws Exception {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.parse(request);
        } else {
            return super.parse(request);
        }
    }

}
