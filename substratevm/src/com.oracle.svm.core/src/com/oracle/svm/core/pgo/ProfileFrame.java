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
     * bytecode indexes. The writer and the recorders sort keys with it before they write them.
     * <p>
     * Keys are created in compile order, which differs from build to build because compilation is
     * parallel, and the recorders keep them in hash maps. Sorting makes the file depend only on the
     * counts: two runs with the same counts write the same file, with the same method and type ids,
     * so profiles can be compared with {@code diff} and checked exactly by tests.
     */
    public static final Comparator<List<ProfileFrame>> CONTEXT_LIST_ORDER = ProfileFrame::compareContexts;

    /** {@link #CONTEXT_LIST_ORDER} for contexts held in arrays. */
    public static final Comparator<ProfileFrame[]> CONTEXT_ORDER = (left, right) -> compareContexts(Arrays.asList(left), Arrays.asList(right));

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

    /**
     * Compares the method descriptors frame by frame, a context that is a prefix of the other one
     * first, then the bytecode indexes frame by frame. Builds no strings: the dump sorts every
     * context it writes.
     */
    private static int compareContexts(List<ProfileFrame> left, List<ProfileFrame> right) {
        int length = Math.min(left.size(), right.size());
        for (int i = 0; i < length; i++) {
            int result = left.get(i).methodDescriptor.compareTo(right.get(i).methodDescriptor);
            if (result != 0) {
                return result;
            }
        }
        int result = Integer.compare(left.size(), right.size());
        for (int i = 0; result == 0 && i < length; i++) {
            result = Integer.compare(left.get(i).bci, right.get(i).bci);
        }
        return result;
    }
}
