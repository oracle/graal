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
package com.oracle.truffle.api.interop.java;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

abstract class LookupMethodNode extends Node {
    static final int LIMIT = 3;

    LookupMethodNode() {
    }

    static LookupMethodNode create() {
        return LookupMethodNodeGen.create();
    }

    public abstract JavaMethodDesc execute(Class<?> clazz, String name, boolean onlyStatic);

    @SuppressWarnings("unused")
    @Specialization(guards = {"onlyStatic == cachedStatic", "clazz == cachedClazz", "cachedName.equals(name)"}, limit = "LIMIT")
    static JavaMethodDesc doCached(Class<?> clazz, String name, boolean onlyStatic,
                    @Cached("onlyStatic") boolean cachedStatic,
                    @Cached("clazz") Class<?> cachedClazz,
                    @Cached("name") String cachedName,
                    @Cached("doUncached(clazz, name, onlyStatic)") JavaMethodDesc cachedMethod) {
        assert cachedMethod == JavaInteropReflect.findMethod(clazz, name, onlyStatic);
        return cachedMethod;
    }

    @Specialization(replaces = "doCached")
    static JavaMethodDesc doUncached(Class<?> clazz, String name, boolean onlyStatic) {
        return JavaInteropReflect.findMethod(clazz, name, onlyStatic);
    }
}
