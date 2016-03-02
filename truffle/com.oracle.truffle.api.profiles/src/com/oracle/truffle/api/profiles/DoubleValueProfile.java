/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.profiles;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * <p>
 * Specialized value profile to capture certain properties of <code>double</code> runtime values.
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
 *     final DoubleValueProfile profile = DoubleValueProfile.createRawIdentityProfile();
 * 
 *     double execute(double input) {
 *         double profiledValue = profile.profile(input);
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
public abstract class DoubleValueProfile extends Profile {

    DoubleValueProfile() {
    }

    /** @since 0.10 */
    public abstract double profile(double value);

    /**
     * Returns a value profile that profiles the exact value of a <code>double</code> using
     * {@link Double#doubleToRawLongBits(double)}.
     *
     * @see IntValueProfile
     * @since 0.10
     */
    public static DoubleValueProfile createRawIdentityProfile() {
        if (Profile.isProfilingEnabled()) {
            return Enabled.create();
        } else {
            return Disabled.INSTANCE;
        }
    }

    static final class Enabled extends DoubleValueProfile {

        private static final byte UNINITIALIZED = 0;
        private static final byte SPECIALIZED = 1;
        private static final byte GENERIC = 2;

        @CompilationFinal private double cachedValue;
        @CompilationFinal private long cachedRawValue;
        @CompilationFinal private byte state = 0;

        @Override
        public double profile(double value) {
            byte localState = this.state;
            if (localState != GENERIC) {
                if (localState == SPECIALIZED) {
                    if (cachedRawValue == Double.doubleToRawLongBits(value)) {
                        return cachedValue;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (localState == UNINITIALIZED) {
                    this.cachedValue = value;
                    this.cachedRawValue = Double.doubleToRawLongBits(value);
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

        double getCachedValue() {
            return cachedValue;
        }

        @Override
        public String toString() {
            return toString(DoubleValueProfile.class, state == UNINITIALIZED, state == GENERIC, //
                            String.format("value == (double)%s (raw %h)", cachedValue, cachedRawValue));
        }

        /* Needed for lazy class loading. */
        static DoubleValueProfile create() {
            return new Enabled();
        }
    }

    static final class Disabled extends DoubleValueProfile {

        static final DoubleValueProfile INSTANCE = new Disabled();

        @Override
        protected Object clone() {
            return INSTANCE;
        }

        @Override
        public double profile(double value) {
            return value;
        }

        @Override
        public String toString() {
            return toStringDisabled(DoubleValueProfile.class);
        }

    }

}
