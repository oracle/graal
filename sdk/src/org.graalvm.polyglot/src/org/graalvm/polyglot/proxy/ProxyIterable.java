/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.proxy;

import java.util.Iterator;
import java.util.Objects;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess.Builder;
import org.graalvm.polyglot.Value;

/**
 * Interface to be implemented to mimic guest language iterables.
 *
 * @see Proxy
 * @see ProxyIterator
 * @since 21.1
 */
public interface ProxyIterable extends Proxy {

    /**
     * Returns an iterator. The returned object must be interpreted as an iterator using the
     * semantics of {@link Context#asValue(Object)} otherwise an {@link IllegalStateException} is
     * thrown. Examples for valid return values are:
     * <ul>
     * <li>{@link ProxyIterator}
     * <li>{@link Iterator}, requires {@link Builder#allowIteratorAccess(boolean) host iterable
     * access}
     * <li>A guest language object representing an iterator
     * </ul>
     *
     * @see ProxyIterator
     * @since 21.1
     */
    Object getIterator();

    /**
     * Creates a proxy iterable backed by a Java {@link Iterable}. If the values of the iterable are
     * host values then they will be {@link Value#asHostObject() unboxed}.
     *
     * @since 21.1
     */
    static ProxyIterable from(Iterable<Object> iterable) {
        Objects.requireNonNull(iterable, "Iterable must be non null.");
        return new ProxyIterable() {
            @Override
            public Object getIterator() {
                return ProxyIterator.from(iterable.iterator());
            }
        };
    }
}
