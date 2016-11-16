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
package com.oracle.truffle.llvm.test.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

public class LifetimeFileFormat {

    private static final String FUNCTION_INDENT = ""; // ???
    private static final String BEGIN_DEAD_END_INDENT = "\t";
    private static final String BASIC_BLOCK_INDENT = "\t\t";
    private static final String VARIABLE_INDENT = "\t\t\t";

    private static final String BEGIN_DEAD = BEGIN_DEAD_END_INDENT + "begin dead:";
    private static final String END_DEAD = BEGIN_DEAD_END_INDENT + "end dead:";

    private enum ParseState {
        LOOKING_FOR_FUNCTION,
        LOOKING_FOR_BEGIN_DEAD,
        LOOKING_FOR_BLOCK_OR_END_DEAD,
        LOOKING_FOR_BLOCK_OR_END,
        LOOKING_FOR_VARIABLE_BEGIN,
        LOOKING_FOR_VARIABLE_END;
    }

    public static void parse(BufferedReader reader, LifetimeFileParserEventListener eventListener) throws IOException {
        ParseState state = ParseState.LOOKING_FOR_FUNCTION;
        String block = null;
        String functionName = null;

        eventListener.startFile();

        while (reader.ready()) {
            String line = reader.readLine();
            switch (state) {
                case LOOKING_FOR_FUNCTION:
                    if (line.startsWith(FUNCTION_INDENT)) {
                        state = ParseState.LOOKING_FOR_BEGIN_DEAD;
                        functionName = line;
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_BEGIN_DEAD:
                    if (line.equals(BEGIN_DEAD)) {
                        state = ParseState.LOOKING_FOR_BLOCK_OR_END_DEAD;
                        eventListener.beginDead();
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_BLOCK_OR_END_DEAD:
                    if (line.equals(END_DEAD)) {
                        state = ParseState.LOOKING_FOR_BLOCK_OR_END;
                        eventListener.endDead();
                    } else if (line.startsWith(BASIC_BLOCK_INDENT)) {
                        block = line.substring(BASIC_BLOCK_INDENT.length());
                        state = ParseState.LOOKING_FOR_VARIABLE_BEGIN;
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_VARIABLE_BEGIN:
                    if (line.startsWith(VARIABLE_INDENT)) {
                        String variableName = line.substring(VARIABLE_INDENT.length());
                        eventListener.variableIndent(variableName);
                    } else if (line.startsWith(BASIC_BLOCK_INDENT)) {
                        eventListener.finishEntry(block);
                        block = line.substring(BASIC_BLOCK_INDENT.length());
                        state = ParseState.LOOKING_FOR_VARIABLE_BEGIN;
                    } else if (line.startsWith(END_DEAD)) {
                        eventListener.finishEntry(block);
                        eventListener.endDead();
                        state = ParseState.LOOKING_FOR_BLOCK_OR_END;
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_BLOCK_OR_END:
                    if (line.startsWith(BASIC_BLOCK_INDENT)) {
                        block = line.substring(BASIC_BLOCK_INDENT.length());
                        state = ParseState.LOOKING_FOR_VARIABLE_END;
                    } else if (line.startsWith(FUNCTION_INDENT)) {
                        eventListener.finishEntry(block);
                        eventListener.functionIndent(functionName);
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_VARIABLE_END:
                    if (line.startsWith(VARIABLE_INDENT)) {
                        String variableName = line.substring(VARIABLE_INDENT.length());
                        eventListener.variableIndent(variableName);
                    } else if (line.startsWith(BASIC_BLOCK_INDENT)) {
                        eventListener.finishEntry(block);
                        block = line.substring(BASIC_BLOCK_INDENT.length());
                        state = ParseState.LOOKING_FOR_VARIABLE_END;
                    } else if (line.startsWith(FUNCTION_INDENT)) {
                        eventListener.finishEntry(block);
                        eventListener.functionIndent(functionName);
                        functionName = line;
                        state = ParseState.LOOKING_FOR_BEGIN_DEAD;
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                default:
                    throw new AssertionError();
            }
        }

        eventListener.finishEntry(block);
        eventListener.endFile(functionName);
    }

    public static class Writer {
        private PrintStream stream;

        public Writer(PrintStream outputStream) {
            stream = outputStream;
        }

        public void writeVariableIndent(Object identifier) {
            stream.println(VARIABLE_INDENT + identifier);
        }

        public void writeBasicBlockIndent(String name) {
            stream.println(BASIC_BLOCK_INDENT + name);
        }

        public void writeBeginDead() {
            stream.println(BEGIN_DEAD);
        }

        public void writeEndDead() {
            stream.println(END_DEAD);
        }

        public void writeFunctionName(String functionName) {
            stream.println(functionName);
        }
    }
}
