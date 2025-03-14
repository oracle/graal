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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Regroups all custom espresso modules that we may inject on the boot class loader.
 */
public final class ModuleExtension {
    private static final ModuleExtension[] ESPRESSO_EXTENSION_MODULES = {
                    new Builder("org.graalvm.continuations", "continuations.jar", (context) -> context.getLanguage().isContinuumEnabled()).build(),
                    new Builder("espresso.hotswap", "hotswap.jar", (context) -> context.getEspressoEnv().JDWPOptions != null).build(),
                    new Builder("espresso.polyglot", "espresso-polyglot.jar", (context) -> context.getEspressoEnv().Polyglot).build(),
                    new Builder("jdk.graal.compiler.espresso", "espresso-graal.jar", (context) -> context.getLanguage().isInternalJVMCIEnabled())  //
                                    .setAutoAdd(true).setPlatform(true).setOptional(true) //
                                    .setRequiresConcealed(Map.of("jdk.graal.compiler", List.of(
                                                    "jdk.graal.compiler.api.replacements",
                                                    "jdk.graal.compiler.api.runtime",
                                                    "jdk.graal.compiler.bytecode",
                                                    "jdk.graal.compiler.code",
                                                    "jdk.graal.compiler.core.common",
                                                    "jdk.graal.compiler.core.common.alloc",
                                                    "jdk.graal.compiler.core.common.memory",
                                                    "jdk.graal.compiler.core.common.spi",
                                                    "jdk.graal.compiler.core.common.type",
                                                    "jdk.graal.compiler.core.target",
                                                    "jdk.graal.compiler.debug",
                                                    "jdk.graal.compiler.graph",
                                                    "jdk.graal.compiler.nodes",
                                                    "jdk.graal.compiler.nodes.cfg",
                                                    "jdk.graal.compiler.nodes.gc",
                                                    "jdk.graal.compiler.nodes.graphbuilderconf",
                                                    "jdk.graal.compiler.nodes.loop",
                                                    "jdk.graal.compiler.nodes.memory",
                                                    "jdk.graal.compiler.nodes.spi",
                                                    "jdk.graal.compiler.options",
                                                    "jdk.graal.compiler.phases.tiers",
                                                    "jdk.graal.compiler.phases.util",
                                                    "jdk.graal.compiler.runtime",
                                                    "jdk.graal.compiler.word")))
                                    // JVMCI is opened to compiler modules by `GraalServices`
                                    .build(),
                    new Builder("jdk.internal.vm.ci.espresso", "espresso-jvmci.jar", (context) -> context.getLanguage().isInternalJVMCIEnabled())  //
                                    .setAutoAdd(true)  //
                                    .setRequiresConcealed(Map.of("jdk.internal.vm.ci", List.of(
                                                    "jdk.vm.ci.amd64",
                                                    "jdk.vm.ci.aarch64",
                                                    "jdk.vm.ci.code",
                                                    "jdk.vm.ci.code.stack",
                                                    "jdk.vm.ci.common",
                                                    "jdk.vm.ci.meta",
                                                    "jdk.vm.ci.runtime")))  //
                                    .build(),
    };

    private static final ModuleExtension[] EMPTY_MODULE_EXTENSION_ARRAY = new ModuleExtension[0];

    private final String moduleName;
    private final String jarName;
    private final Function<EspressoContext, Boolean> isEnabled;
    private final boolean platform;
    private final boolean autoAdd;
    private final boolean optional;
    private final Map<String, List<String>> requiresConcealed;

    private ModuleExtension(String moduleName, String jarName, Function<EspressoContext, Boolean> isEnabled, boolean platform, boolean autoAdd, boolean optional,
                    Map<String, List<String>> requiresConcealed) {
        this.moduleName = moduleName;
        this.jarName = jarName;
        this.isEnabled = isEnabled;
        this.platform = platform;
        this.autoAdd = autoAdd;
        this.optional = optional;
        this.requiresConcealed = requiresConcealed;
    }

    private static final class Builder {
        private final String moduleName;
        private final String jarName;
        private final Function<EspressoContext, Boolean> isEnabled;
        private boolean platform;
        private boolean autoAdd;
        private boolean optional;
        private Map<String, List<String>> requiresConcealed;

        Builder(String moduleName, String jarName, Function<EspressoContext, Boolean> isEnabled) {
            this.moduleName = moduleName;
            this.jarName = jarName;
            this.isEnabled = isEnabled;
        }

        ModuleExtension build() {
            return new ModuleExtension(moduleName, jarName, isEnabled, platform, autoAdd, optional, requiresConcealed == null ? Map.of() : requiresConcealed);
        }

        Builder setPlatform(boolean platform) {
            this.platform = platform;
            return this;
        }

        Builder setAutoAdd(boolean autoAdd) {
            this.autoAdd = autoAdd;
            return this;
        }

        Builder setOptional(boolean optional) {
            this.optional = optional;
            return this;
        }

        Builder setRequiresConcealed(Map<String, List<String>> requiresConcealed) {
            this.requiresConcealed = requiresConcealed;
            return this;
        }
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
                if (me.isOptional()) {
                    Path jar = context.getEspressoLibs().resolve(me.jarName());
                    if (!Files.exists(jar)) {
                        continue;
                    }
                }
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

    public boolean isAutoAdd() {
        return autoAdd;
    }

    public boolean isOptional() {
        return optional;
    }

    public Map<String, List<String>> getRequiresConcealed() {
        return requiresConcealed;
    }
}
