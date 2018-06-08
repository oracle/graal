/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;

final class LLScanner implements Consumer<String> {

    private static final class ScannerDoneException extends RuntimeException {
        public static final long serialVersionUID = 42L;
    }

    static void scan(Source llSource, LLSourceMap sourceMap) throws IOException {
        final LLScanner scanner = new LLScanner(sourceMap);
        try (Stream<String> lines = Files.lines(Paths.get(llSource.getPath()))) {
            lines.forEachOrdered(scanner);
        } catch (LLScanner.ScannerDoneException ignored) {
        }
    }

    private final LLSourceMap map;

    private int currentLine;
    private LLSourceMap.Function function;

    private LLScanner(LLSourceMap map) {
        this.map = map;
        this.currentLine = 0;
        this.function = null;
    }

    @Override
    public void accept(String line) {
        currentLine++;
        if (line.isEmpty() || line.charAt(0) == ';') {
            return;
        }

        if (line.startsWith("define")) {
            // beginning of a new function definition
            beginFunction(line);

        } else if (line.startsWith("}")) {
            // end of a function definition
            endFunction();

        } else if (line.startsWith("!0")) {
            // this is the first entry of the metadata list in the *.ll file
            // after it, no more functions will be defined, so we stop scanning here
            // to avoid parsing the metadata which often accounts for more than half
            // of the total file size
            throw new ScannerDoneException();

        } else if (function != null) {
            // function body may contain only statements, comments and empty lines between blocks
            parseInstruction(line);
        }
    }

    private static final Pattern FUNCTION_NAME_REGEX = Pattern.compile("define .* @\"?(?<functionName>\\S+)\"?\\(.*");

    private void beginFunction(String line) {
        assert function == null;

        final Matcher matcher = FUNCTION_NAME_REGEX.matcher(line);
        if (matcher.matches()) {
            String functionName = matcher.group("functionName");
            functionName = LLVMIdentifier.toGlobalIdentifier(functionName);
            function = new LLSourceMap.Function(functionName, currentLine);
            map.register(functionName, function);

        } else {
            throw new AssertionError(getErrorMessage("function", line));
        }
    }

    // TODO (jkreindl) this doesn't work for 'unreachable'
    private static final Pattern INSTRUCTION_NAME_REGEX = Pattern.compile("\\s* %?\"?(?<instructionName>\\S+)\"? .*");

    private void parseInstruction(String line) {
        assert function != null;

        final Matcher matcher = INSTRUCTION_NAME_REGEX.matcher(line);
        if (matcher.matches()) {
            String instructionName = matcher.group("instructionName");
            instructionName = LLVMIdentifier.toLocalIdentifier(instructionName);
            function.add(instructionName, currentLine);

        } else {
            throw new AssertionError(getErrorMessage("instruction", line));
        }
    }

    private void endFunction() {
        assert function != null;
        function.setEndLine(currentLine);
        function = null;
    }

    private String getErrorMessage(String parsing, String line) {
        return String.format("Could not parse %s name in *.ll file: line %d: >>%s<<", parsing, currentLine, line);
    }
}
