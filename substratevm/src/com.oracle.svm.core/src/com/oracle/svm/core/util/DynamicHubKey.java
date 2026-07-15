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
package com.oracle.svm.core.util;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.DynamicHub;

/**
 * Maps that are conceptually indexed by {@link Class} need different keys before and after type IDs
 * are assigned. Hosted maps can use {@link DynamicHub} keys to identify classes while registrations
 * are still collected, but image heap maps need to be keyed by {@link DynamicHub#getTypeID()},
 * which is stable at run time. This hosted-only wrapper is used for the first map to ensure that
 * {@link DynamicHub} keys do not leak into the image.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public record DynamicHubKey(DynamicHub hub) {
    public int getTypeID() {
        return hub.getTypeID();
    }
}
