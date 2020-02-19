/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.tools.lsp.server.utils;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.graalvm.options.OptionValues;
import org.graalvm.tools.lsp.server.types.Position;
import org.graalvm.tools.lsp.server.types.Range;
import org.graalvm.tools.lsp.server.types.TextDocumentContentChangeEvent;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class SourceUtils {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    private SourceUtils() {
        assert false;
    }

    public static final class SourceFix {
        public final String text;
        public final String removedCharacters;
        public final int characterIdx;

        public SourceFix(String text, String removedChracters, int characterIdx) {
            this.text = text;
            this.removedCharacters = removedChracters;
            this.characterIdx = characterIdx;
        }
    }

    public static boolean isLineValid(int zeroBasedLine, Source source) {
        // Source line is one-based
        return zeroBasedLine >= 0 &&
                        (zeroBasedLine < source.getLineCount() ||
                                        (zeroBasedLine == source.getLineCount() && endsWithNewline(source)) ||
                                        (zeroBasedLine == 0 && source.getLineCount() == 0));
    }

    public static boolean isColumnValid(int line, int column, Source source) {
        return column <= source.getLineLength(zeroBasedLineToOneBasedLine(line, source));
    }

    public static int zeroBasedLineToOneBasedLine(int line, Source source) {
        int lc = source.getLineCount();
        if (lc <= line) {
            return Math.max(1, lc);
        }

        return line + 1;
    }

    public static int zeroBasedColumnToOneBasedColumn(int zeroBasedLine, int oneBasedLine, int zeroBasedColumn, Source source) {
        if (zeroBasedLine >= oneBasedLine) { // line overflow
            return source.getLineLength(oneBasedLine) + 1;
        }
        int lc = source.getLineLength(oneBasedLine);
        if (lc < zeroBasedColumn && zeroBasedColumn > 0) {
            return Math.max(1, lc);
        }

        return zeroBasedColumn + 1;
    }

    private static boolean endsWithNewline(Source source) {
        String text = source.getCharacters().toString();
        boolean isNewlineEnd = !text.isEmpty() && text.charAt(text.length() - 1) == '\n';
        return isNewlineEnd;
    }

    public static Range sourceSectionToRange(SourceSection section) {
        if (section == null) {
            return Range.create(0, 0, 0, 0); // TODO: LSP4J removal
        }
        int endColumn = section.getEndColumn();
        if (section.getCharacters().toString().endsWith("\n")) {
            // TODO(ds) Python problem - without correction, goto definition highlighting is not
            // working
            endColumn -= 1;
        }
        return Range.create(section.getStartLine() - 1, section.getStartColumn() - 1, section.getEndLine() - 1, endColumn);
    }

    public static SourceSection findSourceLocation(TruffleInstrument.Env env, Object object, LanguageInfo defaultLanguageInfo) {
        LanguageInfo languageInfo;
        if (INTEROP.hasLanguage(object)) {
            try {
                languageInfo = env.getLanguageInfo(INTEROP.getLanguage(object));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        } else {
            languageInfo = defaultLanguageInfo;
        }
        Object view = env.getLanguageView(languageInfo, object);
        if (INTEROP.hasSourceLocation(view)) {
            try {
                return INTEROP.getSourceLocation(view);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
        return null;
    }

    public static SourceFix removeLastTextInsertion(TextDocumentSurrogate surrogate, int originalCharacter, TruffleLogger logger) {
        TextDocumentContentChangeEvent lastChange = surrogate.getLastChange();
        if (lastChange == null) {
            return null;
        }
        Range range = lastChange.getRange();
        TextDocumentContentChangeEvent replacementEvent = TextDocumentContentChangeEvent.create("") //
                        .setRange(Range.create(range.getStart(), Position.create(range.getEnd().getLine(), range.getEnd().getCharacter() + lastChange.getText().length()))) //
                        .setRangeLength(lastChange.getText().length());
        String codeBeforeLastChange = applyTextDocumentChanges(Arrays.asList(replacementEvent), surrogate.getSource(), surrogate, logger);
        int characterIdx = originalCharacter - (originalCharacter - range.getStart().getCharacter());

        return new SourceFix(codeBeforeLastChange, lastChange.getText(), characterIdx);
    }

    public static String applyTextDocumentChanges(List<? extends TextDocumentContentChangeEvent> list, Source source, TextDocumentSurrogate surrogate, TruffleLogger logger) {
        Source currentSource = null;
        String text = source.getCharacters().toString();
        StringBuilder sb = new StringBuilder(text);
        for (TextDocumentContentChangeEvent event : list) {
            if (currentSource == null) {
                currentSource = source;
            } else {
                currentSource = Source.newBuilder(currentSource.getLanguage(), sb, currentSource.getName()).cached(false).build();
            }

            Range range = event.getRange();
            if (range == null) {
                // The whole file has changed
                sb.setLength(0); // Clear StringBuilder
                sb.append(event.getText());
                continue;
            }

            Position start = range.getStart();
            Position end = range.getEnd();
            int startLine = start.getLine() + 1;
            int endLine = end.getLine() + 1;
            int replaceBegin = currentSource.getLineStartOffset(startLine) + start.getCharacter();
            int replaceEnd = currentSource.getLineStartOffset(endLine) + end.getCharacter();

            sb.replace(replaceBegin, replaceEnd, event.getText());

            if (surrogate != null && surrogate.hasCoverageData()) {
                updateCoverageData(surrogate, currentSource, event.getText(), range, replaceBegin, replaceEnd, logger);
            }
        }
        return sb.toString();
    }

    private static void updateCoverageData(TextDocumentSurrogate surrogate, Source source, String newText, Range range, int replaceBegin, int replaceEnd, TruffleLogger logger) {
        Source newSourceSnippet = Source.newBuilder("dummyLanguage", newText, "dummyCoverage").cached(false).build();
        int linesNewText = newSourceSnippet.getLineCount() + (newText.endsWith("\n") ? 1 : 0) + (newText.isEmpty() ? 1 : 0);

        Source oldSourceSnippet = source.subSource(replaceBegin, replaceEnd - replaceBegin);
        int liensOldText = oldSourceSnippet.getLineCount() + (oldSourceSnippet.getCharacters().toString().endsWith("\n") ? 1 : 0) + (oldSourceSnippet.getLength() == 0 ? 1 : 0);

        int newLineModification = linesNewText - liensOldText;
        logger.log(Level.FINEST, "newLineModification: {0}", newLineModification);

        if (newLineModification != 0) {
            List<SourceSectionReference> sections = surrogate.getCoverageLocations();
            sections.stream().filter(section -> section.includes(range)).forEach(section -> {
                SourceSectionReference migratedSection = new SourceSectionReference(section);
                migratedSection.setEndLine(migratedSection.getEndLine() + newLineModification);
                surrogate.replace(section, migratedSection);
                logger.log(Level.FINEST, "Included - Old: {0} Fixed: {1}", new Object[]{section, migratedSection});
            });
            sections.stream().filter(section -> section.behind(range)).forEach(section -> {
                SourceSectionReference migratedSection = new SourceSectionReference(section);
                migratedSection.setStartLine(migratedSection.getStartLine() + newLineModification);
                migratedSection.setEndLine(migratedSection.getEndLine() + newLineModification);
                surrogate.replace(section, migratedSection);
                logger.log(Level.FINEST, "Behind   - Old: {0} Fixed: {1}", new Object[]{section, migratedSection});
            });
        }
    }

    public static Range getRangeFrom(TruffleException te) {
        Range range = Range.create(0, 0, 0, 0); // TODO: LSP4J removal
        SourceSection sourceLocation = te.getSourceLocation() != null ? te.getSourceLocation()
                        : (te.getLocation() != null ? te.getLocation().getEncapsulatingSourceSection() : null);
        if (sourceLocation != null && sourceLocation.isAvailable()) {
            range = sourceSectionToRange(sourceLocation);
        }
        return range;
    }

    public static int convertLineAndColumnToOffset(Source source, int oneBasedLineNumber, int column) {
        int offset = source.getLineStartOffset(oneBasedLineNumber);
        if (column > 0) {
            offset += column - 1;
        }
        return offset;
    }

    public static URI getOrFixFileUri(Source source) {
        if (source.getURI().getScheme().equals("file")) {
            return source.getURI();
        } else if (source.getURI().getScheme().equals("truffle")) {
            // We assume, that the source name is a valid file path if
            // the URI has no file scheme
            Path path = Paths.get(source.getName());
            return path.toUri();
        } else {
            throw new IllegalStateException("Source has an URI with unknown schema: " + source.getURI());
        }
    }

    public static boolean isValidSourceSection(SourceSection sourceSection, OptionValues options) {
        SourcePredicate predicate = SourcePredicateBuilder.newBuilder().excludeInternal(options).build();
        return sourceSection != null && sourceSection.isAvailable() && predicate.test(sourceSection.getSource());
    }

}
