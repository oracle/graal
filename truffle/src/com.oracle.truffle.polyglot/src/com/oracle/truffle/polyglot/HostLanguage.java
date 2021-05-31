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
package com.oracle.truffle.polyglot;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.EngineHostAccess;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * Java host language implementation.
 */
final class HostLanguage extends TruffleLanguage<Object> {

    final EngineHostAccess access;
    final PolyglotImpl polyglot;
    final APIAccess api;

    HostLanguage(PolyglotImpl polyglot, EngineHostAccess hostAccess) {
        this.polyglot = polyglot;
        this.access = hostAccess;
        this.api = polyglot.getAPIAccess();
    }

    @SuppressWarnings("serial")
    static class HostLanguageException extends AbstractTruffleException {

        HostLanguageException(String message) {
            super(message);
        }
    }

    @Override
    @TruffleBoundary
    protected Object getLanguageView(Object context, Object value) {
        return access.getLanguageView(context, value);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        String sourceString = request.getSource().getCharacters().toString();
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {

            @CompilationFinal ContextReference<Object> contextRef;

            @Override
            public Object execute(VirtualFrame frame) {
                if (contextRef == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    contextRef = lookupContextReference(HostLanguage.class);
                }
                Object context = contextRef.get();
                return access.findDynamicClass(context, sourceString);
            }
        });
    }

    @Override
    protected void disposeContext(Object context) {
        access.disposeContext(context);
    }

    @Override
    protected boolean patchContext(Object context, Env newEnv) {
        PolyglotLanguageContext languageContext = (PolyglotLanguageContext) EngineAccessor.LANGUAGE.getPolyglotLanguageContext(newEnv);
        PolyglotContextConfig config = languageContext.context.config;
        access.patchHostContext(context, config.hostAccess, config.hostClassLoader, config.classFilter, config.hostClassLoadingAllowed, config.hostLookupAllowed);
        return true;
    }

    @Override
    protected Object createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        PolyglotLanguageContext languageContext = (PolyglotLanguageContext) EngineAccessor.LANGUAGE.getPolyglotLanguageContext(env);
        PolyglotContextConfig config = languageContext.context.config;
        return access.createHostContext(config.hostAccess, config.hostClassLoader, config.classFilter, config.hostClassLoadingAllowed, config.hostLookupAllowed);
    }

    @Override
    protected Object getScope(Object context) {
        return access.getTopScope(context);
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    void addToHostClassPath(PolyglotContextImpl context, TruffleFile entry) {
        if (FileSystems.hasNoIOFileSystem(entry)) {
            throw new HostLanguageException("Host class loading is disabled without IO permissions.");
        }
        access.addToHostClassPath(context.getHostContextObject(), entry);
    }

    Object toGuestValue(Node location, PolyglotContextImpl context, Object hostValue) {
        if (hostValue instanceof Value) {
            Value receiverValue = (Value) hostValue;
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) api.getContext(receiverValue);
            PolyglotContextImpl valueContext = languageContext != null ? languageContext.context : null;
            Object valueReceiver = api.getReceiver(receiverValue);
            if (valueContext != context) {
                valueReceiver = context.migrateValue(location, valueReceiver, valueContext);
            }
            return valueReceiver;
        } else if (HostWrapper.isInstance(hostValue)) {
            return context.migrateHostWrapper(location, HostWrapper.asInstance(hostValue));
        } else {
            return access.toGuestValue(context.getHostContextObject(), hostValue);
        }
    }

}
