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

import java.nio.file.Path;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.debugging.DebugLineMap;
import org.graalvm.wasm.debugging.data.DebugDataUtil;
import org.graalvm.wasm.debugging.data.DebugFunction;
import org.graalvm.wasm.debugging.data.DebugObjectFactory;
import org.graalvm.wasm.debugging.encoding.Attributes;
import org.graalvm.wasm.debugging.languages.DebugLanguageSupport;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;

/**
 * Extracts the debug information and converts it to an internal representation of values.
 */
public class DebugTranslator {
    private final DebugParser parser;
    private final DebugSourceLoader sourceLoader;
    private final String testCompDir;

    public DebugTranslator(byte[] data, String testCompDir) {
        this.parser = new DebugParser(data);
        this.sourceLoader = new DebugSourceLoader();
        this.testCompDir = testCompDir;
    }

    @TruffleBoundary
    public EconomicMap<Integer, DebugFunction> readCompilationUnits(byte[] customData, int debugInfoOffset) {
        assert customData != null : "the array containing the debug information must not be null when trying to parse the information";
        assert debugInfoOffset != DebugUtil.UNDEFINED : "the offset of the debug information must be valid";
        EconomicMap<Integer, DebugFunction> debugFunctions = EconomicMap.create();
        int unitOffset = 0;
        DebugParseUnit entryUnit = parser.readCompilationUnit(debugInfoOffset, unitOffset);
        while (entryUnit != null) {
            // Only read compilation units for which sources are available
            if (parseCompilationUnit(entryUnit, customData, debugInfoOffset) != null) {
                final DebugParseUnit unit = parser.readEntries(debugInfoOffset, unitOffset);
                if (unit != null) {
                    final DebugParserContext context = parseCompilationUnit(unit, customData, debugInfoOffset);
                    if (context != null) {
                        debugFunctions.putAll(context.functions());
                    }
                }
            }
            unitOffset = parser.getNextCompilationUnitOffset(debugInfoOffset, unitOffset);
            if (unitOffset != -1) {
                entryUnit = parser.readCompilationUnit(debugInfoOffset, unitOffset);
            }
        }
        return debugFunctions;
    }

    private DebugParserContext parseCompilationUnit(DebugParseUnit parseUnit, byte[] customData, int debugInfoOffset) {
        final DebugData data = parseUnit.rootData();
        final EconomicMap<Integer, DebugData> entries = parseUnit.entries();

        final int languageId = data.asI32OrDefault(Attributes.LANGUAGE, -1);
        final DebugObjectFactory objectFactory = DebugLanguageSupport.getObjectFactory(languageId);
        if (objectFactory == null) {
            return null;
        }
        final String languageName = objectFactory.languageName();
        final int stmtList = data.asI32OrDefault(Attributes.STMT_LIST, -1);
        if (stmtList == -1) {
            return null;
        }
        String compDir = data.asStringOrNull(Attributes.COMP_DIR);
        if (compDir == null) {
            return null;
        }
        if (!testCompDir.isEmpty()) {
            compDir = testCompDir;
        }
        final int lineOffset = DebugUtil.getLineOffsetOrUndefined(customData, debugInfoOffset);
        final int lineLength = DebugUtil.getLineLengthOrUndefined(customData, debugInfoOffset);
        if (lineOffset == DebugUtil.UNDEFINED || lineLength == DebugUtil.UNDEFINED) {
            return null;
        }
        final DebugLineMap[] fileLineMaps = parser.readLineSectionOrNull(lineOffset + stmtList, lineLength, compDir);
        if (fileLineMaps == null) {
            return null;
        }
        int nullSources = 0;
        Source[] fileSources = new Source[fileLineMaps.length];
        for (int i = 0; i < fileSources.length; i++) {
            final DebugLineMap lineMap = fileLineMaps[i];
            if (lineMap != null) {
                final Path path = lineMap.getFilePath();
                fileSources[i] = sourceLoader.load(path, languageName, !testCompDir.isEmpty());
            }
            if (fileSources[i] == null) {
                nullSources++;
            }
        }
        if (nullSources == fileSources.length) {
            return null;
        }
        final DebugParserContext context = new DebugParserContext(customData, debugInfoOffset, entries, fileLineMaps, fileSources);
        final int[] pcs = DebugDataUtil.readPcsOrNull(data, context);
        if (pcs == null) {
            return null;
        }
        assert pcs.length == 2 : "the pc range of a debug compilation unit must contain exactly two values (start pc and end pc)";
        final int scopeStart = pcs[0];
        final int scopeEnd = pcs[1];
        final DebugParserScope scope = context.globalScope().with(null, scopeStart, scopeEnd);
        for (DebugData child : data.children()) {
            objectFactory.parse(context, scope, child);
        }
        return context;
    }
}
