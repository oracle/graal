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
    private static final ModuleExtension[] ESPRESSO_EXTENSION_MODULES = {
                    new ModuleExtension("org.graalvm.continuations", "continuations.jar", (context) -> context.getEspressoEnv().Continuum),
                    new ModuleExtension("espresso.hotswap", "hotswap.jar", (context) -> context.getEspressoEnv().JDWPOptions != null),
                    new ModuleExtension("espresso.polyglot", "espresso-polyglot.jar", (context) -> context.getEspressoEnv().Polyglot),
    };

    private static final ModuleExtension[] EMPTY_MODULE_EXTENSION_ARRAY = new ModuleExtension[0];

    private final String moduleName;
    private final String jarName;
    private final Function<EspressoContext, Boolean> isEnabled;
    private final boolean platform;

    private ModuleExtension(String moduleName, String jarName, Function<EspressoContext, Boolean> isEnabled) {
        this(moduleName, jarName, isEnabled, false);
    }

    private ModuleExtension(String moduleName, String jarName, Function<EspressoContext, Boolean> isEnabled, boolean platform) {
        this.moduleName = moduleName;
        this.jarName = jarName;
        this.isEnabled = isEnabled;
        this.platform = platform;
    }

    public static ModuleExtension[] getAllExtensions(EspressoContext context) {
        return getExtensions(null, context);
    }

    public static ModuleExtension[] getBootExtensions(EspressoContext context) {
        return getExtensions(false, context);
    }

    public static ModuleExtension[] getPlatformExtensions(EspressoContext context) {
        return getExtensions(true, context);
    }

    @TruffleBoundary
    private static ModuleExtension[] getExtensions(Boolean platform, EspressoContext context) {
        List<ModuleExtension> modules = new ArrayList<>(ESPRESSO_EXTENSION_MODULES.length);
        for (ModuleExtension me : ESPRESSO_EXTENSION_MODULES) {
            if (me.isEnabled.apply(context) && (platform == null || platform == me.platform)) {
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

    public boolean isPlatform() {
        return platform;
    }
}
