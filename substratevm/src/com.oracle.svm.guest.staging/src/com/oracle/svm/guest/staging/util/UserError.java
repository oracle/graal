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
package com.oracle.svm.guest.staging.util;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.UserErrorSupport;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.options.OptionKey;

/**
 * Guest-context entry point for reporting user errors to the image builder.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class UserError {

    private UserError() {
    }

    public static RuntimeException abort(String format, Object... args) {
        UserErrorSupport.singleton().abort(String.format(format, VMError.formatArguments(args)));
        throw VMError.shouldNotReachHere("The image builder unexpectedly returned after reporting a user error.");
    }

    public static void guarantee(boolean condition, String format, Object... args) {
        if (!condition) {
            throw abort(format, args);
        }
    }

    public static RuntimeException invalidOptionValue(OptionKey<?> option, String value, String reason) {
        return abort("Invalid option '%s'. %s.", SubstrateOptionsParser.commandArgument(option, value), reason);
    }

    public static RuntimeException invalidOptionValue(OptionKey<?> option, Boolean value, String reason) {
        return invalidOptionValue(option, value ? "+" : "-", reason);
    }

    public static RuntimeException invalidOptionValue(OptionKey<?> option, Number value, String reason) {
        return invalidOptionValue(option, String.valueOf(value), reason);
    }
}
