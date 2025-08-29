/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider;

/**
 * A list that is filled at image build time while the static analysis is running, and then read at
 * run time.
 * <p>
 * Filling the list at image build time is thread safe. Every object added to the list while the
 * static analysis is running is properly added to the shadow heap.
 * <p>
 * The list is immutable at run time. The run-time list can optionally be sorted, to make code at
 * run time deterministic regardless of the order in which elements are discovered and added at
 * image build time. Sorting happens at image build time, but does not affect the list that users
 * are adding to at image build time.
 * <p>
 * We support (mostly) <b>append-only</b> semantics. Removing elements is not allowed. Changing the
 * content of the list via iterator is <b>not supported</b>. Note that we allow updating elements at
 * existing indexes as this operation is already used. We have deliberately chosen this design to
 * limit the number of analysis rescans needed.
 */
@Platforms(Platform.HOSTED_ONLY.class) //
public final class ImageHeapList {

    @Platforms(Platform.HOSTED_ONLY.class) //
    public static <E> List<E> create(Class<E> elementClass) {
        return create(elementClass, null);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public static List<?> createGeneric(Class<?> elementClass) {
        return create(elementClass, null);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public static <E> List<E> create(Class<E> elementClass, Comparator<E> comparator) {
        VMError.guarantee(!BuildPhaseProvider.isAnalysisFinished(), "Trying to create an ImageHeapList after analysis.");
        return new HostedImageHeapList<>(elementClass, comparator);
    }

    private ImageHeapList() {
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public static final class HostedImageHeapList<E> extends AbstractList<E> {
        private final Comparator<E> comparator;
        private final List<E> hostedList;
        private final RuntimeImageHeapList<E> runtimeList;
        /**
         * Used to signal if this list has been modified. If true, the change should be propagated
         * from the hosted list to the runtime list by calling {@link #update()}. This variable
         * should <b>always</b> be accessed within a <code>synchronized</code> block guarded by the
         * object's intrinsic lock.
         */
        private boolean modified;

        @SuppressWarnings("unchecked")
        private HostedImageHeapList(Class<E> elementClass, Comparator<E> comparator) {
            this.comparator = comparator;
            this.hostedList = new ArrayList<>();
            this.runtimeList = new RuntimeImageHeapList<>((E[]) Array.newInstance(elementClass, 0));
        }

        public RuntimeImageHeapList<E> getRuntimeList() {
            return runtimeList;
        }

        public synchronized boolean needsUpdate() {
            return modified;
        }

        public synchronized void update() {
            /*
             * It is important that the runtime list object does not change because it can be
             * already constant folded into graphs. So we only replace the backing array of the
             * runtime list.
             */
            runtimeList.elementData = hostedList.toArray(runtimeList.elementData);
            if (comparator != null) {
                Arrays.sort(runtimeList.elementData, comparator);
            }
            modified = false;
        }

        @Override
        public synchronized boolean add(E e) {
            modified = true;
            return hostedList.add(e);
        }

        @Override
        public synchronized void add(int index, E element) {
            modified = true;
            hostedList.add(index, element);
        }

        @Override
        public E remove(int index) {
            throw notSupported();
        }

        @Override
        public synchronized E get(int index) {
            return hostedList.get(index);
        }

        @Override
        public synchronized E set(int index, E element) {
            E result = hostedList.set(index, element);
            if (result != element) {
                modified = true;
            }
            return result;
        }

        @Override
        public synchronized int size() {
            return hostedList.size();
        }

        private static UnsupportedOperationException notSupported() {
            /*
             * We have deliberately chosen to only support append-only behavior to limit the number
             * of analysis rescans needed.
             */
            throw new UnsupportedOperationException("ImageHeapList has append-only semantics. Removing elements is forbidden.");
        }
    }
}
