/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

@GenerateUncached
public abstract class LookupDeclaredMethod extends AbstractLookupNode {

    static final int LIMIT = 2;

    public abstract Method.MethodVersion execute(Klass klass, String key, boolean publicOnly, boolean isStatic, int arity) throws ArityException;

    public boolean isInvocable(Klass klass, String key, boolean isStatic) {
        return isInvocable(klass, key, true, isStatic);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "klass.equals(cachedKlass)",
                    "key.equals(cachedMethodName)",
                    "publicOnly == cachedPublicOnly",
                    "isStatic == cachedIsStatic",
                    "arity == cachedArity",
                    "methodVersion != null"}, limit = "LIMIT", assumptions = "methodVersion.getRedefineAssumption()")
    Method.MethodVersion doCached(Klass klass,
                    String key,
                    boolean publicOnly,
                    boolean isStatic,
                    int arity,
                    @Cached("klass") Klass cachedKlass,
                    @Cached("key") String cachedMethodName,
                    @Cached("publicOnly") boolean cachedPublicOnly,
                    @Cached("isStatic") boolean cachedIsStatic,
                    @Cached("arity") int cachedArity,
                    @Cached("doGeneric(klass, key, publicOnly, isStatic, arity)") Method.MethodVersion methodVersion) {
        return methodVersion;
    }

    @Specialization(replaces = "doCached")
    Method.MethodVersion doGeneric(Klass klass, String key, boolean publicOnly, boolean isStatic, int arity) throws ArityException {
        Method method = doLookup(klass, key, publicOnly, isStatic, arity);
        return method == null ? null : method.getMethodVersion();
    }

    @Override
    Method.MethodVersion[] getMethodArray(Klass k) {
        return k.getDeclaredMethodVersions();
    }
}
