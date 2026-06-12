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

package com.oracle.graal.pointsto.standalone.meta;

import com.oracle.graal.pointsto.heap.AbstractImageHeapSnippetReflectionProvider;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Standalone equivalent of the hosted snippet reflection provider.
 *
 * The provider redirects newly created object constants through the shadow heap so heap scanning
 * and verification keep seeing {@link ImageHeapConstant} wrappers instead of raw hosted constants.
 */
public final class StandaloneSnippetReflectionProvider extends AbstractImageHeapSnippetReflectionProvider {
    /**
     * Guest-aware snippet reflection implementation supplied by the selected VMAccess backend.
     */
    private final SnippetReflectionProvider original;

    /**
     * Creates a standalone snippet reflection provider that keeps object constants shadow-heap
     * aware while delegating guest conversions to the original VMAccess-backed provider.
     */
    public StandaloneSnippetReflectionProvider(ImageHeapScanner heapScanner, SnippetReflectionProvider original, WordTypes wordTypes) {
        super(heapScanner, wordTypes);
        this.original = original;
    }

    @Override
    protected JavaConstant forBoxedPrimitive(JavaKind kind, Object value) {
        return original.forBoxed(kind, value);
    }

    /**
     * Converts already-unwrapped constants back to hosted objects by delegating the guest-to-host
     * conversion to the VMAccess-backed provider.
     */
    @Override
    protected <T> T asObjectFromUnwrapped(Class<T> type, JavaConstant constant) {
        return original.asObject(type, constant);
    }

}
