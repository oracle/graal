/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.proxy;

import org.graalvm.polyglot.PolyglotException;

/**
 * Interface to be implemented to mimic lazy primitive values. The primitive value is
 * {@link #asPrimitive() computed} lazily when accessed by the guest language. Valid primitive
 * values are {@link String}, {@link Byte}, {@link Character}, {@link Short}, {@link Integer},
 * {@link Long}, {@link Float} and {@link Double}.
 *
 * @see #asPrimitive()
 * @see Proxy
 * @since 1.0
 * @deprecated without replacement.
 */
@FunctionalInterface
@Deprecated
public interface ProxyPrimitive extends Proxy {

    /**
     * Unboxes the proxy to a primitive value. The primitive value is {@link #asPrimitive()
     * computed} lazily when accessed by the guest language. Valid primitive values are
     * {@link String}, {@link Byte}, {@link Character}, {@link Short}, {@link Integer}, {@link Long}
     * , {@link Float} and {@link Double}. If the returned type is invalid then a
     * {@link IllegalStateException} {@link PolyglotException#isHostException() host exception} is
     * thrown to the guest language.
     *
     * @since 1.0
     */
    Object asPrimitive();

}
