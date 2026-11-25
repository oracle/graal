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

import jdk.vm.ci.meta.JavaConstant;

/**
 * A constant-to-constant replacer with restrictions on internal operations that expand the
 * analysis/hosted universe or image heap,
 * such as creating new universe types, methods or fields, modifying existing ones,
 * marking constants for heap scanning, or adding new entries to an {@code ImageHeapList}
 * or {@code ImageHeapMap}.
 *
 * @param <T> always must be ImageHeapConstant, the {@code com.oracle.svm.util} cannot access it directly.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class HeapConstantToConstantReplacer<T extends JavaConstant> implements Function<JavaConstant, T> {
    /**
     * Marker returned when replacement requires a reachable object.
     * Same as {@link HeapObjectConstantReplacer#CANNOT_REPLACE_MARKER}, because
     * further processing can use the same condition.
     */
    public static final JavaConstant CANNOT_REPLACE_MARKER = HeapObjectConstantReplacer.CANNOT_REPLACE_MARKER;

    @Override
    public final T apply(JavaConstant constant) {
        throw VMError.intentionallyUnimplemented();
    }

    /**
     * @param constant constant to replace
     * @param reachable whether the object is reachable and is being replaced for the image heap;
     *                  when false, the internal operations described above must be avoided
     * @return the replacement, {@code null} if this replacer does not apply, or
     *         {@link #CANNOT_REPLACE_MARKER} if replacement requires a reachable object
     */
    public abstract JavaConstant replaceConstant(JavaConstant constant, boolean reachable);
}
