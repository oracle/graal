/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.impl.ClassRegistries;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.StringTable;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.threads.ThreadsAccess;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

@NodeInfo(language = EspressoLanguage.NAME, description = "The abstract base node for all " + EspressoLanguage.IMPLEMENTATION_NAME + " nodes")
public abstract class EspressoNode extends Node implements ContextAccess {
    @Override
    public final EspressoContext getContext() {
        return EspressoContext.get(this);
    }

    @Override
    public final EspressoLanguage getLanguage() {
        return EspressoLanguage.get(this);
    }

    @Override
    public final Names getNames() {
        return getLanguage().getNames();
    }

    @Override
    public final Types getTypes() {
        return getLanguage().getTypes();
    }

    @Override
    public final Signatures getSignatures() {
        return getLanguage().getSignatures();
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
    public final ThreadsAccess getThreadAccess() {
        return getContext().getThreadAccess();
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
    public final JavaVersion getJavaVersion() {
        return getLanguage().getJavaVersion();
    }

    @Override
    public final NativeAccess getNativeAccess() {
        return getContext().getNativeAccess();
    }
}
