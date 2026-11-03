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
package com.oracle.svm.shared;

import com.oracle.svm.shared.util.VMError;

/**
 * Operations available to shared guest code when it is compiled as an intrinsic or snippet.
 */
public final class SharedIntrinsics {

    private SharedIntrinsics() {
    }

    /**
     * Returns whether the caller is being parsed as an intrinsic or snippet. Ordinary compiled
     * code returns {@code false}; hosted graph-builder plugins provide the precise parsing state.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean inIntrinsic() {
        return false;
    }

    /**
     * Performs a dynamic assertion only when this method is intrinsified in an intrinsic or
     * snippet. Ordinary Java execution is unsupported and fails fast; callers must not use this
     * method as a substitute for a Java assertion.
     *
     * @param condition the condition that must hold
     * @param message the assertion message
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void dynamicAssert(boolean condition, String message) {
        VMError.guarantee(false, "SharedIntrinsics.dynamicAssert must be intrinsified");
    }
}
