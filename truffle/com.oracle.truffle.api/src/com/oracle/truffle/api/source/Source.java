/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.file.Files;
import java.nio.file.spi.FileTypeDetector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage.Registration;

/**
 * Representation of a guest language source code unit and its contents. Sources originate in
 * several ways:
 * <ul>
 * <li><strong>Literal:</strong> An anonymous text string: not named and not indexed. These should
 * be considered value objects; equality is defined based on contents.<br>
 * See {@link Source#fromText(CharSequence, String)}</li>
 * <p>
 * <li><strong>Named Literal:</strong> A text string that can be retrieved by name as if it were a
 * file, but without any assumption that the name is related to a file path. Creating a new literal
 * with an already existing name will replace its predecessor in the index.<br>
 * See {@link Source#fromNamedText(CharSequence, String)}<br>
 * See {@link Source#find(String)}</li>
 * <p>
 * <li><strong>File:</strong> Each file is represented as a canonical object, indexed by the
 * absolute, canonical path name of the file. File contents are <em>read lazily</em> and contents
 * optionally <em>cached</em>. <br>
 * See {@link Source#fromFileName(String)}<br>
 * See {@link Source#fromFileName(String, boolean)}<br>
 * See {@link Source#find(String)}</li>
 * <p>
 * <li><strong>URL:</strong> Each URL source is represented as a canonical object, indexed by the
 * URL. Contents are <em>read eagerly</em> and <em>cached</em>. <br>
 * See {@link Source#fromURL(URL, String)}<br>
 * See {@link Source#find(String)}</li>
 * <p>
 * <li><strong>Reader:</strong> Contents are <em>read eagerly</em> and treated as an anonymous
 * (non-indexed) <em>Literal</em> . <br>
 * See {@link Source#fromReader(Reader, String)}</li>
 * <p>
 * <li><strong>Sub-Source:</strong> A representation of the contents of a sub-range of another
 * {@link Source}.<br>
 * See {@link Source#subSource(Source, int, int)}<br>
 * See {@link Source#subSource(Source, int)}</li>
 * <p>
 * <li><strong>AppendableSource:</strong> Literal contents are provided by the client,
 * incrementally, after the instance is created.<br>
 * See {@link Source#fromAppendableText(String)}<br>
 * See {@link Source#fromNamedAppendableText(String)}</li>
 * </ul>
 * <p>
 * <strong>File cache:</strong>
 * <ol>
 * <li>File content caching is optional, <em>on</em> by default.</li>
 * <li>The first access to source file contents will result in the contents being read, and (if
 * enabled) cached.</li>
 * <li>If file contents have been cached, access to contents via {@link Source#getInputStream()} or
 * {@link Source#getReader()} will be provided from the cache.</li>
 * <li>Any access to file contents via the cache will result in a timestamp check and possible cache
 * reload.</li>
 * </ol>
 * <p>
 */
public abstract class Source {
    private static final Logger LOG = Logger.getLogger(Source.class.getName());

    // TODO (mlvdv) consider canonicalizing and reusing SourceSection instances
    // TODO (mlvdv) connect SourceSections into a spatial tree for fast geometric lookup

    /**
     * Index of all named sources.
     */
    private static final Map<String, WeakReference<Source>> nameToSource = new HashMap<>();

    private static boolean fileCacheEnabled = true;

    /**
     * Locates an existing instance by the name under which it was indexed.
     */
    public static Source find(String name) {
        final WeakReference<Source> nameRef = nameToSource.get(name);
        return nameRef == null ? null : nameRef.get();
    }

