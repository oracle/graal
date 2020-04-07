/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests;

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

import com.oracle.truffle.llvm.tests.options.TestOptions;

@RunWith(Parameterized.class)
public final class ShootoutsSuite extends BaseSulongOnlyHarness {

    private static final String SHOOTOUTS_SUITE_SUBDIR = "/benchmarksgame-2014-08-31/benchmarksgame/bench/";
    private static final String benchmarkSuffix = ".dir/O1_OUT.bc";

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public RunConfiguration configuration;
    @Parameter(value = 2) public String name;

    @Parameters(name = "{2}")
    public static Collection<Object[]> data() {

        final Map<Path, RunConfiguration> runs = new HashMap<>();
        String dir = TestOptions.EXTERNAL_TEST_SUITE_PATH + SHOOTOUTS_SUITE_SUBDIR;
        runs.put(new File(dir + "/binarytrees/binarytrees.gcc-2.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"12"}));
        runs.put(new File(dir + "/binarytrees/binarytrees.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"12"}));
        runs.put(new File(dir + "/fannkuchredux/fannkuchredux.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"9"}));
        runs.put(new File(dir + "/fannkuchredux/fannkuchredux.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"9"}));
        runs.put(new File(dir + "/fasta/fasta.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"100"}));
        runs.put(new File(dir + "/fasta/fasta.gcc-4.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"100"}));
        runs.put(new File(dir + "/fasta/fasta.gcc-5.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"100"}));
        // fastaredux.gcc-2.gcc causes a segfault due to a buffer overflow, so we do not include it
        runs.put(new File(dir + "/fastaredux/fastaredux.gcc-3.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null));
        runs.put(new File(dir + "/mandelbrot/mandelbrot.cint-2.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"500"}));
        runs.put(new File(dir + "/mandelbrot/mandelbrot.gcc-2.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"500"}));
        runs.put(new File(dir + "/mandelbrot/mandelbrot.gcc-8.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"500"}));
        runs.put(new File(dir + "/mandelbrot/mandelbrot.gcc-9.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"500"}));
        runs.put(new File(dir + "/nbody/nbody.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"10"}));
        runs.put(new File(dir + "/spectralnorm/spectralnorm.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"150"}));
        runs.put(new File(dir + "/spectralnorm/spectralnorm.gcc-2.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"150"}));
        runs.put(new File(dir + "/pidigits/pidigits.cint-4.cint" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"10000"}));
        runs.put(new File(dir + "/pidigits/pidigits.gcc" + benchmarkSuffix).toPath(), new RunConfiguration(0, null, new String[]{"10000"}));

        return runs.keySet().stream().map(k -> new Object[]{k, runs.get(k), k.toString().substring(dir.length())}).collect(Collectors.toList());

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
