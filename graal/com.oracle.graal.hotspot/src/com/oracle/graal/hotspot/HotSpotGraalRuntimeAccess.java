/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.options.Option;
import jdk.vm.ci.options.OptionValue;
import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.service.ServiceProvider;
import jdk.vm.ci.service.Services;

import com.oracle.graal.api.runtime.GraalRuntime;
import com.oracle.graal.api.runtime.GraalRuntimeAccess;

@ServiceProvider(GraalRuntimeAccess.class)
public class HotSpotGraalRuntimeAccess implements GraalRuntimeAccess {

    static class Options {
        // @formatter:off
        @Option(help = "Select a graal compiler for hosted compilation (default: use JVMCI system compiler).")
        public static final OptionValue<String> HostedCompiler = new OptionValue<>(null);
        // @formatter:on
    }

    @Override
    public GraalRuntime getRuntime() {
        HotSpotGraalCompiler compiler = getCompiler(Options.HostedCompiler.getValue());
        return compiler.getGraalRuntime();
    }

    private static HotSpotGraalCompiler getCompiler(String config) {
        HotSpotJVMCIRuntimeProvider jvmciRuntime = HotSpotJVMCIRuntime.runtime();
        if (config == null) {
            // default: fall back to the JVMCI system compiler
            return (HotSpotGraalCompiler) jvmciRuntime.getCompiler();
        } else {
            for (JVMCICompilerFactory factory : Services.load(JVMCICompilerFactory.class)) {
                if (factory instanceof HotSpotGraalCompilerFactory) {
                    HotSpotGraalCompilerFactory graalFactory = (HotSpotGraalCompilerFactory) factory;
                    if (config.equals(factory.getCompilerName())) {
                        return graalFactory.createCompiler(jvmciRuntime);
                    }
                }
            }
            throw new JVMCIError("Graal compiler configuration '" + config + "' not found");
        }
    }
}
