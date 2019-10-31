/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
 * @since 19.0
 */
public interface PinnedObject extends AutoCloseable {

    /**
     * Create an open PinnedObject.
     *
     * @since 19.0
     */
    static PinnedObject create(Object object) {
        return ImageSingletons.lookup(PinnedObjectSupport.class).create(object);
    }

    /**
     * Releases the pin for the object. After this call, the object can be moved or discarded by the
     * garbage collector.
     *
     * @since 19.0
     */
    @Override
    void close();

    /**
     * Returns the Object that is the referent of this PinnedObject.
     *
     * @since 19.0
     */
    Object getObject();

    /**
     * Returns the raw address of the pinned object. The object layout is not specified, but usually
     * the address of an object is a pointer to to the first header word. In particular, the result
     * is not a pointer to the first array element when the object is an array.
     *
     * @since 19.0
     */
    PointerBase addressOfObject();

    /**
     * Returns a pointer to the array element with the specified index. The object must be an array.
     * No array bounds check for the index is performed.
     *
     * @since 19.0
     */
    <T extends PointerBase> T addressOfArrayElement(int index);
}
