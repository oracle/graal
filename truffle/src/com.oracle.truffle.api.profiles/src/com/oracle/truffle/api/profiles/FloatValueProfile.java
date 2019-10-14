/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.profiles;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * <p>
 * Specialized value profile to capture certain properties of <code>float</code> runtime values.
 * Value profiles require a runtime check in their initialized state to verify their profiled
 * assumption. Value profiles are limited to capture monomorphic profiles only. This means that if
 * two or more values are profiled within a single profile then the profile has no effect. If the
 * value assumption is invalidated in compiled code then it is invalidated.
 * </p>
 *
 * <p>
 * <b> Usage example: </b>
 *
 * <pre>
 * class SampleNode extends Node {
 *
 *     final FloatValueProfile profile = FloatValueProfile.createRawIdentityProfile();
 *
 *     float execute(float input) {
 *         float profiledValue = profile.profile(input);
 *         // compiler may know now more about profiledValue
 *         return profiledValue;
 *     }
 * }
 * </pre>
 * <p>
 *
 *
 * {@inheritDoc}
 *
 * @see #createRawIdentityProfile()
 * @see ValueProfile
 * @since 0.10
 */
public abstract class FloatValueProfile extends Profile {

    FloatValueProfile() {
    }

    /** @since 0.10 */
    public abstract float profile(float value);

    /**
     * Returns a value profile that profiles the exact value of a <code>float</code> using
     * {@link Float#floatToRawIntBits(float)}.
     *
     * @see IntValueProfile
     * @since 0.10
     */
    public static FloatValueProfile createRawIdentityProfile() {
        if (Profile.isProfilingEnabled()) {
            return Enabled.create();
        } else {
            return Disabled.INSTANCE;
        }
    }

    /**
     * Returns the uncached version of the profile. The uncached version of a profile does nothing.
     *
     * @since 19.0
     */
    public static FloatValueProfile getUncached() {
        return Disabled.INSTANCE;
    }

    static final class Enabled extends FloatValueProfile {

        private static final byte UNINITIALIZED = 0;
        private static final byte SPECIALIZED = 1;
        private static final byte GENERIC = 2;

        @CompilationFinal private float cachedValue;
        @CompilationFinal private int cachedRawValue;
        @CompilationFinal private byte state = 0;

        @Override
        public float profile(float value) {
            byte localState = this.state;
            if (localState != GENERIC) {
                if (localState == SPECIALIZED) {
                    if (cachedRawValue == Float.floatToRawIntBits(value)) {
                        return cachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (localState == UNINITIALIZED) {
                    this.cachedValue = value;
                    this.cachedRawValue = Float.floatToRawIntBits(value);
                    this.state = SPECIALIZED;
                } else {
                    this.state = GENERIC;
                }
            }
            return value;
        }

        boolean isGeneric() {
            return state == GENERIC;
        }

        boolean isUninitialized() {
            return state == UNINITIALIZED;
        }

        float getCachedValue() {
            return cachedValue;
        }

        @Override
        public String toString() {
            return toString(FloatValueProfile.class, state == UNINITIALIZED, state == GENERIC, //
                            String.format("value == (float)%s (raw %h)", cachedValue, cachedRawValue));
        }

        /* Needed for lazy class loading. */
        static FloatValueProfile create() {
            return new Enabled();
        }
    }

    static final class Disabled extends FloatValueProfile {

        static final FloatValueProfile INSTANCE = new Disabled();

        @Override
        protected Object clone() {
            return INSTANCE;
        }

        @Override
        public float profile(float value) {
            return value;
        }

        @Override
        public String toString() {
            return toStringDisabled(FloatValueProfile.class);
        }

    }
}
