/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import java.util.function.Function;

import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.espresso.classfile.descriptors.Descriptor;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.meta.MemberAccess;

public abstract class Member<T extends Descriptor> implements MemberAccess<Klass, Method, Field> {

    public abstract Symbol<Name> getName();

    @Override
    public final Symbol<Name> getSymbolicName() {
        return getName();
    }

    public abstract ObjectKlass getDeclaringKlass();

    @Override
    public final ObjectKlass getDeclaringClass() {
        return getDeclaringKlass();
    }

    @Override
    public final boolean accessChecks(Klass accessingClass, Klass holderClass) {
        return RuntimeConstantPool.memberCheckAccess(accessingClass, holderClass, this);
    }

    @Override
    @Idempotent
    // Re-implement here for indempotent annotation. Some of our nodes benefit from it.
    public boolean isAbstract() {
        return MemberAccess.super.isAbstract();
    }

    @Override
    public final void loadingConstraints(Klass accessingClass, Function<String, RuntimeException> errorHandler) {
        checkLoadingConstraints(accessingClass.getDefiningClassLoader(), getDeclaringKlass().getDefiningClassLoader(), errorHandler);
    }

    public abstract void checkLoadingConstraints(StaticObject loader1, StaticObject loader2, Function<String, RuntimeException> errorHandler);
}
