/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.impl.ContextAccess;

class EspressoOptionCollector implements ContextAccess {
    private final EspressoContext context;

    // Checkstyle: stop field name check

    final boolean InlineFieldAccessors;
    final boolean InlineMethodHandle;
    final boolean SplitMethodHandles;

    final EspressoOptions.VerifyMode Verify;
    final EspressoOptions.SpecCompliancyMode SpecCompliancyMode;
    final boolean EnableManagement;
    final com.oracle.truffle.espresso.jdwp.api.JDWPOptions JDWPOptions;
    final boolean MultiThreaded;
    final boolean SoftExit;

    // Checkstyle: resume field name check

    EspressoOptionCollector(EspressoContext context, TruffleLanguage.Env env) {
        this.context = context;

        this.JDWPOptions = env.getOptions().get(EspressoOptions.JDWPOptions); // null if not
        // specified
        this.InlineFieldAccessors = JDWPOptions != null ? false : env.getOptions().get(EspressoOptions.InlineFieldAccessors);
        this.InlineMethodHandle = JDWPOptions != null ? false : env.getOptions().get(EspressoOptions.InlineMethodHandle);
        this.SplitMethodHandles = JDWPOptions != null ? false : env.getOptions().get(EspressoOptions.SplitMethodHandles);
        this.Verify = env.getOptions().get(EspressoOptions.Verify);
        this.SpecCompliancyMode = env.getOptions().get(EspressoOptions.SpecCompliancy);
        this.EnableManagement = env.getOptions().get(EspressoOptions.EnableManagement);
        this.MultiThreaded = env.getOptions().get(EspressoOptions.MultiThreaded);
        this.SoftExit = env.getOptions().get(EspressoOptions.SoftExit);
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }
}
