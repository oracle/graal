/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(ReflectionLibrary.class)
final class ScopedGuestObject implements TruffleObject {

    public static final int NO_SCOPE = -1;

    volatile Object delegate;
    private volatile boolean pinned;
    private final AtomicReference<ScopedGuestObject[]> scope;
    private final AtomicInteger pos;
    private static final int VAR_SLOTS_INCREMENT = 20;

    ScopedGuestObject(Object delegate) {
        this.delegate = delegate;
        this.pinned = false;
        this.pos = new AtomicInteger();
        this.scope = new AtomicReference<>();
    }

    @ExportMessage
    Object send(Message message, Object[] args,
                    @CachedLibrary(limit = "3") ReflectionLibrary library,
                    @Cached BranchProfile errorProfile,
                    @Cached ConditionProfile scopeProfile) throws Exception {
        Object d = delegate;
        if (d == null) {
            errorProfile.enter();
            throw ScopingException.createReleaseException(
                            "Released objects cannot be accessed. " +
                                            "Avoid accessing scoped objects after their corresponding method has finished execution. " +
                                            "Alternatively, use Value.pin() to prevent a scoped object from being released after the host call completed.");
        }
        Object retval = library.send(d, message, args);
        if (scopeProfile.profile(!needsScope(retval))) {
            return retval;
        }
        ScopedGuestObject sgo = new ScopedGuestObject(retval);
        appendToScope(sgo);
        return sgo;
    }

    static boolean needsScope(Object o) {
        return o instanceof TruffleObject;
    }

    @TruffleBoundary
    void appendToScope(ScopedGuestObject sgo) {
        int p = pos.getAndIncrement();

        boolean successful = false;
        while (!successful) {
            ScopedGuestObject[] s = scope.get();
            if (s == null) {
                // create the scope lazily
                ScopedGuestObject[] newScope = new ScopedGuestObject[VAR_SLOTS_INCREMENT];
                scope.compareAndSet(null, newScope);
                // we don't care if the compareAndExchange above was successful, just get the
                // current scope array
                s = scope.get();
            }
            if (p == s.length) {
                // we need to grow the array
                ScopedGuestObject[] newScope = new ScopedGuestObject[s.length + VAR_SLOTS_INCREMENT];
                for (int i = 0; i < s.length; i++) {
                    newScope[i] = s[i];
                }
                scope.compareAndSet(s, newScope);
            } else if (p > s.length) {
                // another thread is currently growing the array, wait a little
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            } else {
                s[p] = sgo;
                successful = true;
            }
        }
    }

    void releaseIfNotPinned() {
        if (!pinned) {
            release();
        }
    }

    void release() {
        if (delegate == null) {
            throw ScopingException.createReleaseException("Scoped objects can only be released once.");
        }
        delegate = null;
        ScopedGuestObject[] s = scope.get();
        scope.set(null);

        // release children
        for (int i = 0; i < pos.get(); i++) {
            ScopedGuestObject sgo = s[i];
            if (sgo != null) {
                sgo.releaseIfNotPinned();
            }
        }
    }

    public void pin() {
        if (delegate == null) {
            throw ScopingException.createReleaseException("Released objects cannot be pinned.");
        }
        pinned = true;
    }

    private static final class ScopingException extends AbstractTruffleException {

        private static final long serialVersionUID = -6740925699441080285L;
        private static final String RELEASE_EXCEPTION_MSG = "This scoped object has already been released. ";
        private final String message;

        private ScopingException(String message) {
            this.message = message;
        }

        @TruffleBoundary
        private static ScopingException createReleaseException(String message) {
            return new ScopingException(RELEASE_EXCEPTION_MSG + message);
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    static ScopingException createNoScopeException() {
        return new ScopingException("Not a scoped object");
    }
}
