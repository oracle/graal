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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * @deprecated use {@link com.oracle.truffle.api.profiles.ConditionProfile#createCountingProfile()}
 *             instead
 * @since 0.8 or earlier
 */
@Deprecated
@SuppressWarnings("deprecation")
public final class CountingConditionProfile extends ConditionProfile {

    @CompilationFinal private int trueCount;
    @CompilationFinal private int falseCount;

    CountingConditionProfile() {
        /* package protected constructor */
    }

    /** @since 0.8 or earlier */
    @Override
    public boolean profile(boolean value) {
        if (value) {
            if (trueCount == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (CompilerDirectives.inInterpreter()) {
                if (trueCount < Integer.MAX_VALUE) {
                    trueCount++;
                }
            }
        } else {
            if (falseCount == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (CompilerDirectives.inInterpreter()) {
                if (falseCount < Integer.MAX_VALUE) {
                    falseCount++;
                }
            }
        }
        return CompilerDirectives.injectBranchProbability((double) trueCount / (double) (trueCount + falseCount), value);
    }

    /** @since 0.8 or earlier */
    public int getTrueCount() {
        return trueCount;
    }

    /** @since 0.8 or earlier */
    public int getFalseCount() {
        return falseCount;
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        return String.format("%s(trueCount=%s, falseCount=%s)@%x", getClass().getSimpleName(), trueCount, falseCount, hashCode());
    }
}
