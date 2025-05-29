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
package org.graalvm.wasm.debugging.parser;

import java.nio.file.Path;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.debugging.DebugLineMap;
import org.graalvm.wasm.debugging.data.DebugFunction;
import org.graalvm.wasm.debugging.data.DebugObject;
import org.graalvm.wasm.debugging.data.DebugObjectFactory;

/**
 * Context during debug information parsing.
 */
public class DebugParserContext {
    private final byte[] data;
    private final int debugInfoOffset;
    private final EconomicMap<Integer, DebugData> entryData;
    private final EconomicMap<Integer, DebugFunction> functions;
    private final DebugParserScope globalScope;
    private final DebugLineMap[] fileLineMaps;
    private final Path[] filePaths;
    private final String language;
    private final DebugObjectFactory objectFactory;

    public DebugParserContext(byte[] data, int debugInfoOffset, EconomicMap<Integer, DebugData> entryData, DebugLineMap[] fileLineMaps, Path[] filePaths, String language,
                    DebugObjectFactory objectFactory) {
        assert data != null : "the reference to the array containing the debug information (data) must not be null";
        assert entryData != null : "the mapping of locations in the bytecode to debug entries (entryData) must not be null";
        this.data = data;
        this.debugInfoOffset = debugInfoOffset;
        this.entryData = entryData;
        this.functions = EconomicMap.create();
        this.globalScope = DebugParserScope.createGlobalScope();
        this.fileLineMaps = fileLineMaps;
        this.filePaths = filePaths;
        this.language = language;
        this.objectFactory = objectFactory;
    }

    /**
     * Tries to retrieve the data of the debug entry at the given offset.
     * 
     * @param offset the entry offset
     */
    public DebugData dataOrNull(int offset) {
        if (offset == DebugUtil.DEFAULT_I32) {
            return null;
        }
        if (entryData.containsKey(offset)) {
            return entryData.get(offset);
        }
        return null;
    }

    /**
     * Tries to get the source offset based on the given data.
     * 
     * @param fileIndex the file index
     * @param lineNumber the line number in the file
     */
    public int sourceOffsetOrDefault(int fileIndex, int lineNumber, int defaultValue) {
        if (fileLineMaps == null) {
            return defaultValue;
        }
        if (fileIndex >= fileLineMaps.length || fileIndex < 0) {
            return defaultValue;
        }
        final DebugLineMap lineMap = fileLineMaps[fileIndex];
        if (lineMap == null) {
            return defaultValue;
        }
        final int offset = lineMap.getSourceOffset(lineNumber);
        if (offset == -1) {
            return defaultValue;
        }
        return offset;
    }

    /**
     * Tries to get the line map for the given file index.
     * 
     * @param fileIndex the file index
     */
    public DebugLineMap lineMapOrNull(int fileIndex) {
        if (fileLineMaps == null) {
            return null;
        }
        if (fileIndex >= fileLineMaps.length || fileIndex < 0) {
            return null;
        }
        return fileLineMaps[fileIndex];
    }

    /**
     * Ties to get the file path for the given file index.
     * 
     * @param fileIndex the file index
     */
    public Path pathOrNull(int fileIndex) {
        if (filePaths == null) {
            return null;
        }
        if (fileIndex >= filePaths.length || fileIndex < 0) {
            return null;
        }
        return filePaths[fileIndex];
    }

    /**
     * Adds a function to the context.
     * 
     * @param sourceCodeLocation the location of the function in the source code
     * @param function the function
     */
    public void addFunction(int sourceCodeLocation, DebugFunction function) {
        functions.put(sourceCodeLocation, function);
    }

    /**
     * Reads the range section at the given offset.
     * 
     * @param rangeOffset the range offset.
     */
    public IntArrayList readRangeSectionOrNull(int rangeOffset) {
        final int debugRangeOffset = DebugUtil.getRangesOffsetOrUndefined(data, debugInfoOffset);
        final int debugRangeLength = DebugUtil.getRangesLengthOrUndefined(data, debugInfoOffset);
        if (debugRangeOffset == DebugUtil.UNDEFINED || debugRangeLength == DebugUtil.UNDEFINED || Integer.compareUnsigned(rangeOffset, debugRangeLength) >= 0) {
            return null;
        }
        return new DebugParser(data).readRangeSectionOrNull(debugRangeOffset + rangeOffset, debugRangeLength);
    }

    /**
     * Reads the location list at the given offset.
     * 
     * @param locOffset the location offset.
     */
    public byte[] readLocationListOrNull(int locOffset) {
        final int debugLocOffset = DebugUtil.getLocOffsetOrUndefined(data, debugInfoOffset);
        final int debugLocLength = DebugUtil.getLocLengthOrUndefined(data, debugInfoOffset);
        if (debugLocOffset == DebugUtil.UNDEFINED || debugLocLength == DebugUtil.UNDEFINED || Integer.compareUnsigned(locOffset, debugLocLength) >= 0) {
            return null;
        }
        return new DebugParser(data).readLocationListOrNull(debugLocOffset + locOffset, debugLocLength);
    }

    /**
     * @return A mapping of source code locations to function data.
     */
    public EconomicMap<Integer, DebugFunction> functions() {
        return functions;
    }

    /**
     * @return The global values of the current context.
     */
    public List<DebugObject> globals() {
        return globalScope.variables();
    }

    /**
     * @return The global scope of the current context.
     */
    public DebugParserScope globalScope() {
        return globalScope;
    }

    /**
     * @return The object factory of the current context.
     */
    public DebugObjectFactory objectFactory() {
        return objectFactory;
    }

    /**
     * @return The source language of the current context.
     */
    public String language() {
        return language;
    }
}
