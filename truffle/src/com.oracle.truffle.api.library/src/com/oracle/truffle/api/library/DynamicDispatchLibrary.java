/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * A library that allows to dynamically dispatch to export library classes. Sometimes the target
 * exports for a receiver type cannot be statically determined by the receiver class with the
 * {@link ExportLibrary} annotation. To allow such types to dynamically dispatch to exports the
 * dynamic dispatch library can be exported instead. By exporting dynamic dispatch the export target
 * can be chosen dynamically.
 * <p>
 * The dynamic dispatch library requires to implement the dispatch method. The dispatch method
 * returns the target exports class that this receiver should dispatch to. If it returns
 * <code>null</code> then the type will dispatch to the library default exports. The implementation
 * of the dispatch must be stable for a single receiver value. For example it is not allowed to
 * change the dispatch target for a receiver instance. If the dispatch target was changed while the
 * receiver was used by a library then an {@link AssertionError} will be thrown.
 * <p>
 * <h4>Full usage example</h4>
 *
 * <pre>
 * &#64;ExportLibrary(DynamicDispatchLibrary.class)
 * static final class DispatchObject {
 *
 *     final Object data;
 *     final Class<?> dispatchTarget;
 *
 *     DispatchObject(Object data, Class<?> dispatchTarget) {
 *         this.data = data;
 *         this.dispatchTarget = dispatchTarget;
 *     }
 *
 *     &#64;ExportMessage
 *     Class<?> dispatch() {
 *         return dispatchTarget;
 *     }
 * }
 *
 * &#64;GenerateLibrary
 * public abstract static class ArrayLibrary extends Library {
 *
 *     public boolean isArray(Object receiver) {
 *         return false;
 *     }
 *
 *     public abstract int read(Object receiver, int index);
 * }
 *
 * &#64;ExportLibrary(value = ArrayLibrary.class, receiverType = DispatchObject.class)
 * static final class DispatchedBufferArray {
 *
 *     &#64;ExportMessage
 *     static boolean isArray(DispatchObject o) {
 *         return true;
 *     }
 *
 *     &#64;ExportMessage
 *     static int read(DispatchObject o, int index) {
 *         return ((int[]) o.data)[index];
 *     }
 * }
 *
 * public static void main(String[] args) {
 *     ArrayLibrary arrays = LibraryFactory.resolve(ArrayLibrary.class).getUncached();
 *     assert 42 == arrays.read(new DispatchObject(new int[]{42}, DispatchedBufferArray.class), 0);
 * }
 * </pre>
 *
 * @since 19.0
 */
@GenerateLibrary(dynamicDispatchEnabled = false)
public abstract class DynamicDispatchLibrary extends Library {

    /**
     * Constructor for generated subclasses. Subclasses of this class are generated, do not extend
     * this class directly.
     *
     * @since 19.0
     */
    protected DynamicDispatchLibrary() {
    }

    /**
     * Returns a class that {@link ExportLibrary exports} at least one library with an explicit
     * receiver. Returns <code>null</code> to indicate that the default dispatch of the library
     * should be used.
     *
     * @since 19.0
     */
    @Abstract
    public Class<?> dispatch(@SuppressWarnings("unused") Object receiver) {
        return null;
    }

    /**
     * Cast the object receiver type to the dispatched type. This is not supposed to be implemented
     * by dynamic dispatch implementer but is automatically implemented when implementing dynamic
     * dispatch.
     *
     * @since 19.0
     */
    /*
     * Implementation Note: This message is known by the annotation processor directly. No need to
     * export it as a library message. It is also not allowed to be implemented directly by the
     * dynamic dispatch implementer.
     */
    public abstract Object cast(Object receiver);

    static final LibraryFactory<DynamicDispatchLibrary> FACTORY = LibraryFactory.resolve(DynamicDispatchLibrary.class);

    /**
     * Returns the library factory for {@link DynamicDispatchLibrary}.
     *
     * @since 19.0
     */
    public static LibraryFactory<DynamicDispatchLibrary> getFactory() {
        return FACTORY;
    }

}
