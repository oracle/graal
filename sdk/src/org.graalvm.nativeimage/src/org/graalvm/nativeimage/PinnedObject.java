/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import org.graalvm.nativeimage.impl.PinnedObjectSupport;
import org.graalvm.word.PointerBase;

/**
 * Holder for a pinned object, such that the object doesn't move until the pin is removed. The
 * garbage collector treats pinned object specially to ensure that they are not moved or discarded.
 * <p>
 * This class implements {@link AutoCloseable} so that the pinning can be managed conveniently with
 * a try-with-resource block that releases the pinning automatically:
 *
 * <pre>
 *   int[] array = ...
 *   try (PinnedObject pin = PinnedObject.create(array)) {
 *     CIntPointer rawData = pin.addressOfArrayElement(0);
 *     // it is safe to pass rawData to a C function.
 *   }
 *   // it is no longer safe to access rawData.
 * </pre>
 *
 * @since 1.0
 */
public interface PinnedObject extends AutoCloseable {

    /**
     * Create an open PinnedObject.
     *
     * @since 1.0
     */
    static PinnedObject create(Object object) {
        return ImageSingletons.lookup(PinnedObjectSupport.class).create(object);
    }

    /**
     * Releases the pin for the object. After this call, the object can be moved or discarded by the
     * garbage collector.
     *
     * @since 1.0
     */
    @Override
    void close();

    /**
     * Returns the Object that is the referent of this PinnedObject.
     *
     * @since 1.0
     */
    Object getObject();

    /**
     * Returns the raw address of the pinned object. The object layout is not specified, but usually
     * the address of an object is a pointer to to the first header word. In particular, the result
     * is not a pointer to the first array element when the object is an array.
     *
     * @since 1.0
     */
    PointerBase addressOfObject();

    /**
     * Returns a pointer to the array element with the specified index. The object must be an array.
     * No array bounds check for the index is performed.
     *
     * @since 1.0
     */
    <T extends PointerBase> T addressOfArrayElement(int index);
}
