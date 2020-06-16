/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * <p>
 * Specialized value profile to capture certain properties of <code>Object</code> runtime values.
 * Value profiles require a runtime check in their initialized state to verify their profiled
 * assumption. Value profiles are limited to capture monomorphic profiles only. This means that if
 * two or more identities or classes are profiles within a single profile then the profile has no
 * effect and no overhead after compilation. There are specialized versions of this profile with
 * less interpreter footprint for {@link ConditionProfile boolean}, {@link ByteValueProfile byte},
 * {@link IntValueProfile int}, {@link LongValueProfile long}, {@link FloatValueProfile float} and
 * {@link DoubleValueProfile double} values.
 * </p>
 *
 * <p>
 * <b> Usage example: </b>
 *
 * <pre>
 * class SampleNode extends Node {
 *
 * final ValueProfile profile = ValueProfile.create{Identity,Class}Profile();
 *
 *     Object execute(Object input) {
 *         Object profiledValue = profile.profile(input);
 *         // compiler may know now more about profiledValue
 *         return profieldValue;
 *     }
 * }
 * </pre>
 * <p>
 *
 *
 * {@inheritDoc}
 *
 * @see #createIdentityProfile()
 * @see #createClassProfile()
 * @since 0.10
 */
public abstract class ValueProfile extends Profile {

    ValueProfile() {
    }

    /** @since 0.10 */
    public abstract <T> T profile(T value);

    /**
     * <p>
     * Returns a value profile that profiles the exact class of a value. It will check the class of
     * the profiled value and provide additional information to the compiler if only non-null values
     * of exactly one concrete Java class are passed as a parameter to the
     * {@link ValueProfile#profile} method. This can be beneficial if subsequent code can take
     * advantage of knowing the concrete class of the value. The profile will degrade to the generic
     * case if a null value or if at least two instances of two different Java classes are
     * registered.
     * </p>
     *
     * <p>
     * <b>Compilation notes:</b> Value profiles require a runtime check in their initialized state
     * to verify their profiled class. If two classes have been seen on a single profile instance
     * then this profile will transition to a generic state with no overhead.
     * </P>
     *
     * @see ValueProfile usage example
     * @since 0.10
     */
    public static ValueProfile createClassProfile() {
        if (Profile.isProfilingEnabled()) {
            return ExactClass.create();
        } else {
            return Disabled.INSTANCE;
        }
    }

    /**
     * <p>
     * Returns a value profile that profiles the object identity of a value. A single instance can
     * only profile one particular instance.
     * </p>
     *
     * <p>
     * <b>Compilation notes:</b> Identity profiles require a runtime check to verify their profiled
     * object identity. If two identities have been seen on a single profile instance then this
     * profile will transition to a generic state with no overhead.
     * </p>
     *
     * @since 0.10
     */
    public static ValueProfile createIdentityProfile() {
        if (Profile.isProfilingEnabled()) {
            return Identity.create();
        } else {
            return Disabled.INSTANCE;
        }
    }

    /**
     * <p>
     * Returns a value profile that profiles the object equality of a value. A single instance can
     * only profile one set of equal values.
     * </p>
     *
     * <p>
     * <b>Compilation notes:</b> Equality profiles inline the body of the equal method of the first
     * profiled value in order to verify its assumption. Please take care that you do this only for
     * equals implementations that your guest language actually has control over otherwise your
     * compiled code might contain recursions or too much code. If two non equal objects have been
     * seen on a single profile instance then this profile will transition to a generic state with
     * no overhead.
     * </p>
     *
     * @since 0.10
     */
    public static ValueProfile createEqualityProfile() {
        if (Profile.isProfilingEnabled()) {
            return Equality.create();
        } else {
            return Disabled.INSTANCE;
        }
    }

    /**
     * Returns the uncached version of the profile. The uncached version of a profile does nothing.
     *
     * @since 19.0
     */
    public static ValueProfile getUncached() {
        return Disabled.INSTANCE;
    }

