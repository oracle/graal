/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.debugging;

import java.nio.file.Path;
import java.util.SortedSet;
import java.util.TreeSet;

import org.graalvm.collections.EconomicMap;

/**
 * Representation of a source location to source code line number mapping.
 */
public final class DebugLineMap {
    private final Path filePath;
    private final SortedSet<Integer> lines;
    private final EconomicMap<Integer, Integer> sourceLocationToLineMap;
    private final EconomicMap<Integer, Integer> lineToSourceLocationMap;

    public DebugLineMap(Path filePath) {
        this.filePath = filePath;
        this.lines = new TreeSet<>();
        this.sourceLocationToLineMap = EconomicMap.create();
        this.lineToSourceLocationMap = EconomicMap.create();
    }

    public void add(int sourceLocation, int line) {
        if (!lines.contains(line)) {
            lines.add(line);
            lineToSourceLocationMap.put(line, sourceLocation);
        }
        sourceLocationToLineMap.put(sourceLocation, line);
    }

    public Path getFilePath() {
        return filePath;
    }

    public int getFirstLine(int startSourceLocation, int endSourceLocation) {
        for (int i = startSourceLocation; i <= endSourceLocation; i++) {
            final int line = getLine(i);
            if (line != -1) {
                return line;
            }
        }
        return -1;
    }

    public int getLastLine(int startSourceLocation, int endSourceLocation) {
        for (int i = endSourceLocation; i >= startSourceLocation; i--) {
            final int line = getLine(i);
            if (line != -1) {
                return line;
            }
        }
        return -1;
    }

    private int getLine(int sourceLocation) {
        return sourceLocationToLineMap.get(sourceLocation, -1);
    }

    public int getSourceLocation(int line) {
        return lineToSourceLocationMap.get(line, -1);
    }

    public int size() {
        return lineToSourceLocationMap.size();
    }

    /**
     * @return A set of all lines that are part of this line mapping.
     */
    public SortedSet<Integer> lines() {
        return lines;
    }

    /**
     * @return A mapping from source location to line numbers.
     */
    public EconomicMap<Integer, Integer> sourceLocationToLineMap() {
        return sourceLocationToLineMap;
    }
}
