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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.ffi.nfi.NFISulongNativeAccess;

/**
 * Wrapper class for Language filters to avoid signaling. Prevent the installation of a signal
 * handler except on SVM and not with llvm or EspressoLibs. (sulong virtualizes pthread_self but not
 * ptrhead_kill)
 */
public class DisableSignals {
    // avoid the installation of a signal handler except on SVM and not with llvm or espresso libs
    public static boolean standardFilter(EspressoLanguage language) {
        return language.useEspressoLibs() || !EspressoOptions.RUNNING_ON_SVM || (language.nativeBackendId().equals(NFISulongNativeAccess.Provider.ID));
    }

    // various filters
    public static class StandardFilter implements LanguageFilter {
        public static final LanguageFilter INSTANCE = new StandardFilter();

        @Override
        public boolean isValidFor(EspressoLanguage language) {
            return standardFilter(language);
        }
    }

    public static class Version21orLaterFilter implements LanguageFilter {
        public static final LanguageFilter INSTANCE = new Version21orLaterFilter();

        @Override
        public boolean isValidFor(EspressoLanguage language) {
            return (standardFilter(language)) && (language.getJavaVersion().java21OrLater());
        }
    }

    public static class Version17orEarlierFilter implements LanguageFilter {
        public static final LanguageFilter INSTANCE = new Version17orEarlierFilter();

        @Override
        public boolean isValidFor(EspressoLanguage language) {
            return (standardFilter(language)) && (language.getJavaVersion().java17OrEarlier());
        }
    }

}
