/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.debug;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

final class Trace implements Iterable<StopRequest> {

    static final String KEYWORD_OPEN_SCOPE = "OPEN_SCOPE";
    static final String KEYWORD_SUSPEND = "SUSPEND";
    static final String KEYWORD_PARTIAL_SCOPE = "partial";
    static final String KEYWORD_STOP = "STOP";
    static final String KEYWORD_BREAK = "BREAK";
    static final String KEYWORD_MEMBER = "MEMBER";
    static final String KEYWORD_END_MEMBERS = "END_MEMBERS";
    static final String KEYWORD_BUGGY = "buggy";

    static final String KEYWORD_KIND_ANY = "any";
    static final String KEYWORD_KIND_ADDRESS = "address";
    static final String KEYWORD_KIND_CHAR = "char";
    static final String KEYWORD_KIND_EXACT = "exact";
    static final String KEYWORD_KIND_FLOAT_32 = "float32";
    static final String KEYWORD_KIND_FLOAT_64 = "float64";
    static final String KEYWORD_KIND_INT = "int";
    static final String KEYWORD_KIND_STRUCTURED = "structured";
    static final String KEYWORD_KIND_UNAVAILABLE = "unavailable";

    private static final String KEYWORD_HEADER = "#";

    static Trace parse(Path path) {
        final Trace trace = new Trace();
        try {
            final Parser parser = trace.newParser();
            Files.lines(path).filter(s -> !s.isEmpty()).forEachOrdered(parser);
        } catch (Throwable t) {
            throw new AssertionError("Could not read trace file: " + path, t);
        }
        return trace;
    }

    private final List<StopRequest> stops;
    private final List<String> header;
    private boolean suspendOnEntry;

    private Trace() {
        this.stops = new ArrayList<>();
        this.header = new ArrayList<>();
        this.suspendOnEntry = false;
    }

    boolean suspendOnEntry() {
        return suspendOnEntry;
    }

    IntStream requestedBreakpoints() {
        return stops.stream().filter(StopRequest::needsBreakPoint).mapToInt(StopRequest::getLine).distinct();
    }

    static void updateLines(Trace trace, int from, int offset) {
        for (int i = 0; i < trace.stops.size(); i++) {
            StopRequest currentStop = trace.stops.get(i);
            if (currentStop.getLine() >= from) {
                currentStop = currentStop.updateLines(from, offset);
                trace.stops.set(i, currentStop);
            }
        }
    }

    List<String> getHeader() {
        return header;
    }

    @Override
    public Iterator<StopRequest> iterator() {
        return stops.iterator();
    }

    private Parser newParser() {
        return new Parser();
    }

    private final class Parser implements Consumer<String> {

        private final ArrayDeque<String> buffer;
        private final ArrayDeque<LLVMDebugValue.Structured> parents;

        private StopRequest request;
        private StopRequest.Scope scope;
        private LLVMDebugValue.Structured structured;

        private Parser() {
            this.buffer = new ArrayDeque<>();
            this.parents = new ArrayDeque<>();
            this.request = null;
            this.scope = null;
            this.structured = null;
        }

        @Override
        public void accept(String line) {
            split(line);
            final String token = nextToken();
            switch (token) {
                case KEYWORD_SUSPEND:
                    if (request != null) {
                        error();
                    }
                    suspendOnEntry = true;
                    break;

                case KEYWORD_STOP:
                    parseStop(false);
                    break;

                case KEYWORD_BREAK:
                    parseStop(true);
                    break;

                case KEYWORD_OPEN_SCOPE: {
                    String scopeName = buffer.pollFirst(); // may be null
                    String partialScope = buffer.pollFirst(); // often null
                    if (structured != null || !parents.isEmpty() || request == null) {
                        error();
                    }
                    if (KEYWORD_PARTIAL_SCOPE.equals(scopeName) && partialScope == null) {
                        scopeName = null;
                        partialScope = KEYWORD_PARTIAL_SCOPE;
                    }
                    boolean isPartialScope = KEYWORD_PARTIAL_SCOPE.equals(partialScope);
                    scope = new StopRequest.Scope(scopeName, isPartialScope);
                    request.addScope(scope);
                    break;
                }

                case KEYWORD_MEMBER:
                    parseMember();
                    break;

                case KEYWORD_END_MEMBERS:
                    if (structured == null) {
                        error();
                    } else if (parents.isEmpty()) {
                        structured = null;
                    } else {
                        structured = parents.pollLast();
                    }
                    break;

                case KEYWORD_HEADER:
                    header.add(line);
                    buffer.clear();
                    return;

                default:
                    error();
                    break;
            }

            if (!buffer.isEmpty()) {
                error();
            }
        }

