/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vmaccess;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import jdk.graal.compiler.vmaccess.ModuleSupport;
import jdk.graal.compiler.vmaccess.VMAccess;

public final class EspressoExternalVMAccessBuilder implements VMAccess.Builder {
    private List<String> classpath;
    private List<String> modulepath;
    private List<String> addModules;
    private boolean enableAssertions;
    private boolean enableSystemAssertions;
    private Map<String, String> systemProperties;
    private List<String> vmOptions;

    @Override
    public String getVMAccessName() {
        return "espresso-context";
    }

    @Override
    public VMAccess.Builder classPath(List<String> paths) {
        this.classpath = paths;
        return this;
    }

    @Override
    public VMAccess.Builder modulePath(List<String> paths) {
        this.modulepath = paths;
        return this;
    }

    @Override
    public VMAccess.Builder addModules(List<String> modules) {
        this.addModules = modules;
        return this;
    }

    @Override
    public VMAccess.Builder enableAssertions(boolean assertionStatus) {
        this.enableAssertions = assertionStatus;
        return this;
    }

    @Override
    public VMAccess.Builder enableSystemAssertions(boolean assertionStatus) {
        this.enableSystemAssertions = assertionStatus;
        return this;
    }

    @Override
    public VMAccess.Builder systemProperty(String name, String value) {
        if (systemProperties == null) {
            systemProperties = new HashMap<>();
        }
        systemProperties.put(name, value);
        return this;
    }

    @Override
    public VMAccess.Builder vmOption(String option) {
        if (vmOptions == null) {
            vmOptions = new ArrayList<>();
        }
        vmOptions.add(option);
        return this;
    }

    @Override
    public VMAccess build() {
        ModuleAccess.ensureModuleAccess();
        Context.Builder builder = Context.newBuilder();
        builder.option("java.ExposeJVMCIHelper", "true");
        builder.allowAllAccess(true);

        if (classpath != null) {
            builder.option("java.Classpath", String.join(File.pathSeparator, classpath));
        }
        if (modulepath != null) {
            builder.option("java.ModulePath", String.join(File.pathSeparator, modulepath));
        }
        if (addModules != null) {
            builder.option("java.AddModules", String.join(File.pathSeparator, addModules));
        }
        if (systemProperties != null) {
            systemProperties.forEach((key, value) -> builder.option("java.Properties." + key, value));
        }
        builder.option("java.EnableAssertions", Boolean.toString(enableAssertions));
        builder.option("java.EnableSystemAssertions", Boolean.toString(enableSystemAssertions));
        if (vmOptions != null && !vmOptions.isEmpty()) {
            throw new RuntimeException("unimplemented: " + vmOptions);
        }
        builder.allowExperimentalOptions(true);

        Context context = builder.build();
        context.enter();
        try {
            context.initialize("java");
        } catch (PolyglotException e) {
            if (e.isGuestException()) {
                Value o = e.getGuestObject();
                if (o != null) {
                    try {
                        o.invokeMember("printStackTrace");
                    } catch (Throwable t) {
                        // ignore exceptions while trying to print exceptions
                    }
                }
            }
            throw e;
        }
        return new EspressoExternalVMAccess(context);
    }

    private static final class ModuleAccess {
        static {
            ModuleSupport.addExports("jdk.internal.vm.ci.espresso", "jdk.internal.vm.ci",
                            "jdk.vm.ci.amd64",
                            "jdk.vm.ci.aarch64",
                            "jdk.vm.ci.code",
                            "jdk.vm.ci.code.stack",
                            "jdk.vm.ci.common",
                            "jdk.vm.ci.meta",
                            "jdk.vm.ci.meta.annotation",
                            "jdk.vm.ci.riscv64",
                            "jdk.vm.ci.runtime");

            ModuleSupport.addExports("jdk.graal.compiler.espresso.vmaccess", "jdk.internal.vm.ci",
                            "jdk.vm.ci.meta",
                            "jdk.vm.ci.meta.annotation",
                            "jdk.vm.ci.code",
                            "jdk.vm.ci.code.site",
                            "jdk.vm.ci.code.stack",
                            "jdk.vm.ci.common",
                            "jdk.vm.ci.amd64",
                            "jdk.vm.ci.aarch64",
                            "jdk.vm.ci.services",
                            "jdk.vm.ci.runtime");
            ModuleSupport.addExports("jdk.graal.compiler.espresso.vmaccess", "jdk.graal.compiler",
                            "jdk.graal.compiler.api.replacements",
                            "jdk.graal.compiler.core.common.spi",
                            "jdk.graal.compiler.debug",
                            "jdk.graal.compiler.nodes.loop",
                            "jdk.graal.compiler.nodes.spi",
                            "jdk.graal.compiler.phases.util",
                            "jdk.graal.compiler.word");

            ModuleSupport.addExports("jdk.graal.compiler.espresso", "jdk.graal.compiler",
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
                            "jdk.graal.compiler.nodes.gc",
                            "jdk.graal.compiler.nodes.graphbuilderconf",
                            "jdk.graal.compiler.nodes.loop",
                            "jdk.graal.compiler.nodes.memory",
                            "jdk.graal.compiler.nodes.memory.address",
                            "jdk.graal.compiler.nodes.spi",
                            "jdk.graal.compiler.options",
                            "jdk.graal.compiler.phases.tiers",
                            "jdk.graal.compiler.phases.util",
                            "jdk.graal.compiler.replacements",
                            "jdk.graal.compiler.runtime",
                            "jdk.graal.compiler.word");
        }

        static void ensureModuleAccess() {
        }
    }
}
