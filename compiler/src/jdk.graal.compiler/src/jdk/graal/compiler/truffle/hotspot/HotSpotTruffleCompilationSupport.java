/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.truffle.hotspot;

import jdk.graal.compiler.hotspot.CompilerConfigurationFactory;
import jdk.graal.compiler.hotspot.HotSpotGraalOptionValues;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.truffle.AbstractTruffleCompilationSupport;

import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class HotSpotTruffleCompilationSupport extends AbstractTruffleCompilationSupport {

    private volatile String compilerConfigurationName;

    @Override
    public String getCompilerConfigurationName(TruffleCompilerRuntime truffleRuntime) {
        String compilerConfig = compilerConfigurationName;
        if (compilerConfig != null) {
            return compilerConfig;
        }

        // compiler not yet intitialized. try to resolve the configuration name without initializing
        // a JVMCI compiler for lazy class loading.
        return getLazyCompilerConfigurationName();
    }

    public static String getLazyCompilerConfigurationName() {
        final OptionValues options = HotSpotGraalOptionValues.defaultOptions();
        String factoryName = HotSpotTruffleCompilerImpl.Options.TruffleCompilerConfiguration.getValue(options);
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        CompilerConfigurationFactory compilerConfigurationFactory = CompilerConfigurationFactory.selectFactory(factoryName, options, runtime);
        return compilerConfigurationFactory.getName();
    }

    @Override
    public TruffleCompiler createCompiler(TruffleCompilerRuntime runtime) {
        HotSpotTruffleCompilerImpl compiler = HotSpotTruffleCompilerImpl.create(runtime);
        compilerConfigurationName = compiler.getHotspotGraalRuntime().getCompilerConfigurationName();
        return compiler;

    }

    @Override
    public void registerRuntime(TruffleCompilerRuntime runtime) {
        HotSpotTruffleHostEnvironmentLookup.registerRuntime(runtime);
    }

    @Override
    public String getCompilerVersion() {
        return readCompilerVersion();
    }

    public static String readCompilerVersion() {
        InputStream in = HotSpotTruffleCompilationSupport.class.getResourceAsStream("/META-INF/graalvm/jdk.graal.compiler/version");
        if (in == null) {
            throw new InternalError("Compiler must have a version file.");
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return r.readLine();
        } catch (IOException ioe) {
            throw new InternalError(ioe);
        }
    }
}
