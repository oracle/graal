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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.io.IOException;

import org.graalvm.polyglot.HostAccess;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * Java host language implementation.
 */
final class HostLanguage extends TruffleLanguage<HostContext> {

    @CompilationFinal private GuestToHostCodeCache hostToGuestCodeCache;
    @CompilationFinal HostClassCache hostClassCache; // effectively final
    @CompilationFinal ClassLoader contextClassLoader;     // effectively final

    HostLanguage() {
        this.contextClassLoader = TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader();
    }

    GuestToHostCodeCache getGuestToHostCache() {
        GuestToHostCodeCache cache = this.hostToGuestCodeCache;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            hostToGuestCodeCache = cache = new GuestToHostCodeCache();
        }
        return cache;
    }

    void initializeHostAccess(HostAccess policy) {
        assert policy != null;
        HostClassCache cache = HostClassCache.findOrInitialize(PolyglotImpl.getInstance().getAPIAccess(), policy, contextClassLoader);

        if (this.hostClassCache != null) {
            if (this.hostClassCache.hostAccess.equals(cache.hostAccess)) {
                /*
                 * The cache can be effectively be reused if the same host access configuration
                 * applies.
                 */
            } else {
                throw PolyglotEngineException.illegalState("Found different host access configuration for a context with a shared engine. " +
                                "The host access configuration must be the same for all contexts of an engine. " +
                                "Provide the same host access configuration using the Context.Builder.allowHostAccess method when constructing the context.");
            }
        } else {
            this.hostClassCache = cache;
        }
    }

    @SuppressWarnings("serial")
    static class HostLanguageException extends AbstractTruffleException {

        HostLanguageException(String message) {
            super(message);
        }
    }

    @Override
    @TruffleBoundary
    protected Object getLanguageView(HostContext context, Object value) {
        Object wrapped;
        if (value instanceof TruffleObject) {
            InteropLibrary lib = InteropLibrary.getFactory().getUncached(value);
            try {
                assert !lib.hasLanguage(value) || lib.getLanguage(value) != HostLanguage.class;
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere(e);
            }
            wrapped = ToHostNode.convertToObject(value, context.internalContext.getHostContext(), lib);
        } else {
            wrapped = value;
        }
        return HostObject.forObject(wrapped, context);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        String sourceString = request.getSource().getCharacters().toString();
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {

            @CompilationFinal ContextReference<HostContext> contextRef;

            @Override
            public Object execute(VirtualFrame frame) {
                if (contextRef == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    contextRef = lookupContextReference(HostLanguage.class);
                }
                HostContext context = contextRef.get();
                Class<?> allTarget = context.findClass(sourceString);
                return context.toGuestValue(allTarget);
            }
        });
    }

    @Override
    protected void disposeContext(HostContext context) {
        HostClassLoader cl = context.classloader;
        if (cl != null) {
            try {
                cl.close();
            } catch (IOException e) {
                // lets ignore that
            }
            context.classloader = null;
        }
        super.disposeContext(context);
    }

    @Override
    protected HostContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        return new HostContext(this);
    }

    @Override
    protected Object getScope(HostContext context) {
        return context.topScope;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

}
