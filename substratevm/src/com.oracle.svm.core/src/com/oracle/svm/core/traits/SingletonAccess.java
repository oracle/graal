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
package com.oracle.svm.core.traits;

import java.util.EnumSet;

import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.util.VMError;

/**
 * Metadata associated with the {@link SingletonTraitKind#ACCESS} trait. Describes when this
 * singleton can be accessed (e.g., during the native image generator process and/or from within the
 * generated code at runtime).
 */
public class SingletonAccess {
    interface Supplier {
        EnumSet<LayeredImageSingletonBuilderFlags> getAccessFlags();
    }

    public static EnumSet<LayeredImageSingletonBuilderFlags> getAccess(SingletonTrait trait) {
        VMError.guarantee(trait.kind() == SingletonTraitKind.ACCESS);
        return ((Supplier) trait.metadata()).getAccessFlags();
    }
}
