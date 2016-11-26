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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

@RunWith(Parameterized.class)
public final class LifetimeAnalysisSuite {

    private static final Path GCC_SUITE_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../tests/cache/tests/gcc").toPath();
    private static final Path GCC_CONFIG_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../tests/gcc/configs").toPath();
    private static final Path GCC_LTA_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../tests/gcc/lta").toPath();
    private static final Path GCC_LTA_GEN_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../tests/gcc/lta_gen").toPath();

    @Parameter(value = 0) public Path bcFile;
    @Parameter(value = 1) public Path ltaFile;
    @Parameter(value = 2) public Path ltaGenFile;
    @Parameter(value = 3) public String testName;

    @Parameters(name = "{3}")
    public static Collection<Object[]> data() throws AssertionError, IOException {
        return collectTestCases(GCC_CONFIG_DIR, GCC_SUITE_DIR, GCC_LTA_DIR, GCC_LTA_GEN_DIR);
    }

    // TODO: after migration of LTA tests to new infrastructure, these 44 tests fail.
    // See issue #598
    // Fix issue and remove this this hard-coded ignore list.
    private static final String[] ignore = new String[]{"gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr47541.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr49644.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/pr15054-2.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/nrv14.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/pr17697-1.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/pr43655.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/template/sfinae17.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr49039.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/template/condition1.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr37922.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/pr6713.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr42462.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/pr36187.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr40924.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/nrv6.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/pr16372-1.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/expect1.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/template/array25.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr40335.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr44069.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/range-test-2.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/template/partial10.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/pr7503-1.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/cond1.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr56837.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr30567.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr50189.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/strength-reduce.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/range-test-1.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr35634.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr59163.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/20100825.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/fold2.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/pr14029.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/dtor4.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/dtor1.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/template/explicit-args4.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/template/repo9.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/nrv13.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/bool1.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/opt/pr30590.C",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/torture/pr37146-2.C",
                    "gcc-5.2.0/gcc/testsuite/gfortran.fortran-torture/execute/integer_select.f90",
                    "gcc-5.2.0/gcc/testsuite/g++.dg/template/pretty1.C",
                    "gcc-5.2.0/gcc/testsuite/gfortran.fortran-torture/execute/transfer2.f90"};
    private final List<String> ignoreList = Arrays.asList(ignore);

    @Test
    public void test() throws Exception {
        try {
            if (ignoreList.contains(testName)) {
                return;
            }
            LifetimeAnalysisTest.test(bcFile, ltaFile, ltaGenFile);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    protected static final String lifeTimeFileEnding = ".lifetime";
    protected static final Predicate<? super Path> isExecutable = f -> f.getFileName().toString().endsWith(".out");
    protected static final Predicate<? super Path> isIncludeFile = f -> f.getFileName().toString().endsWith(".include");
    protected static final Predicate<? super Path> isLifetimeFile = f -> f.getFileName().toString().endsWith(lifeTimeFileEnding);
    protected static final Predicate<? super Path> isSulong = f -> f.getFileName().toString().endsWith(".bc");
    protected static final Predicate<? super Path> isFile = f -> f.toFile().isFile();

    protected static Collection<Object[]> collectTestCases(Path configPath, Path suiteDir, Path ltaDir, Path ltaGenDir) throws AssertionError, IOException {
        Collection<Object[]> collectTestCases = collectTestCases(suiteDir, ltaDir, ltaGenDir, getWhiteListEntries(configPath));
        for (Object[] o : collectTestCases) {
            Path bcFile = getTestFile((Path) o[0]);
            Path ltFile = (Path) o[1];
            Path ltGenFile = (Path) o[2];

            if (!bcFile.toFile().exists()) {
                throw new AssertionError(bcFile + " does not exist.");
            }
            if (!ltFile.toFile().exists()) {
                throw new AssertionError(ltFile + " does not exist.");
            }
            if (ltFile.toFile().exists()) {
                // remove generated lta file and generate new one
                ltGenFile.toFile().delete();
            }
            o[0] = bcFile;
            o[1] = ltFile;
            o[2] = ltGenFile;
        }
        return collectTestCases;
    }

    private static Path getTestFile(Path folder) throws IOException {
        List<Path> collect = Files.walk(folder).filter(p -> p.toString().endsWith("_O0.bc")).collect(Collectors.toList());
        if (collect.size() != 1) {
            throw new AssertionError("Found " + collect.size() + " matching files in " + folder);
        }
        return collect.get(0);
    }

    private static Collection<Object[]> collectTestCases(Path suiteDir, Path ltaDir, Path ltaGenDir, Set<Path> whiteListEntries) throws AssertionError {
        return whiteListEntries.stream().map(
                        e -> new Object[]{new File(suiteDir.toString(), removeFileEnding(e.toString())).toPath(), new File(ltaDir.toString(), e.toString() + lifeTimeFileEnding).toPath(),
                                        new File(ltaGenDir.toString(), e.toString() + lifeTimeFileEnding).toPath(), e.toString()}).collect(
                                                        Collectors.toList());
    }

    protected static Set<Path> getWhiteListEntries(Path configDir) {
        try {
            return Files.walk(configDir).filter(isIncludeFile).flatMap(f -> {
                try {
                    return Files.lines(f);
                } catch (IOException e) {
                    throw new AssertionError("Error creating whitelist.", e);
                }
            }).map(s -> new File(s).toPath()).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Error creating whitelist.", e);
        }
    }

    private static String removeFileEnding(String s) {
        return s.substring(0, s.lastIndexOf('.'));
    }
}
