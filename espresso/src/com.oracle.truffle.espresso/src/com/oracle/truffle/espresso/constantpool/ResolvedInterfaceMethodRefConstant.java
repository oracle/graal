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
package com.oracle.truffle.espresso.constantpool;

import java.util.Objects;

import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.shared.classfile.ConstantPool;
import com.oracle.truffle.espresso.shared.constantpool.InterfaceMethodRefConstant;
import com.oracle.truffle.espresso.shared.constantpool.Resolvable;
import com.oracle.truffle.espresso.shared.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.shared.descriptors.Symbol.Signature;

public final class ResolvedInterfaceMethodRefConstant implements InterfaceMethodRefConstant, Resolvable.ResolvedConstant {
    private final Method resolved;

    ResolvedInterfaceMethodRefConstant(Method resolved) {
        this.resolved = Objects.requireNonNull(resolved);
    }

    @Override
    public Method value() {
        return resolved;
    }

    @Override
    public Symbol<Name> getHolderKlassName(ConstantPool pool) {
        // return resolved.getDeclaringKlass().getName();
        throw EspressoError.shouldNotReachHere("Method already resolved");
    }

    @Override
    public Symbol<Name> getName(ConstantPool pool) {
        return resolved.getName();
    }

    @Override
    public Symbol<Signature> getDescriptor(ConstantPool pool) {
        return resolved.getRawSignature();
    }
}
