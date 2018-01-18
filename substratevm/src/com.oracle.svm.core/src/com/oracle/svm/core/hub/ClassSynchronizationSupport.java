/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.AutomaticFeature;

/**
 * We want the {@link DynamicHub} to be immutable at run time, so we cannot use it for
 * synchronization operations. So we need a different object to synchronize on when a static method
 * is declared synchronized. We use the class {@link ClassSynchronizationTarget}, which is not used
 * for anything else.
 *
 * For "static synchronized" methods, the class is a compile time constant, so the new target is
 * also a compile time constant. We could do the {@link #synchronizationTargets lookup} also at run
 * time when we detect synchronization on a {@link DynamicHub}, but this is currently not necessary.
 * Instead, we disallow such synchronizations already during native image generation and fail with
 * an unsupported feature error.
 */
public final class ClassSynchronizationSupport {
    @Platforms(HOSTED_ONLY.class) //
    private final ConcurrentMap<DynamicHub, ClassSynchronizationTarget> synchronizationTargets = new ConcurrentHashMap<>();

    public static ClassSynchronizationTarget synchronizationTarget(DynamicHub hub) {
        ClassSynchronizationSupport support = ImageSingletons.lookup(ClassSynchronizationSupport.class);
        return support.synchronizationTargets.computeIfAbsent(hub, unused -> new ClassSynchronizationTarget());
    }

    public static final class ClassSynchronizationTarget {
    }
}

@AutomaticFeature
final class ClassSynchronizationFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ClassSynchronizationSupport.class, new ClassSynchronizationSupport());
    }
}
