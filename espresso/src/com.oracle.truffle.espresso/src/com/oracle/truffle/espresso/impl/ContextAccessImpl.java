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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.StringTable;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.threads.ThreadAccess;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

public abstract class ContextAccessImpl implements ContextAccess {
    private final EspressoContext context;

    public ContextAccessImpl(EspressoContext context) {
        this.context = context;
    }

    @Override
    public final EspressoContext getContext() {
        return context;
    }

    @Override
    public final EspressoLanguage getLanguage() {
        return getContext().getLanguage();
    }

    @Override
    public final Meta getMeta() {
        return getContext().getMeta();
    }

    @Override
    public final VM getVM() {
        return getContext().getVM();
    }

    @Override
    public final ThreadAccess getThreadAccess() {
        return getContext().getThreadAccess();
    }

    @Override
    public final GuestAllocator getAllocator() {
        return getContext().getAllocator();
    }

    @Override
    public final InterpreterToVM getInterpreterToVM() {
        return getContext().getInterpreterToVM();
    }

    @Override
    public final StringTable getStrings() {
        return getContext().getStrings();
    }

    @Override
    public final ClassRegistries getRegistries() {
        return getContext().getRegistries();
    }

    @Override
    public final Substitutions getSubstitutions() {
        return getContext().getSubstitutions();
    }

    @Override
    public final NativeAccess getNativeAccess() {
        return getContext().getNativeAccess();
    }
}
