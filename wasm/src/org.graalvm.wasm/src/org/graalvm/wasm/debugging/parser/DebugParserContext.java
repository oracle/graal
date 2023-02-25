/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.debugging.DebugLineMap;
import org.graalvm.wasm.debugging.data.DebugObject;
import org.graalvm.wasm.debugging.data.DebugFunction;

import com.oracle.truffle.api.source.Source;

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
    private final Source[] fileSources;

    public DebugParserContext(byte[] data, int debugInfoOffset, EconomicMap<Integer, DebugData> entryData, DebugLineMap[] fileLineMaps, Source[] fileSources) {
        this.data = data;
        this.debugInfoOffset = debugInfoOffset;
        this.entryData = entryData;
        this.functions = EconomicMap.create();
        this.globalScope = DebugParserScope.createGlobalScope();
        this.fileLineMaps = fileLineMaps;
        this.fileSources = fileSources;
    }

    /**
     * Tries to retrieve the data of the debug entry at the given offset.
     * 
     * @param offset the entry offset
     */
    public Optional<DebugData> tryGetData(int offset) {
        if (entryData.containsKey(offset)) {
            return Optional.of(entryData.get(offset));
        }
        return Optional.empty();
    }

    /**
     * Tries to get the source location based on the given data.
     * 
     * @param fileIndex the file index
     * @param lineNumber the line number in the file
     */
    public OptionalInt tryGetSourceLocation(int fileIndex, int lineNumber) {
        if (fileLineMaps == null) {
            return OptionalInt.empty();
        }
        if (fileIndex >= fileLineMaps.length || fileIndex < 0) {
            return OptionalInt.empty();
        }
        final DebugLineMap lineMap = fileLineMaps[fileIndex];
        final int pc = lineMap.getSourceLocation(lineNumber);
        if (pc == -1) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(pc);
    }

    /**
     * Tries to get the line map for the given file index.
     * 
     * @param fileIndex the file index
     */
    public Optional<DebugLineMap> tryGetLineMap(int fileIndex) {
        if (fileLineMaps == null) {
            return Optional.empty();
        }
        if (fileIndex >= fileLineMaps.length || fileIndex < 0) {
            return Optional.empty();
        }
        final DebugLineMap lineMap = fileLineMaps[fileIndex];
        if (lineMap != null) {
            return Optional.of(lineMap);
        }
        return Optional.empty();
    }

    /**
     * Ties to get the source for the given file index.
     * 
     * @param fileIndex the file index
     */
    public Optional<Source> tryGetSource(int fileIndex) {
        if (fileSources == null) {
            return Optional.empty();
        }
        if (fileIndex >= fileSources.length || fileIndex < 0) {
            return Optional.empty();
        }
        final Source source = fileSources[fileIndex];
        if (source == null) {
            return Optional.empty();
        }
        return Optional.of(source);
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
    public IntArrayList readRangeSection(int rangeOffset) {
        return new DebugParser(data).readRangeSection(DebugUtil.rangesOffset(data, debugInfoOffset) + rangeOffset);
    }

    /**
     * Reads the location list at the given offset.
     * 
     * @param locOffset the location offset.
     */
    public byte[] readLocationList(int locOffset) {
        return new DebugParser(data).readLocationList(DebugUtil.locOffset(data, debugInfoOffset) + locOffset);
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
}