    static final class Disabled extends ValueProfile {

        static final ValueProfile INSTANCE = new Disabled();

        @Override
        protected Object clone() {
            return INSTANCE;
        }

        @Override
        public <T> T profile(T value) {
            return value;
        }

        @Override
        public String toString() {
            return toStringDisabled(ValueProfile.class);
        }

    }

    static final class Equality extends ValueProfile {

        private static final Object GENERIC = new Object();

        @CompilationFinal protected Object cachedValue = null;

        Equality() {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T profile(T newValue) {
            // Field needs to be cached in local variable for thread safety and startup speed.
            Object cached = this.cachedValue;
            if (cached != GENERIC) {
                if (cached != null && cached.equals(newValue)) {
                    return (T) cached;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    if (cached == null && newValue != null) {
                        cachedValue = newValue;
                    } else {
                        cachedValue = GENERIC;
                    }
                }
            }
            return newValue;
        }

        public boolean isGeneric() {
            return getCachedValue() == GENERIC;
        }

        public boolean isUninitialized() {
            return getCachedValue() == null;
        }

        public Object getCachedValue() {
            return cachedValue;
        }

        @Override
        public String toString() {
            return toString(ValueProfile.class, isUninitialized(), isGeneric(),
                            String.format("value == %s@%x", cachedValue != null ? cachedValue.getClass().getSimpleName() : "null", Objects.hash(cachedValue)));
        }

        /* Needed for lazy class loading. */
        static ValueProfile create() {
            return new Equality();
        }

    }

    static final class Identity extends ValueProfile {

        private static final Object UNINITIALIZED = new Object();
        private static final Object GENERIC = new Object();

        @CompilationFinal protected Object cachedValue = UNINITIALIZED;

        Identity() {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T profile(T newValue) {
            // Field needs to be cached in local variable for thread safety and startup speed.
            Object cached = this.cachedValue;
            if (cached != GENERIC) {
                if (cached == newValue) {
                    return (T) cached;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    if (cachedValue == UNINITIALIZED) {
                        cachedValue = newValue;
                    } else {
                        cachedValue = GENERIC;
                    }
                }
            }
            return newValue;
        }

        public boolean isGeneric() {
            return getCachedValue() == GENERIC;
        }

        public boolean isUninitialized() {
            return getCachedValue() == UNINITIALIZED;
        }

        public Object getCachedValue() {
            return cachedValue;
        }

        @Override
        public String toString() {
            return toString(ValueProfile.class, isUninitialized(), isGeneric(),
                            String.format("value == %s@%x", cachedValue != null ? cachedValue.getClass().getSimpleName() : "null", Objects.hash(cachedValue)));
        }

        /* Needed for lazy class loading. */
        static ValueProfile create() {
            return new Identity();
        }

    }

    static final class ExactClass extends ValueProfile {

        @CompilationFinal protected Class<?> cachedClass;

        ExactClass() {
        }

        public static ValueProfile create() {
            return new ExactClass();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T profile(T value) {
            // Field needs to be cached in local variable for thread safety and startup speed.
            Class<?> clazz = cachedClass;
            if (clazz != Object.class) {
                if (clazz != null && value != null && value.getClass() == clazz) {
                    if (CompilerDirectives.inInterpreter()) {
                        return value;
                    } else {
                        return (T) CompilerDirectives.castExact(value, clazz);
                    }
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    if (clazz == null && value != null) {
                        cachedClass = value.getClass();
                    } else {
                        cachedClass = Object.class;
                    }
                }
            }
            return value;
        }

        boolean isGeneric() {
            return cachedClass == Object.class;
        }

        boolean isUninitialized() {
            return cachedClass == null;
        }

        Class<?> getCachedClass() {
            return cachedClass;
        }

        @Override
        public String toString() {
            return toString(ValueProfile.class, cachedClass == null, cachedClass == Object.class,
                            String.format("value.getClass() == %s.class", cachedClass != null ? cachedClass.getSimpleName() : "null"));
        }

    }

}
