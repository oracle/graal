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

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.llvm.test.AbstractMainArgsTestBase.ProgramWithMainArgs;
import com.oracle.truffle.llvm.test.ShootoutsTestSuite.Benchmark;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions.OptimizationLevel;

@RunWith(Parameterized.class)
public class ShootoutsTestSuite extends AbstractMainArgsTestBase<Benchmark> {

    /**
     * TODOS. knucletoide folder: include folder <br />
     * some benchmarks only compile with higher optimization levels <br />
     * cint meteor: C sort <br />
     * nbody: also escaping function pointers <br />
     * pidigits: gmp <br />
     * regexdna: does not terminate <br />
     * revcomp: does not terminate
     */
    enum Benchmark implements ProgramWithMainArgs {

        C_BINARY_TREES_1("binarytrees/binarytrees.gcc-2.gcc.c", 12),
        C_BINARY_TREES_2("binarytrees/binarytrees.gcc.c", 12),
        C_FANNKUCHREDUX_1("fannkuchredux/fannkuchredux.cint.c", 9),
        C_FANNKUCHREDUX_2("fannkuchredux/fannkuchredux.gcc.c", 9),
        C_FASTA1("fasta/fasta.cint.c", 100),
        C_FASTA2("fasta/fasta.gcc-4.gcc.c", 100),
        C_FASTA3("fasta/fasta.gcc-5.gcc.c", 100),
        C_FASTA_REDUX1("fastaredux/fastaredux.gcc-2.gcc.c"),
        C_FASTA_REDUX2("fastaredux/fastaredux.gcc-3.gcc.c"),
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
        private String[] args;
        private Set<TestCaseFlag> flags = new HashSet<>();

        Benchmark(String path, Object... arg) {
            this.file = new File(LLVMPaths.BENCHMARK_GAME_SUITE, path);
            String[] convertedArgs = new String[arg.length];
            for (int i = 0; i < arg.length; i++) {
                convertedArgs[i] = arg[i].toString();
            }
            this.args = convertedArgs;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public List<String> getMainArgs() {
            return Arrays.asList(args);
        }

        @Override
        public Set<TestCaseFlag> getFlags() {
            return flags;
        }
    }

    public ShootoutsTestSuite(Benchmark program) {
        super(program);
    }

    @Parameterized.Parameters
    public static List<Benchmark[]> getTestFiles() {
        return getTestFiles(Arrays.asList(Benchmark.values()));
    }

    @Override
    protected TestCaseFiles getTestCaseFiles(Benchmark prog) {
        return TestHelper.compileToLLVMIRWithClang(prog.getFile(),
                        TestHelper.getTempLLFile(prog.getFile(), "_main"), prog.getFlags(),
                        ClangOptions.builder().optimizationLevel(OptimizationLevel.O1));
    }

    @Override
    @Test
    public void test() throws Throwable {
        super.test();
    }

}
