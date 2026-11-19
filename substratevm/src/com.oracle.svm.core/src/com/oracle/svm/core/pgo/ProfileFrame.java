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
package com.oracle.svm.core.pgo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.guest.staging.core.heap.UnknownPrimitiveField;
import com.oracle.svm.shared.BuildPhaseProvider.AfterCompilation;

/**
 * One frame of the calling context of a profiled site: a method, in canonical JVM descriptor form
 * such as {@code Ljava/lang/String;.charAt(I)C}, and a bytecode index in that method. A calling
 * context is an array of frames, innermost first: the first frame holds the site itself, each
 * further frame the call that inlined the previous one.
 * <p>
 * Like the sites that hold them, frames are created during compilation, after analysis, so their
 * fields are {@link UnknownObjectField}s and {@link UnknownPrimitiveField}s.
 */
public final class ProfileFrame {

    /**
     * Orders calling contexts by their method descriptors, frame by frame, and then by their
     * bytecode indexes. The writer and the registries use it to produce a deterministic order.
     */
    public static final Comparator<List<ProfileFrame>> CONTEXT_LIST_ORDER = Comparator.comparing(ProfileFrame::descriptorKey).thenComparing(ProfileFrame::bciKey);

    /** {@link #CONTEXT_LIST_ORDER} for contexts held in arrays. */
    public static final Comparator<ProfileFrame[]> CONTEXT_ORDER = Comparator.comparing(Arrays::asList, CONTEXT_LIST_ORDER);

    @UnknownObjectField(availability = AfterCompilation.class) private final String methodDescriptor;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private final int bci;

    public ProfileFrame(String methodDescriptor, int bci) {
        this.methodDescriptor = Objects.requireNonNull(methodDescriptor);
        this.bci = bci;
    }

    public String methodDescriptor() {
        return methodDescriptor;
    }

    public int bci() {
        return bci;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProfileFrame that && bci == that.bci && methodDescriptor.equals(that.methodDescriptor);
    }

    @Override
    public int hashCode() {
        return 31 * methodDescriptor.hashCode() + bci;
    }

    @Override
    public String toString() {
        return methodDescriptor + ":" + bci;
    }

    private static String descriptorKey(List<ProfileFrame> context) {
        StringBuilder result = new StringBuilder();
        for (ProfileFrame frame : context) {
            if (!result.isEmpty()) {
                result.append('\u0000');
            }
            result.append(frame.methodDescriptor());
        }
        return result.toString();
    }

    private static String bciKey(List<ProfileFrame> context) {
        StringBuilder result = new StringBuilder("[");
        for (ProfileFrame frame : context) {
            if (result.length() > 1) {
                result.append(", ");
            }
            result.append(frame.bci());
        }
        return result.append(']').toString();
    }
}
