/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import java.io.*;
import java.util.*;

import com.oracle.truffle.api.*;

/**
 * A representation of source code information, suitable for hash table keys with equality defined
 * in terms of content. There are three kinds of sources supported at present.
 * <ul>
 * <li><strong>File:</strong> Each file is represented as a canonical object, indexed by the
 * absolute, canonical path name of the file. The textual contents of the file may be supplied when
 * the object is created, or it may be read lazily. Only one lazy attempt will be made to read a
 * file, and failure will result silently in null content.</li>
 * <li><strong>Literal Source:</strong> A named text string, whose contents are supplied concretely
 * (possibly via an {@link InputStream}), can also be used as a source. These are represented as
 * value objects whose equality depends on both name and contents.</li>
 * <li><strong>Fake Files:</strong> A named text string used for testing; its contents can be
 * retrieved by name, unlike literal sources.</li>
 * </ul>
 * <p>
 * <strong>Cache:</strong>
 * <ol>
 * <li>Access to source file contents via {@link Source#getInputStream()} or
 * {@link Source#getReader()} does <em>not</em> by itself result in the file's contents being cached
 * in the {@link Source} object.</li>
 * <li>Access to source file contents via {@link Source#getCode()} or any other {@link Source}
 * methods related to file's contents <em>will</em> result in the contents being cached in the
 * {@link Source} object.</li>
 * <li>Once source file contents have been cached, access to source file contents via
 * {@link Source#getInputStream()} or {@link Source#getReader()} will be provided from the cache.</li>
 * <li>Any access to source file contents via the cache will result in a timestamp check and
 * possible cache reload.</li>
 * </ol>
 */
public final class SourceManager {

    // Only files and fake files are indexed.
    private final Map<String, SourceImpl> pathToSource = new HashMap<>();

    public SourceManager() {

    }

    /**
     * Gets the canonical representation of a source file, whose contents will be read lazily and
     * then cached.
     *
     * @param reset forces any existing {@link Source} cache to be cleared, forcing a re-read
     */
    public Source get(String fileName, boolean reset) {

        SourceImpl source = pathToSource.get(fileName);
        if (source == null) {
            final File file = new File(fileName);
            String path = null;
            if (file.exists()) {
                try {
                    path = file.getCanonicalPath();
                } catch (IOException e) {
                    throw new RuntimeException("Can't find file " + fileName);
                }
            }
            source = pathToSource.get(path);
            if (source == null) {
                source = new FileSourceImpl(file, fileName, path);
                pathToSource.put(path, source);
            }
        }
        if (reset) {
            source.reset();
        }
        return source;
    }

    /**
     * Gets the canonical representation of a source file, whose contents will be read lazily and
     * then cached.
     */
    public Source get(String fileName) {
        return get(fileName, false);
    }

    /**
     * Creates a source from literal text.
     */
    @SuppressWarnings("static-method")
    public Source get(String name, String code) {
        assert code != null;
        return new LiteralSourceImpl(name, code);
    }

    /**
     * Creates a source whose contents will be read immediately and cached.
     */
    @SuppressWarnings("static-method")
    public Source get(String name, InputStream stream) throws IOException {
        InputStreamReader reader = new InputStreamReader(stream);
        return new LiteralSourceImpl(name, readCode(reader));
    }

    /**
     * Creates a source from literal text, but which acts as a file and can be retrieved by name
     * (unlike other literal sources); intended for testing.
     */
    public Source getFakeFile(String name, String code) {
        final SourceImpl source = new LiteralSourceImpl(name, code);
        pathToSource.put(name, source);
        return source;
    }

    private static String readCode(Reader reader) throws IOException {
        final StringBuilder builder = new StringBuilder();
        final char[] buffer = new char[1024];

        while (true) {
            final int n = reader.read(buffer);
            if (n == -1) {
                break;
            }
            builder.append(buffer, 0, n);
        }

        return builder.toString();
    }

    private abstract static class SourceImpl implements Source {

        protected TextMap textMap = null;

        protected abstract void reset();

        public final InputStream getInputStream() {
            return new ByteArrayInputStream(getCode().getBytes());
        }

        /**
         * Gets the text (not including a possible terminating newline) in a (1-based) numbered
         * line.
         */
        public final String getCode(int lineNumber) {
            checkTextMap();
            final int offset = textMap.lineStartOffset(lineNumber);
            final int length = textMap.lineLength(lineNumber);
            return getCode().substring(offset, offset + length);
        }

        /**
         * The number of text lines in the source.
         */
        public final int getLineCount() {
            return checkTextMap().lineCount();
        }

        /**
         * The 1-based number of the line that includes a 0-based character offset.
         */
        public final int getLineNumber(int offset) {
            return checkTextMap().offsetToLine(offset);
        }

        /**
         * The 0-based character offset at the start of a (1-based) numbered line.
         */
        public final int getLineStartOffset(int lineNumber) {
            return checkTextMap().lineStartOffset(lineNumber);
        }

        /**
         * The number of characters (not counting a possible terminating newline) in a (1-based)
         * numbered line.
         */
        public final int getLineLength(int lineNumber) {
            return checkTextMap().lineLength(lineNumber);
        }

        public final SourceSection createSection(String identifier, int startOffset, int sectionLength) throws IllegalArgumentException {
            final int codeLength = getCode().length();
            if (!(startOffset >= 0 && sectionLength >= 0 && startOffset + sectionLength <= codeLength)) {
                throw new IllegalArgumentException("text positions out of range");
            }
            checkTextMap();
            final int startLine = getLineNumber(startOffset);
            final int startColumn = startOffset - getLineStartOffset(startLine) + 1;

            return new SourceSectionImpl(this, identifier, startLine, startColumn, startOffset, sectionLength);
        }

