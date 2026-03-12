/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.image;

import com.oracle.svm.core.SubstrateOptions;

import java.util.Comparator;
import java.util.List;

/**
 * Sorter of {@link ImageHeapObject} instances after they have been assigned to heap partitions,
 * influencing the layout of the resulting image heap.
 * <p>
 * The {@link #sort(List)} method is invoked once for each partition, after all objects have been
 * assigned to that partition.
 * <p>
 * The ordering of objects is subject to the following constraints:
 * <ul>
 * <li>Large objects (see {@link #isLarge}) may be placed only at the beginning or the end of the
 * list.</li>
 * <li>{@link com.oracle.svm.core.hub.DynamicHub} objects (objects whose
 * {@code getObjectClass() == Class.class}) must be located at the beginning of the list, possibly
 * interleaved with large objects.</li>
 * </ul>
 * <p>
 * Valid order:
 *
 * <pre>
 * [ (large|dynamic_hub)* ][ other_non_large* ][ large* ]
 * </pre>
 * <p>
 * The {@link #isValidOrder} method is provided to check whether a given list of objects meets these
 * constraints.
 */
public abstract class ImageHeapObjectSorter {
    private static final String INVALID_ORDER_MESSAGE_PREFIX = "Objects are not in a valid order: ";
    private static final String INVALID_ORDER_MESSAGE = INVALID_ORDER_MESSAGE_PREFIX +
                    "Large objects must be at the start or end, and Class objects must be at the start (optionally after large objects).";
    public static final Comparator<ImageHeapObject> NO_OP_COMPARATOR = (_, _) -> 0;

    private final int pageSize;

    protected ImageHeapObjectSorter() {
        this.pageSize = SubstrateOptions.getPageSize();
    }

    /**
     * Sorts the provided list of {@link ImageHeapObject}s using the specified {@link Comparator} to
     * define the primary ordering of the elements. Any further tie-breaking or secondary sorting
     * within groups defined by the primary comparator should be handled by the implementation of
     * {@link #doSort(List, Comparator)}.
     */
    public final void sort(List<ImageHeapObject> objects, Comparator<ImageHeapObject> primaryComparator) {
        doSort(objects, primaryComparator);
        assert isValidOrder(objects) : INVALID_ORDER_MESSAGE;
    }

    public final void sort(List<ImageHeapObject> objects) {
        doSort(objects, NO_OP_COMPARATOR);
        assert isValidOrder(objects) : INVALID_ORDER_MESSAGE;
    }

    protected abstract void doSort(List<ImageHeapObject> objects, Comparator<ImageHeapObject> primaryComparator);

    protected int compareGroup(ImageHeapObject a, ImageHeapObject b) {
        boolean aIsDynamicHub = a.getObjectClass() == Class.class;
        boolean bIsDynamicHub = b.getObjectClass() == Class.class;
        if (aIsDynamicHub != bIsDynamicHub) {
            // Place DynamicHubs before other objects
            return aIsDynamicHub ? -1 : 1;
        }

        boolean aIsLargeObject = isLarge(a);
        boolean bIsLargeObject = isLarge(b);
        if (aIsLargeObject != bIsLargeObject) {
            // Place large objects before others
            return aIsLargeObject ? -1 : 1;
        }

        return 0;
    }

    protected boolean isLarge(ImageHeapObject object) {
        return object.getSize() >= pageSize;
    }

    private boolean isValidOrder(List<? extends ImageHeapObject> objects) {
        boolean seenNonLargeAndNonClass = false;
        boolean inTrailingLarge = false;

        for (int i = 0; i < objects.size(); i++) {
            ImageHeapObject obj = objects.get(i);
            boolean large = isLarge(obj);

            if (!inTrailingLarge) {
                if (large) {
                    if (seenNonLargeAndNonClass) {
                        inTrailingLarge = true;
                    }
                    continue;
                }
                boolean isClass = obj.getObjectClass() == Class.class;
                if (seenNonLargeAndNonClass && isClass) {
                    assert false : invalidOrderMessage("Class object after non-class non-large at index " + i + ".");
                    return false;
                }
                if (!isClass) {
                    seenNonLargeAndNonClass = true;
                }
            } else {
                if (!large) {
                    assert false : invalidOrderMessage("Non-large after trailing large at index " + i + ".");
                    return false;
                }
            }
        }
        return true;
    }

    private static String invalidOrderMessage(String details) {
        return INVALID_ORDER_MESSAGE_PREFIX + details;
    }
}
