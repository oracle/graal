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
/**
 * The Graal SDK proxy interfaces allow to mimic guest language objects in Graal languages. For
 * example, one can implement the {@link org.graalvm.polyglot.proxy.ProxyObject} interface and then
 * pass the object to a guest language. The guest language will then treat this instance as an
 * object. For instance if the language tries to write into a field it will call
 * {@link org.graalvm.polyglot.proxy.ProxyObject#putMember(String, org.graalvm.polyglot.Value)}. The
 * API is designed to be language agnostic, therefore they it can be used with any Graal guest
 * language.
 * <p>
 * See <link <a href="http://www.graalvm.org/docs/embed">graalvm.org</a> for more examples on how to
 * use this API.
 *
 * @see org.graalvm.polyglot.proxy.Proxy for more an overview over all available proxy interfaces.
 * @since 1.0
 */
package org.graalvm.polyglot.proxy;