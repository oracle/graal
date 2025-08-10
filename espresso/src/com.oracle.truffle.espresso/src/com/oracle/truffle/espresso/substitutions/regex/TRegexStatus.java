/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions.regex;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public enum TRegexStatus {
    Validate("Validate=true", 0),
    Match("MatchingMode=match", Constants.MATCH_AVAILABLE),
    FullMatch("MatchingMode=fullmatch", Constants.FULLMATCH_AVAILABLE),
    Search("MatchingMode=search", Constants.SEARCH_AVAILABLE);

    static final class Constants {
        // @formatter:off
        private static final int SUPPORTED =            0b00_0001;
        private static final int MATCH_AVAILABLE =      0b00_0010;
        private static final int FULLMATCH_AVAILABLE =  0b00_0100;
        private static final int SEARCH_AVAILABLE =     0b00_1000;

        private static final int GUEST_COMPILED =       0b01_0000;
        private static final int INITALIZED =           0b10_0000;
        
        private static final int TREGEX_AVAILABLE_MASK = MATCH_AVAILABLE | FULLMATCH_AVAILABLE | SEARCH_AVAILABLE;
        private static final int VALID_GROUPCOUNT_MASK = TREGEX_AVAILABLE_MASK | GUEST_COMPILED;
        // @formatter:on
    }

    private final String actionString;
    private final int mask;

    TRegexStatus(String actionString, int mask) {
        this.actionString = actionString;
        this.mask = mask;
    }

    public String actionString() {
        return actionString;
    }

    public static int get(Meta meta, StaticObject pattern) {
        assert meta.tRegexSupport != null && meta.tRegexSupport.java_util_regex_Pattern_HIDDEN_status != null;
        return meta.tRegexSupport.java_util_regex_Pattern_HIDDEN_status.getInt(pattern);
    }

    public static void init(Meta meta, StaticObject pattern) {
        set(meta, pattern, Constants.INITALIZED);
    }

    public static void setSupported(Meta meta, StaticObject pattern) {
        set(meta, pattern, Constants.SUPPORTED);
    }

    public static void setTRegexStatus(Meta meta, StaticObject pattern, TRegexStatus action) {
        set(meta, pattern, action.mask);
    }

    public static void setGuestCompiled(Meta meta, StaticObject pattern) {
        set(meta, pattern, Constants.GUEST_COMPILED);
    }

    private static void set(Meta meta, StaticObject pattern, int state) {
        boolean success = false;
        while (!success) {
            int old = get(meta, pattern);
            int newValue = (old | state);
            if (old != newValue) {
                success = meta.tRegexSupport.java_util_regex_Pattern_HIDDEN_status.compareAndSwapInt(pattern, old, newValue);
            }
        }
    }

    public static boolean isSupported(Meta meta, StaticObject pattern) {
        return isStatus(meta, pattern, Constants.SUPPORTED);
    }

    public static boolean isInitialized(Meta meta, StaticObject pattern) {
        return isStatus(meta, pattern, Constants.INITALIZED);
    }

    public static boolean isTRegexCompiled(Meta meta, StaticObject pattern) {
        return isStatus(meta, pattern, Constants.TREGEX_AVAILABLE_MASK);
    }

    public static boolean isTRegexCompiled(Meta meta, StaticObject pattern, TRegexStatus action) {
        assert action != Validate;
        return isStatus(meta, pattern, action.mask);
    }

    public static boolean isGuestCompiled(Meta meta, StaticObject pattern) {
        return isStatus(meta, pattern, Constants.GUEST_COMPILED);
    }

    public static boolean isGroupDataValid(Meta meta, StaticObject pattern) {
        return isStatus(meta, pattern, Constants.VALID_GROUPCOUNT_MASK);
    }

    private static boolean isStatus(Meta meta, StaticObject pattern, int mask) {
        return (get(meta, pattern) & mask) != 0;
    }

}
