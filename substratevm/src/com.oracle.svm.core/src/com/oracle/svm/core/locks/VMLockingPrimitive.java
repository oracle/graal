/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.locks;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.VMError;

public abstract class VMLockingPrimitive {

    /**
     * The function that initializes the locking primitive.
     *
     * @return zero in case of success, a non-zero value otherwise.
     */
    @Uninterruptible(reason = "Too early for safepoints.")
    public int initialize() {
        throw VMError.shouldNotReachHere("Locking primitive cannot be used during native image generation.");
    }

    /**
     * The function that destroys the locking primitive.
     *
     * <p>
     * Only a locking primitive that has been initialized by {@link #initialize()} should be
     * destroyed using this function.
     * </p>
     *
     * @return zero in case of success, a non-zero value otherwise.
     */
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public int destroy() {
        throw VMError.shouldNotReachHere("Locking primitive cannot be used during native image generation.");
    }
}
