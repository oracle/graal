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
package com.oracle.svm.util;

import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.util.VMError;

/**
 * Extends functionality of object replacers to specify different
 * replacement if the output of the replacer is meant to be stored
 * in the image heap, or expand the analysis/hosted universe through internal operations.
 *
 * <p>
 * Expected behavior of {@link #replaceObject(Object, boolean)} when {@code reachable} is {@code false} is
 * that it does not expand the analysis/hosted universe or image heap through internal operations,
 * such as creating new universe types, methods or fields, modifying existing ones,
 * marking constants for heap scanning, or adding new entries to an {@code ImageHeapList}
 * or {@code ImageHeapMap}.
 * </p>
 *
 * <p>
 * Returns {@link #CANNOT_REPLACE_MARKER}, if {@code reachable} is false and the replacer cannot
 * replace the object without using aforementioned mentioned internal operations.
 * </p>
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class HeapObjectReplacer implements Function<Object, Object> {
    /**
     * This marker is used when the replacer cannot reliably replace
     * the object without using aforementioned internal operations.
     */
    public static final Object CANNOT_REPLACE_MARKER = new Object();

    @Override
    public final Object apply(Object object) {
        throw VMError.intentionallyUnimplemented();
    }

    /**
     * Replace an object, optionally before it reaches the image heap. When {@code reachable} is
     * true, this method may perform internal operations that expand the universe or image heap,
     * such as adding entries to an {@code ImageHeapList}.
     *
     * @param object that is replaced
     * @param reachable whether the object is reachable and is being replaced for the image heap,
     *                  when false, the internal operations described above must be avoided
     * @return new object if the old one is replaced, the old object if it is unchanged, or
     *         {@link #CANNOT_REPLACE_MARKER} if replacement requires a reachable object
     */
    public abstract Object replaceObject(Object object, boolean reachable);
}
