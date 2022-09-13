/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.Meta;

@GenerateUncached
public abstract class LookupVirtualMethodNode extends AbstractLookupNode {
    static final int LIMIT = 2;

    public abstract Method[] execute(Klass klass, String methodName, int arity) throws ArityException;

    public boolean isInvocable(Klass klass, String member) {
        return isInvocable(klass, member, true, false);
    }

    @SuppressWarnings("unused")
    @Specialization
    Method[] doPrimitive(PrimitiveKlass klass,
                    String methodName,
                    int arity) {
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "methodName.equals(cachedMethodName)",
                    "arity == cachedArity"}, limit = "LIMIT")
    Method[] doArrayCached(ArrayKlass klass,
                    String methodName,
                    int arity,
                    @Cached("methodName") String cachedMethodName,
                    @Cached("arity") int cachedArity,
                    @Cached("doGeneric(getJLObject(klass.getMeta()), methodName, arity)") Method[] methods) {
        return methods;
    }

    @Specialization(replaces = "doArrayCached")
    Method[] doArrayGeneric(ArrayKlass klass,
                    String methodName,
                    int arity) throws ArityException {
        return doGeneric(getJLObject(klass.getMeta()), methodName, arity);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "klass.equals(cachedKlass.getKlass())",
                    "methodName.equals(cachedMethodName)",
                    "arity == cachedArity"}, limit = "LIMIT", assumptions = "cachedKlass.getAssumption()")
    Method[] doCached(ObjectKlass klass,
                    String methodName,
                    int arity,
                    @Cached("klass.getKlassVersion()") ObjectKlass.KlassVersion cachedKlass,
                    @Cached("methodName") String cachedMethodName,
                    @Cached("arity") int cachedArity,
                    @Cached("doGeneric(cachedKlass.getKlass(), methodName, arity)") Method[] methods) {
        return methods;
    }

    @Specialization(replaces = "doCached")
    Method[] doGeneric(ObjectKlass klass, String key, int arity) throws ArityException {
        return doLookup(klass, key, true, false, arity);
    }

    protected static ObjectKlass getJLObject(Meta meta) {
        return meta.java_lang_Object;
    }

    public static boolean isCandidate(Method m) {
        return m.isPublic() && !m.isStatic() && !m.isSignaturePolymorphicDeclared();
    }

    @Override
    Method.MethodVersion[] getMethodArray(Klass k) {
        assert k instanceof ObjectKlass;
        return ((ObjectKlass) k).getVTable();
    }
}
