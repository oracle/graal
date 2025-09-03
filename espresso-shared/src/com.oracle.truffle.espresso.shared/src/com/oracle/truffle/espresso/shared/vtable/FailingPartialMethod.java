/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.vtable;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * A delegating {@link PartialMethod}, that indicates a method selection failure.
 */
public final class FailingPartialMethod<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> implements PartialMethod<C, M, F> {
    private final PartialMethod<C, M, F> delegate;

    FailingPartialMethod(PartialMethod<C, M, F> delegate) {
        this.delegate = delegate;
    }

    /**
     * The original {@link PartialMethod} used to construct this {@link FailingPartialMethod}.
     * Provided so runtimes that do not use {@link #asMethodAccess()} can recover the original
     * method nonetheless.
     */
    public PartialMethod<C, M, F> original() {
        return delegate;
    }

    @Override
    public boolean isSelectionFailure() {
        return true;
    }

    @Override
    public boolean isConstructor() {
        return delegate.isConstructor();
    }

    @Override
    public boolean isClassInitializer() {
        return delegate.isClassInitializer();
    }

    @Override
    public int getModifiers() {
        return delegate.getModifiers();
    }

    @Override
    public PartialMethod<C, M, F> withVTableIndex(int index) {
        PartialMethod<C, M, F> result = delegate.withVTableIndex(index);
        if (result == delegate) {
            return this;
        }
        return new FailingPartialMethod<>(result);
    }

    @Override
    public Symbol<Name> getSymbolicName() {
        return delegate.getSymbolicName();
    }

    @Override
    public Symbol<Signature> getSymbolicSignature() {
        return delegate.getSymbolicSignature();
    }

    @Override
    public M asMethodAccess() {
        return delegate.asMethodAccess();
    }
}
