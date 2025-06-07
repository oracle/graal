/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.image.DisallowedImageHeapObjects.DisallowedObjectReporter;

import jdk.graal.compiler.api.replacements.Fold;

public interface ForeignSupport {
    @Fold
    static boolean isAvailable() {
        return ImageSingletons.contains(ForeignSupport.class);
    }

    @Fold
    static ForeignSupport singleton() {
        return ImageSingletons.lookup(ForeignSupport.class);
    }

    Object linkToNative(Object... args) throws Throwable;

    void onMemorySegmentReachable(Object obj, DisallowedObjectReporter reporter);

    void onScopeReachable(Object obj, DisallowedObjectReporter reporter);

    /**
     * This annotation is used to mark substitution methods that substitute an
     * {@code jdk.internal.misc.ScopedMemoryAccess.Scoped}-annotated method. This will signal the
     * bytecode parser that special instrumentation support is required. Such substitution methods
     * are expected to already have a certain structure.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scoped {
    }
}
