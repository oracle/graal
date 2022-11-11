/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.asm;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionVerifier;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.Register;

/**
 * Factory class for creating {@link CompilationResultBuilder}s.
 */
public interface CompilationResultBuilderFactory {

    class Options {
        @Option(help = "Path to jar file containing LIR instruction verifier.", type = OptionType.Debug) //
        public static final OptionKey<String> LIRInstructionVerifierPath = new OptionKey<>(null);
    }

    /**
     * Creates a new {@link CompilationResultBuilder}.
     */
    CompilationResultBuilder createBuilder(CodeGenProviders providers,
                    FrameMap frameMap,
                    Assembler<?> asm,
                    DataBuilder dataBuilder,
                    FrameContext frameContext,
                    OptionValues options,
                    DebugContext debug,
                    CompilationResult compilationResult,
                    Register nullRegister);

    /**
     * The default factory creates a standard {@link CompilationResultBuilder}.
     */
    CompilationResultBuilderFactory Default = new CompilationResultBuilderFactory() {

        private List<LIRInstructionVerifier> lirInstructionVerifiers = new ArrayList<>();
        private volatile boolean isVerifierInitialized = false;

        private void initializeLIRVerifiers(String lirInstructionVerifierPath) {
            try {
                URL verifierURL = Paths.get(lirInstructionVerifierPath).toUri().toURL();
                URLClassLoader cl = new URLClassLoader(new URL[]{verifierURL}, ClassLoader.getPlatformClassLoader());
                for (LIRInstructionVerifier verifier : ServiceLoader.load(LIRInstructionVerifier.class, cl)) {
                    if (verifier.isEnabled()) {
                        lirInstructionVerifiers.add(verifier);
                    }
                }
            } catch (MalformedURLException e) {
                throw GraalError.shouldNotReachHere(e, "Malformed URL encountered.");
            }
        }

        @Override
        public CompilationResultBuilder createBuilder(CodeGenProviders providers,
                        FrameMap frameMap,
                        Assembler<?> asm,
                        DataBuilder dataBuilder,
                        FrameContext frameContext,
                        OptionValues options,
                        DebugContext debug,
                        CompilationResult compilationResult,
                        Register uncompressedNullRegister) {
            if (!isVerifierInitialized) {
                synchronized (lirInstructionVerifiers) {
                    if (!isVerifierInitialized) {
                        String lirInstructionVerifierPath = Options.LIRInstructionVerifierPath.getValue(options);
                        if (lirInstructionVerifierPath != null && !lirInstructionVerifierPath.isEmpty()) {
                            initializeLIRVerifiers(lirInstructionVerifierPath);
                        }
                        isVerifierInitialized = true;
                    }
                }
            }
            return new CompilationResultBuilder(providers,
                            frameMap,
                            asm,
                            dataBuilder,
                            frameContext,
                            options,
                            debug,
                            compilationResult,
                            uncompressedNullRegister,
                            EconomicMap.create(Equivalence.DEFAULT),
                            lirInstructionVerifiers);
        }
    };
}