    /**
     * Gets the canonical representation of a source file, whose contents will be read lazily and
     * then cached.
     *
     * @param fileName name
     * @param reset forces any existing {@link Source} cache to be cleared, forcing a re-read
     * @return canonical representation of the file's contents.
     * @throws IOException if the file can not be read
     */
    public static Source fromFileName(String fileName, boolean reset) throws IOException {

        final WeakReference<Source> nameRef = nameToSource.get(fileName);
        Source source = nameRef == null ? null : nameRef.get();
        if (source == null) {
            final File file = new File(fileName);
            if (!file.canRead()) {
                throw new IOException("Can't read file " + fileName);
            }
            final String path = file.getCanonicalPath();
            final WeakReference<Source> pathRef = nameToSource.get(path);
            source = pathRef == null ? null : pathRef.get();
            if (source == null) {
                source = new FileSource(file, fileName, path);
                nameToSource.put(path, new WeakReference<>(source));
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
     *
     * @param fileName name
     * @return canonical representation of the file's contents.
     * @throws IOException if the file can not be read
     */
    public static Source fromFileName(String fileName) throws IOException {
        return fromFileName(fileName, false);
    }

    /**
     * Gets the canonical representation of a source file whose contents are the responsibility of
     * the client:
     * <ul>
     * <li>If no Source exists corresponding to the provided file name, then a new Source is created
     * whose contents are those provided. It is confirmed that the file resolves to a file name, so
     * it can be indexed by canonical path. However there is no confirmation that the text supplied
     * agrees with the file's contents or even whether the file is readable.</li>
     * <li>If a Source exists corresponding to the provided file name, and that Source was created
     * originally by this method, then that Source will be returned after replacement of its
     * contents with no further confirmation.</li>
     * <li>If a Source exists corresponding to the provided file name, and that Source was not
     * created originally by this method, then an exception will be raised.</li>
     * </ul>
     *
     * @param chars textual source code already read from the file, must not be null
     * @param fileName
     * @return canonical representation of the file's contents.
     * @throws IOException if the file cannot be found, or if an existing Source not created by this
     *             method matches the file name
     */
    public static Source fromFileName(CharSequence chars, String fileName) throws IOException {
        CompilerAsserts.neverPartOfCompilation();
        assert chars != null;

        final WeakReference<Source> nameRef = nameToSource.get(fileName);
        Source source = nameRef == null ? null : nameRef.get();
        if (source == null) {
            final File file = new File(fileName);
            // We are going to trust that the fileName is readable.
            final String path = file.getCanonicalPath();
            final WeakReference<Source> pathRef = nameToSource.get(path);
            source = pathRef == null ? null : pathRef.get();
            if (source == null) {
                source = new ClientManagedFileSource(file, fileName, path, chars);
                nameToSource.put(path, new WeakReference<>(source));
            }
        } else if (source instanceof ClientManagedFileSource) {
            final ClientManagedFileSource modifiableSource = (ClientManagedFileSource) source;
            modifiableSource.setCode(chars);
            return modifiableSource;
        } else {
            throw new IOException("Attempt to modify contents of a file Source");
        }
        return source;
    }

    /**
     * Creates an anonymous source from literal text: not named and not indexed.
     *
     * @param chars textual source code
     * @param description a note about the origin, for error messages and debugging
     * @return a newly created, non-indexed source representation
     */
    public static Source fromText(CharSequence chars, String description) {
        CompilerAsserts.neverPartOfCompilation();
        return new LiteralSource(description, chars.toString());
    }

    /**
     * Creates an anonymous source from literal text that is provided incrementally after creation:
     * not named and not indexed.
     *
     * @param description a note about the origin, for error messages and debugging
     * @return a newly created, non-indexed, initially empty, appendable source representation
     */
    public static Source fromAppendableText(String description) {
        CompilerAsserts.neverPartOfCompilation();
        return new AppendableLiteralSource(description);
    }

    /**
     * Creates a source from literal text that can be retrieved by name, with no assumptions about
     * the structure or meaning of the name. If the name is already in the index, the new instance
     * will replace the previously existing instance in the index.
     *
     * @param chars textual source code
     * @param name string to use for indexing/lookup
     * @return a newly created, source representation
     */
    public static Source fromNamedText(CharSequence chars, String name) {
        CompilerAsserts.neverPartOfCompilation();
        final Source source = new LiteralSource(name, chars.toString());
        nameToSource.put(name, new WeakReference<>(source));
        return source;
    }

    /**
     * Creates a source from literal text that is provided incrementally after creation and which
     * can be retrieved by name, with no assumptions about the structure or meaning of the name. If
     * the name is already in the index, the new instance will replace the previously existing
     * instance in the index.
     *
     * @param name string to use for indexing/lookup
     * @return a newly created, indexed, initially empty, appendable source representation
     */
    public static Source fromNamedAppendableText(String name) {
        CompilerAsserts.neverPartOfCompilation();
        final Source source = new AppendableLiteralSource(name);
        nameToSource.put(name, new WeakReference<>(source));
        return source;
    }

    /**
     * Creates a {@linkplain Source Source instance} that represents the contents of a sub-range of
     * an existing {@link Source}.
     *
     * @param base an existing Source instance
     * @param baseCharIndex 0-based index of the first character of the sub-range
     * @param length the number of characters in the sub-range
     * @return a new instance representing a sub-range of another Source
     * @throws IllegalArgumentException if the specified sub-range is not contained in the base
     */
    public static Source subSource(Source base, int baseCharIndex, int length) {
        CompilerAsserts.neverPartOfCompilation();
        final SubSource subSource = SubSource.create(base, baseCharIndex, length);
        return subSource;
    }

    /**
     * Creates a {@linkplain Source Source instance} that represents the contents of a sub-range at
     * the end of an existing {@link Source}.
     *
     * @param base an existing Source instance
     * @param baseCharIndex 0-based index of the first character of the sub-range
     * @return a new instance representing a sub-range at the end of another Source
     * @throws IllegalArgumentException if the index is out of range
     */
    public static Source subSource(Source base, int baseCharIndex) {
        CompilerAsserts.neverPartOfCompilation();

        return subSource(base, baseCharIndex, base.getLength() - baseCharIndex);
    }

    /**
     * Creates a source whose contents will be read immediately from a URL and cached.
     *
     * @param url
     * @param description identifies the origin, possibly useful for debugging
     * @return a newly created, non-indexed source representation
     * @throws IOException if reading fails
     */
    public static Source fromURL(URL url, String description) throws IOException {
        CompilerAsserts.neverPartOfCompilation();
        return URLSource.get(url, description);
    }

    /**
     * Creates a source whose contents will be read immediately and cached.
     *
     * @param reader
     * @param description a note about the origin, possibly useful for debugging
     * @return a newly created, non-indexed source representation
     * @throws IOException if reading fails
     */
    public static Source fromReader(Reader reader, String description) throws IOException {
        CompilerAsserts.neverPartOfCompilation();
        return new LiteralSource(description, read(reader));
    }

    /**
     * Creates a source from raw bytes. This can be used if the encoding of strings in your language
     * is not compatible with Java strings, or if your parser returns byte indices instead of
     * character indices. The returned source is then indexed by byte, not by character.
     *
     * @param bytes the raw bytes of the source
     * @param description a note about the origin, possibly useful for debugging
     * @param charset how to decode the bytes into Java strings
     * @return a newly created, non-indexed source representation
     */
    public static Source fromBytes(byte[] bytes, String description, Charset charset) {
        return fromBytes(bytes, 0, bytes.length, description, charset);
    }

    /**
     * Creates a source from raw bytes. This can be used if the encoding of strings in your language
     * is not compatible with Java strings, or if your parser returns byte indices instead of
     * character indices. The returned source is then indexed by byte, not by character. Offsets are
     * relative to byteIndex.
     *
     * @param bytes the raw bytes of the source
     * @param byteIndex where the string starts in the byte array
     * @param length the length of the string in the byte array
     * @param description a note about the origin, possibly useful for debugging
     * @param charset how to decode the bytes into Java strings
     * @return a newly created, non-indexed source representation
     */
    public static Source fromBytes(byte[] bytes, int byteIndex, int length, String description, Charset charset) {
        CompilerAsserts.neverPartOfCompilation();
        return new BytesSource(description, bytes, byteIndex, length, charset);
    }

    // TODO (mlvdv) enable per-file choice whether to cache?
    /**
     * Enables/disables caching of file contents, <em>disabled</em> by default. Caching of sources
     * created from literal text or readers is always enabled.
     */
    public static void setFileCaching(boolean enabled) {
        fileCacheEnabled = enabled;
    }

    private static String read(Reader reader) throws IOException {
        final BufferedReader bufferedReader = new BufferedReader(reader);
        final StringBuilder builder = new StringBuilder();
        final char[] buffer = new char[1024];

        try {
            while (true) {
                final int n = bufferedReader.read(buffer);
                if (n == -1) {
                    break;
                }
                builder.append(buffer, 0, n);
            }
        } finally {
            bufferedReader.close();
        }
        return builder.toString();
    }

    private Source() {
    }

    private String mimeType;
    private TextMap textMap;

    abstract void reset();

    /**
     * Returns the name of this resource holding a guest language program. An example would be the
     * name of a guest language source code file.
     *
     * @return the name of the guest language program
     */
    public abstract String getName();

    /**
     * Returns a short version of the name of the resource holding a guest language program (as
     * described in {@link #getName()}). For example, this could be just the name of the file,
     * rather than a full path.
     *
     * @return the short name of the guest language program
     */
    public abstract String getShortName();

    /**
     * The normalized, canonical name if the source is a file.
     */
    public abstract String getPath();

    /**
     * The URL if the source is retrieved via URL.
     *
     * @return URL or <code>null</code>
     */
    public abstract URL getURL();

    /**
     * Access to the source contents.
     */
    public abstract Reader getReader();

    /**
     * Access to the source contents.
     */
    public final InputStream getInputStream() {
        return new ByteArrayInputStream(getCode().getBytes());
    }

    /**
     * Gets the number of characters in the source.
     */
    public final int getLength() {
        return getTextMap().length();
    }

    /**
     * Returns the complete text of the code.
     */
    public abstract String getCode();

    /**
     * Returns a subsection of the code test.
     */
    public String getCode(int charIndex, int charLength) {
        return getCode().substring(charIndex, charIndex + charLength);
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     */
    public final String getCode(int lineNumber) {
        final int offset = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        return getCode().substring(offset, offset + length);
    }

    /**
     * The number of text lines in the source, including empty lines; characters at the end of the
     * source without a terminating newline count as a line.
     */
    public final int getLineCount() {
        return getTextMap().lineCount();
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     */
    public final int getLineNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToLine(offset);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the column at the position.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     */
    public final int getColumnNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToCol(offset);
    }

    /**
     * Given a 1-based line number, return the 0-based offset of the first character in the line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     */
    public final int getLineStartOffset(int lineNumber) throws IllegalArgumentException {
        return getTextMap().lineStartOffset(lineNumber);
    }

    /**
     * The number of characters (not counting a possible terminating newline) in a (1-based)
     * numbered line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     */
    public final int getLineLength(int lineNumber) throws IllegalArgumentException {
        return getTextMap().lineLength(lineNumber);
    }

    /**
     * Append text to a Source explicitly created as <em>Appendable</em>.
     *
     * @param chars the text to append
     * @throws UnsupportedOperationException by concrete subclasses that do not support appending
     */
    public void appendCode(CharSequence chars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a representation of a contiguous region of text in the source.
     * <p>
     * This method performs no checks on the validity of the arguments.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     * @param identifier terse description of the region
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section
     * @param charIndex the 0-based index of the first character of the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     */
    public final SourceSection createSection(String identifier, int startLine, int startColumn, int charIndex, int length) {
        checkRange(charIndex, length);
        return createSectionImpl(identifier, startLine, startColumn, charIndex, length, SourceSection.EMTPY_TAGS);
    }

    /**
     * Creates a representation of a contiguous region of text in the source.
     * <p>
     * This method performs no checks on the validity of the arguments.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     * @param identifier terse description of the region
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section
     * @param charIndex the 0-based index of the first character of the section
     * @param length the number of characters in the section
     * @param tags the tags associated with this section. Tags must be non-null and
     *            {@link String#intern() interned}.
     * @return newly created object representing the specified region
     */
    public final SourceSection createSection(String identifier, int startLine, int startColumn, int charIndex, int length, String... tags) {
        checkRange(charIndex, length);
        return createSectionImpl(identifier, startLine, startColumn, charIndex, length, tags);
    }

    private SourceSection createSectionImpl(String identifier, int startLine, int startColumn, int charIndex, int length, String[] tags) {
        return new SourceSection(null, this, identifier, startLine, startColumn, charIndex, length, tags);
    }

    /**
     * Creates a representation of a contiguous region of text in the source. Computes the
     * {@code charIndex} value by building a {@code TextMap map} of lines in the source.
     * <p>
     * Checks the position arguments for consistency with the source.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     * @param identifier terse description of the region
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @throws IllegalArgumentException if arguments are outside the text of the source
     * @throws IllegalStateException if the source is one of the "null" instances
     */
    public final SourceSection createSection(String identifier, int startLine, int startColumn, int length) {
        final int lineStartOffset = getTextMap().lineStartOffset(startLine);
        if (startColumn > getTextMap().lineLength(startLine)) {
            throw new IllegalArgumentException("column out of range");
        }
        final int startOffset = lineStartOffset + startColumn - 1;
        return createSectionImpl(identifier, startLine, startColumn, startOffset, length, SourceSection.EMTPY_TAGS);
    }

    /**
     * Creates a representation of a contiguous region of text in the source. Computes the
     * {@code (startLine, startColumn)} values by building a {@code TextMap map} of lines in the
     * source.
     * <p>
     * Checks the position arguments for consistency with the source.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     *
     * @param identifier terse description of the region
     * @param charIndex 0-based position of the first character in the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @throws IllegalArgumentException if either of the arguments are outside the text of the
     *             source
     * @throws IllegalStateException if the source is one of the "null" instances
     */
    public final SourceSection createSection(String identifier, int charIndex, int length) throws IllegalArgumentException {
        return createSection(identifier, charIndex, length, SourceSection.EMTPY_TAGS);
    }

    /**
     * Creates a representation of a contiguous region of text in the source. Computes the
     * {@code (startLine, startColumn)} values by building a {@code TextMap map} of lines in the
     * source.
     * <p>
     * Checks the position arguments for consistency with the source.
     * <p>
     * The resulting representation defines hash/equality around equivalent location, presuming that
     * {@link Source} representations are canonical.
     *
     *
     * @param identifier terse description of the region
     * @param charIndex 0-based position of the first character in the section
     * @param length the number of characters in the section
     * @param tags the tags associated with this section. Tags must be non-null and
     *            {@link String#intern() interned}.
     * @return newly created object representing the specified region
     * @throws IllegalArgumentException if either of the arguments are outside the text of the
     *             source
     * @throws IllegalStateException if the source is one of the "null" instances
     */
    public final SourceSection createSection(String identifier, int charIndex, int length, String... tags) throws IllegalArgumentException {
        checkRange(charIndex, length);
        final int startLine = getLineNumber(charIndex);
        final int startColumn = charIndex - getLineStartOffset(startLine) + 1;
        return createSectionImpl(identifier, startLine, startColumn, charIndex, length, tags);
    }

    void checkRange(int charIndex, int length) {
        if (!(charIndex >= 0 && length >= 0 && charIndex + length <= getCode().length())) {
            throw new IllegalArgumentException("text positions out of range");
        }
    }

    /**
     * Creates a representation of a line of text in the source identified only by line number, from
     * which the character information will be computed.
     *
     * @param identifier terse description of the line
     * @param lineNumber 1-based line number of the first character in the section
     * @return newly created object representing the specified line
     * @throws IllegalArgumentException if the line does not exist the source
     * @throws IllegalStateException if the source is one of the "null" instances
     */
    public final SourceSection createSection(String identifier, int lineNumber) {
        final int charIndex = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        return createSection(identifier, charIndex, length);
    }

    /**
     * Creates a representation of a line number in this source, suitable for use as a hash table
     * key with equality defined to mean equivalent location.
     *
     * @param lineNumber a 1-based line number in this source
     * @return a representation of a line in this source
     */
    public final LineLocation createLineLocation(int lineNumber) {
        return new LineLocation(this, lineNumber);
    }

    /**
     * An object suitable for using as a key into a hashtable that defines equivalence between
     * different source types.
     */
    Object getHashKey() {
        return getName();
    }

    final TextMap getTextMap() {
        if (textMap == null) {
            textMap = createTextMap();
        }
        return textMap;
    }

    final void clearTextMap() {
        textMap = null;
    }

    TextMap createTextMap() {
        final String code = getCode();
        if (code == null) {
            throw new RuntimeException("can't read file " + getName());
        }
        return TextMap.fromString(code);
    }

    /**
     * Associates the source with specified MIME type. The mime type may be used to select the right
     * {@link Registration Truffle language} to use to execute the returned source. The value of
     * MIME type can be obtained via {@link #getMimeType()} method.
     *
     * @param mime mime type to use
     * @return new (identical) source, just associated {@link #getMimeType()}
     */
    public final Source withMimeType(String mime) {
        try {
            Source another = (Source) clone();
            another.mimeType = mime;
            return another;
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * MIME type that is associated with this source. By default file extensions known to the system
     * are used to determine the MIME type (via registered {@link FileTypeDetector} classes), yet
     * one can directly {@link #withMimeType(java.lang.String) provide a MIME type} to each source.
     *
     * @return MIME type of this source or <code>null</code>, if unknown
     */
    public String getMimeType() {
        if (mimeType == null) {
            mimeType = findMimeType();
        }
        return mimeType;
    }

    String findMimeType() {
        return null;
    }

    final boolean equalMime(Source other) {
        if (mimeType == null) {
            return other.mimeType == null;
        }
        return mimeType.equals(other.mimeType);
    }

    private static final class LiteralSource extends Source implements Cloneable {

        private final String description;
        private final String code;

        public LiteralSource(String description, String code) {
            this.description = description;
            this.code = code;
        }

        @Override
        public String getName() {
            return description;
        }

        @Override
        public String getShortName() {
            return description;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getPath() {
            return null;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            return new StringReader(code);
        }

        @Override
        void reset() {
        }

        @Override
        public int hashCode() {
            return Objects.hash(description, code);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (obj instanceof LiteralSource) {
                LiteralSource other = (LiteralSource) obj;
                return Objects.equals(description, other.description) && code.equals(other.code) && equalMime(other);
            }
            return false;
        }
    }

    private static final class AppendableLiteralSource extends Source implements Cloneable {
        private final String description;
        final List<CharSequence> codeList = new ArrayList<>();

        public AppendableLiteralSource(String description) {
            this.description = description;
        }

        @Override
        public String getName() {
            return description;
        }

        @Override
        public String getShortName() {
            return description;
        }

        @Override
        public String getCode() {
            return getCodeFromIndex(0);
        }

        @Override
        public String getPath() {
            return description;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            return new StringReader(getCode());
        }

        @Override
        void reset() {
        }

        private String getCodeFromIndex(int index) {
            StringBuilder sb = new StringBuilder();
            for (int i = index; i < codeList.size(); i++) {
                CharSequence s = codeList.get(i);
                sb.append(s);
            }
            return sb.toString();
        }

        @Override
        public void appendCode(CharSequence chars) {
            codeList.add(chars);
            clearTextMap();
        }

    }

    private static final class FileSource extends Source implements Cloneable {

        private final File file;
        private final String name; // Name used originally to describe the source
        private final String path;  // Normalized path description of an actual file

        private String code = null;  // A cache of the file's contents

        public FileSource(File file, String name, String path) {
            this.file = file.getAbsoluteFile();
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
        Object getHashKey() {
            return path;
        }

        @Override
        public String getCode() {
            if (fileCacheEnabled) {
                if (code == null) {
                    try {
                        code = read(getReader());
                    } catch (IOException e) {
                    }
                }
                return code;
            }
            try {
                return read(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            } catch (IOException e) {
            }
            return null;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            if (code != null) {
                return new StringReader(code);
            }
            try {
                return new InputStreamReader(new FileInputStream(file), "UTF-8");
            } catch (FileNotFoundException e) {

                throw new RuntimeException("Can't find file " + path, e);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Unsupported encoding in file " + path, e);
            }
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        String findMimeType() {
            try {
                return Files.probeContentType(file.toPath());
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
            return null;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof FileSource) {
                FileSource other = (FileSource) obj;
                return path.equals(other.path) && equalMime(other);
            }
            return false;
        }

        @Override
        void reset() {
            this.code = null;
        }
    }

    // TODO (mlvdv) if we keep this, hoist a superclass in common with FileSource.
    private static final class ClientManagedFileSource extends Source implements Cloneable {

        private final File file;
        private final String name; // Name used originally to describe the source
        private final String path;  // Normalized path description of an actual file
        private String code;  // The file's contents, as provided by the client

        public ClientManagedFileSource(File file, String name, String path, CharSequence chars) {
            this.file = file.getAbsoluteFile();
            this.name = name;
            this.path = path;
            setCode(chars);
        }

        void setCode(CharSequence chars) {
            this.code = chars.toString();
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
        Object getHashKey() {
            return path;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            return new StringReader(code);
        }

        @Override
        String findMimeType() {
            try {
                return Files.probeContentType(file.toPath());
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
            return null;
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof ClientManagedFileSource) {
                ClientManagedFileSource other = (ClientManagedFileSource) obj;
                return path.equals(other.path) && equalMime(other);
            }
            return false;
        }

        @Override
        void reset() {
            this.code = null;
        }
    }

    private static final class URLSource extends Source implements Cloneable {

        private static final Map<URL, WeakReference<URLSource>> urlToSource = new HashMap<>();

        public static URLSource get(URL url, String name) throws IOException {
            WeakReference<URLSource> sourceRef = urlToSource.get(url);
            URLSource source = sourceRef == null ? null : sourceRef.get();
            if (source == null) {
                source = new URLSource(url, name);
                urlToSource.put(url, new WeakReference<>(source));
            }
            return source;
        }

        private final URL url;
        private final String name;
        private String code;  // A cache of the source contents

        public URLSource(URL url, String name) throws IOException {
            this.url = url;
            this.name = name;
            URLConnection c = url.openConnection();
            if (super.mimeType == null) {
                super.mimeType = c.getContentType();
            }
            code = read(new InputStreamReader(c.getInputStream()));
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
        public String getPath() {
            return url.getPath();
        }

        @Override
        public URL getURL() {
            return url;
        }

        @Override
        public Reader getReader() {
            return new StringReader(code);
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        void reset() {
        }
    }

    private static final class SubSource extends Source implements Cloneable {
        private final Source base;
        private final int baseIndex;
        private final int subLength;

        private static SubSource create(Source base, int baseIndex, int length) {
            if (baseIndex < 0 || length < 0 || baseIndex + length > base.getLength()) {
                throw new IllegalArgumentException("text positions out of range");
            }
            return new SubSource(base, baseIndex, length);
        }

        private SubSource(Source base, int baseIndex, int length) {
            this.base = base;
            this.baseIndex = baseIndex;
            this.subLength = length;
        }

        @Override
        void reset() {
            assert false;
        }

        @Override
        public String getName() {
            return base.getName();
        }

        @Override
        public String getShortName() {
            return base.getShortName();
        }

        @Override
        public String getPath() {
            return base.getPath();
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            assert false;
            return null;
        }

        @Override
        public String getCode() {
            return base.getCode(baseIndex, subLength);
        }
    }

    private static final class BytesSource extends Source implements Cloneable {

        private final String name;
        private final byte[] bytes;
        private final int byteIndex;
        private final int length;
        private final CharsetDecoder decoder;

        public BytesSource(String name, byte[] bytes, int byteIndex, int length, Charset decoder) {
            this.name = name;
            this.bytes = bytes;
            this.byteIndex = byteIndex;
            this.length = length;
            this.decoder = decoder.newDecoder();
        }

        @Override
        void reset() {
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
        public String getPath() {
            return name;
        }

        @Override
        public URL getURL() {
            return null;
        }

        @Override
        public Reader getReader() {
            return null;
        }

        @Override
        public String getCode() {
            ByteBuffer bb = ByteBuffer.wrap(bytes, byteIndex, length);
            CharBuffer chb;
            try {
                chb = decoder.decode(bb);
            } catch (CharacterCodingException ex) {
                return "";
            }
            return chb.toString();
        }

        @Override
        public String getCode(int byteOffset, int codeLength) {
            ByteBuffer bb = ByteBuffer.wrap(bytes, byteIndex + byteOffset, codeLength);
            CharBuffer chb;
            try {
                chb = decoder.decode(bb);
            } catch (CharacterCodingException ex) {
                return "";
            }
            return chb.toString();
        }

        @Override
        void checkRange(int charIndex, int rangeLength) {
            if (!(charIndex >= 0 && rangeLength >= 0 && charIndex + rangeLength <= length)) {
                throw new IllegalArgumentException("text positions out of range");
            }
        }

        @Override
        TextMap createTextMap() {
            return TextMap.fromString(getCode());
        }
    }

    /**
     * A utility for converting between coordinate systems in a string of text interspersed with
     * newline characters. The coordinate systems are:
     * <ul>
     * <li>0-based character offset from the beginning of the text, where newline characters count
     * as a single character and the first character in the text occupies position 0.</li>
     * <li>1-based position in the 2D space of lines and columns, in which the first position in the
     * text is at (1,1).</li>
     * </ul>
     * <p>
     * This utility is based on positions occupied by characters, not text stream positions as in a
     * text editor. The distinction shows up in editors where you can put the cursor just past the
     * last character in a buffer; this is necessary, among other reasons, so that you can put the
     * edit cursor in a new (empty) buffer. For the purposes of this utility, however, there are no
     * character positions in an empty text string and there are no lines in an empty text string.
     * <p>
     * A newline character designates the end of a line and occupies a column position.
     * <p>
     * If the text ends with a character other than a newline, then the characters following the
     * final newline character count as a line, even though not newline-terminated.
     * <p>
     * <strong>Limitations:</strong>
     * <ul>
     * <li>Does not handle multiple character encodings correctly.</li>
     * <li>Treats tabs as occupying 1 column.</li>
     * <li>Does not handle multiple-character line termination sequences correctly.</li>
     * </ul>
     */
    private static final class TextMap {

        // 0-based offsets of newline characters in the text, with sentinel
        private final int[] nlOffsets;

        // The number of characters in the text, including newlines (which count as 1).
        private final int textLength;

        // Is the final text character a newline?
        final boolean finalNL;

        public TextMap(int[] nlOffsets, int textLength, boolean finalNL) {
            this.nlOffsets = nlOffsets;
            this.textLength = textLength;
            this.finalNL = finalNL;
        }

        /**
         * Constructs map permitting translation between 0-based character offsets and 1-based
         * lines/columns.
         */
        public static TextMap fromString(String text) {
            final int textLength = text.length();
            final ArrayList<Integer> lines = new ArrayList<>();
            lines.add(0);
            int offset = 0;

            while (offset < text.length()) {
                final int nlIndex = text.indexOf('\n', offset);
                if (nlIndex >= 0) {
                    offset = nlIndex + 1;
                    lines.add(offset);
                } else {
                    break;
                }
            }
            lines.add(Integer.MAX_VALUE);

            final int[] nlOffsets = new int[lines.size()];
            for (int line = 0; line < lines.size(); line++) {
                nlOffsets[line] = lines.get(line);
            }

            final boolean finalNL = textLength > 0 && (textLength == nlOffsets[nlOffsets.length - 2]);

            return new TextMap(nlOffsets, textLength, finalNL);
        }

        /**
         * Converts 0-based character offset to 1-based number of the line containing the character.
         *
         * @throws IllegalArgumentException if the offset is outside the string.
         */
        public int offsetToLine(int offset) throws IllegalArgumentException {
            if (offset < 0 || offset >= textLength) {
                if (offset == 0 && textLength == 0) {
                    return 1;
                }
                throw new IllegalArgumentException("offset out of bounds");
            }
            int line = 1;
            while (offset >= nlOffsets[line]) {
                line++;
            }
            return line;
        }

        /**
         * Converts 0-based character offset to 1-based number of the column occupied by the
         * character.
         * <p>
         * Tabs are not expanded; they occupy 1 column.
         *
         * @throws IllegalArgumentException if the offset is outside the string.
         */
        public int offsetToCol(int offset) throws IllegalArgumentException {
            return 1 + offset - nlOffsets[offsetToLine(offset) - 1];
        }

        /**
         * The number of characters in the mapped text.
         */
        public int length() {
            return textLength;
        }

        /**
         * The number of lines in the text; if characters appear after the final newline, then they
         * also count as a line, even though not newline-terminated.
         */
        public int lineCount() {
            if (textLength == 0) {
                return 0;
            }
            return finalNL ? nlOffsets.length - 2 : nlOffsets.length - 1;
        }

        /**
         * Converts 1-based line number to the 0-based offset of the line's first character; this
         * would be the offset of a newline if the line is empty.
         *
         * @throws IllegalArgumentException if there is no such line in the text.
         */
        public int lineStartOffset(int line) throws IllegalArgumentException {
            if (textLength == 0) {
                return 0;
            }
            if (lineOutOfRange(line)) {
                throw new IllegalArgumentException("line out of bounds");
            }
            return nlOffsets[line - 1];
        }

        /**
         * Gets the number of characters in a line, identified by 1-based line number;
         * <em>does not</em> include the final newline, if any.
         *
         * @throws IllegalArgumentException if there is no such line in the text.
         */
        public int lineLength(int line) throws IllegalArgumentException {
            if (textLength == 0) {
                return 0;
            }
            if (lineOutOfRange(line)) {
                throw new IllegalArgumentException("line out of bounds");
            }
            if (line == nlOffsets.length - 1 && !finalNL) {
                return textLength - nlOffsets[line - 1];
            }
            return (nlOffsets[line] - nlOffsets[line - 1]) - 1;

        }

        /**
         * Is the line number out of range.
         */
        private boolean lineOutOfRange(int line) {
            return line <= 0 || line >= nlOffsets.length || (line == nlOffsets.length - 1 && finalNL);
        }

    }

}
