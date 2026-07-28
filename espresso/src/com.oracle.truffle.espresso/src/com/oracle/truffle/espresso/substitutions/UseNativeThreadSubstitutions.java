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

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.nfi.NFISulongNativeAccess;

/**
 * Wrapper class for language filters to decide when to use noop substitutions for nio's
 * {@code NativeThread}.
 */
public class UseNativeThreadSubstitutions {
    /**
     * Disables NIO's {@code NativeThread} signals on EspressoLibs and Sulong. On EspressoLibs, this
     * will not cause problems because guest NIO native functions will eventually call host NIO
     * functions, which maintain working {@code NativeThread} signals. <br>
     *
     * LLVM currently offers no way to support NIO's {@code NativeThread} signals. Consequently, all
     * guest code relying on them is expected to fail when using {@link NFISulongNativeAccess}.
     */
    public static boolean standardFilter(EspressoLanguage language) {
        return language.useEspressoLibs() || language.nativeBackendId().equals(NFISulongNativeAccess.Provider.ID);
    }

    /**
     * The native init function overwrites the previously installed signal handler. When running on
     * HotSpot, this may not be desirable. To be safe, we substitute this function on HotSpot to
     * ensure we do not overwrite any settings established by the host VM.
     */
    public static class InitFilter implements LanguageFilter {
        public static final LanguageFilter INSTANCE = new InitFilter();

        @Override
        public boolean isValidFor(EspressoLanguage language) {
            // substitute on Hotspot too
            return standardFilter(language) || !ImageInfo.inImageRuntimeCode();
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
