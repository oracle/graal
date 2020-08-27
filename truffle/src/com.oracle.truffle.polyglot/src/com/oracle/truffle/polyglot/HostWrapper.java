/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Proxy;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;

interface HostWrapper {

    Object getGuestObject();

    PolyglotContextImpl getContext();

    PolyglotLanguageContext getLanguageContext();

    static boolean isInstance(Object v) {
        if (v == null) {
            return false;
        } else if (v instanceof Proxy) {
            return isHostProxy(v);
        } else {
            return v instanceof HostWrapper;
        }
    }

    @TruffleBoundary
    static boolean isHostProxy(Object v) {
        if (Proxy.isProxyClass(v.getClass())) {
            return Proxy.getInvocationHandler(v) instanceof HostWrapper;
        } else {
            return false;
        }
    }

    static HostWrapper asInstance(Object v) {
        if (v instanceof Proxy) {
            return getHostProxy(v);
        } else {
            return (HostWrapper) v;
        }
    }

    @TruffleBoundary
    static HostWrapper getHostProxy(Object v) {
        return (HostWrapper) Proxy.getInvocationHandler(v);
    }

    @TruffleBoundary
    static boolean equalsProxy(HostWrapper wrapper, Object other) {
        if (other == null) {
            return false;
        } else if (Proxy.isProxyClass(other.getClass())) {
            return equals(wrapper.getLanguageContext(), wrapper.getGuestObject(), getHostProxy(other).getGuestObject());
        } else {
            return false;
        }
    }

    @TruffleBoundary
    static boolean equals(PolyglotLanguageContext languageContext, Object receiver, Object obj) {
        if (obj == null) {
            return false;
        } else if (receiver == obj) {
            return true;
        }
        if (languageContext != null && (languageContext.context.closed || languageContext.context.invalid)) {
            return false;
        }
        Object prev = null;
        try {
            prev = PolyglotValue.hostEnter(languageContext);
        } catch (Throwable t) {
            // enter might fail if context was closed asynchonously.
            // Can no longer call interop.
            return false;
        }
        try {
            InteropLibrary receiverLib = InteropLibrary.getUncached(receiver);
            InteropLibrary objLib = InteropLibrary.getUncached(obj);
            return receiverLib.isIdentical(receiver, obj, objLib);
        } catch (Throwable t) {
            // propagate errors in languages they should be reported.
            throw PolyglotImpl.guestToHostException(languageContext, t);
        } finally {
            try {
                PolyglotValue.hostLeave(languageContext, prev);
            } catch (Throwable t) {
                // ignore errors leaving we cannot propagate them.
            }
        }

    }

    @TruffleBoundary
    static int hashCode(PolyglotLanguageContext languageContext, Object receiver) {
        if (languageContext != null && (languageContext.context.closed || languageContext.context.invalid)) {
            return System.identityHashCode(receiver);
        }
        Object prev = null;
        try {
            prev = PolyglotValue.hostEnter(languageContext);
        } catch (Throwable t) {
            // enter might fail if context was closed.
            // Can no longer call interop.
            return System.identityHashCode(receiver);
        }
        try {
            InteropLibrary receiverLib = InteropLibrary.getUncached(receiver);
            if (receiverLib.hasIdentity(receiver)) {
                return receiverLib.identityHashCode(receiver);
            } else {
                return System.identityHashCode(receiver);
            }
        } catch (Throwable t) {
            // propagate errors in languages they should be reported.
            throw PolyglotImpl.guestToHostException(languageContext, t);
        } finally {
            try {
                PolyglotValue.hostLeave(languageContext, prev);
            } catch (Throwable t) {
                // ignore errors leaving we cannot propagate them.
            }
        }
    }

    static String toString(HostWrapper thisObj) {
        PolyglotLanguageContext thisContext = thisObj.getLanguageContext();
        Object thisGuestObject = thisObj.getGuestObject();
        if (thisContext != null) {
            try {
                return thisContext.asValue(thisGuestObject).toString();
            } catch (Exception e) {
            }
        }
        return "Error in toString()";
    }

}
