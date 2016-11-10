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

    @Option(commandLineName = "Debug", help = "Turns debugging on/off.", name = "debug") //
    protected static final Boolean DEBUG = false;

    @Option(commandLineName = "Verbose", help = "Enables verbose printing of debugging information.", name = "verbose") //
    protected static final Boolean VERBOSE = false;

    @Option(commandLineName = "PrintPerformanceWarnings", help = "Prints performance warnings.", name = "printPerformanceWarnings") //
    protected static final Boolean PRINT_PERFORMANCE_WARNINGS = false;

    @Option(commandLineName = "PerformanceWarningsAreFatal", help = "Terminates the program after a performance issue is encountered.", name = "performanceWarningsAreFatal") //
    protected static final Boolean PERFORMANCE_WARNING_ARE_FATAL = false;

    @Option(commandLineName = "PrintASTs", help = "Prints the Truffle ASTs for the parsed functions.", name = "printFunctionASTs") //
    protected static final Boolean PRINT_FUNCTION_ASTS = false;

    @Option(commandLineName = "PrintExecutionTime", help = "Prints the execution time for the main function of the program.", name = "printExecutionTime") //
    protected static final Boolean PRINT_EXECUTION_TIME = false;

    @Option(commandLineName = "PrintNativeCallStats", help = "Outputs stats about native call site frequencies.", name = "printNativeCallStatistics") //
    protected static final Boolean NATIVE_CALL_STATS = false;

    @Option(commandLineName = "PrintLifetimeAnalysisStats", help = "Outputs the results of the lifetime analysis (if enabled).", name = "printLifetimeAnalysisStatistics") //
    protected static final Boolean PRINT_LIFE_TIME_ANALYSIS_STATS = false;

    @Option(commandLineName = "TraceExecution", help = "Trace execution, printing each SSA assignment.", name = "traceExecution") //
    protected static final Boolean TRACE_EXECUTION = false;

}
