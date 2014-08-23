/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import com.oracle.truffle.api.*;

/**
 * Abstract utility class to speculate on conditions. Condition profiles are intended to be used as
 * part of if conditions.
 *
 * Example usage:
 *
 * <pre>
 * private final ConditionProfile zero = ConditionProfile.createBinaryProfile();
 * 
 * int value = ...;
 * if (zero.profile(value == 0)) {
 *   return 0;
 * } else {
 *   return value;
 * }
 *
 * </pre>
 *
 * All instances of {@code ConditionProfile} (and subclasses) must be held in {@code final} fields
 * for compiler optimizations to take effect.
 *
 * @see #createCountingProfile()
 * @see #createBinaryProfile()
 */
public abstract class ConditionProfile {

    public abstract boolean profile(boolean value);

    /**
     * Returns a {@link ConditionProfile} that speculates on conditions to be never
     * <code>true</code> or to be never <code>false</code>. Additionally to a binary profile this
     * method returns a condition profile that also counts the number of times the condition was
     * true and false. This information is reported to the underlying optimization system using
     * {@link CompilerDirectives#injectBranchProbability(double, boolean)}. Condition profiles are
     * intended to be used as part of if conditions.
     *
     * @see ConditionProfile
     * @see #createBinaryProfile()
     */
    public static ConditionProfile createCountingProfile() {
        return new CountingConditionProfile();
    }

    /**
     * Returns a {@link ConditionProfile} that speculates on conditions to be never true or to be
     * never false. Condition profiles are intended to be used as part of if conditions.
     *
     * @see ConditionProfile
     * @see ConditionProfile#createCountingProfile()
     */
    public static ConditionProfile createBinaryProfile() {
        return new BinaryConditionProfile();
    }

}
