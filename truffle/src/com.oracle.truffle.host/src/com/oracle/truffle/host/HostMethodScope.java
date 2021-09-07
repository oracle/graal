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

import java.lang.reflect.Field;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.host.HostMethodDesc.SingleMethod;

import sun.misc.Unsafe;

final class HostMethodScope {

    private static final ScopedObject[] EMTPY_SCOPE_ARRAY = new ScopedObject[0];
    private static final Unsafe UNSAFE = getUnsafe();

    private ScopedObject[] scope;
    private int nextDynamicIndex;

    HostMethodScope(ScopedObject[] staticScope) {
        this.scope = staticScope;
        this.nextDynamicIndex = staticScope.length;
    }

    HostMethodScope(int initialDynamicCapacity) {
        this.scope = new ScopedObject[initialDynamicCapacity];
        this.nextDynamicIndex = 0;
    }

    static HostMethodScope openDynamic(SingleMethod method, int argumentCount, BranchProfile seenScope) {
        if (method.hasScopedParameters()) {
            seenScope.enter();
            return new HostMethodScope(argumentCount);
        }
        return null;
    }

    static HostMethodScope openStatic(SingleMethod method) {
        CompilerAsserts.partialEvaluationConstant(method);
        if (method.hasScopedParameters()) {
            int[] scopePos = method.getScopedParameters();
            ScopedObject[] scopeArray;
            if (scopePos.length > 0) {
                scopeArray = new ScopedObject[scopePos.length];
            } else {
                scopeArray = EMTPY_SCOPE_ARRAY;
            }
            return new HostMethodScope(scopeArray);
        }
        return null;
    }

    static Object addToScopeDynamic(HostMethodScope scope, Object value) {
        if (scope != null) {
            assert !(value instanceof ScopedObject);
            return scope.addToScopeDynamicImpl(value);
        }
        return value;
    }

    static Object addToScopeStatic(HostMethodScope scope, SingleMethod method, int argumentIndex, Object value) {
        CompilerAsserts.partialEvaluationConstant(method);
        if (scope != null) {
            assert !(value instanceof ScopedObject);
            int[] scopePos = method.getScopedParameters();
            int targetIndex = scopePos[argumentIndex];
            if (targetIndex != SingleMethod.NO_SCOPE) {
                return scope.scope[targetIndex] = new ScopedObject(scope, value, targetIndex);
            }
        }
        return value;
    }

    static void pin(Object value) {
        if (value instanceof ScopedObject) {
            ((ScopedObject) value).pin();
        }
    }

    static void closeStatic(HostMethodScope scope, SingleMethod method, BranchProfile seenDynamicScope) {
        if (method.hasScopedParameters()) {
            int[] scopePos = method.getScopedParameters();
            ScopedObject[] array = scope.scope;
            for (int i = 0; i < scopePos.length; i++) {
                ScopedObject o = array[i];
                // static scoped objects may be null on error of the host invocation
                if (o != null) {
                    o.release();
                }
            }
            for (int i = scopePos.length; i < array.length; i++) {
                seenDynamicScope.enter();
                ScopedObject o = array[i];
                // static scoped objects may be null on error of the host invocation
                if (o != null) {
                    o.release();
                }
            }

        } else {
            assert scope == null;
        }
    }

    static void closeDynamic(HostMethodScope scope, SingleMethod method) {
        if (method.hasScopedParameters()) {
            ScopedObject[] array = scope.scope;
            for (int i = 0; i < array.length; i++) {
                ScopedObject o = array[i];
                // static scoped objects may be null on error of the host invocation
                if (o != null) {
                    o.release();
                }
            }
        } else {
            assert scope == null;
        }
    }

