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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.tests.options.TestOptions;

@RunWith(Parameterized.class)
public class SulongSuite extends BaseSuiteHarness {

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public String testName;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        Path suitesPath = new File(TestOptions.TEST_SUITE_PATH).toPath();
        Set<String> blacklist = getBlacklist();
        return getData(suitesPath, blacklist);
    }

    protected static Set<String> getBlacklist() {
        Set<String> filenameBlacklist = new HashSet<>();

        if (Platform.isAArch64()) {
            // Tests that cause the JVM to crash.
            filenameBlacklist.addAll(Arrays.asList(
                            "c/builtin_gcc/__builtin_va_list.c",
                            "c/truffle-c/structTest/passPerValue9.c", "c/truffle-c/structTest/structTest23.c", "c/truffle-c/structTest/structTest24.c",
                            "c/truffle-c/structTest/structTest25.c", "c/truffle-c/structTest/structTest26.c", "c/truffle-c/structTest/structTest27.c",
                            "c/varargs/var80bit.c", "c/varargs/varFloatVec.c", "c/varargs/varFunctionPointer.c", "c/varargs/varSmallStruct.c", "c/varargs/varStructBeforePrimitive.c",
                            "c/varargs/varStructBeforePrimitiveAMD64Explicite.c", "c/varargs/varStructDouble.c", "c/varargs/varStructDoubleAMD64Explicite.c",
                            "c/varargs/varStructLong.c", "c/varargs/varStructLongAMD64Explicite.c", "c/varargs/varStructModify.c", "c/varargs/varStructModifyPtr.c",
                            "c/varargs/varStructPtr.c", "c/varargs/varStructStackOnly.c", "c/varargs/varStructStackOnlyAMD64Explicite.c",
                            "cpp/test005.cpp", "cpp/test015.cpp", "cpp/test017.cpp", "cpp/test018.cpp", "cpp/test019.cpp", "cpp/test020.cpp", "cpp/test022.cpp", "cpp/test023.cpp", "cpp/test024.cpp",
                            "cpp/test028.cpp",
                            "cpp/test031.cpp", "cpp/test033.cpp", "cpp/test034.cpp", "cpp/test036.cpp", "cpp/test039.cpp",
                            "cpp/test041.cpp", "cpp/test042.cpp", "cpp/test043.cpp", "cpp/test044.cpp", "cpp/test045.cpp", "cpp/test046.cpp", "cpp/test047.cpp", "cpp/test049.cpp", "cpp/test050.cpp",
                            "cpp/test051.cpp", "cpp/test052.cpp", "cpp/test053.cpp", "cpp/testRuntimeError.cpp",
                            "libc/memcpy/memcpy-struct-mixed.c", "libc/vfprintf/vfprintf.c", "libc/vprintf/vprintf.c"));
            // Tests that fail.
            filenameBlacklist.addAll(Arrays.asList(
                            "c/arrays/intArray.c",
                            "c/builtin_gcc/__builtin_copysign.c", "c/builtin_gcc/__builtin_fabsl.c", "c/builtin_gcc/__builtin_fpclassify.c", "c/builtin_gcc/__builtin_isfinite.c",
                            "c/builtin_gcc/__builtin_isinf.c", "c/builtin_gcc/__builtin_isnan.c", "c/builtin_gcc/__builtin_signbit.c", "c/builtin_gcc/__builtin_signbitl.c",
                            "c/lfplayout.c",
                            "c/longdouble/add.c", "c/longdouble/longdouble-add.c", "c/longdouble/longdouble-div.c", "c/longdouble/longdouble-mul.c", "c/longdouble/longdouble-sub.c",
                            "c/max-unsigned-int-to-double-cast.c",
                            "c/stdlib/math/fmodl.c", "c/stdlib/math/sqrt.c", "c/stdlib/signal_errno.c", "c/stdlib/stat.c",
                            "c/truffle-c/arrayTest/arrayTest18.c", "c/truffle-c/arrayTest/arrayTest22.c", "c/truffle-c/arrayTest/arrayTest5.c", "c/truffle-c/charTest/charArray.c",
                            "c/truffle-c/programTest/programTest0.c", "c/truffle-c/structTest/structTest22.c", "c/truffle-c/unionTest/memberInitialization2.c", "c/truffle-c/unionTest/unionTest12.c",
                            "cpp/test004.cpp", "cpp/test011.cpp", "cpp/test013.cpp", "cpp/test014.cpp", "cpp/test016.cpp",
                            "cpp/test021.cpp", "cpp/test025.cpp", "cpp/test026.cpp", "cpp/test027.cpp", "cpp/test029.cpp",
                            "cpp/test030.cpp", "cpp/test032.cpp", "cpp/test035.cpp", "cpp/test037.cpp", "cpp/test038.cpp", "cpp/test040.cpp", "cpp/test048.cpp",
                            "libc/errno/errno.c"));
            // Bitcode libc++ causes a segfault during a destructor (atexit) call.
            // See https://github.com/oracle/graal/issues/2276
            // Blacklist all c++ tests for now
            filenameBlacklist.addAll(Arrays.asList(
                            "cpp/test003.cpp",
                            "cpp/test004.cpp",
                            "cpp/test005.cpp",
                            "cpp/test006.cpp",
                            "cpp/test007.cpp",
                            "cpp/test008.cpp",
                            "cpp/test009.cpp",
                            "cpp/test010.cpp",
                            "cpp/test011.cpp",
                            "cpp/test013.cpp",
                            "cpp/test014.cpp",
                            "cpp/test015.cpp",
                            "cpp/test016.cpp",
                            "cpp/test017.cpp",
                            "cpp/test018.cpp",
                            "cpp/test019.cpp",
                            "cpp/test020.cpp",
                            "cpp/test021.cpp",
                            "cpp/test022.cpp",
                            "cpp/test023.cpp",
                            "cpp/test024.cpp",
                            "cpp/test025.cpp",
                            "cpp/test026.cpp",
                            "cpp/test027.cpp",
                            "cpp/test028.cpp",
                            "cpp/test029.cpp",
                            "cpp/test030.cpp",
                            "cpp/test031.cpp",
                            "cpp/test032.cpp",
                            "cpp/test033.cpp",
                            "cpp/test034.cpp",
                            "cpp/test035.cpp",
                            "cpp/test036.cpp",
                            "cpp/test037.cpp",
                            "cpp/test038.cpp",
                            "cpp/test039.cpp",
                            "cpp/test040.cpp",
                            "cpp/test041.cpp",
                            "cpp/test042.cpp",
                            "cpp/test043.cpp",
                            "cpp/test044.cpp",
                            "cpp/test045.cpp",
                            "cpp/test046.cpp",
                            "cpp/test047.cpp",
                            "cpp/test048.cpp",
                            "cpp/test049.cpp",
                            "cpp/test050.cpp",
                            "cpp/test051.cpp",
                            "cpp/test052.cpp",
                            "cpp/test053.cpp",
                            "cpp/testRuntimeError.cpp",
                            "cpp/testStaticReferenceInitFunction.cpp",
                            "cpp/testStaticReferenceInitGlobal.cpp",
                            "cpp/builtin/clz.c",
                            "cpp/builtin/arithmetic/arithmetic_sadd_i16.c",
                            "cpp/builtin/arithmetic/arithmetic_sadd_i32.c",
                            "cpp/builtin/arithmetic/arithmetic_sadd_i64.c",
                            "cpp/builtin/arithmetic/arithmetic_sadd_i8.c",
                            "cpp/builtin/arithmetic/arithmetic_smul_i16.c",
                            "cpp/builtin/arithmetic/arithmetic_smul_i32.c",
                            "cpp/builtin/arithmetic/arithmetic_smul_i64.c",
                            "cpp/builtin/arithmetic/arithmetic_smul_i8.c",
                            "cpp/builtin/arithmetic/arithmetic_ssub_i16.c",
                            "cpp/builtin/arithmetic/arithmetic_ssub_i32.c",
                            "cpp/builtin/arithmetic/arithmetic_ssub_i64.c",
                            "cpp/builtin/arithmetic/arithmetic_ssub_i8.c",
                            "cpp/builtin/arithmetic/arithmetic_uadd_i16.c",
                            "cpp/builtin/arithmetic/arithmetic_uadd_i32.c",
                            "cpp/builtin/arithmetic/arithmetic_uadd_i64.c",
                            "cpp/builtin/arithmetic/arithmetic_uadd_i8.c",
                            "cpp/builtin/arithmetic/arithmetic_umul_i16.c",
                            "cpp/builtin/arithmetic/arithmetic_umul_i32.c",
                            "cpp/builtin/arithmetic/arithmetic_umul_i64.c",
                            "cpp/builtin/arithmetic/arithmetic_umul_i8.c",
                            "cpp/builtin/arithmetic/arithmetic_usub_i16.c",
                            "cpp/builtin/arithmetic/arithmetic_usub_i32.c",
                            "cpp/builtin/arithmetic/arithmetic_usub_i64.c",
                            "cpp/builtin/arithmetic/arithmetic_usub_i8.c"));

        }

        return filenameBlacklist.stream().map((s) -> s.concat(".dir")).collect(Collectors.toSet());
    }

    protected static Collection<Object[]> getData(Path suitesPath, Set<String> blacklist) {
        try (Stream<Path> files = Files.walk(suitesPath)) {
            Stream<Path> destDirs = files.filter(SulongSuite::isReference).map(Path::getParent);
            Collection<Object[]> collection = destDirs.map(testPath -> new Object[]{testPath, suitesPath.relativize(testPath).toString()}).collect(Collectors.toList());
            collection.removeIf(d -> blacklist.contains(d[1]));
            return collection;
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }

    private static boolean isReference(Path path) {
        return path.endsWith("ref.out") && (!Platform.isDarwin() || pathStream(path).noneMatch(p -> p.endsWith("ref.out.dSYM")));
    }

    private static Stream<Path> pathStream(Path path) {
        return StreamSupport.stream(path.spliterator(), false);
    }

    @Override
    protected Predicate<? super Path> getIsSulongFilter() {
        return f -> {
            boolean isBC = f.getFileName().toString().endsWith(".bc");
            boolean isOut = f.getFileName().toString().endsWith(".out");
            return isBC || (isOut && !Platform.isDarwin());
        };
    }

    @Override
    protected Path getTestDirectory() {
        return path;
    }

    @Override
    protected String getTestName() {
        return testName;
    }
}
