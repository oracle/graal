/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.hotspot;

import static org.graalvm.compiler.options.OptionValues.GLOBAL;

import java.util.function.Supplier;

import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalCompilerFactory;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleRuntimeAccess;

import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.services.Services;

@ServiceProvider(TruffleRuntimeAccess.class)
public class HotSpotTruffleRuntimeAccess implements TruffleRuntimeAccess {

    static class Options {
        // @formatter:off
        @Option(help = "Select a Graal compiler configuration for Truffle compilation (default: use Graal system compiler configuration).")
        public static final OptionKey<String> TruffleCompilerConfiguration = new OptionKey<>(null);
        // @formatter:on
    }

    @Override
    public TruffleRuntime getRuntime() {
        Services.exportJVMCITo(getClass());

        // initialize JVMCI to make sure the TruffleCompiler option is parsed
        JVMCI.initialize();

        return new HotSpotTruffleRuntime(new LazyGraalRuntime());
    }

    private static final class LazyGraalRuntime implements Supplier<GraalRuntime> {

        private volatile GraalRuntime graalRuntime;

        @Override
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

        static GraalJVMCICompiler getCompiler() {
            if (!Options.TruffleCompilerConfiguration.hasBeenSet(GLOBAL)) {
                JVMCICompiler compiler = JVMCI.getRuntime().getCompiler();
                if (compiler instanceof GraalJVMCICompiler) {
                    return (GraalJVMCICompiler) compiler;
                }
            }
            CompilerConfigurationFactory compilerConfigurationFactory = CompilerConfigurationFactory.selectFactory(TruffleCompilerOptions.getValue(Options.TruffleCompilerConfiguration));
            return HotSpotGraalCompilerFactory.createCompiler(JVMCI.getRuntime(), compilerConfigurationFactory);
        }
    }
}
