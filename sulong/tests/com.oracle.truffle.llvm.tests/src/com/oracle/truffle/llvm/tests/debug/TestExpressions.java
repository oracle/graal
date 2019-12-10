/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class TestExpressions implements Iterable<StopRequest> {

    private TestExpressions() {
        this.stops = new ArrayList<>();
        this.stopReqExpressionMap = new HashMap<>();
        this.stopReqAllowFailure = new HashMap<>();
    }

    IntStream requestedBreakpoints() {
        return stops.stream().filter(StopRequest::needsBreakPoint).mapToInt(StopRequest::getLine).distinct();
    }

    @Override
    public Iterator<StopRequest> iterator() {
        return stops.iterator();
    }

    Map<String, String> getExpressions(StopRequest sr) {
        return stopReqExpressionMap.get(sr);
    }

    boolean getFailure(StopRequest sr) {
        return stopReqAllowFailure.get(sr);
    }

    static TestExpressions parse(Path path) {
        final TestExpressions te = new TestExpressions();
        try {
            final Parser parser = te.newParser();
            Files.lines(path).filter(s -> !s.isEmpty()).forEachOrdered(parser);
        } catch (Throwable t) {
            throw new AssertionError("Could not read test file: " + path, t);
        }
        return te;
    }

    private List<StopRequest> stops;
    private Map<StopRequest, Map<String, String>> stopReqExpressionMap;
    private Map<StopRequest, Boolean> stopReqAllowFailure;

    private Parser newParser() {
        return new Parser();
    }

    static void updateLines(TestExpressions te, int from, int offset) {
        for (int i = 0; i < te.stops.size(); i++) {
            StopRequest currentStop = te.stops.get(i);
            if (currentStop.getLine() >= from) {
                currentStop = currentStop.updateLines(from, offset);
                te.stops.set(i, currentStop);
            }
        }
    }

    private final class Parser implements Consumer<String> {

        private static final String KEYWORD_HEADER = "#";
        private static final String KEYWORD_BREAK = "BREAK";
        private static final String KEYWORD_END = "END";
        private static final String KEYWORD_FAILURE = "FAILURE";

        private final LinkedList<String> buffer;
        private StopRequest request;
        private Map<String, String> map;

        private Parser() {
            this.buffer = new LinkedList<>();
            this.request = null;
            this.map = null;
        }

        @Override
        @TruffleBoundary
        public void accept(String line) {
            split(line);
            final String token = nextToken();
            switch (token) {
                case KEYWORD_BREAK:
                    parseNewBreak();
                    break;

                case KEYWORD_END:
                    if (map == null) {
                        error();
                    } else {
                        stopReqExpressionMap.put(request, map);
                        map = null;
                    }
                    break;

                case KEYWORD_HEADER:
                    buffer.clear();
                    return;

                case KEYWORD_FAILURE:
                    stopReqAllowFailure.replace(request, new Boolean(true));
                    break;

                default:
                    // add line with pattern ' "firstString" "secondString" ' to map
                    String[] lines = line.trim().substring(1).split("\"", 4);
                    try {
                        if (lines[0].length() <= 0) {
                            error();
                        }
                        if (lines[2].length() <= 0) {
                            error();
                        }
                        // System.out.printf("\"%s\" must evaluate to \"%s\"\n", lines[0],
                        // lines[2]);
                        map.put(lines[0], lines[2]);
                        buffer.clear();
                    } catch (ArrayIndexOutOfBoundsException ae) {
                        System.err.println("ARRAYINDEX_OUT_OF_BOUNDS_" + line);
                        error();
                    }
                    break;
            }

            if (!buffer.isEmpty()) {
                error();
            }
        }

        private void parseNewBreak() {
            if (map != null) {
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

            if (!nextToken().contentEquals("CONTINUE")) {
                error();
            }

            final String functionName = nextToken();
            request = new StopRequest(ContinueStrategy.CONTINUE, functionName, line, true);
            stops.add(request);
            stopReqAllowFailure.put(request, new Boolean(false));
            map = new HashMap<>();
        }

        private String nextToken() {
            final String token = buffer.pollFirst();
            if (token != null) {
                return token;
            } else {
                throw new AssertionError("Invalid TestExpressions!");
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

        private void error() {
            throw new AssertionError("Invalid TestExpressions!");
        }

        private void error(Throwable cause) {
            throw new AssertionError("Invalid TestExpressions!", cause);
        }
    }

}
