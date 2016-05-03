/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.spi.FileTypeDetector;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.Node;
import java.io.InputStreamReader;
import java.util.Objects;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Representation of a source code unit and its contents. Source instances are created by using one
 * of existing factory methods, each loading the file from a different source/medium.
 *
 * <h3>From a file on disk</h3>
 *
 * Each file is represented as a canonical object, indexed by the absolute, canonical path name of
 * the file. File content is <em>read lazily</em> and may be optionally <em>cached</em>. Sample
 * usage: <br>
 *
 * {@link SourceSnippets#fromFile}
 *
 * Methods of interest are {@link Source#fromFileName(String)},
 * {@link Source#fromFileName(String, boolean)}, and {@link Source#find(String)}.
 *
 * <h3>Read from an URL</h3>
 *
 * One can read remote or in JAR resources using the {@link Source#fromURL(URL, String)} factory:
 * <br>
 *
 * {@link SourceSnippets#fromURL}
 *
 * Each URL source is represented as a canonical object, indexed by the URL. Contents are
 * <em>read eagerly</em> and <em>cached</em> locally.
 *
 * <h3>Source from a literal text</h3>
 *
 * An anonymous immutable code snippet can be created from a string via the
 * {@link Source#fromText(CharSequence, String)} factory method: <br>
 *
 * {@link SourceSnippets#fromAString}
 *
 * the created {@link Source} doesn't have associated {@link #getMimeType() mime type}. One has to
 * additionally attach one to it via {@link #withMimeType(java.lang.String)} method.
 *
 * <h3>Reading from a stream</h3>
 *
 * If one has a {@link Reader} one can convert its content into a {@link Source} via
 * {@link Source#fromReader(Reader, String)} method: <br>
 *
 * {@link SourceSnippets#fromReader}
 *
 * the content is <em>read eagerly</em> and it doesn't have associated {@link #getMimeType() mime
 * type}. One has to additionally attach one to it via {@link #withMimeType(java.lang.String)}
 * method.
 *
 * <!-- <strong>Sub-Source:</strong> A representation of the contents of a sub-range of another
 * {@link Source}.<br>
 * See {@link Source#subSource(Source, int, int)}<br>
 * See {@link Source#subSource(Source, int)}
 * <p>
 * <strong>AppendableSource:</strong> Literal contents are provided by the client, incrementally,
 * after the instance is created.<br>
 * See {@link Source#fromAppendableText(String)}<br>
 * -->
 *
 * <h2>Immutability of {@link Source}</h2>
 *
 * <p>
 * {@link Source} is an immutable object - once (lazily) loaded, it remains the same. The source
 * object can be associated with various attributes like {@link #getName()}, {@link #getShortName()}
 * , {@link #getPath()}, {@link #getMimeType()} and these are immutable as well. The system makes
 * the best effort to derive values of these attributes from the location and/or content of the
 * {@link Source} object. However, to give the user that creates the source control over these
 * attributes, the API offers an easy way to alter values of these attributes by creating clones of
 * the source via {@link #withMimeType(java.lang.String)}, {@link #withName(java.lang.String)},
 * {@link #withPath(java.lang.String)} or {@link #withShortName(java.lang.String)} methods.
 * </p>
 * <p>
 * While {@link Source} is immutable, the world around it is changing. The content of a file from
 * which a {@link Source#fromFileName(java.lang.String) source has been read} may change few seconds
 * later. How can we balance the immutability with ability to see real state of the world? In this
 * case, one can request a reload of a new version of the
 * {@link Source#fromFileName(java.lang.String, boolean) source for the same file}. The newly loaded
 * {@link Source} will be different than the previous one, however it will have the same attributes
 * ({@link #getName()}, presumably also {@link #getMimeType()}, etc.). There isn't much to do about
 * this - just keep in mind that there can be multiple different {@link Source} objects representing
 * the same source origin.
 * </p>
 *
 * @since 0.8 or earlier
 */
public abstract class Source {
    // TODO (mlvdv) consider canonicalizing and reusing SourceSection instances
    // TODO (mlvdv) connect SourceSections into a spatial tree for fast geometric lookup

    static boolean fileCacheEnabled = true;

    private static final String NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE = "do not create sub sources from compiled code";

    private final Content content;
    private String name;
    private String shortName;
    private String path;
    private String mimeType;
    private TextMap textMap;

    /**
     * Locates an existing instance of a {@link Source} with given {@link #getName() name}.
     *
     * @param name the {@link #getName() name} of a source to seek for
     * @return found source or <code>null</code> if no source with name is known
     * @since 0.8 or earlier
     */
    public static Source find(String name) {
        return SourceImpl.findSource(name);
    }

    /**
     * Gets the canonical representation of a source file, whose contents will be read lazily and
     * then cached. The {@link #getShortName() short name} of the source is equal to
     * {@link File#getName() name of the file}. The {@link #getName() name} of the file is exactly
     * the provided <code>fileName</code> string. The {@link #getPath() path} is
     * {@link File#getCanonicalPath() canonical path} of the provided file name.
     *
     * @param fileName path to the file with the source
     * @param reload forces any existing {@link Source} cache to be cleared, forcing a re-read - as
     *            a result the newly returned {@link Source} may have content different to any
     *            previously loaded one, which is up-to-date with current state on the disk
     * @return source representing the file's content
     * @throws IOException if the file cannot be read
     * @since 0.8 or earlier
     */
    public static Source fromFileName(String fileName, boolean reload) throws IOException {
        if (!reload) {
            Source source = find(fileName);
            if (source != null && source.content() instanceof FileSourceImpl) {
                return source;
            }
        }
        final File file = new File(fileName);
        if (!file.canRead()) {
            throw new IOException("Can't read file " + fileName);
        }
        final String path = file.getCanonicalPath();
        final FileSourceImpl content = new FileSourceImpl(file, fileName, path);
        return new SourceImpl(content);
    }

    /**
     * Gets the canonical representation of a source file, whose contents will be read lazily and
     * then cached. The {@link #getShortName() short name} of the source is equal to
     * {@link File#getName() name of the file}. The {@link #getName() name} of the file is exactly
     * the provided <code>fileName</code> string. The {@link #getPath() path} is
     * {@link File#getCanonicalPath() canonical path} of the provided file name.
     *
     * @param fileName path to the file with the source
     * @return source representing the file's content
     * @throws IOException if the file cannot be read
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    public static Source fromFileName(CharSequence chars, String fileName) throws IOException {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromFileName from compiled code");
        assert chars != null;

        final File file = new File(fileName);
        // We are going to trust that the fileName is readable.
        final String path = file.getCanonicalPath();
        Content content = new ClientManagedFileSourceImpl(file, fileName, path, chars);
        Source source = new SourceImpl(content);
        return source;
    }

    /**
     * Creates an anonymous source from literal text. The {@link #getName() name} of the source is
     * <code>name</code>. The {@link #getShortName()} is also <code>name</code>, as well as
     * {@link #getPath() path}.
     *
     * @param chars textual source code
     * @param name a note about the origin, for error messages and debugging - used as
     *            {@link Source#getName()} and {@link Source#getShortName()}, the name can be
     *            <code>null</code>
     * @return a newly created, source representation
     * @since 0.8 or earlier
     */
    public static Source fromText(CharSequence chars, String name) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromText from compiled code");
        Content content = new LiteralSourceImpl(name, chars.toString());
        return new SourceImpl(content);
    }

    /**
     * Creates an anonymous source from literal text that is provided incrementally after creation.
     * The {@link #getName() name}, {@link #getShortName() short name} and {@link #getPath() path}
     * are set to <code>name</code>.
     *
     * @param name name for the newly created source
     * @return a newly created, non-indexed, initially empty, appendable source representation
     * @since 0.8 or earlier
     */
    public static Source fromAppendableText(String name) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromAppendableText from compiled code");
        Content content = new AppendableLiteralSourceImpl(name);
        return new SourceImpl(content);
    }

    /**
     * Creates a source from literal text that can be retrieved by name, with no assumptions about
     * the structure or meaning of the name. If the name is already in the index, the new instance
     * will replace the previously existing instance in the index.
     *
     * @param chars textual source code
     * @param name string to use for indexing/lookup
     * @return a newly created, source representation
     * @since 0.8 or earlier
     * @deprecated use {@link #fromText(java.lang.CharSequence, java.lang.String)}
     */
    @Deprecated
    public static Source fromNamedText(CharSequence chars, String name) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromNamedText from compiled code");
        Content content = new LiteralSourceImpl(name, chars.toString());
        final Source source = new SourceImpl(content);
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
     * @since 0.8 or earlier
     * @deprecated use {@link #fromAppendableText(java.lang.String)}
     */
    @Deprecated
    public static Source fromNamedAppendableText(String name) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromNamedAppendable from compiled code");
        final Content content = new AppendableLiteralSourceImpl(name);
        final Source source = new SourceImpl(content);
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
     * @since 0.8 or earlier
     */
    public static Source subSource(Source base, int baseCharIndex, int length) {
        CompilerAsserts.neverPartOfCompilation(NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE);
        final SubSourceImpl subSource = SubSourceImpl.create(base, baseCharIndex, length);
        return new SourceImpl(subSource);
    }

    /**
     * Creates a {@linkplain Source Source instance} that represents the contents of a sub-range at
     * the end of an existing {@link Source}.
     *
     * @param base an existing Source instance
     * @param baseCharIndex 0-based index of the first character of the sub-range
     * @return a new instance representing a sub-range at the end of another Source
     * @throws IllegalArgumentException if the index is out of range
     * @since 0.8 or earlier
     */
    public static Source subSource(Source base, int baseCharIndex) {
        CompilerAsserts.neverPartOfCompilation(NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE);

        return subSource(base, baseCharIndex, base.getLength() - baseCharIndex);
    }

    /**
     * Creates a source whose contents will be read immediately from a URL and cached.
     *
     * @param url
     * @param description identifies the origin, possibly useful for debugging
     * @return a newly created, non-indexed source representation
     * @throws IOException if reading fails
     * @since 0.8 or earlier
     */
    public static Source fromURL(URL url, String description) throws IOException {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromURL from compiled code");
        Content content = URLSourceImpl.get(url, description);
        return new SourceImpl(content);
    }

    /**
     * Creates a source whose contents will be read immediately and cached.
     *
     * @param reader
     * @param description a note about the origin, possibly useful for debugging
     * @return a newly created, non-indexed source representation
     * @throws IOException if reading fails
     * @since 0.8 or earlier
     */
    public static Source fromReader(Reader reader, String description) throws IOException {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromReader from compiled code");
        Content content = new LiteralSourceImpl(description, read(reader));
        return new SourceImpl(content);
    }

    /**
     * Creates a source from raw bytes. This can be used if your parser returns byte indices instead
     * of character indices. The returned source is however still indexed by character.
     *
     * The {@link #getName() name}, {@link #getShortName() short name} and {@link #getPath() path}
     * are set to value of <code>name</code>
     *
     * @param bytes the raw bytes of the source
     * @param name name of the created source
     * @param charset how to decode the bytes into Java strings
     * @return a newly created, non-indexed source representation
     * @since 0.8 or earlier
     */
    public static Source fromBytes(byte[] bytes, String name, Charset charset) {
        return fromBytes(bytes, 0, bytes.length, name, charset);
    }

    /**
     * Creates a source from raw bytes. This can be used if your parser returns byte indices instead
     * of character indices. The returned source is however still indexed by character. Offsets are
     * starting at byteIndex.
     *
     * The {@link #getName() name}, {@link #getShortName() short name} and {@link #getPath() path}
     * are set to value of <code>name</code>
     *
     * @param bytes the raw bytes of the source
     * @param byteIndex where the string starts in the byte array
     * @param length the length of bytes to use from the byte array
     * @param name name of the created source
     * @param charset how to decode the bytes into Java strings
     * @return a newly created, non-indexed source representation
     * @since 0.8 or earlier
     */
    public static Source fromBytes(byte[] bytes, int byteIndex, int length, String name, Charset charset) {
        CompilerAsserts.neverPartOfCompilation("do not call Source.fromBytes from compiled code");
        Content content = new BytesSourceImpl(name, bytes, byteIndex, length, charset);
        return new SourceImpl(content);
    }

    // TODO (mlvdv) enable per-file choice whether to cache?
    /**
     * Enables/disables caching of file contents, <em>disabled</em> by default. Caching of sources
     * created from literal text or readers is always enabled.
     *
     * @since 0.8 or earlier
     * @deprecated globally configurable caching is uncontrollable as it allows any piece of code to
     *             influence behavior of completely independent piece of code
     */
    @Deprecated
    public static void setFileCaching(boolean enabled) {
        fileCacheEnabled = enabled;
    }

    static String read(Reader reader) throws IOException {
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

    Source(Content content) {
        this.content = content;
    }

    Content content() {
        return content;
    }

    /**
     * Returns the name of this resource holding a guest language program. An example would be the
     * name of a guest language source code file. Name is supposed to be at least as long as
     * {@link #getShortName()} and as long, or shorter than {@link #getPath()}.
     *
     * One can change name for an existing source by creating its clone via
     * {@link #withName(java.lang.String)} method.
     *
     * @return the name of the guest language program
     * @since 0.8 or earlier
     */
    public String getName() {
        return name == null ? content().getName() : name;
    }

    /**
     * Returns a short version of the name of the resource holding a guest language program (as
     * described in {@link #getName()}). For example, this could be just the name of the file,
     * rather than a full path.
     *
     * One can change name for an existing source by creating its clone via
     * {@link #withShortName(java.lang.String)} method.
     *
     * @return the short name of the guest language program
     * @since 0.8 or earlier
     */
    public String getShortName() {
        return shortName == null ? content().getShortName() : shortName;
    }

    /**
     * The fully qualified name of the source. In case this source originates from a {@link File},
     * then the default path is the normalized, {@link File#getCanonicalPath() canonical path}.
     *
     * One can change path associated with a file by calling {@link #withPath(java.lang.String)} and
     * obtaining cloned instance of the source with new path.
     *
     * @since 0.8 or earlier
     */
    public String getPath() {
        return path == null ? content().getPath() : path;
    }

    /**
     * The URL if the source is retrieved via URL.
     *
     * @return URL or <code>null</code>
     * @since 0.8 or earlier
     */
    public URL getURL() {
        return content().getURL();
    }

    /**
     * Get URI of the source. Every source has an associated {@link URI}, which can be used as a
     * persistent identification of the source. For example one can
     * {@link com.oracle.truffle.api.debug.Debugger#setLineBreakpoint(int, java.net.URI, int, boolean)
     * register a breakpoint using a URI} to a source that isn't loaded yet and it will be activated
     * when the source is
     * {@link com.oracle.truffle.api.vm.PolyglotEngine#eval(com.oracle.truffle.api.source.Source)
     * evaluated}. The {@link URI} returned by this method should be as unique as possible, yet it
     * can happen that different {@link Source sources} return the same {@link #getURI} - for
     * example when content of a {@link Source#fromFileName file on a disk} changes and is
     * re-loaded.
     *
     * @return a URI, it's never <code>null</code>
     * @since 0.14
     */
    public URI getURI() {
        return content().getURI();
    }

    /**
     * Access to the source contents.
     *
     * @since 0.8 or earlier
     */
    public Reader getReader() {
        try {
            return content().getReader();
        } catch (final IOException ex) {
            return new Reader() {
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    throw ex;
                }

                @Override
                public void close() throws IOException {
                }
            };
        }
    }

    /**
     * Access to the source contents.
     *
     * @since 0.8 or earlier
     */
    public final InputStream getInputStream() {
        return new ByteArrayInputStream(getCode().getBytes());
    }

    /**
     * Gets the number of characters in the source.
     *
     * @since 0.8 or earlier
     */
    public final int getLength() {
        return getTextMap().length();
    }

    /**
     * Returns the complete text of the code.
     *
     * @since 0.8 or earlier
     */
    public String getCode() {
        return content().getCode();
    }

    /**
     * Returns a subsection of the code test.
     *
     * @since 0.8 or earlier
     */
    public String getCode(int charIndex, int charLength) {
        return getCode().substring(charIndex, charIndex + charLength);
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     *
     * @since 0.8 or earlier
     */
    public final String getCode(int lineNumber) {
        final int offset = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        return getCode().substring(offset, offset + length);
    }

    /**
     * The number of text lines in the source, including empty lines; characters at the end of the
     * source without a terminating newline count as a line.
     *
     * @since 0.8 or earlier
     */
    public final int getLineCount() {
        return getTextMap().lineCount();
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 0.8 or earlier
     */
    public final int getLineNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToLine(offset);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the column at the position.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 0.8 or earlier
     */
    public final int getColumnNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToCol(offset);
    }

    /**
     * Given a 1-based line number, return the 0-based offset of the first character in the line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 0.8 or earlier
     */
    public final int getLineStartOffset(int lineNumber) throws IllegalArgumentException {
        return getTextMap().lineStartOffset(lineNumber);
    }

    /**
     * The number of characters (not counting a possible terminating newline) in a (1-based)
     * numbered line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 0.8 or earlier
     */
    public final int getLineLength(int lineNumber) throws IllegalArgumentException {
        return getTextMap().lineLength(lineNumber);
    }

    /**
     * Append text to a Source explicitly created as <em>Appendable</em>.
     *
     * @param chars the text to append
     * @throws UnsupportedOperationException by concrete subclasses that do not support appending
     * @since 0.8 or earlier
     */
    public void appendCode(CharSequence chars) {
        content().appendCode(chars);
        clearTextMap();
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
     * @since 0.8 or earlier
     */
    public final SourceSection createSection(String identifier, int startLine, int startColumn, int charIndex, int length) {
        checkRange(charIndex, length);
        return createSectionImpl(identifier, startLine, startColumn, charIndex, length, SourceSection.EMTPY_TAGS);
    }

    /**
     * @deprecated tags are now determined by {@link Node#isTaggedWith(Class)}. Use
     *             {@link #createSection(String, int, int, int, int)} instead.
     * @since 0.12
     */
    @Deprecated
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    public final SourceSection createSection(String identifier, int charIndex, int length) throws IllegalArgumentException {
        return createSection(identifier, charIndex, length, SourceSection.EMTPY_TAGS);
    }

    /**
     * @deprecated tags are now determined by {@link Node#isTaggedWith(Class)}. Use
     *             {@link #createSection(String, int, int)} instead.
     * @since 0.12
     */
    @Deprecated
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    public final LineLocation createLineLocation(int lineNumber) {
        return new LineLocation(this, lineNumber);
    }

    /**
     * An object suitable for using as a key into a hashtable that defines equivalence between
     * different source types.
     */
    Object getHashKey() {
        return content().getHashKey();
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
     * @since 0.8 or earlier
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
     * Associates the source with specified name.
     *
     * @param newName name to be returned from {@link #getName()} method
     * @return new (identical) source, with changed {@link #getName()}
     * @since 0.14
     */
    final Source withName(String newName) {
        try {
            Source another = (Source) clone();
            another.name = newName;
            return another;
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Associates the source with specified short name.
     *
     * @param newShortName name to be returned from {@link #getShortName()} method
     * @return new (identical) source, with changed {@link #getShortName()}
     * @since 0.14
     */
    final Source withShortName(String newShortName) {
        try {
            Source another = (Source) clone();
            another.shortName = newShortName;
            return another;
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Associates the source with specified short name.
     *
     * @param newPath name to be returned from {@link #getPath()} method
     * @return new (identical) source, with changed {@link #getPath()}
     * @since 0.14
     */
    final Source withPath(String newPath) {
        try {
            Source another = (Source) clone();
            another.path = newPath;
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
     * @since 0.8 or earlier
     */
    public String getMimeType() {
        if (mimeType == null) {
            try {
                mimeType = content().findMimeType();
            } catch (IOException ex) {
                // swallow and return null
            }
        }
        return mimeType;
    }

    final boolean equalAttributes(Source other) {
        return Objects.equals(getMimeType(), other.getMimeType()) &&
                        Objects.equals(getName(), other.getName()) &&
                        Objects.equals(getShortName(), other.getShortName()) &&
                        Objects.equals(getPath(), other.getPath());
    }
}

// @formatter:off
class SourceSnippets {
    public static Source fromFile(File dir, String name) throws IOException {
        // BEGIN: SourceSnippets#fromFile
        File file = new File(dir, name);
        assert name.endsWith(".java") : "Imagine 'c:\\sources\\Example.java' file";

        Source source = Source.fromFileName(file.getPath());

        assert file.getName().equals(source.getShortName());
        assert file.getPath().equals(source.getPath());
        assert file.getPath().equals(source.getName());
        assert file.toURI().equals(source.getURI());
        assert "text/x-java".equals(source.getMimeType());
        // END: SourceSnippets#fromFile
        return source;
    }

    public static Source fromURL() throws IOException, URISyntaxException {
        // BEGIN: SourceSnippets#fromURL
        URL resource = SourceSnippets.class.getResource("sample.js");
        Source source = Source.fromURL(resource, "/your/pkg/sample.js");
        assert "/your/pkg/sample.js".equals(source.getShortName());
        assert resource.toExternalForm().equals(source.getPath());
        assert "/your/pkg/sample.js".equals(source.getName());
        assert "application/javascript".equals(source.getMimeType());
        assert resource.toURI().equals(source.getURI());
        // END: SourceSnippets#fromURL
        return source;
    }

    public static Source fromReader() throws IOException {
        // BEGIN: SourceSnippets#fromReader
        Reader stream = new InputStreamReader(
            SourceSnippets.class.getResourceAsStream("sample.js")
        );
        Source source = Source.fromReader(stream, "/your/pkg/sample.js");
        assert "/your/pkg/sample.js".equals(source.getShortName());
        assert "/your/pkg/sample.js".equals(source.getPath());
        assert "/your/pkg/sample.js".equals(source.getName());
        assert null == source.getMimeType();
        // END: SourceSnippets#fromReader
        return source;
    }

    public static Source fromAString() throws Exception {
        // BEGIN: SourceSnippets#fromAString
        Source source = Source.fromText("function() {\n"
            + "  return 'Hi';\n"
            + "}\n", "/my/scripts/hi.js"
        );
        assert "/my/scripts/hi.js".equals(source.getShortName());
        assert "/my/scripts/hi.js".equals(source.getPath());
        assert "/my/scripts/hi.js".equals(source.getName());
        assert null == source.getMimeType() : "No mime type associated";
        // END: SourceSnippets#fromAString
        return source;
    }
}
// @formatter:on
