/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.handles;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.word.PointerBase;

/**
 * Implements a view to a primitive array for access from native code. Depending on the
 * configuration of the virtual machine, this may either pin the array (see {@link PinnedObject}) or
 * create a copy of the array to be used by native code.
 * <p>
 * This class implements {@link AutoCloseable} so that the view can be managed conveniently with a
 * try-with-resource block that cleans up the underlying reference automatically:
 *
 * <pre>
 *   int[] array = ...
 *   try (PrimitiveArrayView view = PrimitiveArrayView.createForReadingAndWriting(array)) {
 *     CIntPointer rawData = view.addressOfArrayElement(0);
 *     // it is safe to pass rawData to a C function.
 *   }
 *   // it is no longer safe to access rawData.
 * </pre>
 */
public interface PrimitiveArrayView extends AutoCloseable {

    /**
     * If the view is a copy, the contents of the copy will be written back to the underlying array
     * upon closure.
     * <p>
     * Generally used if the memory copy will be read from and written to.
     */
    static PrimitiveArrayView createForReadingAndWriting(Object object) {
        return ImageSingletons.lookup(PrimitiveArrayViewSupport.class).createForReadingAndWriting(object);
    }

    /**
     * If the view is a copy, the contents of the copy will not be written back to the underlying
     * array upon closure. This may improve performance if the memory data will not be changed. If a
     * {@link PinnedObject} is used, any changes will also affect the Java object.
     * <p>
     * Generally used if the object will only be read from.
     */
    static PrimitiveArrayView createForReading(Object object) {
        return ImageSingletons.lookup(PrimitiveArrayViewSupport.class).createForReading(object);
    }

    /**
     * Releases the view. If the underlying array was pinned, then it is unpinned. Otherwise the
     * contents of the copy may be copied back if requested and finally the memory is freed.
     */
    @Override
    void close();

    /**
     * Stops tracking the view. If the underlying array was pinned, then it is unpinned. If the view
     * uses a copy in native memory, the user is responsible for freeing that memory. The memory
     * contents are not copied to the heap.
     */
    void untrack();

    /**
     * Returns true if the view uses a copy. Any changes to a copy will not be visible in Java
     * unless the changes are synchronized back.
     */
    boolean isCopy();

    /**
     * If the view uses a copy, then the contents of the copy are written back to the original array
     * on the heap.
     */
    void syncToHeap();

    /**
     * Returns a pointer to the array element with the specified index. No array bounds check for
     * the index is performed.
     */
    <T extends PointerBase> T addressOfArrayElement(int index);
}
