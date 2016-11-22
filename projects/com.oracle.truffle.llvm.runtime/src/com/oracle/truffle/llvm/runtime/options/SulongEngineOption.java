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
package com.oracle.truffle.llvm.runtime.options;

import com.oracle.truffle.llvm.option.Constants;
import com.oracle.truffle.llvm.option.Option;
import com.oracle.truffle.llvm.option.OptionCategory;

@OptionCategory(name = "Base Options")
abstract class SulongEngineOption {

    @Option(commandLineName = "LLVM", help = "Version of the used LLVM File Format 3.2/3.8.", name = "llvmVersion") //
    protected static final String LLVM_VERSION = "3.2";

    @Option(commandLineName = "NodeConfiguration", help = "The node configuration (node factory) to be used in Sulong.", name = "nodeConfiguration") //
    protected static final String NODE_CONFIGURATION = "default";

    @Option(commandLineName = "StackSizeKB", help = "The stack size in KB.", name = "stackSize") //
    protected static final Integer STACK_SIZE_KB = 81920;

    @Option(commandLineName = "DynamicNativeLibraryPath", help = "The native library search paths delimited by " +
                    Constants.OPTION_ARRAY_SEPARATOR + " .", name = "dynamicNativeLibraryPath") //
    protected static final String[] DYN_LIBRARY_PATHS = new String[0];

    @Option(commandLineName = "DynamicBitcodeLibraries", help = "The paths to shared bitcode libraries delimited by " +
                    Constants.OPTION_ARRAY_SEPARATOR + " .", name = "dynamicBitcodeLibraries") //
    protected static final String[] DYN_BITCODE_LIBRARIES = new String[0];

    @Option(commandLineName = "ProjectRoot", help = "Overrides the root of the project. This option exists to set the project root from mx.", name = "projectRoot") //
    protected static final String PROJECT_ROOT = "./projects";

    @Option(commandLineName = "ExecutionCount", help = "Execute each program for as many times as specified by this option.", name = "executionCount") //
    protected static final Integer EXECUTION_COUNT = 1;

}
