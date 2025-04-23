/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.ParameterizedEspressoType;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@GenerateUncached
public abstract class ProxyInstantiateNode extends EspressoNode {
    static final int LIMIT = 3;

    public abstract StaticObject execute(WrappedProxyKlass proxyKlass, Object foreignObject, EspressoType targetType);

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "cachedTargetType == targetType",
                    "cachedWrappedProxyKlass == proxyKlass"}, limit = "LIMIT")
    StaticObject doParameterizedCached(WrappedProxyKlass proxyKlass, Object foreignObject, ParameterizedEspressoType targetType,
                    @Cached("proxyKlass") WrappedProxyKlass cachedWrappedProxyKlass,
                    @Cached("targetType") ParameterizedEspressoType cachedTargetType,
                    @Bind("getLanguage()") EspressoLanguage language,
                    @Cached(value = "cachedWrappedProxyKlass.fillTypeArguments(cachedTargetType.getTypeArguments())", dimensions = 1) EspressoType[] resolvedGenericTypes,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        return cachedWrappedProxyKlass.createProxyInstance(resolvedGenericTypes, foreignObject, interop, language);
    }

    @Specialization(replaces = "doParameterizedCached")
    StaticObject doParameterizedUncached(WrappedProxyKlass proxyKlass, Object foreignObject, ParameterizedEspressoType targetType,
                    @Bind("getLanguage()") EspressoLanguage language,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        EspressoType[] resolvedGenericTypes = proxyKlass.fillTypeArguments(targetType.getTypeArguments());
        return proxyKlass.createProxyInstance(resolvedGenericTypes, foreignObject, interop, language);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "!isParameterizedType(targetType)",
                    "cachedWrappedProxyKlass == proxyKlass"}, limit = "LIMIT")
    StaticObject doNoGenericsCached(WrappedProxyKlass proxyKlass, Object foreignObject, EspressoType targetType,
                    @Cached("proxyKlass") WrappedProxyKlass cachedWrappedProxyKlass,
                    @Cached(value = "cachedWrappedProxyKlass.fillTypeArguments()", dimensions = 1) EspressoType[] resolvedGenericTypes,
                    @Bind("getLanguage()") EspressoLanguage language,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        return proxyKlass.createProxyInstance(resolvedGenericTypes, foreignObject, interop, language);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "!isParameterizedType(targetType)", replaces = "doNoGenericsCached")
    StaticObject doNoGenericsUncached(WrappedProxyKlass proxyKlass, Object foreignObject, EspressoType targetType,
                    @Bind("getLanguage()") EspressoLanguage language,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        EspressoType[] resolvedGenericTypes = proxyKlass.fillTypeArguments();
        return proxyKlass.createProxyInstance(resolvedGenericTypes, foreignObject, interop, language);
    }

    static boolean isParameterizedType(EspressoType type) {
        return type instanceof ParameterizedEspressoType;
    }
}
