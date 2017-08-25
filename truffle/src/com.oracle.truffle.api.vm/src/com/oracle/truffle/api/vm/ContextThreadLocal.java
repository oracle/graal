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
package com.oracle.truffle.api.vm;

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;

final class ContextThreadLocal extends ThreadLocal<Object> {

    private final Assumption constant = Truffle.getRuntime().createAssumption("constant context");
    @CompilationFinal private volatile WeakReference<Object> constantContext = new WeakReference<>(null);

    private final Assumption singleThread = Truffle.getRuntime().createAssumption("constant context store");
    private Object firstContext;
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
        if (constant.isValid()) {
            Object constantC = this.constantContext.get();
            if (context == constantC) {
                // did we read an invalid value?
                if (!constant.isValid()) {
                    CompilerDirectives.transferToInterpreter();
                    return getConstantSlowPath(context);
                }
                return constantC;
            } else {
                CompilerDirectives.transferToInterpreter();
                return getConstantSlowPath(context);
            }
        }
        return context;
    }

    private synchronized Object getConstantSlowPath(Object context) {
        Object localContext = constantContext.get();
        if (localContext != context) {
            if (localContext == null) {
                constantContext = new WeakReference<>(context);
            } else {
                constant.invalidate();
                constantContext.clear();
            }
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
                this.firstContext = value;
            } else {
                CompilerDirectives.transferToInterpreter();
                prev = setReturnParentSlowPath(value);
            }
            return prev;
        }
        return setTLReturnParent(value);
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
        Object prev;
        if (currentThread == storeThread) {
            prev = this.firstContext;
            this.firstContext = context;
        } else {
            if (storeThread == null) {
                this.firstThread = currentThread;
                prev = this.firstContext;
                this.firstContext = context;
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