        private void parseStop(boolean needsBreakPoint) {
            if (structured != null || !parents.isEmpty()) {
                error();
            }

            final String lineStr = nextToken();
            int line = -1;
            try {
                line = Integer.parseInt(lineStr);
            } catch (NumberFormatException nfe) {
                error(nfe);
            }
            if (request != null && line == request.getLine()) {
                // we cannot tell how many instructions belong to a single source-level line across
                // optimization levels, so this is illegal
                throw new AssertionError(String.format("Invalid trace: Subsequent breaks on line: %d", line));
            }

            final String nextActionStr = nextToken();
            final ContinueStrategy strategy;
            switch (nextActionStr) {
                case "STEP_INTO":
                    strategy = ContinueStrategy.STEP_INTO;
                    break;
                case "STEP_OUT":
                    strategy = ContinueStrategy.STEP_OUT;
                    break;
                case "STEP_OVER":
                    strategy = ContinueStrategy.STEP_OVER;
                    break;
                case "KILL":
                    strategy = ContinueStrategy.KILL;
                    break;
                case "CONTINUE":
                    strategy = ContinueStrategy.CONTINUE;
                    break;
                case "UNWIND":
                    strategy = ContinueStrategy.UNWIND;
                    break;
                case "NONE":
                    strategy = ContinueStrategy.NONE;
                    break;
                default:
                    throw new AssertionError("Invalid trace: Unknown continuation strategy: " + nextActionStr);
            }

            final String functionName = nextToken();
            request = new StopRequest(strategy, functionName, line, needsBreakPoint);
            stops.add(request);
        }

        private boolean parseBugginess() {
            final String token = buffer.pollFirst();
            return KEYWORD_BUGGY.equals(token) || (structured != null && structured.isBuggy());
        }

        private void parseMember() {
            final String kind = nextToken();
            final String type = nextToken();
            final String name = nextToken();

            LLVMDebugValue dbgValue = null;
            switch (kind) {
                case Trace.KEYWORD_KIND_ANY: {
                    dbgValue = new LLVMDebugValue.Any(type);
                    break;
                }
                case Trace.KEYWORD_KIND_CHAR: {
                    final String value = nextToken();
                    if (value.length() != 1) {
                        error();
                    }
                    final boolean isBuggy = parseBugginess();
                    dbgValue = new LLVMDebugValue.Char(type, value.charAt(0), isBuggy);
                    break;
                }
                case Trace.KEYWORD_KIND_INT: {
                    final String value = nextToken();
                    try {
                        final BigInteger intVal = new BigInteger(value);
                        final boolean isBuggy = parseBugginess();
                        dbgValue = new LLVMDebugValue.Int(type, intVal, isBuggy);
                    } catch (NumberFormatException nfe) {
                        error(nfe);
                    }
                    break;
                }
                case Trace.KEYWORD_KIND_FLOAT_32: {
                    final String value = nextToken();
                    try {
                        final float floatVal = Float.parseFloat(value);
                        final boolean isBuggy = parseBugginess();
                        dbgValue = new LLVMDebugValue.Float_32(type, floatVal, isBuggy);
                    } catch (NumberFormatException nfe) {
                        error(nfe);
                    }
                    break;
                }
                case Trace.KEYWORD_KIND_FLOAT_64: {
                    final String value = nextToken();
                    try {
                        final double floatVal = Double.parseDouble(value);
                        final boolean isBuggy = parseBugginess();
                        dbgValue = new LLVMDebugValue.Float_64(type, floatVal, isBuggy);
                    } catch (NumberFormatException nfe) {
                        error(nfe);
                    }
                    break;
                }
                case Trace.KEYWORD_KIND_ADDRESS: {
                    final String value = nextToken();
                    final boolean isBuggy = parseBugginess();
                    dbgValue = new LLVMDebugValue.Address(type, value, isBuggy);
                    break;
                }
                case Trace.KEYWORD_KIND_EXACT: {
                    final String value = nextToken();
                    final boolean isBuggy = parseBugginess();
                    dbgValue = new LLVMDebugValue.Exact(type, value, isBuggy);
                    break;
                }
                case Trace.KEYWORD_KIND_STRUCTURED: {
                    final boolean isBuggy = parseBugginess();
                    final LLVMDebugValue.Structured newStructured = new LLVMDebugValue.Structured(type, isBuggy);
                    if (structured != null) {
                        parents.addLast(structured);
                        structured.addMember(name, newStructured);
                        structured = newStructured;
                    } else {
                        scope.addMember(name, newStructured);
                    }
                    structured = newStructured;
                    return;
                }
                case Trace.KEYWORD_KIND_UNAVAILABLE: {
                    final boolean isBuggy = parseBugginess();
                    dbgValue = new LLVMDebugValue.Unavailable(type, isBuggy);
                    break;
                }
                default:
                    throw new AssertionError("Invalid trace: Unknown member kind: " + kind);
            }

            if (structured != null) {
                structured.addMember(name, dbgValue);
            } else {
                scope.addMember(name, dbgValue);
            }
        }

        private void split(String line) {
            final String str = line.trim();

            int from = 0;
            while (from < str.length()) {
                int to;
                char ch = str.charAt(from);
                if (ch == '\"') {
                    from += 1;
                    to = str.indexOf('\"', from);
                    if (to == -1) {
                        error();
                    }
                } else {
                    to = str.indexOf(' ', from + 1);
                    if (to == -1) {
                        to = str.length();
                    }
                }

                final String nextToken = str.substring(from, to);
                buffer.addLast(nextToken);
                from = to + 1;

                while (from < str.length() && str.charAt(from) == ' ') {
                    from++;
                }
            }
        }

        private String nextToken() {
            final String token = buffer.pollFirst();
            if (token != null) {
                return token;
            } else {
                throw new AssertionError("Invalid Trace!");
            }
        }

        private void error() {
            throw new AssertionError("Invalid Trace!");
        }

        private void error(Throwable cause) {
            throw new AssertionError("Invalid Trace!", cause);
        }
    }

}
