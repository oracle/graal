/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition.plugins.api;

import java.util.List;

import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.redefinition.plugins.impl.RedefinitionPluginHandler;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public abstract class InternalRedefinitionPlugin {

    private EspressoContext context;
    private RedefinitionPluginHandler redefinitionPluginHandler;

    public EspressoContext getContext() {
        return context;
    }

    public void activate(EspressoContext espressoContext, RedefinitionPluginHandler handler) {
        this.context = espressoContext;
        this.redefinitionPluginHandler = handler;
    }

    public boolean shouldRerunClassInitializer(@SuppressWarnings("unused") ObjectKlass klass, @SuppressWarnings("unused") boolean changed) {
        return false;
    }

    public void collectExtraClassesToReload(@SuppressWarnings("unused") List<RedefineInfo> redefineInfos, @SuppressWarnings("unused") List<RedefineInfo> additional) {
        // default does nothing
    }

    public void postClassRedefinition(@SuppressWarnings("unused") ObjectKlass[] changedKlasses) {
        // default does nothing
    }

    protected void registerClassLoadAction(String className, ClassLoadAction action) {
        redefinitionPluginHandler.registerClassLoadAction(className, action);
    }
}
