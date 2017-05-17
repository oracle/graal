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

import com.oracle.truffle.llvm.option.Option;
import com.oracle.truffle.llvm.option.OptionCategory;

@OptionCategory(name = "Debug Options")
abstract class SulongDebugOption {

    @Option(commandLineName = "Debug", help = "Turns debugging on/off. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.", name = "debug") //
    protected static final String DEBUG = String.valueOf(false);

    @Option(commandLineName = "PrintMetadata", help = "Prints the parsed metadata. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.", name = "printMetadata") //
    protected static final String PRINT_METADATA = String.valueOf(false);

    @Option(commandLineName = "PerformanceWarningsAreFatal", help = "Terminates the program after a performance issue is encountered.", name = "performanceWarningsAreFatal") //
    protected static final Boolean PERFORMANCE_WARNING_ARE_FATAL = false;

    @Option(commandLineName = "TracePerformanceWarnings", help = "Reports all LLVMPerformance.warn() invokations in compiled code.", name = "tracePerformanceWarnings") //
    protected static final Boolean PERFORMANCE_WARNINGS = false;

    @Option(commandLineName = "PrintASTs", help = "Prints the Truffle ASTs for the parsed functions. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.", name = "printFunctionASTs") //
    protected static final String PRINT_FUNCTION_ASTS = String.valueOf(false);

    @Option(commandLineName = "PrintNativeCallStats", help = "Outputs stats about native call site frequencies. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.", name = "printNativeCallStatistics") //
    protected static final String NATIVE_CALL_STATS = String.valueOf(false);

    @Option(commandLineName = "PrintLifetimeAnalysisStats", help = "Prints the results of the lifetime analysis. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.", name = "printLifetimeAnalysisStatistics") //
    protected static final String PRINT_LIFE_TIME_ANALYSIS_STATS = String.valueOf(false);

}
