/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.Uninterruptible;

@DuplicatedInNativeCode
public class HubType {
    // Instance hubs.
    public static final int INSTANCE = 0;
    public static final int REFERENCE_INSTANCE = 1;
    public static final int POD_INSTANCE = 2;
    public static final int STORED_CONTINUATION_INSTANCE = 3;

    // Other hubs (heap objects never reference those hubs).
    public static final int OTHER = 4;

    // Array hubs.
    public static final int PRIMITIVE_ARRAY = 5;
    public static final int OBJECT_ARRAY = 6;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInstance(int hubType) {
        return hubType <= STORED_CONTINUATION_INSTANCE;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isReferenceInstance(int hubType) {
        return hubType == REFERENCE_INSTANCE;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isPodInstance(int hubType) {
        return hubType == POD_INSTANCE;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isArray(int hubType) {
        return hubType >= PRIMITIVE_ARRAY;
    }
}
