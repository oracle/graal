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
package com.oracle.truffle.llvm.test.debug;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

final class TraceWriter {

    static void write(Trace trace, Path path) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
            final TraceWriter traceWriter = new TraceWriter(writer);
            traceWriter.emitTrace(trace);
            writer.flush();

        } catch (IOException e) {
            throw new AssertionError("Error while writing trace to file: " + path, e);
        }
    }

    private static final String INDENT = "    ";

    private final BufferedWriter writer;
    private int indentLevel;

    private TraceWriter(BufferedWriter writer) {
        this.writer = writer;
        this.indentLevel = 0;
    }

    private void emitTrace(Trace trace) throws IOException {
        if (trace.suspendOnEntry()) {
            newLine(Trace.KEYWORD_SUSPEND);
            newLine();
            newLine();
        }

        for (StopRequest stop : trace) {
            emitStop(stop);
        }
        newLine();
    }

    private void emitStop(StopRequest stop) throws IOException {
        newLine(stop.needsBreakPoint() ? Trace.KEYWORD_BREAK : Trace.KEYWORD_STOP);
        appendWord(String.valueOf(stop.getLine()));
        appendWord(String.valueOf(stop.getNextAction()));
        appendWord(stop.getFunctionName());

        addIndentation();
        for (StopRequest.Scope scope : stop) {
            emitScope(scope);
        }
        dropIndentation();
        newLine();
        newLine();
    }

    private void emitScope(StopRequest.Scope scope) throws IOException {
        newLine(Trace.KEYWORD_OPEN_SCOPE);
        if (scope.getName() != null && !scope.getName().isEmpty()) {
            appendWord(scope.getName());
        }
        if (scope.isPartial()) {
            appendWord(Trace.KEYWORD_PARTIAL_SCOPE);
        }

        addIndentation();
        for (String name : scope.getLocals().keySet()) {
            emitMember(name, scope.getLocals().get(name));
        }
        dropIndentation();
    }

    private void emitMember(String name, LLVMDebugValue value) throws IOException {
        newLine(Trace.KEYWORD_MEMBER);
        appendWord(value.getKind());
        appendWord(value.getExpectedType());
        appendWord(name);

        if (value instanceof LLVMDebugValue.Structured) {
            if (value.isBuggy()) {
                appendWord(Trace.KEYWORD_BUGGY);
            }

            LLVMDebugValue.Structured structured = (LLVMDebugValue.Structured) value;

            addIndentation();
            for (String memberName : structured.getExpectedMembers().keySet()) {
                emitMember(memberName, structured.getExpectedMembers().get(memberName));
            }
            dropIndentation();

            newLine(Trace.KEYWORD_END_MEMBERS);

        } else {
            if (value.getExpectedDisplayValue() != null) {
                appendWord(value.getExpectedDisplayValue());
            }
            if (value.isBuggy()) {
                appendWord(Trace.KEYWORD_BUGGY);
            }
        }
    }

    private void addIndentation() {
        indentLevel++;
    }

    private void dropIndentation() {
        indentLevel--;
    }

    private void appendWord(Object word) throws IOException {
        writer.write(" \"");
        writer.write(String.valueOf(word));
        writer.write("\"");
    }

    private void newLine(String cmd) throws IOException {
        writer.newLine();
        for (int i = 0; i < indentLevel; i++) {
            writer.write(INDENT);
        }
        writer.write(cmd);
    }

    private void newLine() throws IOException {
        writer.newLine();
    }
}