        public SourceSection createSection(String identifier, int startLine, int startColumn, int sectionLength) {
            checkTextMap();
            final int lineStartOffset = textMap.lineStartOffset(startLine);
            if (startColumn > textMap.lineLength(startLine)) {
                throw new IllegalArgumentException("column out of range");
            }
            final int startOffset = lineStartOffset + startColumn - 1;
            return new SourceSectionImpl(this, identifier, startLine, startColumn, startOffset, sectionLength);
        }

        public SourceSection createSection(String identifier, int startLine, int startColumn, int charIndex, int length) {
            return new SourceSectionImpl(this, identifier, startLine, startColumn, charIndex, length);
        }

        private TextMap checkTextMap() {
            if (textMap == null) {
                final String code = getCode();
                if (code == null) {
                    throw new RuntimeException("can't read file " + getName());
                }
                textMap = new TextMap(code);
            }
            return textMap;
        }
    }

    private static class LiteralSourceImpl extends SourceImpl {

        private final String name; // Name used originally to describe the source
        private final String code;

        public LiteralSourceImpl(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getShortName() {
            return name;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getPath() {
            return name;
        }

        @Override
        public Reader getReader() {
            return new StringReader(code);
        }

        @Override
        protected void reset() {
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + name.hashCode();
            result = prime * result + (code == null ? 0 : code.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof LiteralSourceImpl)) {
                return false;
            }
            LiteralSourceImpl other = (LiteralSourceImpl) obj;
            return name.equals(other.name) && code.equals(other.code);
        }

    }

    private static class FileSourceImpl extends SourceImpl {

        private final File file;
        private final String name; // Name used originally to describe the source
        private final String path;  // Normalized path description of an actual file

        private String code = null;  // A cache of the file's contents
        private long timeStamp;      // timestamp of the cache in the file system

        public FileSourceImpl(File file, String name, String path) {
            this.file = file;
            this.name = name;
            this.path = path;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getShortName() {
            return file.getName();
        }

        @Override
        public String getCode() {
            if (code == null || timeStamp != file.lastModified()) {
                try {
                    code = readCode(getReader());
                    timeStamp = file.lastModified();
                } catch (IOException e) {
                }
            }
            return code;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Reader getReader() {
            if (code != null && timeStamp == file.lastModified()) {
                return new StringReader(code);
            }
            try {
                return new FileReader(file);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Can't find file " + path);
            }
        }

        @Override
        protected void reset() {
            this.code = null;
        }

    }

    private static class SourceSectionImpl implements SourceSection {

        private final Source source;
        private final String identifier;
        private final int startLine;
        private final int startColumn;
        private final int charIndex;
        private final int charLength;

        /**
         * Creates a new object representing a contiguous text section within the source code of a
         * guest language program's text.
         * <p>
         * The starting location of the section is specified using two different coordinate:
         * <ul>
         * <li><b>(row, column)</b>: rows and columns are 1-based, so the first character in a
         * source file is at position {@code (1,1)}.</li>
         * <li><b>character index</b>: 0-based offset of the character from the beginning of the
         * source, so the first character in a file is at index {@code 0}.</li>
         * </ul>
         * The <b>newline</b> that terminates each line counts as a single character for the purpose
         * of a character index. The (row,column) coordinates of a newline character should never
         * appear in a text section.
         * <p>
         *
         * @param source object representing the complete source program that contains this section
         * @param identifier an identifier used when printing the section
         * @param startLine the 1-based number of the start line of the section
         * @param startColumn the 1-based number of the start column of the section
         * @param charIndex the 0-based index of the first character of the section
         * @param charLength the length of the section in number of characters
         */
        public SourceSectionImpl(Source source, String identifier, int startLine, int startColumn, int charIndex, int charLength) {
            this.source = source;
            this.identifier = identifier;
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.charIndex = charIndex;
            this.charLength = charLength;
        }

        public final Source getSource() {
            return source;
        }

        public final int getStartLine() {
            return startLine;
        }

        public final int getStartColumn() {
            return startColumn;
        }

        public final int getCharIndex() {
            return charIndex;
        }

        public final int getCharLength() {
            return charLength;
        }

        public final int getCharEndIndex() {
            return charIndex + charLength;
        }

        public final String getIdentifier() {
            return identifier;
        }

        public final String getCode() {
            return getSource().getCode().substring(charIndex, charIndex + charLength);
        }

        public final String getShortDescription() {
            return String.format("%s:%d", source.getShortName(), startLine);
        }

        @Override
        public String toString() {
            return String.format("%s:%d", source.getName(), startLine);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + charIndex;
            result = prime * result + charLength;
            result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
            result = prime * result + ((source == null) ? 0 : source.hashCode());
            result = prime * result + startColumn;
            result = prime * result + startLine;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof SourceSectionImpl)) {
                return false;
            }
            SourceSectionImpl other = (SourceSectionImpl) obj;
            if (charIndex != other.charIndex) {
                return false;
            }
            if (charLength != other.charLength) {
                return false;
            }
            if (identifier == null) {
                if (other.identifier != null) {
                    return false;
                }
            } else if (!identifier.equals(other.identifier)) {
                return false;
            }
            if (source == null) {
                if (other.source != null) {
                    return false;
                }
            } else if (!source.equals(other.source)) {
                return false;
            }
            if (startColumn != other.startColumn) {
                return false;
            }
            if (startLine != other.startLine) {
                return false;
            }
            return true;
        }

    }

}
