/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

@GenerateUncached
public abstract class LookupDeclaredMethod extends Node {

    static final int LIMIT = 2;

    public abstract Method execute(Klass klass, String methodName, boolean publicOnly, boolean isStatic, int arity);

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "klass.equals(cachedKlass)",
                    "methodName.equals(cachedMethodName)",
                    "publicOnly == cachedPublicOnly",
                    "isStatic == cachedIsStatic",
                    "arity == cachedArity"}, limit = "LIMIT")
    Method doCached(Klass klass,
                    String methodName,
                    boolean publicOnly,
                    boolean isStatic,
                    int arity,
                    @Cached("klass") Klass cachedKlass,
                    @Cached("methodName") String cachedMethodName,
                    @Cached("publicOnly") boolean cachedPublicOnly,
                    @Cached("isStatic") boolean cachedIsStatic,
                    @Cached("arity") int cachedArity,
                    @Cached("doGeneric(klass, methodName, publicOnly, isStatic, arity)") Method method) {
        return method;
    }

    @Specialization(replaces = "doCached")
    Method doGeneric(Klass klass, String methodName, boolean publicOnly, boolean isStatic, int arity) {
        for (Method m : klass.getDeclaredMethods()) {
            if (m.isPublic() == publicOnly && m.isStatic() == isStatic && m.getName().toString().equals(methodName)) {
                if (m.getParameterCount() == arity) {
                    return m;
                }
            }
        }
        return null;
    }
}
