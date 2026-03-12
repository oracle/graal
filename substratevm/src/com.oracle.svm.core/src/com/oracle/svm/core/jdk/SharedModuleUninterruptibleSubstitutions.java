/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.guest.staging.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.guest.staging.Uninterruptible;
import com.oracle.svm.shared.collections.EnumBitmask;

/**
 * This file contains substitutions to {@linkplain AnnotateOriginal annotate} methods in the shared
 * module with {@link Uninterruptible}. Since this annotation is guest annotation, it is not
 * available in the shared module. If we decide to move {@link Uninterruptible} to the shared module
 * (GR-73396), the substitutions in here should be removed and added to the methods directly.
 */
final class SharedModuleUninterruptibleSubstitutions {
}

@TargetClass(EnumBitmask.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class Target_com_oracle_svm_shared_collections_EnumBitmask {

    @AnnotateOriginal
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static native boolean hasBit(int bitmask, Enum<?> flag);

    @AnnotateOriginal
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static native int flagBit(Enum<?> flag);
}
