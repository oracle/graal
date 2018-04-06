/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * Abstract character matcher that profiles the results of {@link #match(char)} with a binary
 * {@link ConditionProfile}.
 */
public abstract class ProfiledCharMatcher implements CharMatcher {

    private final boolean invert;

    private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

    /**
     * Construct a new {@link ProfiledCharMatcher}.
     * 
     * @param invert if this is set to true, the result of {@link #match(char)} is always inverted.
     */
    protected ProfiledCharMatcher(boolean invert) {
        this.invert = invert;
    }

    @Override
    public boolean match(char c) {
        return profile.profile(matchChar(c) != invert);
    }

    protected abstract boolean matchChar(char c);

    protected String modifiersToString() {
        return invert ? "!" : "";
    }

    static int highByte(int i) {
        return i >> Byte.SIZE;
    }

    static int lowByte(int i) {
        return i & 0xff;
    }
}
