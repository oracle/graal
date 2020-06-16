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

import org.graalvm.nativeimage.impl.ObjectHandlesSupport;

/**
 * Manages a set of {@link ObjectHandles}. The handles returned by {@link #create} are bound to the
 * creating handle set, i.e., the handle can only be {@link #get accessed} and {@link #destroy
 * destroyed} using the exact same handle set used for creation.
 *
 * @since 19.0
 */
public interface ObjectHandles {

    /**
     * A set of handles that is kept alive globally.
     *
     * @since 19.0
     */
    static ObjectHandles getGlobal() {
        return ImageSingletons.lookup(ObjectHandlesSupport.class).getGlobalHandles();
    }

    /**
     * Creates a new set of handles. Objects are kept alive until the returned {@link ObjectHandles}
     * instance gets unreachable.
     *
     * @since 19.0
     */
    static ObjectHandles create() {
        return ImageSingletons.lookup(ObjectHandlesSupport.class).createHandles();
    }

    /**
     * Creates a handle to the specified object. The object is kept alive by the garbage collector
     * at least until {@link #destroy} is called for the returned handle. The object can be null.
     *
     * @since 19.0
     */
    ObjectHandle create(Object object);

    /**
     * Extracts the object from a given handle.
     *
     * @since 19.0
     */
    <T> T get(ObjectHandle handle);

    /**
     * Destroys the given handle. After calling this method, the handle must not be used anymore.
     *
     * @since 19.0
     */
    void destroy(ObjectHandle handle);
}
