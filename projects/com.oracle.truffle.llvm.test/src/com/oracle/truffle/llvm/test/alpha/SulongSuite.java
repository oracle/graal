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
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.LLVM;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.tools.util.ProcessUtil;

@RunWith(Parameterized.class)
public class SulongSuite {

    private static final Path SULONG_SUITE_DIR = new File(LLVMBaseOptionFacade.getProjectRoot() + "/tests/cache/tests/sulong").toPath();
    private static final Predicate<? super Path> isExecutable = f -> f.getFileName().toString().endsWith(".out");
    private static final Predicate<? super Path> notExecutable = f -> !isExecutable.test(f);
    private static final Predicate<? super Path> isFile = f -> f.toFile().isFile();

    private Path path;

    public SulongSuite(Path path) {
        this.path = path;
    }

    @Parameters
    public static Collection<Object[]> data() {
        try {
            return Files.walk(SULONG_SUITE_DIR).filter(isExecutable).map(f -> f.getParent()).map(f -> new Object[]{f}).collect(Collectors.toList());
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }

    @Test
    public void test() throws Exception {
        assert Files.walk(path).filter(isExecutable).count() == 1;
        Path referenceFile = Files.walk(path).filter(isExecutable).findFirst().get();
        List<Path> testCandidates = Files.walk(path).filter(isFile).filter(notExecutable).collect(Collectors.toList());

        final int referenceResult = ProcessUtil.executeNativeCommand(referenceFile.toAbsolutePath().toString()).getReturnValue();

        for (Path candidate : testCandidates) {
            int sulongResult = LLVM.executeMain(candidate.toAbsolutePath().toFile());
            Assert.assertEquals(path.toAbsolutePath().toString(), referenceResult, sulongResult);
        }
    }
}
