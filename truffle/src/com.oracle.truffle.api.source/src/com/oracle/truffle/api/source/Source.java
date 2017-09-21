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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.spi.FileTypeDetector;
import java.util.Objects;

import com.oracle.truffle.api.source.impl.SourceAccessor;

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
 * The starting point is {@link Source#newBuilder(java.io.File)} method.
 *
 * <h3>Read from an URL</h3>
 *
 * One can read remote or in JAR resources using the {@link Source#newBuilder(java.net.URL)}
 * factory: <br>
 *
 * {@link SourceSnippets#fromURL}
 *
 * Each URL source is represented as a canonical object, indexed by the URL. Contents are <em>read
 * eagerly</em> once the {@link Builder#build()} method is called.
 *
 * <h3>Source from a literal text</h3>
 *
 * An anonymous immutable code snippet can be created from a string via the
 * {@link Source#newBuilder(java.lang.String) } factory method: <br>
 *
 * {@link SourceSnippets#fromAString}
 *
 * the created {@link Source} doesn't have associated {@link #getMimeType() mime type}. One has to
 * explicitly attach via {@link Builder#mimeType(java.lang.String)} method. The created
 * {@link Source} doesn't have associated {@link #getName() name}, one has to attach it via
 * {@link Builder#name(java.lang.String)} method.
 *
 * <h3>Reading from a stream</h3>
 *
 * If one has a {@link Reader} one can convert its content into a {@link Source} via
 * {@link Source#newBuilder(java.io.Reader)} method: <br>
 *
 * {@link SourceSnippets#fromReader}
 *
 * the content is <em>read eagerly</em> once the {@link Builder#build()} method is called. It
 * doesn't have associated {@link #getMimeType() mime type} and {@link #getName()}. Both values have
 * to be explicitly provided by {@link Builder#name(java.lang.String) } and
 * {@link Builder#mimeType(java.lang.String)} methods otherwise {@link MissingMIMETypeException}
 * and/or {@link MissingNameException} are thrown.
 *
 *
 * <h2>Immutability of {@link Source}</h2>
 *
 * <p>
 * {@link Source} is an immutable object - once (lazily) loaded, it remains the same. The source
 * object can be associated with various attributes like {@link #getName()} , {@link #getURI() ()},
 * {@link #getMimeType()} and these are immutable as well. The system makes the best effort to
 * derive values of these attributes from the location and/or content of the {@link Source} object.
 * However, to give the user that creates the source control over these attributes, the API offers
 * an easy way to alter values of these attributes by creating clones of the source via
 * {@link Builder#mimeType(java.lang.String)}, {@link Builder#name(java.lang.String)},
 * {@link Builder#uri(java.net.URI)} methods.
 * </p>
 * <p>
 * While {@link Source} is immutable, the world around it is changing. The content of a file from
 * which a {@link Source#newBuilder(java.io.File) source has been read} may change few seconds
 * later. How can we balance the immutability with ability to see real state of the world? In this
 * case, one can load of a new version of the {@link Source#newBuilder(java.io.File) source for the
 * same file}. The newly loaded {@link Source} will be different than the previous one, however it
 * will have the same attributes ({@link #getName()}, presumably also {@link #getMimeType()}, etc.).
 * There isn't much to do about this - just keep in mind that there can be multiple different
 * {@link Source} objects representing the same {@link #getURI() source origin}.
 * </p>
 *
 * @since 0.8 or earlier
 */
public abstract class Source {
    // TODO (mlvdv) consider canonicalizing and reusing SourceSection instances
    // TODO (mlvdv) connect SourceSections into a spatial tree for fast geometric lookup

    private static final Source EMPTY = new SourceImpl(new LiteralSourceImpl("<empty>", ""));
    private static final String NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE = "do not create sub sources from compiled code";

    private final Content content;
    private final URI uri;
    private final String name;
    private String mimeType;
    private String languageId;
    private final boolean internal;
    private final boolean interactive;
    private volatile TextMap textMap;

    /**
     * Creates new {@link Source} builder for specified <code>file</code>. Once the source is built
     * the {@link Source#getName() name} will become {@link File#getName()} and the
     * {@link Source#getCharacters()} will be loaded from the file, unless
     * {@link Builder#content(java.lang.String) redefined} on the builder. Sample usage:
     * <p>
     * {@link SourceSnippets#fromFile}
     * <p>
     * The system tries to deduce appropriate {@link Source#getMimeType()} by consulting registered
     * {@link FileTypeDetector file type detectors}.
     *
     * @param file the location of the file to load content from
     * @return new instance of builder
     * @since 0.15
     */
    public static Builder<IOException, RuntimeException, RuntimeException> newBuilder(File file) {
        return EMPTY.new Builder<>(file);
    }

    /**
     * Builds new {@link Source source} from a provided text. One needs to specify a
     * {@link Builder#mimeType(java.lang.String)}, possibly a {@link Builder#name(java.lang.String)}
     * and other attributes and then can {@link Builder#build()} a new instance of the source.
     * Sample usage:
     *
     * {@link SourceSnippets#fromAString}
     *
     * @param text the text to be returned by {@link Source#getCharacters()}
     * @return new builder to configure additional properties
     * @since 0.15
     */
    public static Builder<RuntimeException, MissingMIMETypeException, MissingNameException> newBuilder(String text) {
        return newBuilder((CharSequence) text);
    }

    /**
     * Builds new {@link Source source} from a provided character sequence. One needs to specify a
     * {@link Builder#mimeType(java.lang.String)}, possibly a {@link Builder#name(java.lang.String)}
     * and other attributes and then can {@link Builder#build()} a new instance of the source. The
     * given characters must not mutate after they were accessed for the first time. Sample usage:
     *
     * {@link SourceSnippets#fromAString}
     *
     * @param characters the text to be returned by {@link Source#getCharacters()}
     * @return new builder to configure additional properties
     * @since 0.28
     */
    public static Builder<RuntimeException, MissingMIMETypeException, MissingNameException> newBuilder(CharSequence characters) {
        return EMPTY.new Builder<>(characters);
    }

    /**
     * Creates a {@linkplain Source Source instance} that represents the contents of a sub-range of
     * an <code>this</code> {@link Source}.
     *
     * @param baseCharIndex 0-based index of the first character of the sub-range
     * @param length the number of characters in the sub-range
     * @return a new instance representing a sub-range of another Source
     * @throws IllegalArgumentException if the specified sub-range is not contained in the base
     * @since 0.15
     */
    public Source subSource(int baseCharIndex, int length) {
        SourceAccessor.neverPartOfCompilation(NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE);
        final SubSourceImpl subSource = SubSourceImpl.create(this, baseCharIndex, length);
        return new SourceImpl(subSource);
    }

    /**
     * Creates a new source whose content will be read from the provided URL once it is
     * {@link Builder#build() constructed}. Example:
     *
     * {@link SourceSnippets#fromURL}
     *
     * @param url the URL to read from and identify the source by
     * @return new builder to configure and {@link Builder#build() construct} {@link Source} from
     * @since 0.15
     */
    public static Builder<IOException, RuntimeException, RuntimeException> newBuilder(URL url) {
        return EMPTY.new Builder<>(url);
    }

    /**
     * Creates a new source whose content will be read once it is {@link Builder#build()
     * constructed}. Multiple {@link Source} instances constructed by a single {@link Builder}
     * instance share the content, read only once. When building source from reader, it is essential
     * to {@link Builder#mimeType(java.lang.String) specify MIME type}. Example follows:
     *
     * {@link SourceSnippets#fromReader}
     *
     * @param reader reader to read the content from
     * @return new builder to configure and {@link Builder#build() construct} {@link Source} from
     * @since 0.15
     */
    public static Builder<IOException, MissingMIMETypeException, MissingNameException> newBuilder(Reader reader) {
        return EMPTY.new Builder<>(reader);
    }

    static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    static String read(Reader reader) throws IOException {
        final StringBuilder builder = new StringBuilder();
        final char[] buffer = new char[1024];
        try {
            while (true) {
                final int n = reader.read(buffer);
                if (n == -1) {
                    break;
                }
                builder.append(buffer, 0, n);
            }
        } finally {
            reader.close();
        }
        return builder.toString();
    }

    Source(Content content, String mimeType, String languageId, URI uri, String name, boolean internal, boolean interactive) {
        this.content = content;
        this.mimeType = mimeType;
        this.languageId = languageId;
        this.name = name;
        this.internal = internal;
        this.interactive = interactive;
        this.uri = uri;
    }

    Content content() {
        return content;
    }

    /**
     * Returns the name of this resource holding a guest language program. An example would be the
     * name of a guest language source code file. Name is supposed to be shorter than
     * {@link #getPath()}.
     *
     * @return the name of the guest language program
     * @since 0.8 or earlier
     */
    public String getName() {
        return name == null ? content().getName() : name;
    }

    /**
     * The fully qualified name of the source. In case this source originates from a {@link File},
     * then the default path is the normalized, {@link File#getCanonicalPath() canonical path}.
     *
     * @since 0.8 or earlier
     */
    public String getPath() {
        return content().getPath();
    }

    /**
     * Check whether this source has been marked as <em>internal</em>, meaning that it has been
     * provided by the infrastructure, language implementation, or system library. <em>Internal</em>
     * sources are presumed to be irrelevant to guest language programmers, as well as possibly
     * confusing and revealing of language implementation details.
     * <p>
     * On the other hand, tools should be free to make <em>internal</em> sources visible in
     * (possibly privileged) modes that are useful for language implementors.
     * <p>
     * One can specify whether a source is internal when {@link Builder#internal() building it}.
     *
     * @return whether this source is marked as <em>internal</em>
     * @since 0.15
     */
    public boolean isInternal() {
        return internal;
    }

    /**
     * Check whether this source has been marked as <em>interactive</em>. Interactive sources are
     * provided by an entity which is able to interactively read output and provide an input during
     * the source execution; that can be a user I/O through an interactive shell for instance.
     * <p>
     * Depending on {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#isInteractive()
     * language interactive} capability, when <em>interactive</em> sources are executed, the
     * appropriate result could be passed directly to the polyglot engine
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setOut(OutputStream) output stream}
     * or {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setErr(OutputStream) error stream}
     * and polyglot engine
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setIn(InputStream) input stream} can
     * be used to read user input during the execution, to clarify the execution behavior by asking
     * questions for instance. Non-interactive languages are expected to ignore this property.
     * <p>
     * One can specify whether a source is interactive when {@link Builder#interactive() building
     * it}.
     *
     * @return whether this source is marked as <em>interactive</em>
     * @since 0.21
     */
    public boolean isInteractive() {
        return interactive;
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
     * {@link com.oracle.truffle.api.debug.DebuggerSession#install(com.oracle.truffle.api.debug.Breakpoint)
     * register a breakpoint using a URI} to a source that isn't loaded yet and it will be activated
     * when the source is
     * {@link com.oracle.truffle.api.vm.PolyglotEngine#eval(com.oracle.truffle.api.source.Source)
     * evaluated}. The {@link URI} returned by this method should be as unique as possible, yet it
     * can happen that different {@link Source sources} return the same {@link #getURI} - for
     * example when content of a {@link Source#newBuilder(java.io.File) file on a disk} changes and
     * is re-loaded.
     *
     * @return a URI, it's never <code>null</code>
     * @since 0.14
     */
    public URI getURI() {
        return uri == null ? content().getURI() : uri;
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
     * Access to the source contents. Causes the contents of this source to be loaded if they are
     * loaded lazily.
     *
     * @since 0.8 or earlier
     */
    public final InputStream getInputStream() {
        return new ByteArrayInputStream(getCharacters().toString().getBytes());
    }

    /**
     * Gets the number of characters in the source. Causes the contents of this source to be loaded
     * if they are loaded lazily.
     *
     * @since 0.8 or earlier
     */
    public final int getLength() {
        return getTextMap().length();
    }

    /**
     * Returns the code sequence as {@link CharSequence}. Causes the contents of this source to be
     * loaded if they are loaded lazily.
     *
     * @since 0.28
     */
    public CharSequence getCharacters() {
        return content().getCharacters();
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     * Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @since 0.28
     */
    public final CharSequence getCharacters(int lineNumber) {
        final int offset = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        return getCharacters().subSequence(offset, offset + length);
    }

    /**
     * Returns the complete text of the code. Causes the contents of this source to be loaded if
     * they are loaded lazily.
     *
     * @since 0.8 or earlier
     * @deprecated use {@link #getCharacters()} instead.
     */
    @Deprecated
    public String getCode() {
        return content().getCharacters().toString();
    }

    /**
     * Returns a subsection of the code test. Causes the contents of this source to be loaded if
     * they are loaded lazily.
     *
     * @since 0.8 or earlier
     * @deprecated use {@link #getCharacters() getCodeSequence()}.
     *             {@link CharSequence#subSequence(int, int)} subSequence(charIndex, charIndex +
     *             charLength)
     */
    @Deprecated
    public String getCode(int charIndex, int charLength) {
        return getCharacters().subSequence(charIndex, charIndex + charLength).toString();
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     * Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @since 0.8 or earlier
     * @deprecated use {@link #getCharacters(int)} instead.
     */
    @Deprecated
    public final String getCode(int lineNumber) {
        return getCharacters(lineNumber).toString();
    }

    /**
     * The number of text lines in the source, including empty lines; characters at the end of the
     * source without a terminating newline count as a line. Causes the contents of this source to
     * be loaded if they are loaded lazily.
     *
     * @since 0.8 or earlier
     */
    public final int getLineCount() {
        return getTextMap().lineCount();
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position. Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 0.8 or earlier
     */
    public final int getLineNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToLine(offset);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the column at the position.
     * Causes the contents of this source to be loaded if they are loaded lazily.
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
     * numbered line. Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 0.8 or earlier
     */
    public final int getLineLength(int lineNumber) throws IllegalArgumentException {
        return getTextMap().lineLength(lineNumber);
    }

    /**
     * Returns an unavailable source section indicating that the source location is not available.
     * Unavailable source sections have the same characteristics as empty source sections with
     * character index <code>0</code>, but returns <code>false</code> for
     * {@link SourceSection#isAvailable()}.
     *
     * @see SourceSection#isAvailable()
     * @since 0.18
     */
    public final SourceSection createUnavailableSection() {
        return new SourceSection(this, 0, -1);
    }

    /**
     * Creates a representation of a line of text in the source identified only by line number, from
     * which the character information will be computed. Please note that calling this method does
     * cause the {@link Source#getCharacters() code} of this source to be loaded.
     *
     * @param lineNumber 1-based line number of the first character in the section
     * @return newly created object representing the specified line
     * @throws IllegalArgumentException if the given lineNumber does not exist the source
     * @since 0.17
     */
    public final SourceSection createSection(int lineNumber) {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber < 1");
        }
        final int charIndex = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        SourceSection section = new SourceSection(this, charIndex, length);
        assert assertValid(section);
        return section;
    }

    /**
     * Creates a representation of a contiguous region of text in the source. Please note that
     * calling this method does only cause the {@link Source#getCharacters() code} of this source to
     * be loaded if assertions enabled. The bounds of the source section are only verified if
     * assertions (-ea) are enabled in the host system. An {@link IllegalArgumentException} is
     * thrown if the given indices are out of bounds of the source bounds.
     *
     * @param charIndex 0-based position of the first character in the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @throws IllegalArgumentException if charIndex < 0 or length < 0; in case assertions are
     *             enabled also if the given bounds are out of the source bounds.
     * @since 0.17
     */
    public final SourceSection createSection(int charIndex, int length) {
        if (charIndex < 0) {
            throw new IllegalArgumentException("charIndex < 0");
        } else if (length < 0) {
            throw new IllegalArgumentException("length < 0");
        }
        SourceSection section = new SourceSection(this, charIndex, length);
        assert assertValid(section);
        return section;
    }

    /**
     * Creates a representation of a contiguous region of text in the source. Computes the
     * {@code charIndex} value by building a text map of lines in the source. Please note that
     * calling this method does cause the {@link Source#getCharacters() code} of this source to be
     * loaded.
     *
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @throws IllegalArgumentException if arguments are outside the text of the source bounds
     * @see #createSection(int, int)
     * @since 0.17
     */
    public final SourceSection createSection(int startLine, int startColumn, int length) {
        if (startLine <= 0) {
            throw new IllegalArgumentException("startLine < 1");
        } else if (startColumn <= 0) {
            throw new IllegalArgumentException("startColumn < 1");
        } else if (length < 0) {
            throw new IllegalArgumentException("length < 0");
        }

        final int lineStartOffset = getTextMap().lineStartOffset(startLine);
        if (startColumn > getTextMap().lineLength(startLine)) {
            throw new IllegalArgumentException("column out of range");
        }
        final int charIndex = lineStartOffset + startColumn - 1;
        if (charIndex + length > getCharacters().length()) {
            throw new IllegalArgumentException("charIndex out of range");
        }
        SourceSection section = new SourceSection(this, charIndex, length);
        assert assertValid(section);
        return section;
    }

    private static boolean assertValid(SourceSection section) {
        if (!section.isValid()) {
            throw new IllegalArgumentException("Invalid source section bounds.");
        }
        return true;
    }

    /**
     * Creates a representation of a line number in this source, suitable for use as a hash table
     * key with equality defined to mean equivalent location.
     *
     * @param lineNumber a 1-based line number in this source
     * @return a representation of a line in this source
     * @since 0.8 or earlier
     * @deprecated without replacement
     */
    @SuppressWarnings("deprecation")
    @Deprecated
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
        TextMap res = textMap;
        if (res == null) {
            synchronized (this) {
                if (textMap == null) {
                    textMap = createTextMap();
                }
                res = textMap;
            }
        }
        return res;
    }

    final synchronized void clearTextMap() {
        textMap = null;
    }

    TextMap createTextMap() {
        final CharSequence code = getCharacters();
        if (code == null) {
            throw new RuntimeException("can't read file " + getName());
        }
        return TextMap.fromCharSequence(code);
    }

    /**
     * MIME type that is associated with this source. By default file extensions known to the system
     * are used to determine the MIME type (via registered {@link FileTypeDetector} classes), yet
     * one can directly {@link Builder#mimeType(java.lang.String) provide a MIME type} to each
     * source.
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

    /**
     * Returns the language this source was created with.
     *
     * @return the language of this source or <code>null</code>, if unknown
     * @since 0.28
     * @see Builder#language(java.lang.String)
     */
    public String getLanguage() {
        return languageId;
    }

    final boolean equalAttributes(Source other) {
        return Objects.equals(getMimeType(), other.getMimeType()) &&
                        Objects.equals(getName(), other.getName()) &&
                        Objects.equals(getPath(), other.getPath());
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <E extends Exception> E raise(Class<E> type, Exception ex) throws E {
        throw (E) ex;
    }

    /**
     * Allows one to specify additional attribute before {@link #build() creating} new
     * {@link Source} instance. One can specify {@link #name(java.lang.String)},
     * {@link #mimeType(java.lang.String)}, {@link #content(java.lang.String)} and/or whether a
     * {@link Source} is {@link #internal() internal} or not.
     *
     * To load a source from disk one can use:
     * <p>
     * {@link SourceSnippets#fromFile}
     * <p>
     * To load source from a {@link URL} one can use:
     * <p>
     * {@link SourceSnippets#fromURL}
     * <p>
     * To create a source representing text in a string use:
     * <p>
     * {@link SourceSnippets#fromAString}
     * <p>
     * or read a source from a {@link Reader}:
     * <p>
     * {@link SourceSnippets#fromReader}
     * <p>
     *
     * The system does all it can to guarantee that newly created {@link Source source} has a
     * {@link Source#getMimeType() MIME type assigned}. In some situations the mime type can be
     * guessed, in others it has to be explicitly specified via the
     * {@link #mimeType(java.lang.String)} method.
     *
     * Once your builder is configured, call {@link #build()} to perform the loading and
     * construction of new {@link Source}.
     *
     * @param <E1> the (checked) exception that one should expect when calling {@link #build()}
     *            method - usually an {@link IOException},
     *            {@link Source#newBuilder(java.lang.String) sometimes} none.
     * @param <E2> either a {@link MissingMIMETypeException} to signal that one has to call
     *            {@link #mimeType(java.lang.String)} or a {@link RuntimeException} to signal
     *            everything seems to be OK
     * @param <E3> either a {@link MissingNameException} to signal that one has to call
     *            {@link #name(java.lang.String)} or a {@link RuntimeException} to signal everything
     *            seems to be OK
     * @since 0.15
     */
    public final class Builder<E1 extends Exception, E2 extends Exception, E3 extends Exception> {
        private final Object origin;
        private URI uri;
        private String name;
        private String path;
        private String mime;
        private String language;
        private CharSequence content;
        private boolean internal;
        private boolean interactive;

        private Builder(Object origin) {
            this.origin = origin;
        }

        /**
         * Gives a new name to the {@link #build() to-be-built} {@link Source}.
         *
         * @param newName name that replaces the previously given one, cannot be <code>null</code>
         * @return instance of <code>this</code> builder
         * @since 0.15
         */
        @SuppressWarnings("unchecked")
        public Builder<E1, E2, RuntimeException> name(String newName) {
            Objects.requireNonNull(newName);
            this.name = newName;
            return (Builder<E1, E2, RuntimeException>) this;
        }

        Builder<E1, E2, E3> path(String p) {
            this.path = p;
            return this;
        }

        /**
         * Explicitly assigns a {@link Source#getMimeType() MIME type} to the {@link #build()
         * to-be-built} {@link Source}. This method returns the builder parametrized with
         * {@link Source} type parameter to signal to the compiler that it is safe to call
         * {@link #build()} method and create an instance of a {@link Source}. Example:
         *
         * {@link SourceSnippets#fromAString}
         *
         * @param newMimeType the new mime type to be assigned
         * @return instance of <code>this</code> builder ready to {@link #build() create new source}
         * @since 0.15
         */
        @SuppressWarnings("unchecked")
        public Builder<E1, RuntimeException, E3> mimeType(String newMimeType) {
            Objects.requireNonNull(newMimeType);
            this.mime = newMimeType;
            return (Builder<E1, RuntimeException, E3>) this;
        }

        /**
         * Assigns a language ID to the {@link #build() to-be-built} {@link Source}. After a
         * language ID is set, it's not necessary to assign the MIME type.
         *
         * @param newLanguage the id of the language
         * @return instance of <code>this</code> builder
         * @since 0.28
         */
        @SuppressWarnings("unchecked")
        public Builder<E1, RuntimeException, E3> language(String newLanguage) {
            Objects.requireNonNull(newLanguage);
            if (this.mime == null) {
                this.mime = "x-unknown";
            }
            this.language = newLanguage;
            return (Builder<E1, RuntimeException, E3>) this;
        }

        /**
         * Marks the source as internal. Internal sources are those that aren't created by user, but
         * rather inherently present by the language system. Calling this method influences result
         * of create {@link Source#isInternal()}
         *
         * @return the instance of this builder
         * @since 0.15
         */
        public Builder<E1, E2, E3> internal() {
            this.internal = true;
            return this;
        }

        /**
         * Marks the source as interactive. {@link com.oracle.truffle.api.vm.PolyglotEngine#eval
         * Evaluation} of interactive sources by an
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#isInteractive() interactive
         * language} can use the {@link com.oracle.truffle.api.vm.PolyglotEngine} streams to print
         * the result and read an input. However, non-interactive languages are expected to ignore
         * the interactive property of sources and not use the polyglot engine streams. Any desired
         * printing of the evaluated result provided by a non-interactive language needs to be
         * handled by the caller. Calling of this method influences the result of
         * {@link Source#isInteractive()}.
         *
         * @return the instance of this builder
         * @since 0.21
         */
        public Builder<E1, E2, E3> interactive() {
            this.interactive = true;
            return this;
        }

        /**
         * Assigns new {@link URI} to the {@link #build() to-be-created} {@link Source}. Each source
         * provides {@link Source#getURI()} as a persistent identification of its location. A
         * default value for the method is deduced from the location or content, but one can change
         * it by using this method
         *
         * @param ownUri the URL to use instead of default one, cannot be <code>null</code>
         * @return the instance of this builder
         * @since 0.15
         */
        public Builder<E1, E2, E3> uri(URI ownUri) {
            Objects.requireNonNull(ownUri);
            this.uri = ownUri;
            return this;
        }

        /**
         * Specifies content of {@link #build() to-be-built} {@link Source}. Using this method one
         * can ignore the real content of a file or URL and use already read one, or completely
         * different one. Example:
         *
         * {@link SourceSnippets#fromURLWithOwnContent}
         *
         * @param code the code to be available via {@link Source#getCharacters()}
         * @return instance of this builder - which's {@link #build()} method no longer throws an
         *         {@link IOException}
         * @since 0.15
         */
        @SuppressWarnings("unchecked")
        public Builder<RuntimeException, E2, E3> content(String code) {
            this.content = code;
            return (Builder<RuntimeException, E2, E3>) this;
        }

        /**
         * Specifies content of {@link #build() to-be-built} {@link Source}. Using this method one
         * can ignore the real content of a file or URL and use already read one, or completely
         * different one. The given characters must not mutate after they were accessed for the
         * first time. Example:
         *
         * {@link SourceSnippets#fromURLWithOwnContent}
         *
         * @param characters the code to be available via {@link Source#getCharacters()}
         * @return instance of this builder - which's {@link #build()} method no longer throws an
         *         {@link IOException}
         * @since 0.28
         */
        @SuppressWarnings("unchecked")
        public Builder<RuntimeException, E2, E3> content(CharSequence characters) {
            this.content = characters;
            return (Builder<RuntimeException, E2, E3>) this;
        }

        Builder<E1, E2, E3> content(byte[] arr, int offset, int length, Charset encoding) {
            this.content = new String(arr, offset, length, encoding);
            return this;
        }

        /**
         * Uses configuration of this builder to create new {@link Source} object. The return value
         * is parametrized to ensure your code doesn't compile until you specify a MIME type:
         * <ul>
         * <li>either via file related methods like {@link Source#newBuilder(java.io.File)} that can
         * guess the MIME type</li>
         * <li>or directly via {@link #mimeType(java.lang.String)} method on this builder
         * </ul>
         * This method may throw an exception - especially when dealing with files (e.g.
         * {@link Source#newBuilder(java.net.URL)}, {@link Source#newBuilder(java.io.File)} or
         * {@link Source#newBuilder(java.io.Reader)} this method may throw {@link IOException} that
         * one needs to deal with. In case of other building styles (like
         * {@link Source#newBuilder(java.lang.String)} one doesn't need to capture any exception
         * when calling this method.
         *
         * @return the source object
         * @throws E1 exception if something went wrong while creating the source
         * @throws E2 eliminate this exception by calling {@link #mimeType(java.lang.String) }
         * @throws E3 eliminate this exception by calling {@link #name(java.lang.String) }
         * @since 0.15
         */
        public Source build() throws E1, E2, E3 {
            Content holder;
            try {
                if (origin instanceof File) {
                    holder = buildFile(content == null);
                } else if (origin instanceof Reader) {
                    holder = buildReader();
                } else if (origin instanceof URL) {
                    holder = buildURL();
                } else {
                    holder = buildString();
                }
                String type = this.mime == null ? holder.findMimeType() : this.mime;
                if (type == null) {
                    throw raise(RuntimeException.class, new MissingMIMETypeException());
                }
                if (content != null) {
                    holder.code = content;
                }
                SourceImpl ret = new SourceImpl(holder, type, language, uri, name, internal, interactive);
                if (ret.getName() == null) {
                    throw raise(RuntimeException.class, new MissingNameException());
                }
                return ret;
            } catch (IOException ex) {
                throw raise(RuntimeException.class, ex);
            }
        }

        private Content buildFile(boolean read) throws IOException {
            final File file = (File) origin;
            File absoluteFile = file.getCanonicalFile();
            FileSourceImpl fileSource = new FileSourceImpl(
                            read ? Source.read(file) : null,
                            absoluteFile,
                            name == null ? file.getName() : name,
                            path == null ? absoluteFile.getPath() : path);
            return fileSource;
        }

        private Content buildReader() throws IOException {
            final Reader r = (Reader) origin;
            if (content == null) {
                content = read(r);
            }
            r.close();
            LiteralSourceImpl ret = new LiteralSourceImpl(
                            name, content);
            return ret;
        }

        private Content buildURL() throws IOException {
            final URL url = (URL) origin;
            String computedName = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
            URLSourceImpl ret = new URLSourceImpl(url, content, computedName);
            return ret;
        }

        private Content buildString() {
            final CharSequence r = (CharSequence) origin;
            if (content == null) {
                content = r;
            }
            LiteralSourceImpl ret = new LiteralSourceImpl(
                            name, content);
            return ret;
        }
    }
}

// @formatter:off
// Checkstyle: stop
class SourceSnippets {
    public static Source fromFile(File dir, String name) throws IOException {
        // BEGIN: SourceSnippets#fromFile
        File file = new File(dir, name);
        assert name.endsWith(".java") : "Imagine 'c:\\sources\\Example.java' file";

        Source source = Source.newBuilder(file).build();

        assert file.getName().equals(source.getName());
        assert file.getPath().equals(source.getPath());
        assert file.toURI().equals(source.getURI());
        assert "text/x-java".equals(source.getMimeType());
        // END: SourceSnippets#fromFile
        return source;
    }

    public static Source likeFileName(String fileName) throws IOException {
        // BEGIN: SourceSnippets#likeFileName
        File file = new File(fileName);
        Source source = Source.newBuilder(file.getCanonicalFile()).
            name(file.getPath()).
            build();
        // END: SourceSnippets#likeFileName
        return source;
    }

    public static Source fromURL(Class<?> relativeClass) throws IOException, URISyntaxException {
        // BEGIN: SourceSnippets#fromURL
        URL resource = relativeClass.getResource("sample.js");
        Source source = Source.newBuilder(resource)
            .name("sample.js")
            .build();
        assert resource.toExternalForm().equals(source.getPath());
        assert "sample.js".equals(source.getName());
        assert "application/javascript".equals(source.getMimeType());
        assert resource.toURI().equals(source.getURI());
        // END: SourceSnippets#fromURL
        return source;
    }

    public static Source fromURLWithOwnContent(Class<?> relativeClass) {
        // BEGIN: SourceSnippets#fromURLWithOwnContent
        URL resource = relativeClass.getResource("sample.js");
        Source source = Source.newBuilder(resource)
            .name("sample.js")
            .content("{}")
            .build();
        assert resource.toExternalForm().equals(source.getPath());
        assert "sample.js".equals(source.getName());
        assert "application/javascript".equals(source.getMimeType());
        assert resource.toExternalForm().equals(source.getURI().toString());
        assert "{}".equals(source.getCharacters());
        // END: SourceSnippets#fromURLWithOwnContent
        return source;
    }

    public static Source fromReader(Class<?> relativeClass) throws IOException {
        // BEGIN: SourceSnippets#fromReader
        Reader stream = new InputStreamReader(
                        relativeClass.getResourceAsStream("sample.js")
        );
        Source source = Source.newBuilder(stream)
            .name("sample.js")
            .mimeType("application/javascript")
            .build();
        assert "sample.js".equals(source.getName());
        assert "application/javascript".equals(source.getMimeType());
        // END: SourceSnippets#fromReader
        return source;
    }

    public static Source fromAString() {
        // BEGIN: SourceSnippets#fromAString
        Source source = Source.newBuilder("function() {\n"
            + "  return 'Hi';\n"
            + "}\n")
            .name("hi.js")
            .mimeType("application/javascript")
            .build();
        assert "hi.js".equals(source.getName());
        assert "application/javascript".equals(source.getMimeType());
        // END: SourceSnippets#fromAString
        return source;
    }

    public static boolean loaded = true;
}
// @formatter:on
