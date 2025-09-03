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
import java.util.TreeMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

/**
 * Representation of a source location to source code line number mapping.
 */
public final class DebugLineMap {
    private final Path filePath;
    private final TreeMap<Integer, Integer> sourceOffsetToLineMap;
    private final TreeMap<Integer, Integer> lineToSourceOffsetMap;

    public DebugLineMap(Path filePath) {
        this.filePath = filePath;
        this.sourceOffsetToLineMap = new TreeMap<>();
        this.lineToSourceOffsetMap = new TreeMap<>();
    }

    public void add(int sourceOffset, int line) {
        if (line == 0) {
            // invalid line
            return;
        }
        if (!lineToSourceOffsetMap.containsKey(line)) {
            lineToSourceOffsetMap.put(line, sourceOffset);
        }
        sourceOffsetToLineMap.put(sourceOffset, line);
    }

    public Path getFilePath() {
        return filePath;
    }

    /**
     * @param startOffset The start offset
     * @param endOffset The end offset
     * @return The next line, if one exists in the given offset range, -1 otherwise.
     */
    public int getNextLine(int startOffset, int endOffset) {
        final Integer location = sourceOffsetToLineMap.ceilingKey(startOffset);
        if (location == null) {
            return -1;
        }
        if (location <= endOffset) {
            return sourceOffsetToLineMap.get(location);
        }
        return -1;
    }

    /**
     * 
     * @param startOffset The start offset
     * @param endOffset The end offset
     * @return A {@link DebugLineSection} for the given offset range.
     */
    public DebugLineSection getLineIndexMap(int startOffset, int endOffset) {
        final EconomicSet<Integer> uniqueLines = EconomicSet.create();
        final EconomicMap<Integer, Integer> offsetToLineIndexMap = EconomicMap.create();
        final EconomicMap<Integer, Integer> lineToLineIndexMap = EconomicMap.create();
        int location = startOffset;
        while (location <= endOffset) {
            final Integer nextLocation = sourceOffsetToLineMap.ceilingKey(location);
            if (nextLocation == null) {
                break;
            }
            final int line = sourceOffsetToLineMap.get(nextLocation);
            if (!uniqueLines.contains(line)) {
                uniqueLines.add(line);
                lineToLineIndexMap.put(line, lineToLineIndexMap.size());
            }
            final int lineIndex = lineToLineIndexMap.get(line);
            offsetToLineIndexMap.put(nextLocation, lineIndex);
            location = nextLocation + 1;
        }
        return new DebugLineSection(uniqueLines, offsetToLineIndexMap);
    }

    /**
     * 
     * @param line The line
     * @return The source offset of the line, if it exists, -1 otherwise.
     */
    public int getSourceOffset(int line) {
        final Integer value = lineToSourceOffsetMap.get(line);
        if (value == null) {
            return -1;
        }
        return value;
    }
}
