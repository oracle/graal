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
package com.oracle.truffle.llvm.test.options;

import com.oracle.truffle.llvm.option.Option;
import com.oracle.truffle.llvm.option.OptionCategory;

@OptionCategory(name = "Test Options")
abstract class TestOptions {
    @Option(commandLineName = "IgnoreFortran", help = "Ignores all Fortran tests.", name = "ignoreFortran") //
    protected static final Boolean IGNORE_FORTRAN = false;

    @Option(commandLineName = "TestDiscoveryPath", help = "Looks for newly supported test cases in the specified path. E.g., when executing the GCC test cases you can use /gcc.c-torture/execute to discover newly working torture test cases.", //
                    name = "testDiscoveryPath") //
    protected static final String TEST_DISCOVERY_PATH = null;

    @Option(commandLineName = "TestAOTImage", help = "Test an AOT compiled Sulong image. The value of this option should point to the compiled Sulong binary.", name = "testAOTImage") //
    protected static final String TEST_AOT_IMAGE = null;
}
