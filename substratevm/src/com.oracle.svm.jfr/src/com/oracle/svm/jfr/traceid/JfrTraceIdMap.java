/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.jfr.traceid;

import java.util.Arrays;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;

/**
 * Map for storing trace ids. Initialized before compilation with static class count from analysis.
 */
public class JfrTraceIdMap {
    @UnknownObjectField(types = {long[].class}) private long[] traceIDs;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTraceIdMap() {
    }

    @Fold
    static JfrTraceIdMap singleton() {
        return ImageSingletons.lookup(JfrTraceIdMap.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initialize(int size) {
        traceIDs = new long[size];
        Arrays.fill(traceIDs, -1);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    long getId(int index) {
        return traceIDs[index];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void setId(int index, long id) {
        traceIDs[index] = id;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long getId(Class<?> clazz) {
        long id = traceIDs[getIndex(clazz)];
        assert id != -1;
        return id;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void setId(Class<?> clazz, long id) {
        traceIDs[getIndex(clazz)] = id;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getIndex(Class<?> clazz) {
        DynamicHub hub = DynamicHub.fromClass(clazz);
        return hub.getTypeID() + 1; // Off-set by 1 for error-catcher
    }
}
