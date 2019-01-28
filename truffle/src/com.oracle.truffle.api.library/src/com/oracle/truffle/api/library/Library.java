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

import com.oracle.truffle.api.nodes.Node;

/**
 * Libraries.
 *
 * <h3>Specifying library messages</h3>
 * <h3>Exporting library messages</h3>
 * <h3>Dynamic Dispatch</h3>
 * <h3>Reflection</h3>
 *
 */
public abstract class Library extends Node {

    protected Library() {
    }

    /**
     * Returns <code>true</code> if this library supports calling methods on that library with the
     * given receiver. If a library method/message is called if the library returns
     * <code>false</code> for accepts then an {@link AssertionError} is thrown if assertion errors
     * are enabled, otherwise a {@link NullPointerException} or {@link ClassCastException} may be
     * thrown by the method. The accepts message may be invoked or implemented reflectively.
     * <p>
     * A library that was created using a receiver value i.e. a cached library only guarantees to
     * accept the value it was constructed with. The method may return <code>false</code> for other
     * receiver types. Such libraries need to verify acceptance before calling a library method with
     * a receiver. If receiver values are not accepted then a new library library needs to be
     * created or fetched. Dispatched versions of libraries always return <code>true</code> for any
     * value as they take care of dispatching to any receiver type.
     * <p>
     * Code for calling the accepts method can e generated using the {@link CachedLibrary} of
     * Truffle DSL. It recommended to not directly call accepts but let the DSL take care of this
     * step.
     * <p>
     * The accepts message may be be exported by a library. When exported by a library it can only
     * be further restricted. The minimum restriction the exact receiver type of the exported
     * receiver type. An implementation of accepts may just return <code>true</code> to provide such
     * behavior.
     *
     * @see LibraryFactory#createCached(Object) to create cached libraries with a receiver value
     * @see LibraryFactory#createCached(Object) to create cached and dispatched libraries
     * @see LibraryFactory#getUncached(Object) to get the uncached library from a receiver value.
     * @see LibraryFactory#getUncached() to get the uncached and dispatched library .
     * @since 1.0
     */
    public abstract boolean accepts(Object receiver);

}
