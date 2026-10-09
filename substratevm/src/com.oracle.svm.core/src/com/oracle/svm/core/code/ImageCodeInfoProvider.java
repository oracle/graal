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
package com.oracle.svm.core.code;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.jvmci.shared.meta.SharedMethod;
import com.oracle.svm.shared.Uninterruptible;

/**
 * Provides access to image code information associated with a shared method.
 * <p>
 * In the closed-world image, every {@link SharedMethod} passed to
 * {@link #getImageCodeInfo(SharedMethod)} implements this provider. This is a provider invariant,
 * rather than a property of the shared JVMCI method interface. Access can return to
 * {@link SharedMethod} when {@link ImageCodeInfo} moves to shared JVMCI code; this follow-up is
 * tracked in GR-79468.
 */
public interface ImageCodeInfoProvider {

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    ImageCodeInfo getImageCodeInfo();

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static ImageCodeInfo getImageCodeInfo(SharedMethod method) {
        return ((ImageCodeInfoProvider) method).getImageCodeInfo();
    }
}
