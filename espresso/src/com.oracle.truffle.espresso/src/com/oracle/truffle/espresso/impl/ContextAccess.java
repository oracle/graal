/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

public interface ContextAccess extends LanguageAccess {
    EspressoContext getContext();

    default EspressoLanguage getLanguage() {
        return getContext().getLanguage();
    }

    default Meta getMeta() {
        return getContext().getMeta();
    }

    default VM getVM() {
        return getContext().getVM();
    }

    default ThreadAccess getThreadAccess() {
        return getContext().getThreadAccess();
    }

    default GuestAllocator getAllocator() {
        return getContext().getAllocator();
    }

    default InterpreterToVM getInterpreterToVM() {
        return getContext().getInterpreterToVM();
    }

    default StringTable getStrings() {
        return getContext().getStrings();
    }

    default ClassRegistries getRegistries() {
        return getContext().getRegistries();
    }

    default Substitutions getSubstitutions() {
        return getContext().getSubstitutions();
    }

    default NativeAccess getNativeAccess() {
        return getContext().getNativeAccess();
    }
}
