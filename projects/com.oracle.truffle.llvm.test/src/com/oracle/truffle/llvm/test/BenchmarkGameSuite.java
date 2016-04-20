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
package com.oracle.truffle.llvm.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.llvm.LLVM;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions.OptimizationLevel;

@RunWith(Parameterized.class)
public class BenchmarkGameSuite extends TestSuiteBase {

    private Benchmark bench;

    public BenchmarkGameSuite(Benchmark bench) {
        this.bench = bench;
    }

    /**
     * TODOS. knucletoide folder: include folder <br />
     * some benchmarks only compile with higher optimization levels <br />
     * cint meteor: C sort <br />
     * nbody: also escaping function pointers <br />
     * pidigits: gmp <br />
     * regexdna: does not terminate <br />
     * revcomp: does not terminate
     */
    enum Benchmark {

        C_BINARY_TREES_1("binarytrees/binarytrees.gcc-2.gcc.c", 12),
        C_BINARY_TREES_2("binarytrees/binarytrees.gcc.c", 12),
        C_FANNKUCHREDUX_1("fannkuchredux/fannkuchredux.cint.c", 9),
        C_FANNKUCHREDUX_2("fannkuchredux/fannkuchredux.gcc.c", 9),
        C_FASTA1("fasta/fasta.cint.c", 100),
        C_FASTA2("fasta/fasta.gcc-4.gcc.c", 100),
        C_FASTA3("fasta/fasta.gcc-5.gcc.c", 100),
        C_FASTA_REDUX1("fastaredux/fastaredux.gcc-2.gcc.c", null),
        C_FASTA_REDUX2("fastaredux/fastaredux.gcc-3.gcc.c", null),
        C_MANDELBROT1("mandelbrot/mandelbrot.cint-2.cint.c", 500),
        C_MANDELBROT2("mandelbrot/mandelbrot.gcc-2.gcc.c", 500),
        C_MANDELBROT3("mandelbrot/mandelbrot.gcc-8.gcc.c", 500),
        C_MANDELBROT4("mandelbrot/mandelbrot.gcc-9.gcc.c", 500),
        C_NBODY1("nbody/nbody.cint.c", 10),
        C_SPECTRALNORM1("spectralnorm/spectralnorm.cint.c", 150),
        C_SPECTRALNORM2("spectralnorm/spectralnorm.gcc-2.gcc.c", 150),
        C_PIDIGTS("pidigits/pidigits.cint-4.cint.c", 10000),
        C_PIDIGITS2("pidigits/pidigits.gcc.c", 10000);

        private File file;
        private String arg;
        private Set<TestCaseFlag> flags;

        Benchmark(String path, Object arg) {
            this(path, arg, Collections.emptySet());
        }

        Benchmark(String path, Object arg, Set<TestCaseFlag> flags) {
            this.file = new File(LLVMPaths.BENCHMARK_GAME_SUITE, path);
            this.arg = arg == null ? null : arg.toString();
            this.flags = flags;
        }
    }

    @Parameterized.Parameters
    public static List<Benchmark[]> getTestFiles() {
        List<Benchmark[]> files = new ArrayList<>();
        for (Benchmark bench : Benchmark.values()) {
            files.add(new Benchmark[]{bench});
        }
        return files;
    }

    @Test
    public void test() throws Throwable {
        File f = bench.file;
        TestCaseFiles compileResult = TestHelper.compileToLLVMIRWithClang(f, TestHelper.getTempLLFile(f, "_main"), bench.flags, ClangOptions.builder().optimizationLevel(OptimizationLevel.O1));
        int truffleResult;
        if (bench.arg != null) {
            truffleResult = LLVM.executeMain(compileResult.getBitCodeFile(), bench.arg);
        } else {
            truffleResult = LLVM.executeMain(compileResult.getBitCodeFile());
        }
        if (!compileResult.hasFlag(TestCaseFlag.UNDEFINED_RETURN_CODE)) {
            assertEquals(0, truffleResult);
        }
    }

}