    @TruffleBoundary
    private synchronized Object addToScopeDynamicImpl(Object argument) {
        ScopedObject[] localScope = scope;
        int index = nextDynamicIndex;
        if (index >= localScope.length) {
            scope = localScope = Arrays.copyOf(localScope, localScope.length << 1);
        }
        // index overflowed and turned negative
        if (index < 0) {
            throw createReleaseException("Too many scoped values created for scoped method instance.");
        }

        Object newArgument = localScope[index] = new ScopedObject(this, argument, index);
        nextDynamicIndex = index + 1;
        return newArgument;
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    static final class PinnedObject implements TruffleObject {

        final Object delegate;

        PinnedObject(Object delegate) {
            this.delegate = delegate;
        }

    }

    @ExportLibrary(ReflectionLibrary.class)
    static final class ScopedObject implements TruffleObject {

        static final Object OTHER_VALUE = new Object();
        static final ReflectionLibrary OTHER_VALUE_UNCACHED = ReflectionLibrary.getFactory().getUncached(OTHER_VALUE);
        static final long DELEGATE_OFFSET;
        static {
            Field f;
            try {
                f = ScopedObject.class.getDeclaredField("delegate");
            } catch (NoSuchFieldException | SecurityException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            DELEGATE_OFFSET = UNSAFE.objectFieldOffset(f);
        }

        volatile Object delegate; // null if freed
        volatile HostMethodScope scope; // null if pinned
        /*
         * Index in the scope to remove it from the scope list when pinned. Negative index indicate
         * a position in the dynamic scope and positive indices a postion in the static scope.
         */
        private final int index;

        ScopedObject(HostMethodScope scope, Object delegate, int index) {
            this.delegate = delegate;
            this.scope = scope;
            this.index = index;
        }

        @ExportMessage
        Object send(Message message, Object[] args,
                        @CachedLibrary(limit = "5") ReflectionLibrary library,
                        @Cached BranchProfile seenError,
                        @Cached BranchProfile seenOther) throws Exception {
            if (message.getLibraryClass() != InteropLibrary.class) {
                seenOther.enter();
                return fallbackSend(message, args);
            }
            Object d = this.delegate;
            if (d == null) {
                seenError.enter();
                throw createReleaseException("Released objects cannot be accessed. " +
                                "Avoid accessing scoped objects after their corresponding method has finished execution. " +
                                "Alternatively, use Value.pin() to prevent a scoped object from being released after the host call completed.");
            }
            assert d != null : "delegate must not be null here";
            Object returnValue = library.send(d, message, args);
            if (message.getReturnType() == Object.class) {
                /*
                 * Object return type indicates for an interop message that any interop value may be
                 * returned.
                 */
                return HostMethodScope.addToScopeDynamic(this.scope, returnValue);
            } else {
                return returnValue;
            }
        }

        @TruffleBoundary
        private static Object fallbackSend(Message message, Object[] args) throws Exception {
            /*
             * This is a convenient way to trigger the default implementation for all other
             * libraries. We do not want to redirect anything other than interop.
             */
            return OTHER_VALUE_UNCACHED.send(OTHER_VALUE, message, args);
        }

        void release() {
            Object d = this.delegate;
            assert d != null;
            if (d instanceof PinnedObject || !UNSAFE.compareAndSwapObject(this, DELEGATE_OFFSET, d, null)) {
                // pinned in the meantime
                // we can assume that as no other thread is releasing other then the current thread
                // therefore the only thing that could have happened here is a pin
                return;
            }
            assert this.delegate == null : "Scoped objects can only be released once.";
        }

        void pin() {
            Object expect;
            HostMethodScope s;
            Object update;
            do {
                s = this.scope;
                expect = this.delegate;
                if (expect instanceof PinnedObject) {
                    // already pinned -> do nothing.
                    return;
                } else if (expect == null) {
                    // released in the meantime
                    throw createReleaseException("Released objects cannot be pinned.");
                }
                // we need to actually change the value for the pin update
                // otherwise we race with release
                update = new PinnedObject(expect);
            } while (!UNSAFE.compareAndSwapObject(this, DELEGATE_OFFSET, expect, update));

            // if the pin was successful we need to clear the reference in the scope array
            // this can be racy as this is not for semantics but for the garbage collector

            /*
             * Its important to free up the referenced scope otherwise pinned objects might keep
             * references alive unnecessarily.
             */
            this.scope = null;

            // we need to lock otherwise dynamic scope might get resized at the same time.
            synchronized (s) {
                s.scope[index] = null;
            }

            assert this.delegate != null : "delegate must not be set to null after pinning ";
        }

        Object unwrapForGuest() {
            Object d = this.delegate;
            if (d == null) {
                throw createReleaseException("Released objects cannot be converted to a guest value.");
            } else {
                if (d instanceof PinnedObject) {
                    return ((PinnedObject) d).delegate;
                } else {
                    return d;
                }
            }
        }

    }

    @TruffleBoundary
    private static RuntimeException createReleaseException(String message) {
        return HostEngineException.toEngineException(HostLanguage.get(null).access, new IllegalStateException("This scoped object has already been released. " + message));
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

}
