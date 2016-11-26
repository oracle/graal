/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test.alpha;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

@RunWith(Parameterized.class)
public final class ShootoutsSulongOnlySuite extends BaseSulongOnlyHarness {

    private static final Path SHOOTOUTS_SUITE_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../tests/cache/tests/benchmarksgame").toPath();
    private static final String benchmarkSuffix = "_clang_O1.bc";

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public RunConfiguration configuration;
    @Parameter(value = 2) public String name;

    @Parameters(name = "{2}")
    public static Collection<Object[]> data() {

        final Map<Path, RunConfiguration> runs = new HashMap<>();
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/binarytrees/binarytrees.gcc-2.gcc/binarytrees.gcc-2.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "12"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/binarytrees/binarytrees.gcc/binarytrees.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "12"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/fannkuchredux/fannkuchredux.cint/fannkuchredux.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "9"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/fannkuchredux/fannkuchredux.gcc/fannkuchredux.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "9"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/fasta/fasta.cint/fasta.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "100"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/fasta/fasta.gcc-4.gcc/fasta.gcc-4.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "100"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/fasta/fasta.gcc-5.gcc/fasta.gcc-5.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "100"));
        /*
         * TODO: fix this segfault, issue #599
         * @formatter:off
         * TODO: segfault
         * runs.put(new File(SHOOTOUTS_SUITE_DIR + "/fastaredux/fastaredux.gcc-2.gcc/fastaredux.gcc-2.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null));
         * @formatter:on
         */
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/fastaredux/fastaredux.gcc-3.gcc/fastaredux.gcc-3.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/mandelbrot/mandelbrot.cint-2.cint/mandelbrot.cint-2.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "500"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/mandelbrot/mandelbrot.gcc-2.gcc/mandelbrot.gcc-2.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "500"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/mandelbrot/mandelbrot.gcc-8.gcc/mandelbrot.gcc-8.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "500"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/mandelbrot/mandelbrot.gcc-9.gcc/mandelbrot.gcc-9.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "500"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/nbody/nbody.cint/nbody.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "10"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/spectralnorm/spectralnorm.cint/spectralnorm.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "150"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/spectralnorm/spectralnorm.gcc-2.gcc/spectralnorm.gcc-2.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "150"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/pidigits/pidigits.cint-4.cint/pidigits.cint-4.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "10000"));
        runs.put(new File(SHOOTOUTS_SUITE_DIR + "/pidigits/pidigits.gcc/pidigits.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, "10000"));

        return runs.keySet().stream().map(k -> new Object[]{k, runs.get(k), k.getFileName().toString()}).collect(Collectors.toList());

    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public RunConfiguration getConfiguration() {
        return configuration;
    }

}
