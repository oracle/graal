/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

abstract class LookupConstructorNode extends Node {
    static final int LIMIT = 3;

    LookupConstructorNode() {
    }

    static LookupConstructorNode create() {
        return LookupConstructorNodeGen.create();
    }

    public abstract JavaMethodDesc execute(Class<?> clazz);

    @SuppressWarnings("unused")
    @Specialization(guards = {"clazz == cachedClazz"}, limit = "LIMIT")
    static JavaMethodDesc doCached(Class<?> clazz,
                    @Cached("clazz") Class<?> cachedClazz,
                    @Cached("doUncached(clazz)") JavaMethodDesc cachedMethod) {
        assert cachedMethod == doUncached(clazz);
        return cachedMethod;
    }

    @Specialization(replaces = "doCached")
    static JavaMethodDesc doUncached(Class<?> clazz) {
        return JavaClassDesc.forClass(clazz).lookupConstructor();
    }
}
