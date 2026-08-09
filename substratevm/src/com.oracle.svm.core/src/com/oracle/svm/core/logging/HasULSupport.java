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
package com.oracle.svm.core.logging;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/// Guards use of unified logging so that it folds away is not supported.
public class HasULSupport {
    /// Reason to use with [com.oracle.svm.shared.AlwaysInline].
    public static final String UL_CONDITIONAL = "Remove UL usage if UL not supported";

    /// Returns whether unified logging is supported.
    @Fold
    public static boolean get() {
        return SubstrateOptions.StrictRuntimeJavaOptions.getValue();
    }

    /// Throws an error if unified logging is not supported.
    public static void require() {
        if (!get()) {
            VMError.shouldNotReachHere("Unified logging not supported");
        }
    }
}
