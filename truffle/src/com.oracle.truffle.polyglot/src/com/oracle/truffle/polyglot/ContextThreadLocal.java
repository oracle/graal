/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;

final class ContextThreadLocal extends ThreadLocal<Object> {

    private final Assumption singleThread = Truffle.getRuntime().createAssumption("constant context store");
    private PolyglotContextImpl firstContext;
    @CompilationFinal private volatile Thread firstThread;

    @Override
    protected Object initialValue() {
        if (Thread.currentThread() == firstThread) {
            // must only happen once
            Object context = firstContext;
            firstContext = null;
            firstThread = null;
            return context;
        }
        return null;
    }

    public boolean isSet() {
        if (singleThread.isValid()) {
            boolean set = firstContext != null;
            return Thread.currentThread() == firstThread && set;
        } else {
            return getTL() != null;
        }
    }

    public Object getNoThreadCheck() {
        if (singleThread.isValid()) {
            return firstContext;
        } else {
            return getTL();
        }
    }

    @Override
    public Object get() {
        Object context;
        if (singleThread.isValid()) {
            if (Thread.currentThread() == firstThread) {
                context = firstContext;
            } else {
                CompilerDirectives.transferToInterpreter();
                context = getImplSlowPath();
            }
        } else {
            context = getTL();
        }
        return context;
    }

    @Override
    public void set(Object value) {
        setReturnParent(value);
    }

    Object setReturnParent(Object value) {
        if (singleThread.isValid()) {
            Object prev;
            if (Thread.currentThread() == firstThread) {
                prev = this.firstContext;
                this.firstContext = (PolyglotContextImpl) value;
            } else {
                CompilerDirectives.transferToInterpreter();
                prev = setReturnParentSlowPath(value);
            }
            return prev;
        } else {
            return setTLReturnParent(value);
        }
    }

    private synchronized Object getImplSlowPath() {
        if (!singleThread.isValid()) {
            return getTL();
        }
        return null;
    }

    @TruffleBoundary
    private Object getTL() {
        Thread current = Thread.currentThread();
        if (current instanceof PolyglotThread) {
            PolyglotThread polyglotThread = ((PolyglotThread) current);
            Object context = polyglotThread.context;
            if (context == null && firstThread == current) {
                context = polyglotThread.context = firstContext;
                firstContext = null;
                firstThread = null;
            }
            return context;
        } else {
            return super.get();
        }
    }

    @TruffleBoundary
    private Object setTLReturnParent(Object context) {
        Thread current = Thread.currentThread();
        if (current instanceof PolyglotThread) {
            PolyglotThread polyglotThread = ((PolyglotThread) current);
            Object prev = polyglotThread.context;
            polyglotThread.context = context;
            return prev;
        } else {
            Object prev = super.get();
            super.set(context);
            return prev;
        }
    }

    private synchronized Object setReturnParentSlowPath(Object context) {
        if (!singleThread.isValid()) {
            return setTLReturnParent(context);
        }
        Thread currentThread = Thread.currentThread();
        Thread storeThread = firstThread;
        Object prev = this.firstContext;
        if (currentThread == storeThread) {
            this.firstContext = (PolyglotContextImpl) context;
        } else {
            if (storeThread == null) {
                this.firstThread = currentThread;
                this.firstContext = (PolyglotContextImpl) context;
            } else {
                singleThread.invalidate();
                return setTLReturnParent(context);
            }
        }
        return prev;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

}
