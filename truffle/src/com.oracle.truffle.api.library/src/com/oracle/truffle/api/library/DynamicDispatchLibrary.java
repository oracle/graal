/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library;

import com.oracle.truffle.api.library.GenerateLibrary.Abstract;

/**
 * Base library if the receiver export needs to be dispatched.
 */
@GenerateLibrary
public abstract class DynamicDispatchLibrary extends Library {

    /**
     * Returns a class that {@link ExportLibrary exports} at least one library with an explicit
     * receiver. Returns <code>null</code> to indicate that the default dispatch of the library
     * should be used.
     */
    @Abstract
    public Class<?> dispatch(@SuppressWarnings("unused") Object receiver) {
        return null;
    }

    public static LibraryFactory<DynamicDispatchLibrary> resolve() {
        return Lazy.RESOLVED_LIBRARY;
    }

    /**
     * Cast the object receiver type to the dispatched type. This is not supposed to be implemented
     * by dynamic dispatch implementer but is automatically implemented when implementing dynamic
     * dispatch.
     *
     * @param receiver
     * @return
     */
    /*
     * This message is known by the annotation processor directly.
     */
    public abstract Object cast(Object receiver);

    /*
     * This indirection is needed to avoid cyclic class initialization. The enclosing class needs to
     * be loaded before Dispatch.resolve can be used.
     */
    static final class Lazy {

        private Lazy() {
            /* No instances */
        }

        static final LibraryFactory<DynamicDispatchLibrary> RESOLVED_LIBRARY = LibraryFactory.resolve(DynamicDispatchLibrary.class);

    }
}
