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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.svm.guest.staging.util.UserError;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.UserErrorSupport;

import jdk.graal.compiler.options.OptionKey;

/** Error-reporting helpers for runtime option validation callbacks. */
public final class RuntimeOptionValidation {
    public static RuntimeException invalidOptionValue(OptionKey<?> optionKey, Object value, String reason) {
        if (SubstrateUtil.HOSTED) {
            if (userErrorReportingAvailable()) {
                if (value instanceof Boolean booleanValue) {
                    throw UserError.invalidOptionValue(optionKey, booleanValue, reason);
                } else if (value instanceof Number numberValue) {
                    throw UserError.invalidOptionValue(optionKey, numberValue, reason);
                }
                throw UserError.invalidOptionValue(optionKey, String.valueOf(value), reason);
            }
            String valueString = value instanceof Boolean booleanValue ? booleanValue ? "+" : "-" : String.valueOf(value);
            String argument = SubstrateOptionsParser.commandArgument(optionKey, valueString);
            throw new RuntimeOptionValidationException("Invalid option '" + argument + "'. " + reason + ".");
        }

        throw new IllegalArgumentException("Invalid option '" + optionKey.getName() + "=" + value + "'. " + reason + ".");
    }

    public static RuntimeException abort(String message) {
        if (SubstrateUtil.HOSTED) {
            if (userErrorReportingAvailable()) {
                throw UserError.abort("%s", message);
            }
            throw new RuntimeOptionValidationException(message);
        }
        throw new IllegalArgumentException(message);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static boolean userErrorReportingAvailable() {
        return ImageSingletonsSupport.isInstalled() && ImageSingletons.contains(UserErrorSupport.class);
    }
}
