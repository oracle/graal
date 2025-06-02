/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import com.oracle.svm.core.util.UserError;
import org.graalvm.nativeimage.hosted.AccessCondition;
import org.graalvm.nativeimage.impl.TypeReachabilityCondition;

public final class DynamicAccessSupport {
    private static boolean sealed = false;

    static void setRegistrationSealed() {
        sealed = true;
    }

    public static void printErrorIfSealedOrInvalidCondition(AccessCondition condition, String registrationEntry) {
        if (condition == null) {
            UserError.abort(new NullPointerException(), "Condition value must not be null. Please ensure that all values you register are not null.");
        }
        if (!(condition instanceof TypeReachabilityCondition)) {
            UserError.abort(new IllegalStateException(), "Condition %s is not valid. Condition must be either alwaysTrue or typeReached. %s", condition);
        }
        if (sealed) {
            UserError.abort(new IllegalStateException(), "Registration for runtime access after Feature#afterRegistration is not allowed. You tried to register %s", registrationEntry);
        }
    }
}
