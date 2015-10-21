/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static jdk.vm.ci.inittimer.InitTimer.timer;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.inittimer.InitTimer;
import jdk.vm.ci.options.Option;
import jdk.vm.ci.options.OptionType;
import jdk.vm.ci.options.OptionValue;
import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.runtime.JVMCIRuntime;
import jdk.vm.ci.service.Services;

import com.oracle.graal.phases.tiers.CompilerConfiguration;

public abstract class HotSpotGraalCompilerFactory implements JVMCICompilerFactory {

    static class Options {

        // @formatter:off
        @Option(help = "In tiered mode compile the compiler itself using optimized first tier code.", type = OptionType.Expert)
        public static final OptionValue<Boolean> CompileGraalWithC1Only = new OptionValue<>(true);
        // @formatter:on

    }

    @SuppressWarnings("try")
    private static class Lazy {

        static {
            try (InitTimer t = timer("HotSpotBackendFactory.register")) {
                for (HotSpotBackendFactory backend : Services.load(HotSpotBackendFactory.class)) {
                    backend.register();
                }
            }
        }

        static void registerBackends() {
            // force run of static initializer
        }
    }

    protected abstract HotSpotBackendFactory getBackendFactory(Architecture arch);

    protected abstract CompilerConfiguration createCompilerConfiguration();

    @SuppressWarnings("try")
    @Override
    public HotSpotGraalCompiler createCompiler(JVMCIRuntime runtime) {
        HotSpotJVMCIRuntime jvmciRuntime = (HotSpotJVMCIRuntime) runtime;
        try (InitTimer t = timer("HotSpotGraalRuntime.<init>")) {
            Lazy.registerBackends();
            HotSpotGraalRuntime graalRuntime = new HotSpotGraalRuntime(jvmciRuntime, this);
            HotSpotGraalVMEventListener.addRuntime(graalRuntime);
            return new HotSpotGraalCompiler(jvmciRuntime, graalRuntime);
        }
    }

    @Override
    public String[] getTrivialPrefixes() {
        if (Options.CompileGraalWithC1Only.getValue()) {
            return new String[]{"jdk/vm/ci", "com/oracle/graal"};
        }
        return null;
    }
}
