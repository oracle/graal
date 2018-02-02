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
 * Proxy interfaces allow to mimic guest language objects, arrays, executables, primitives and
 * native objects in Graal languages. Every Graal language will treat instances of proxies like an
 * object of that particular language. Multiple proxy interfaces can be implemented at the same
 * time. For example, it is useful to provide proxy values that are objects with members and arrays
 * at the same time.
 * <p>
 * Exceptions thrown by proxies are wrapped with a {@link PolyglotException} when the proxy is
 * invoked in a guest language. It is possible to unwrap the {@link PolyglotException} using
 * {@link PolyglotException#asHostException()}.
 *
 * @see ProxyArray to mimic arrays
 * @see ProxyObject to mimic objects with members
 * @see ProxyExecutable to mimic objects that can be executed
 * @see ProxyNativeObject to mimic native objects
 * @see ProxyPrimitive to mimic lazy primitive values
 *
 * @since 1.0
 */
public interface Proxy {

}
