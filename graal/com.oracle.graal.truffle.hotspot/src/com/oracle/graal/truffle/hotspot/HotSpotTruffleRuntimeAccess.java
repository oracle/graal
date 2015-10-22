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
package com.oracle.graal.truffle.hotspot;

import java.util.function.Supplier;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.options.Option;
import jdk.vm.ci.options.OptionValue;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.service.ServiceProvider;
import jdk.vm.ci.service.Services;

import com.oracle.graal.api.runtime.GraalJVMCICompiler;
import com.oracle.graal.api.runtime.GraalRuntime;
import com.oracle.graal.hotspot.HotSpotGraalCompilerFactory;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleRuntimeAccess;

@ServiceProvider(TruffleRuntimeAccess.class)
public class HotSpotTruffleRuntimeAccess implements TruffleRuntimeAccess {

    static class Options {
        // @formatter:off
        @Option(help = "Select a graal compiler for Truffle compilation (default: use JVMCI system compiler).")
        public static final OptionValue<String> TruffleCompiler = new OptionValue<>(null);
        // @formatter:on
    }

    public TruffleRuntime getRuntime() {
        // initialize JVMCI to make sure the TruffleCompiler option is parsed
        JVMCI.initialize();

        Supplier<GraalRuntime> lazyRuntime;
        if (Options.TruffleCompiler.hasDefaultValue()) {
            lazyRuntime = new LazySystemGraalRuntime();
        } else {
            HotSpotGraalCompilerFactory factory = findCompilerFactory(Options.TruffleCompiler.getValue());
            lazyRuntime = new LazyCustomGraalRuntime(factory);
        }

        return new HotSpotTruffleRuntime(lazyRuntime);
    }

    private static HotSpotGraalCompilerFactory findCompilerFactory(String name) {
        for (JVMCICompilerFactory factory : Services.load(JVMCICompilerFactory.class)) {
            if (factory instanceof HotSpotGraalCompilerFactory) {
                if (name.equals(factory.getCompilerName())) {
                    return (HotSpotGraalCompilerFactory) factory;
                }
            }
        }
        throw new JVMCIError("Graal compiler configuration '%s' not found.", name);
    }

    private abstract static class LazyGraalRuntime implements Supplier<GraalRuntime> {

        private volatile GraalRuntime graalRuntime;

        public GraalRuntime get() {
            if (graalRuntime == null) {
                synchronized (this) {
                    if (graalRuntime == null) {
                        graalRuntime = getCompiler().getGraalRuntime();
                    }
                }
            }
            return graalRuntime;
        }

        protected abstract GraalJVMCICompiler getCompiler();
    }

    private static final class LazyCustomGraalRuntime extends LazyGraalRuntime {

        private final HotSpotGraalCompilerFactory factory;

        private LazyCustomGraalRuntime(HotSpotGraalCompilerFactory factory) {
            this.factory = factory;
        }

        @Override
        protected GraalJVMCICompiler getCompiler() {
            return factory.createCompiler(JVMCI.getRuntime());
        }
    }

    private static final class LazySystemGraalRuntime extends LazyGraalRuntime {

        @Override
        protected GraalJVMCICompiler getCompiler() {
            JVMCICompiler compiler = JVMCI.getRuntime().getCompiler();
            if (compiler instanceof GraalJVMCICompiler) {
                return (GraalJVMCICompiler) compiler;
            } else {
                throw new JVMCIError("JVMCI system compiler '%s' is not a Graal compiler.", compiler.getClass().getName());
            }
        }
    }
}
