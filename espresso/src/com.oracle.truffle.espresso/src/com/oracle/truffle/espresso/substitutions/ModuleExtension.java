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

package com.oracle.truffle.espresso.substitutions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Regroups all custom espresso modules that we may inject on the boot class loader.
 */
public final class ModuleExtension {
    private static final ModuleExtension[] ESPRESSO_EXTENSION_MODULES = new ModuleExtension[]{
                    new ModuleExtension("espresso.hotswap", "hotswap.jar", (context) -> context.getEspressoEnv().JDWPOptions != null),
                    new ModuleExtension("espresso.polyglot", "polyglot.jar", (context) -> context.getEspressoEnv().Polyglot),
    };
    private static final ModuleExtension[] EMPTY_MODULE_EXTENSION_ARRAY = new ModuleExtension[0];

    private final String moduleName;
    private final String jarName;
    private final Function<EspressoContext, Boolean> isEnabled;

    private ModuleExtension(String moduleName, String jarName, Function<EspressoContext, Boolean> isEnabled) {
        this.moduleName = moduleName;
        this.jarName = jarName;
        this.isEnabled = isEnabled;
    }

    @TruffleBoundary
    public static ModuleExtension[] get(EspressoContext context) {
        List<ModuleExtension> modules = new ArrayList<>(ESPRESSO_EXTENSION_MODULES.length);
        for (ModuleExtension me : ESPRESSO_EXTENSION_MODULES) {
            if (me.isEnabled.apply(context)) {
                modules.add(me);
            }
        }
        return modules.toArray(EMPTY_MODULE_EXTENSION_ARRAY);
    }

    public String moduleName() {
        return moduleName;
    }

    public String jarName() {
        return jarName;
    }
}
