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
package com.oracle.truffle.polyglot;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

/*
 * Note: this type may be a good candidate for being standarized in the Truffle utilities package in the future.
 */
@SuppressWarnings("rawtypes")
final class WeakAssumedValue<T> {

    private static final AtomicReferenceFieldUpdater<WeakAssumedValue, Profile> PROFILE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(WeakAssumedValue.class,
                    Profile.class, "profile");
    private static final Assumption INVALID_ASSUMPTION;

    static {
        Assumption assumption = Truffle.getRuntime().createAssumption();
        assumption.invalidate();
        INVALID_ASSUMPTION = assumption;
    }

    @CompilationFinal private volatile Profile<T> profile;
    private final String name;

    WeakAssumedValue(String name) {
        this.name = name;
    }

    public void invalidate() {
        invalidateImpl(this.profile);
    }

    private void invalidateImpl(Profile<T> currentProfile) {
        if (currentProfile != null) {
            currentProfile.assumption.invalidate();
        }
        Profile<?> previous = PROFILE_UPDATER.getAndSet(this, Profile.INVALID);
        assert previous == currentProfile || previous == Profile.INVALID;
    }

    /**
     * Returns <code>true</code> if this assumed value contains a valid constant value, else
     * <code>false</code>.
     */
    public boolean isValid() {
        Profile<T> p = this.profile;
        if (p == null) {
            return false;
        }
        return p.assumption.isValid();
    }

    /**
     * Resets the weak assumed value. Do not use this repeatedly to avoid deoptimization loops.
     */
    public void reset() {
        invalidateImpl(this.profile);
        this.profile = null;
    }

    /**
     * Return the contained value as a constant if it can be resolved or <code>null</code> if it
     * cannot be resolved to a constant. In {@link CompilerDirectives#inInterpreter() interpreted}
     * mode this method always returns <code>null</code>.
     */
    public T getConstant() {
        if (!CompilerDirectives.inCompiledCode() || !CompilerDirectives.isPartialEvaluationConstant(this)) {
            /*
             * This is only relevant if we are in compiled code as in interpreted mode the value is
             * never constant.
             */
            return null;
        }
        Profile<T> p = this.profile;
        if (p != null) {
            if (p.assumption.isValid()) {
                return p.get();
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * Updates the internal value and invalidates the profile if a new value is set.
     */
    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public void update(T newValue) {
        assert newValue != null;
        Profile<T> currentProfile = this.profile;
        if (currentProfile == Profile.INVALID) {
            // already invalid -> nothing to speculate on
            return;
        }
        Profile<T> newProfile;
        if (currentProfile == null) {
            // fresh profile set the profile
            newProfile = new Profile<>(newValue, name);
            if (!PROFILE_UPDATER.compareAndSet(this, currentProfile, newProfile)) {
                update(newValue);
            }
        } else if (currentProfile.get() == newValue) {
            // value is the same. No reason to invalidate.
            return;
        } else {
            invalidateImpl(currentProfile);
        }
    }

    static final class Profile<V> {
        private static final Profile<?> INVALID = new Profile<>();

        // Invariant to simplify conditions: type is non-null if assumption is valid
        final Assumption assumption;
        final TruffleWeakReference<V> reference;

        private Profile() {
            this.assumption = INVALID_ASSUMPTION;
            this.reference = null;
        }

        private Profile(V value, String name) {
            assert value != null;
            this.assumption = Truffle.getRuntime().createAssumption(name);
            this.reference = new TruffleWeakReference<>(value);
        }

        V get() {
            TruffleWeakReference<V> ref = reference;
            if (ref == null) {
                return null;
            }
            return ref.get();
        }
    }

}
