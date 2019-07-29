/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.runtime.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Tries to replace invocations of {@link String#equals(Object)} with identity checks by caching
 * parameter {@code a}. Parameter {@code b} is expected to be final!
 */
@GenerateUncached
public abstract class StringEqualsNode extends Node {

    public abstract boolean execute(String a, String b);

    @SuppressWarnings("unused")
    @Specialization(guards = {"a == cachedA", "cachedA.equals(b)"}, limit = "4")
    static boolean cacheIdentity(String a, String b,
                    @Cached("a") String cachedA) {
        CompilerAsserts.compilationConstant(b);
        return true;
    }

    @Specialization(replaces = "cacheIdentity")
    static boolean doEquals(String a, String b) {
        CompilerAsserts.compilationConstant(b);
        return b.equals(a);
    }
}
