/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;

/**
 * Reusable language for testing that allows wrap all methods.
 */
@TruffleLanguage.Registration(id = ProxyLanguage.ID, name = ProxyLanguage.ID, version = "1.0", contextPolicy = TruffleLanguage.ContextPolicy.SHARED, characterMimeTypes = "application/x-proxy-language")
@ProvidedTags({ExpressionTag.class, StatementTag.class, RootBodyTag.class, RootTag.class})
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

    @Override
    protected Object getLanguageView(LanguageContext context, Object value) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.getLanguageView(context, value);
        } else {
            return super.getLanguageView(context, value);
        }
    }

    @Override
    protected Object getScopedView(LanguageContext context, Node location, Frame frame, Object value) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.getScopedView(context, location, frame, value);
        } else {
            return super.getScopedView(context, location, frame, value);
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

    @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    @Override
    protected Object findMetaObject(LanguageContext context, Object value) {
        if (wrapper) {
            delegate.languageInstance = this;
            return delegate.findMetaObject(context, value);
        } else {
            return value.toString();
        }
    }

    @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
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
