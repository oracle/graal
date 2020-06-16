/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.TruffleFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.options.TargetStream;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;

final class LLScanner {

    static final LLSourceMap NOT_FOUND = new LLSourceMap(null);

    private static TruffleFile findMapping(Path canonicalBCPath, String pathMappings, LLVMContext context) {
        if (pathMappings.isEmpty()) {
            return null;
        }

        final String[] mappings = pathMappings.split(":");
        for (String mapping : mappings) {
            final String[] splittedMapping = mapping.split("=");
            if (splittedMapping.length != 2) {
                throw new LLVMParserException("Malformed path mapping for *.ll files: " + pathMappings);
            }
            final Path mappedBCFile = Paths.get(splittedMapping[0]).normalize().toAbsolutePath();
            if (mappedBCFile.equals(canonicalBCPath)) {
                final Path mappedLLFile = Paths.get(splittedMapping[1]).normalize().toAbsolutePath();
                return context.getEnv().getInternalTruffleFile(mappedLLFile.toUri());
            }
        }

        return null;
    }

    private static TruffleFile findLLPathMapping(String bcPath, String pathMappings, LLVMContext context) {
        if (bcPath == null) {
            return null;
        }

        final Path canonicalBCPath = Paths.get(bcPath).normalize().toAbsolutePath();
        final TruffleFile mappedFile = findMapping(canonicalBCPath, pathMappings, context);
        if (mappedFile != null) {
            return mappedFile;
        }

        return context.getEnv().getInternalTruffleFile(getLLPath(canonicalBCPath.toString()));
    }

    private static String getLLPath(String canonicalBCPath) {
        if (canonicalBCPath.endsWith(".bc")) {
            return canonicalBCPath.substring(0, canonicalBCPath.length() - ".bc".length()) + ".ll";
        }
        return canonicalBCPath + ".ll";
    }

    static LLSourceMap findAndScanLLFile(String bcPath, String pathMappings, LLVMContext context) {
        if (bcPath == null) {
            return NOT_FOUND;
        }

        final TruffleFile llFile = findLLPathMapping(bcPath, pathMappings, context);
        if (llFile == null || !llFile.exists() || !llFile.isReadable()) {
            TargetStream stream = context.llDebugVerboseStream();
            if (stream != null) {
                stream.println("Cannot find .ll file for " + bcPath);
            }
            return NOT_FOUND;
        }

        try (BufferedReader llReader = llFile.newBufferedReader()) {
            final Source llSource = Source.newBuilder("llvm", llFile).mimeType("text/x-llvmir").build();
            final LLSourceMap sourceMap = new LLSourceMap(llSource);

            final LLScanner scanner = new LLScanner(sourceMap);
            for (String line = llReader.readLine(); line != null; line = llReader.readLine()) {
                if (!scanner.continueAfter(line)) {
                    break;
                }
            }
            return sourceMap;
        } catch (IOException e) {
            throw new LLVMParserException("Error while reading from file: " + llFile.getPath());
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

    // parse a line and indicate if the scanner should continue after it
    private boolean continueAfter(String line) {
        currentLine++;
        if (line.isEmpty() || line.charAt(0) == ';') {
            return true;
        }

        if (line.startsWith("define")) {
            // beginning of a new function definition
            beginFunction(line);

        } else if (line.startsWith("}")) {
            // end of a function definition
            endFunction();

        } else if (function != null) {
            // function body may contain only statements, comments and empty lines between blocks
            parseInstruction(line);

        } else if (line.startsWith("@")) {
            parseGlobal(line);

        } else if (line.startsWith("!0")) {
            // this is the first entry of the metadata list in the *.ll file
            // after it, no more functions will be defined, so we stop scanning here
            // to avoid parsing the metadata which often accounts for more than half
            // of the total file size
            return false;
        }

        return true;
    }

    private static final Pattern FUNCTION_NAME_REGEX = Pattern.compile("define .* @((?<functionNameUnquoted>[^\\s(\"]+)|\"(?<functionNameQuoted>[^\"]+)\")\\(.*");

    private void beginFunction(String line) {
        assert function == null;

        final Matcher matcher = FUNCTION_NAME_REGEX.matcher(line);
        if (matcher.matches()) {
            String functionName = matcher.group("functionNameUnquoted");
            if (functionName == null) {
                functionName = matcher.group("functionNameQuoted");
            }
            functionName = LLVMIdentifier.toGlobalIdentifier(functionName);
            function = new LLSourceMap.Function(functionName, currentLine);
            map.registerFunction(functionName, function);

        } else {
            throw new LLVMParserException(getErrorMessage("function", line));
        }
    }

    private static final Pattern VALUE_NAME_REGEX = Pattern.compile("\\s*\"?(?<instructionName>\\S+)\"? .*");
    private static final Pattern OP_NAME_REGEX = Pattern.compile("\\s*(?<instructionName>\\S+).*");

    private void parseInstruction(String line) {
        assert function != null;

        String id = null;
        Matcher matcher = VALUE_NAME_REGEX.matcher(line);
        if (matcher.matches()) {
            id = matcher.group("instructionName");
            id = LLVMIdentifier.toLocalIdentifier(id);
        }

        matcher = OP_NAME_REGEX.matcher(line);
        if (matcher.matches()) {
            id = matcher.group("instructionName");
        }

        if (id != null) {
            function.add(id, currentLine);
        } else {
            throw new LLVMParserException(getErrorMessage("instruction", line));
        }
    }

    private void endFunction() {
        assert function != null;
        function.setEndLine(currentLine);
        function = null;
    }

    private static final Pattern GLOBAL_NAME_REGEX = Pattern.compile("@\"?(?<globalName>\\S+)\"? =.*");

    private void parseGlobal(String line) {
        final Matcher matcher = GLOBAL_NAME_REGEX.matcher(line);
        if (matcher.matches()) {
            String globalName = matcher.group("globalName");
            globalName = LLVMIdentifier.toGlobalIdentifier(globalName);
            map.registerGlobal(globalName);
        } else {
            throw new LLVMParserException(getErrorMessage("global", line));
        }
    }

    private String getErrorMessage(String parsing, String line) {
        return String.format("Could not parse %s name in *.ll file: line %d: >>%s<<", parsing, currentLine, line);
    }
}
