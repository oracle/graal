/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.option;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.guest.staging.GuestStagingDependencyBridge;

/**
 * Common validations for {@link RuntimeOptionKey runtime options}.
 * <p>
 * Reusable callbacks are stored in static fields because a method-reference expression at each
 * option declaration can create a separate callback implementation class. Reusing a field keeps
 * one callback object and implementation class reachable for all options with the same validation.
 */
public final class RuntimeOptionValidators {
    public static final BiConsumer<RuntimeOptionKey<Integer>, Integer> PERCENTAGE = RuntimeOptionValidators::percentage;
    public static final BiConsumer<RuntimeOptionKey<Integer>, Integer> NON_NEGATIVE = RuntimeOptionValidators::nonNegative;

    public static final Consumer<? super RuntimeOptionKey<Boolean>> NOT_ENABLED_ON_AARCH64 = RuntimeOptionValidators::notEnabledOnAArch64;
    public static final Consumer<? super RuntimeOptionKey<Boolean>> NOT_ENABLED_ON_WINDOWS = RuntimeOptionValidators::notEnabledOnWindows;

    public static void percentage(RuntimeOptionKey<Integer> optionKey, int value) {
        if (value < 0 || value > 100) {
            throw RuntimeOptionValidation.invalidOptionValue(optionKey, value, "The value must be in the range [0, 100]");
        }
    }

    public static void nonNegative(RuntimeOptionKey<Integer> optionKey, int value) {
        if (value < 0) {
            throw RuntimeOptionValidation.invalidOptionValue(optionKey, value, "The value must be greater than or equal to 0");
        }
    }

    public static void notEnabledOnAArch64(RuntimeOptionKey<Boolean> optionKey) {
        if (Platform.includedIn(Platform.AARCH64.class) && optionKey.getValue()) {
            unsupportedPlatform(optionKey, true, "AArch64");
        }
    }

    public static void notEnabledOnWindows(RuntimeOptionKey<Boolean> optionKey) {
        if (Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) && optionKey.getValue()) {
            unsupportedPlatform(optionKey, true, "Windows");
        }
    }

    private static void unsupportedPlatform(RuntimeOptionKey<?> optionKey, Object value, String unsupportedPlatform) {
        throw RuntimeOptionValidation.invalidOptionValue(optionKey, value, "The option is not supported on " + unsupportedPlatform);
    }

    public static void onlyEnabledOnAMD64(RuntimeOptionKey<Boolean> optionKey) {
        if (!Platform.includedIn(Platform.AMD64.class) && optionKey.getValue()) {
            throw RuntimeOptionValidation.invalidOptionValue(optionKey, true, "The option can only be enabled on AMD64");
        }
    }

    public static void notEnabledWithG1(RuntimeOptionKey<Boolean> optionKey) {
        if (GuestStagingDependencyBridge.singleton().useG1GC() && optionKey.getValue()) {
            throw RuntimeOptionValidation.invalidOptionValue(optionKey, true, "The option is not supported when using G1");
        }
    }

}
