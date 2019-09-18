/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.nodes.LanguageInfo;
import java.nio.charset.Charset;
import java.util.function.Supplier;
import org.graalvm.polyglot.io.FileSystem;

/**
 * Representation of a source code unit and its contents that can be evaluated in a language. Each
 * source is associated with the the ID of the language.
 *
 * <h3>From a file on disk</h3>
 *
 * Each file is represented as a canonical object, indexed by the absolute, canonical path name of
 * the file. File content is <em>read eagerly</em> and may be optionally <em>cached</em>. Sample
 * usage: <br>
 *
 * {@link SourceSnippets#fromFile}
 *
 * The starting point is {@link Source#newBuilder(String, TruffleFile)} method.
 *
 * <h3>Read from an URL</h3>
 *
 * One can read remote or in JAR resources using the {@link Source#newBuilder(String, java.net.URL)}
 * factory: <br>
 *
 * {@link SourceSnippets#fromURL}
 *
 * Each URL source is represented as a canonical object, indexed by the URL. Contents are <em>read
 * eagerly</em> once the {@link SourceBuilder#build()} method is called.
 *
 * <h3>Source from a literal text</h3>
 *
 * An anonymous immutable code snippet can be created from a string via the
 * {@link Source#newBuilder(String, CharSequence, String) } factory method: <br>
 *
 * {@link SourceSnippets#fromAString}
 *
 * <h3>Reading from a stream</h3>
 *
 * If one has a {@link Reader} one can convert its content into a {@link Source} via
 * {@link Source#newBuilder(String, java.io.Reader, String)} method: <br>
 *
 * {@link SourceSnippets#fromReader}
 *
 * the content is <em>read eagerly</em> once the {@link SourceBuilder#build()} method is called.
 *
 * <h3>Reading from bytes</h3>
 *
 * Sources can be created from bytes. Please note that all character related methods will throw an
 * {@link UnsupportedOperationException} if that is the case.
 *
 * <h2>Attributes</h2>
 *
 * The source object can be associated with various attributes like {@link #getName()} ,
 * {@link #getURI() ()}, {@link #getMimeType()} and these are immutable. The system makes the best
 * effort to derive values of these attributes from the location and/or content of the
 * {@link Source} object. However, to give the user that creates the source control over these
 * attributes, the API offers an easy way to alter values of these attributes.
 *
 * <h2>Character and binary based Sources</h2>
 *
 * A source is either {@link #hasBytes() byte} or {@link #hasCharacters() character} based. For
 * literal sources it depends on whether the byte or character based factory method was used. When
 * the source was loaded from a {@link File file} or {@link URL url} then the
 * {@link LanguageInfo#getDefaultMimeType() default MIME type} of the provided language will be used
 * to determine whether bytes or characters should be loaded. The behavior can be customized by
 * specifying a {@link SourceBuilder#mimeType(String) MIME type} or
 * {@link SourceBuilder#content(ByteSequence) content} explicitly. If the specified or inferred MIME
 * type starts with <code>'text/</code> or the MIME types is <code>null</code> then it will be
 * interpreted as character based, otherwise byte based.
 *
 * @since 0.8 or earlier
 */
@SuppressWarnings("deprecation")
public abstract class Source {

    /**
     * Constant to be used as an argument to {@link SourceBuilder#content(CharSequence)} to set no
     * content to the Source built.
     *
     * @since 19.0
     */
    public static final CharSequence CONTENT_NONE = null;
    private static final CharSequence CONTENT_UNSET = new String();

    private static final String UNKNOWN_MIME_TYPE = "content/unknown";
    private static final Source EMPTY = new SourceImpl.Key(null, null, null, null, null, null, null, false, false, false, true).toSourceNotInterned();

    private static final String NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE = "do not create sub sources from compiled code";
    private static final String URI_SCHEME = "truffle";
    private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;
    private static final int BUFFER_SIZE = 8192;
    static final Class<?> BYTE_SEQUENCE_CLASS = ByteSequence.create(new byte[0]).getClass();

    private static final InternedSources SOURCES = new InternedSources();

    private volatile TextMap textMap;
    private volatile URI computedURI;
    volatile org.graalvm.polyglot.Source polyglotSource;

    abstract Object getSourceId();

    Source() {
    }

    /**
     * Returns the language this source was created with. The string returned matches the
     * {@link LanguageInfo#getId() id} of the language.
     *
     * @return the language of this source.
     * @since 0.28
     */
    public abstract String getLanguage();

    /**
     * Returns the name of this resource holding a guest language program. An example would be the
     * name of a guest language source code file. Name is supposed to be shorter than
     * {@link #getPath()}.
     *
     * @return the name of the guest language program
     * @since 0.8 or earlier
     */
    public abstract String getName();

    /**
     * The fully qualified name of the source. In case this source originates from a {@link File} or
     * {@link TruffleFile}, then the path is the normalized, {@link File#getCanonicalPath()
     * canonical path} for absolute files, or the relative path otherwise. If the source originates
     * from an {@link URL}, then it's the path component of the URL.
     *
     * @since 0.8 or earlier
     */
    public abstract String getPath();

    /**
     * Check whether this source has been marked as <em>internal</em>, meaning that it has been
     * provided by the infrastructure, language implementation, or system library. <em>Internal</em>
     * sources are presumed to be irrelevant to guest language programmers, as well as possibly
     * confusing and revealing of language implementation details.
     * <p>
     * On the other hand, tools should be free to make <em>internal</em> sources visible in
     * (possibly privileged) modes that are useful for language implementors.
     * <p>
     * One can specify whether a source is internal when {@link SourceBuilder#internal(boolean)
     * building it}.
     *
     * @return whether this source is marked as <em>internal</em>
     * @since 0.15
     */
    public abstract boolean isInternal();

    /**
     * Returns <code>true</code> if code caching is enabled for this source. If <code>true</code>
     * then the source does not require parsing every time this source is evaluated. If
     * <code>false</code> then the source requires parsing every time the source is evaluated but
     * does not remember any state. Disabling caching may be useful if the source is known to only
     * be evaluated once.
     *
     * @return whether this source is allowed to be <em>cached</em>
     * @since 19.0
     */
    public abstract boolean isCached();

    /**
     * Check whether this source has been marked as <em>interactive</em>. Interactive sources are
     * provided by an entity which is able to interactively read output and provide an input during
     * the source execution; that can be a user I/O through an interactive shell for instance.
     * <p>
     * Depending on {@link com.oracle.truffle.api.TruffleLanguage.Registration#interactive()
     * language interactive} capability, when <em>interactive</em> sources are executed, the
     * appropriate result can be passed directly to the environment
     * {@link com.oracle.truffle.api.TruffleLanguage.Env#out() output stream} or
     * {@link com.oracle.truffle.api.TruffleLanguage.Env#err() error stream} and
     * {@link com.oracle.truffle.api.TruffleLanguage.Env#in() input stream} can be used to read user
     * input during the execution, to clarify the execution behavior by asking questions for
     * instance. Non-interactive languages are expected to ignore this property.
     * <p>
     * One can specify whether a source is interactive when
     * {@link SourceBuilder#interactive(boolean) building it}.
     *
     * @return whether this source is marked as <em>interactive</em>
     * @since 0.21
     */
    public abstract boolean isInteractive();

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public final boolean equals(Object obj) {
        if (!(obj instanceof Source)) {
            return false;
        }
        return getSourceId().equals(((Source) obj).getSourceId());
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public final int hashCode() {
        return getSourceId().hashCode();
    }

    /**
     * Creates a {@linkplain Source Source instance} that represents the contents of a sub-range of
     * an <code>this</code> {@link Source}.
     *
     * @param baseCharIndex 0-based index of the first character of the sub-range
     * @param length the number of characters in the sub-range
     * @return a new instance representing a sub-range of another Source
     * @throws IllegalArgumentException if the specified sub-range is not contained in the base
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @since 0.15
     */
    public Source subSource(int baseCharIndex, int length) {
        if (hasBytes()) {
            throw new UnsupportedOperationException("Operation is only enabled for character based sources.");
        }
        CompilerAsserts.neverPartOfCompilation(NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE);
        return SubSourceImpl.create(this, baseCharIndex, length);
    }

    /**
     * Returns all characters of the source.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @since 0.28
     */
    public abstract CharSequence getCharacters();

    /**
     * Returns <code>true</code> if this source represents a byte based source, else
     * <code>false</code>. A source is either a byte based or a character based source, never both
     * at the same time.
     * <p>
     * The method {@link #getBytes()} is only supported if this method returns <code>true</code>.
     *
     * @see #getBytes()
     * @since 19.0
     */
    public abstract boolean hasBytes();

    /**
     * Returns <code>true</code> if this source represents a character based source, else
     * <code>false</code>. A source is either a byte based or a character based source, never both
     * at the same time.
     *
     * <p>
     * The following methods are only supported if {@link #hasCharacters()} is <code>true</code>:
     * <ul>
     * <li>{@link #getReader()}
     * <li>{@link #getCharacters()}
     * <li>{@link #getLineCount()}
     * <li>{@link #getLineNumber(int)}
     * <li>{@link #getColumnNumber(int)}
     * <li>{@link #getLineStartOffset(int)}
     * <li>{@link #getLineLength(int)}
     * </ul>
     *
     * @since 19.0
     */
    public abstract boolean hasCharacters();

    /**
     * Returns the bytes of the source if it is a {@link #hasBytes() byte based source}.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasBytes() bytes}
     *             .
     * @see #hasBytes()
     * @since 19.0
     */
    public abstract ByteSequence getBytes();

    /**
     * The URL if the source is retrieved via URL.
     *
     * @return URL or <code>null</code>
     * @since 0.8 or earlier
     */
    public abstract URL getURL();

    abstract URI getOriginalURI();

    /**
     * Get URI of the source. Every source has an associated {@link URI}, which can be used as a
     * persistent identification of the source. For example one can
     * {@link com.oracle.truffle.api.debug.DebuggerSession#install(com.oracle.truffle.api.debug.Breakpoint)
     * register a breakpoint using a URI} to a source that isn't loaded yet and it will be activated
     * when the source is evaluated. The {@link URI} returned by this method should be as unique as
     * possible, yet it can happen that different {@link Source sources} return the same
     * {@link #getURI} - for example when content of a {@link Source#newBuilder(String, TruffleFile)
     * file on a disk} changes and is re-loaded.
     *
     * @return a URI, it's never <code>null</code>
     * @since 0.14
     */
    public final URI getURI() {
        URI uri = getOriginalURI();
        if (uri == null) {
            uri = computedURI;
            if (uri == null) {
                byte[] bytes = hasBytes() ? getBytes().toByteArray() : getCharacters().toString().getBytes();
                uri = computedURI = getNamedURI(getName(), bytes);
            }
        }
        return uri;
    }

    /**
     * Returns the MIME type that is associated with this source. Sources have a <code>null</code>
     * MIME type by default. If the MIME type is <code>null</code> then the
     * {@link Language#getDefaultMimeType() default MIME type} of the language will be used to
     * interpret the source. If set explicitly then the language needs to
     * {@link Language#getMimeTypes() support} the MIME type in order to {@link Context#eval(Source)
     * evaluate} the source. If not <code>null</code> the MIME type is already verified containing
     * no spaces and a '/' between group and sub-group. An example for a valid MIME type is:
     * <code>text/javascript</code>.
     * <p>
     * The MIME type can be guessed by the system based on {@link #findMimeType(TruffleFile) files}
     * or {@link #findMimeType(URL) urls}
     *
     * @see LanguageInfo#getDefaultMimeType()
     * @see LanguageInfo#getMimeTypes()
     * @see Source#findMimeType(TruffleFile)
     * @see Source#findMimeType(URL)
     * @return MIME type of this source or <code>null</code>, if not explicitly set.
     * @since 19.0
     */
    public abstract String getMimeType();

    /**
     * Access to the source contents.
     *
     * @since 0.8 or earlier
     */
    public final Reader getReader() {
        return new CharSequenceReader(getCharacters());
    }

    /**
     * @since 0.8 or earlier
     * @deprecated without replacement use either {@link #getBytes()} or {@link #getCharacters()}
     *             directly.
     */
    @Deprecated
    public final InputStream getInputStream() {
        return new ByteArrayInputStream(getCharacters().toString().getBytes());
    }

    /**
     * Gets the number of characters or bytes of the source.
     *
     * @throws UnsupportedOperationException if this source does not contain {@link #hasCharacters()
     *             characters}, nor {@link #hasBytes() bytes}.
     * @since 0.8
     */
    public final int getLength() {
        if (hasCharacters()) {
            return getTextMap().length();
        } else if (hasBytes()) {
            return getBytes().length();
        } else {
            throw new UnsupportedOperationException("Operation is only enabled for sources with character or byte content.");
        }
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     * Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @since 0.28
     */
    public final CharSequence getCharacters(int lineNumber) {
        final int offset = getTextMap().lineStartOffset(lineNumber);
        final int length = getTextMap().lineLength(lineNumber);
        return getCharacters().subSequence(offset, offset + length);
    }

    /**
     * The number of text lines in the source, including empty lines; characters at the end of the
     * source without a terminating newline count as a line. Causes the contents of this source to
     * be loaded if they are loaded lazily.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @since 0.8 or earlier
     */
    public final int getLineCount() {
        return getTextMap().lineCount();
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position. Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
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
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 0.8 or earlier
     */
    public final int getColumnNumber(int offset) throws IllegalArgumentException {
        return getTextMap().offsetToCol(offset);
    }

    /**
     * Given a 1-based line number, return the 0-based offset of the first character in the line.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
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
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
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
     * {@link SourceSection#isAvailable()}. Unavailable source sections may be created for character
     * and byte based sources.
     *
     * @see SourceSection#isAvailable()
     * @since 0.18
     */
    public final SourceSection createUnavailableSection() {
        return new SourceSectionUnavailable(this);
    }

    /**
     * Create representation of a contiguous region in the source that does not have the character
     * content available.
     *
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section, or
     *            <code>-1</code> when the column is not defined
     * @param endLine 1-based line number of the last character in the section
     * @param endColumn 1-based column number of the last character in the section, or
     *            <code>-1</code> when the column is not defined
     * @throws UnsupportedOperationException if this source has {@link #hasBytes() bytes}.
     * @since 19.0
     */
    public final SourceSection createSection(int startLine, int startColumn, int endLine, int endColumn) {
        if (hasBytes()) {
            throw new UnsupportedOperationException("Operation is only enabled for character based sources.");
        }
        if (startLine < 1) {
            throw new IllegalArgumentException("lineNumber < 1");
        }
        if (startLine > endLine) {
            throw new IllegalArgumentException("startLine " + startLine + " > endLine " + endLine);
        }
        if (startLine == endLine && startColumn > endColumn) {
            throw new IllegalArgumentException("startColumn " + startColumn + " > endColumn " + endColumn);
        }
        if (hasCharacters()) {
            if (startColumn < 1 || endColumn < 1) {
                throw new IllegalArgumentException("columnNumber < 1");
            }
            final int charIndex = getTextMap().lineColumnToOffset(startLine, startColumn);
            final int endIndex = getTextMap().lineColumnToOffset(endLine, endColumn);
            assert charIndex <= endIndex : charIndex + " > " + endIndex;
            int length = endIndex + 1 - charIndex;
            int sourceLength = getTextMap().length();
            if (length == 1 && charIndex + length > sourceLength) {
                // When the start and end position is the same, reduce to zero-length section
                // when on the very end of the source
                length = 0;
            }
            if (charIndex + length > sourceLength) {
                throw new IllegalArgumentException("end position out of range");
            }
            SourceSection section = new SourceSectionLoaded(this, charIndex, length);
            assert assertValid(section);
            return section;
        } else {
            if (startColumn == -1) {
                if (endColumn != -1) {
                    throw new IllegalArgumentException("endColumn can not be specified when startColumn is not.");
                }
                return new SourceSectionUnloaded.Lines(this, startLine, endLine);
            } else {
                if (startColumn < 1 || endColumn < 1) {
                    throw new IllegalArgumentException("columnNumber < 1");
                }
                return new SourceSectionUnloaded.LinesAndColumns(this, startLine, startColumn, endLine, endColumn);
            }
        }
    }

    /**
     * Creates a representation of a line of text in the source identified only by line number, from
     * which the character information will be computed. Please note that calling this method does
     * cause the {@link Source#getCharacters() code} of this source to be loaded, if there is any.
     * If no {@link Source#getCharacters() code} is {@link Source#hasCharacters() available}, a
     * SourceSection without {@link SourceSection#getCharacters() character content} is created with
     * the {@link SourceSection#getStartLine() start line} and the same
     * {@link SourceSection#getEndLine() end line} defined only.
     *
     * @param lineNumber 1-based line number of the first character in the section
     * @return newly created object representing the specified line
     * @throws UnsupportedOperationException if this source contains {@link #hasBytes() bytes}.
     * @throws IllegalArgumentException if the given lineNumber does not exist the source
     * @since 0.17
     */
    public final SourceSection createSection(int lineNumber) {
        if (hasBytes()) {
            throw new UnsupportedOperationException("Operation is only enabled for character based sources.");
        }
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber < 1");
        }
        SourceSection section;
        if (hasCharacters()) {
            final int charIndex = getTextMap().lineStartOffset(lineNumber);
            final int length = getTextMap().lineLength(lineNumber);
            section = new SourceSectionLoaded(this, charIndex, length);
            assert assertValid(section);
        } else {
            section = new SourceSectionUnloaded.Lines(this, lineNumber, lineNumber);
        }
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
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @throws IllegalArgumentException if charIndex < 0 or length < 0; in case assertions are
     *             enabled also if the given bounds are out of the source bounds.
     * @since 0.17
     */
    public final SourceSection createSection(int charIndex, int length) {
        if (hasBytes()) {
            throw new UnsupportedOperationException("Operation is only enabled for character based sources.");
        }
        if (charIndex < 0) {
            throw new IllegalArgumentException("charIndex < 0");
        } else if (length < 0) {
            throw new IllegalArgumentException("length < 0");
        }
        SourceSection section;
        if (hasCharacters()) {
            section = new SourceSectionLoaded(this, charIndex, length);
            assert assertValid(section);
        } else {
            section = new SourceSectionUnloaded.Indexed(this, charIndex, length);
        }
        return section;
    }

    /**
     * Creates a representation of a contiguous region of text in the source. When character content
     * is available, computes the {@code charIndex} value by building a text map of lines in the
     * source. Please note that calling this method does cause the {@link Source#getCharacters()
     * code} of this source to be loaded, if there is any. If no {@link Source#getCharacters() code}
     * is {@link Source#hasCharacters() available}, {@link UnsupportedOperationException} is thrown.
     * Use {@link #createSection(int, int, int, int)} to create a SourceSection without character
     * content.
     *
     * @param startLine 1-based line number of the first character in the section
     * @param startColumn 1-based column number of the first character in the section
     * @param length the number of characters in the section
     * @return newly created object representing the specified region
     * @throws UnsupportedOperationException if this source does not contain {@link #hasCharacters()
     *             characters}.
     * @throws IllegalArgumentException if arguments are outside the text of the source bounds
     * @see #createSection(int, int)
     * @see #createSection(int, int, int, int)
     * @since 0.17
     */
    public final SourceSection createSection(int startLine, int startColumn, int length) {
        if (hasBytes() || !hasCharacters()) {
            throw new UnsupportedOperationException("Operation is only enabled for character based sources.");
        }
        if (startLine <= 0) {
            throw new IllegalArgumentException("startLine < 1");
        } else if (startColumn <= 0) {
            throw new IllegalArgumentException("startColumn < 1");
        } else if (hasCharacters() && length < 0) {
            throw new IllegalArgumentException("length < 0");
        }
        final int lineStartOffset = getTextMap().lineStartOffset(startLine);
        final int lineLength = getTextMap().lineLength(startLine);
        if (startColumn > (lineLength + 1)) {
            throw new IllegalArgumentException("column out of range");
        }
        final int charIndex = lineStartOffset + startColumn - 1;
        if (charIndex + length > getCharacters().length()) {
            throw new IllegalArgumentException("charIndex out of range");
        }
        SourceSection section = new SourceSectionLoaded(this, charIndex, length);
        assert assertValid(section);
        return section;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String toString() {
        return "Source [language=" + getLanguage() + ", name=" + getName() + ", path=" + getPath() + ", internal=" + isInternal() + ", cached=" + isCached() +
                        ", interactive=" + isInteractive() + ", hasBytes=" + hasBytes() + ", hasCharacters=" + hasCharacters() + ", URL=" + getURL() + ", URI=" + getURI() +
                        ", mimeType=" + getMimeType() + "]";
    }

    private static boolean assertValid(SourceSection section) {
        if (!section.isValid()) {
            throw new IllegalArgumentException("Invalid source section bounds.");
        }
        return true;
    }

    abstract boolean isLegacy();

    abstract Source copy();

    final TextMap getTextMap() {
        if (hasBytes()) {
            throw new UnsupportedOperationException("Operation is only enabled for character based sources.");
        }
        TextMap res = textMap;
        if (res == null) {
            res = textMap = createTextMap();
        }
        assert res != null;
        return res;
    }

    TextMap createTextMap() {
        final CharSequence code = getCharacters();
        if (code == null) {
            throw new RuntimeException("can't read file " + getName());
        }
        return TextMap.fromCharSequence(code);
    }

    private URI getNamedURI(String name, byte[] bytes) {
        return getNamedURI(name, bytes, 0, bytes.length);
    }

    private URI getNamedURI(String name, byte[] bytes, int byteIndex, int length) {
        String digest;
        if (bytes != null) {
            digest = digest(bytes, byteIndex, length);
        } else {
            digest = Integer.toString(System.identityHashCode(this), 16);
        }
        if (name != null) {
            digest += '/' + name;
        }
        try {
            return new URI(URI_SCHEME, digest, null);
        } catch (URISyntaxException ex) {
            throw new Error(ex);    // Should not happen
        }
    }

    /**
     * Creates a new character based literal source from a character sequence. The given characters
     * must not mutate after they were accessed for the first time.
     * <p>
     * Use this method for sources that do originate from a literal. For file or URL sources use the
     * appropriate builder constructor and {@link SourceBuilder#content(CharSequence)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromAString}
     *
     * @param language the language id, must not be <code>null</code>
     * @param characters the character sequence or string, must not be <code>null</code>
     * @param name the name of the source, if <code>null</code> then <code>"Unnamed"</code> will be
     *            used as name.
     * @since 19.0
     */
    public static LiteralBuilder newBuilder(String language, CharSequence characters, String name) {
        return EMPTY.new LiteralBuilder(language, characters).name(name);
    }

    /**
     * Creates a new byte based literal source from a byte sequence. The given bytes must not mutate
     * after they were accessed for the first time.
     * <p>
     * Use this method for sources that do originate from a literal. For file or URL sources use the
     * appropriate builder constructor and {@link SourceBuilder#content(ByteSequence)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromBytes}
     *
     * @param language the language id, must not be <code>null</code>
     * @param bytes the byte sequence or string, must not be <code>null</code>
     * @param name the name of the source, if <code>null</code> then <code>"Unnamed"</code> will be
     *            used as name.
     * @since 19.0
     */
    public static LiteralBuilder newBuilder(String language, ByteSequence bytes, String name) {
        return EMPTY.new LiteralBuilder(language, bytes).name(name);
    }

    /**
     * Creates a new file based source builder from a given file. A file based source is either
     * interpreted as {@link Source#hasBytes() binary} or {@link Source#hasCharacters() character}
     * source depending on the {@link LanguageInfo#getDefaultMimeType() default MIME type} of the
     * language, the {@link SourceBuilder#content(ByteSequence) contents} or the specified
     * {@link SourceBuilder#mimeType(String) MIME type}. A language may be detected from an existing
     * file using {@link #findLanguage(TruffleFile)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromFile}
     *
     * @param language the language id, must not be <code>null</code>
     * @param file the file to use and load, must not be <code>null</code>
     * @since 19.0
     */
    public static SourceBuilder newBuilder(String language, TruffleFile file) {
        return EMPTY.new LiteralBuilder(language, file);
    }

    /*
     * Internal constructor only for polyglot sources.
     */
    static SourceBuilder newBuilder(String language, File source) {
        return EMPTY.new LiteralBuilder(language, source);
    }

    /**
     * Creates a new URL based source builder from a given URL. A URL based source is either
     * interpreted as {@link Source#hasBytes() binary} or {@link Source#hasCharacters() character}
     * source depending on the {@link LanguageInfo#getDefaultMimeType() default MIME type} of the
     * language, the {@link SourceBuilder#content(ByteSequence) contents} or the specified
     * {@link SourceBuilder#mimeType(String) MIME type}. A language may be detected from an existing
     * file using {@link #findLanguage(URL)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromURL}
     *
     * @param language the language id, must not be <code>null</code>
     * @param url the URL to use and load, must not be <code>null</code>
     * @since 19.0
     */
    public static SourceBuilder newBuilder(String language, URL url) {
        return EMPTY.new LiteralBuilder(language, url);
    }

    /**
     * Creates new character based literal source from a reader.
     * <p>
     * Use this method for sources that do originate from a literal. For file or URL sources use the
     * appropriate builder constructor and {@link SourceBuilder#content(CharSequence)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromReader}
     *
     * @since 19.0
     */
    public static SourceBuilder newBuilder(String language, Reader source, String name) {
        return EMPTY.new LiteralBuilder(language, source).name(name);
    }

    /**
     * Creates a new source builder that inherits from the given Source. The Source properties can
     * be modified using the builder methods.
     *
     * @param source the source to inherit the properties from
     * @since 19.2.0
     */
    public static LiteralBuilder newBuilder(Source source) {
        return EMPTY.new LiteralBuilder(source);
    }

    /**
     * @since 0.15
     * @deprecated use {@link #newBuilder(String, TruffleFile, String)}
     */
    @Deprecated
    public static Builder<IOException, RuntimeException, RuntimeException> newBuilder(File file) {
        return EMPTY.new Builder<>(file);
    }

    /**
     * @since 0.15
     * @deprecated use {@link #newBuilder(String, CharSequence, String)}
     */
    @Deprecated
    public static Builder<RuntimeException, MissingMIMETypeException, MissingNameException> newBuilder(String text) {
        return newBuilder((CharSequence) text);
    }

    /**
     * @since 0.28
     * @deprecated use {@link #newBuilder(String, CharSequence, String)}
     */
    @Deprecated
    public static Builder<RuntimeException, MissingMIMETypeException, MissingNameException> newBuilder(CharSequence characters) {
        return EMPTY.new Builder<>(characters);
    }

    /**
     * @since 0.15
     * @deprecated use {@link #newBuilder(String, URL)}
     */
    @Deprecated
    public static Builder<IOException, RuntimeException, RuntimeException> newBuilder(URL url) {
        return EMPTY.new Builder<>(url);
    }

    /**
     * @since 0.15
     * @deprecated use {@link #newBuilder(String, Reader, String)}
     */
    @Deprecated
    public static Builder<IOException, MissingMIMETypeException, MissingNameException> newBuilder(Reader reader) {
        return EMPTY.new Builder<>(reader);
    }

    /**
     * Returns the first language that supports evaluating the probed mime type of a given
     * {@link File file}. This method is a shortcut for:
     *
     * <code>
     * <pre>
     * String mimeType = Source.findMimeType(file);
     * return mimeType != null ? Source.findLanguage(mimeType) : null;
     * </pre>
     * </code>
     *
     * @param file the file to find the first language for
     * @throws IOException if an error opening the file occurred.
     * @see #findMimeType(URL)
     * @see #findLanguage(String)
     * @since 19.0
     */
    public static String findLanguage(TruffleFile file) throws IOException {
        String mimeType = findMimeType(file);
        return mimeType != null ? findLanguage(mimeType) : null;
    }

    /**
     * Returns the first language that supports evaluating the probed mime type of a given
     * {@link URL URL}. This method is a shortcut for:
     *
     * <code>
     * <pre>
     * String mimeType = Source.findMimeType(url);
     * return mimeType != null ? Source.findLanguage(mimeType) : null;
     * </pre>
     * </code>
     *
     * @param url the url to find the first language for
     * @throws IOException if an error opening the url occurred.
     * @see #findMimeType(URL)
     * @see #findLanguage(String)
     * @since 19.0
     */
    public static String findLanguage(URL url) throws IOException {
        String mimeType = findMimeType(url);
        return mimeType != null ? findLanguage(mimeType) : null;
    }

    /**
     * Returns the probed MIME type for a given file, or <code>null</code> if no MIME type could be
     * resolved. Typically the MIME type is identified using the file extension and/or using its
     * contents. Probing the MIME type of an {@link TruffleFile} may require to opening the file.
     *
     * @throws IOException if an error opening the file occurred.
     * @throws SecurityException if the used {@link FileSystem filesystem} denied file reading.
     * @see #findLanguage(TruffleFile)
     * @since 19.0
     */
    public static String findMimeType(TruffleFile file) throws IOException {
        return file.getMimeType();
    }

    /**
     * Returns the probed MIME type for a given url, or <code>null</code> if no MIME type could be
     * resolved. Typically the MIME type is identified using the file extension, connection
     * meta-data and/or using it contents. Returns <code>null</code> if the language of the given
     * file could not be detected. Probing the language of an URL may require to open a new URL
     * connection.
     *
     * @throws IOException if an error opening the url occurred.
     * @throws SecurityException if the used {@link FileSystem filesystem} denied file reading.
     * @see #findLanguage(URL)
     * @since 19.0
     */
    public static String findMimeType(URL url) throws IOException {
        return findMimeType(url, url.openConnection(), null, SourceAccessor.getCurrentFileSystemContext());
    }

    /**
     * Returns the first installed language that supports evaluating sources for a given MIME type.
     * Returns <code>null</code> if no language was found that supports a given MIME type. The
     * languages are queried in the same order as returned by {@link Engine#getLanguages()}.
     *
     * @since 19.0
     */
    public static String findLanguage(String mimeType) {
        return org.graalvm.polyglot.Source.findLanguage(mimeType);
    }

    private static IllegalArgumentException invalidMimeType() {
        return new IllegalArgumentException("Invalid MIME type provided. MIME types consist of a type and a subtype separated by '/'.");
    }

    private static final int[] S = new int[]{
                    0x29, 0x2E, 0x43, 0xC9, 0xA2, 0xD8, 0x7C, 0x01, 0x3D, 0x36, 0x54, 0xA1, 0xEC, 0xF0, 0x06, 0x13,
                    0x62, 0xA7, 0x05, 0xF3, 0xC0, 0xC7, 0x73, 0x8C, 0x98, 0x93, 0x2B, 0xD9, 0xBC, 0x4C, 0x82, 0xCA,
                    0x1E, 0x9B, 0x57, 0x3C, 0xFD, 0xD4, 0xE0, 0x16, 0x67, 0x42, 0x6F, 0x18, 0x8A, 0x17, 0xE5, 0x12,
                    0xBE, 0x4E, 0xC4, 0xD6, 0xDA, 0x9E, 0xDE, 0x49, 0xA0, 0xFB, 0xF5, 0x8E, 0xBB, 0x2F, 0xEE, 0x7A,
                    0xA9, 0x68, 0x79, 0x91, 0x15, 0xB2, 0x07, 0x3F, 0x94, 0xC2, 0x10, 0x89, 0x0B, 0x22, 0x5F, 0x21,
                    0x80, 0x7F, 0x5D, 0x9A, 0x5A, 0x90, 0x32, 0x27, 0x35, 0x3E, 0xCC, 0xE7, 0xBF, 0xF7, 0x97, 0x03,
                    0xFF, 0x19, 0x30, 0xB3, 0x48, 0xA5, 0xB5, 0xD1, 0xD7, 0x5E, 0x92, 0x2A, 0xAC, 0x56, 0xAA, 0xC6,
                    0x4F, 0xB8, 0x38, 0xD2, 0x96, 0xA4, 0x7D, 0xB6, 0x76, 0xFC, 0x6B, 0xE2, 0x9C, 0x74, 0x04, 0xF1,
                    0x45, 0x9D, 0x70, 0x59, 0x64, 0x71, 0x87, 0x20, 0x86, 0x5B, 0xCF, 0x65, 0xE6, 0x2D, 0xA8, 0x02,
                    0x1B, 0x60, 0x25, 0xAD, 0xAE, 0xB0, 0xB9, 0xF6, 0x1C, 0x46, 0x61, 0x69, 0x34, 0x40, 0x7E, 0x0F,
                    0x55, 0x47, 0xA3, 0x23, 0xDD, 0x51, 0xAF, 0x3A, 0xC3, 0x5C, 0xF9, 0xCE, 0xBA, 0xC5, 0xEA, 0x26,
                    0x2C, 0x53, 0x0D, 0x6E, 0x85, 0x28, 0x84, 0x09, 0xD3, 0xDF, 0xCD, 0xF4, 0x41, 0x81, 0x4D, 0x52,
                    0x6A, 0xDC, 0x37, 0xC8, 0x6C, 0xC1, 0xAB, 0xFA, 0x24, 0xE1, 0x7B, 0x08, 0x0C, 0xBD, 0xB1, 0x4A,
                    0x78, 0x88, 0x95, 0x8B, 0xE3, 0x63, 0xE8, 0x6D, 0xE9, 0xCB, 0xD5, 0xFE, 0x3B, 0x00, 0x1D, 0x39,
                    0xF2, 0xEF, 0xB7, 0x0E, 0x66, 0x58, 0xD0, 0xE4, 0xA6, 0x77, 0x72, 0xF8, 0xEB, 0x75, 0x4B, 0x0A,
                    0x31, 0x44, 0x50, 0xB4, 0x8F, 0xED, 0x1F, 0x1A, 0xDB, 0x99, 0x8D, 0x33, 0x9F, 0x11, 0x83, 0x14
    };

    private static final boolean ALLOW_IO = SourceAccessor.ACCESSOR.engineSupport().isIOAllowed();

    static Source buildSource(String language, Object origin, String name, String path, String mimeType, Object content, URL url, URI uri, Charset encoding,
                    boolean internal, boolean interactive, boolean cached, boolean legacy, Supplier<Object> fileSystemContext) throws IOException {
        String useName = name;
        URI useUri = uri;
        Object useContent = content;
        String useMimeType = mimeType;
        String usePath = path;
        URL useUrl = url;
        Object useOrigin = origin;
        Charset useEncoding = encoding;

        if (useOrigin instanceof File) {
            final File file = (File) useOrigin;
            TruffleFile truffleFile = SourceAccessor.getTruffleFile(file.toPath().toString(), fileSystemContext.get());
            useOrigin = truffleFile;
        }

        if (useOrigin == CONTENT_UNSET) {
            useContent = useContent == CONTENT_UNSET ? null : useContent;
        } else if (useOrigin instanceof TruffleFile) {
            TruffleFile file = (TruffleFile) useOrigin;
            if (!file.isAbsolute() && useContent == CONTENT_NONE) {
                if (useUri == null) {
                    useUri = file.toRelativeUri();
                }
            } else {
                file = file.exists() ? file.getCanonicalFile() : file;
                if (useUri == null) {
                    useUri = file.toUri();
                }
            }
            useName = useName == null ? file.getName() : useName;
            usePath = usePath == null ? file.getPath() : usePath;
            useMimeType = useMimeType == null ? SourceAccessor.getMimeType(file, getValidMimeTypes(language)) : useMimeType;
            if (legacy) {
                useMimeType = useMimeType == null ? UNKNOWN_MIME_TYPE : useMimeType;
                useEncoding = useEncoding == null ? getEncoding(file, useMimeType) : useEncoding;
                useContent = useContent == CONTENT_UNSET ? read(file, useEncoding) : useContent;
            } else {
                if (useContent == CONTENT_UNSET) {
                    if (isCharacterBased(language, useMimeType)) {
                        useEncoding = useEncoding == null ? getEncoding(file, useMimeType) : useEncoding;
                        useContent = read(file, useEncoding);
                    } else {
                        useContent = ByteSequence.create(file.readAllBytes());
                    }
                }
            }
        } else if (useOrigin instanceof URL) {
            useUrl = (URL) useOrigin;
            String urlPath = useUrl.getPath();
            int lastIndex = urlPath.lastIndexOf('/');
            useName = useName == null && lastIndex != -1 ? useUrl.getPath().substring(lastIndex + 1) : useName;
            URI tmpUri;
            try {
                tmpUri = useUrl.toURI();
            } catch (URISyntaxException ex) {
                throw new IOException("Bad URL: " + useUrl, ex);
            }
            useUri = useUri == null ? tmpUri : useUri;
            usePath = usePath == null ? useUrl.getPath() : usePath;
            try {
                TruffleFile truffleFile = SourceAccessor.getTruffleFile(tmpUri, fileSystemContext.get());
                if (legacy) {
                    useMimeType = useMimeType == null ? SourceAccessor.getMimeType(truffleFile, getValidMimeTypes(language)) : useMimeType;
                    useMimeType = useMimeType == null ? UNKNOWN_MIME_TYPE : useMimeType;
                    useEncoding = useEncoding == null ? getEncoding(truffleFile, useMimeType) : useEncoding;
                    useContent = useContent == CONTENT_UNSET ? read(truffleFile, useEncoding) : useContent;
                } else {
                    if (useContent == CONTENT_UNSET) {
                        if (isCharacterBased(language, useMimeType)) {
                            useEncoding = useEncoding == null ? getEncoding(truffleFile, useMimeType) : useEncoding;
                            useContent = read(truffleFile, useEncoding);
                        } else {
                            useContent = ByteSequence.create(truffleFile.readAllBytes());
                        }
                    }
                }
            } catch (FileSystemNotFoundException fsnf) {
                if (ALLOW_IO && SourceAccessor.isDefaultFileSystem(fileSystemContext.get())) {
                    // Not a recognized by FileSystem, fall back to URLConnection only for allowed
                    // IO without a custom FileSystem
                    URLConnection connection = useUrl.openConnection();
                    useEncoding = useEncoding == null ? StandardCharsets.UTF_8 : useEncoding;
                    if (legacy) {
                        useMimeType = useMimeType == null ? findMimeType(useUrl, connection, getValidMimeTypes(language), fileSystemContext.get()) : useMimeType;
                        useMimeType = useMimeType == null ? UNKNOWN_MIME_TYPE : useMimeType;
                        useContent = useContent == CONTENT_UNSET ? read(new InputStreamReader(connection.getInputStream(), useEncoding)) : useContent;
                    } else {
                        if (useContent == CONTENT_UNSET) {
                            if (isCharacterBased(language, useMimeType)) {
                                useContent = read(new InputStreamReader(connection.getInputStream(), useEncoding));
                            } else {
                                useContent = ByteSequence.create(readBytes(connection));
                            }
                        }
                    }
                } else {
                    throw new SecurityException("Reading of URL " + useUrl + " is not allowed.");
                }
            }
        } else if (useOrigin instanceof Reader) {
            final Reader r = (Reader) useOrigin;
            useContent = useContent == CONTENT_UNSET ? read(r) : useContent;
        } else if (useOrigin instanceof ByteSequence) {
            useContent = useContent == CONTENT_UNSET ? useOrigin : useContent;
        } else {
            assert useOrigin instanceof CharSequence;
            useContent = useContent == CONTENT_UNSET ? useOrigin : useContent;
        }
        if (!legacy && useName == null) {
            useName = "Unnamed";
        }

        useContent = enforceInterfaceContracts(useContent);
        SourceImpl.Key key = new SourceImpl.Key(useContent, useMimeType, language, useUrl, useUri, useName, usePath, internal, interactive, cached, legacy);
        return SOURCES.intern(key);
    }

    static byte[] readBytes(URLConnection connection) throws IOException {
        long size = connection.getContentLengthLong();
        if (size < 0) {
            size = BUFFER_SIZE;
        } else {
            if (size > Integer.MAX_VALUE) {
                throw new OutOfMemoryError("Too many bytes.");
            }
        }
        try (InputStream inputStream = connection.getInputStream()) {
            return readBytes(inputStream, (int) size);
        }
    }

    /*
     * Copied from Files#read
     */
    private static byte[] readBytes(InputStream source, int initialSize) throws IOException {
        int capacity = initialSize;
        byte[] buf = new byte[capacity];
        int nread = 0;
        int n;
        for (;;) {
            // read to EOF which may read more or less than initialSize (eg: file
            // is truncated while we are reading)
            while ((n = source.read(buf, nread, capacity - nread)) > 0) {
                nread += n;
            }

            // if last call to source.read() returned -1, we are done
            // otherwise, try to read one more byte; if that failed we're done too
            if (n < 0 || (n = source.read()) < 0) {
                break;
            }

            // one more byte was read; need to allocate a larger buffer
            if (capacity <= MAX_BUFFER_SIZE - capacity) {
                capacity = Math.max(capacity << 1, BUFFER_SIZE);
            } else {
                if (capacity == MAX_BUFFER_SIZE) {
                    throw new OutOfMemoryError("Required array size too large");
                }
                capacity = MAX_BUFFER_SIZE;
            }
            buf = Arrays.copyOf(buf, capacity);
            buf[nread++] = (byte) n;
        }
        return (capacity == nread) ? buf : Arrays.copyOf(buf, nread);
    }

    static String read(TruffleFile file, Charset encoding) throws IOException {
        return new String(file.readAllBytes(), encoding);
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

    static String digest(byte[] message, int from, int length) {
        int[] m = new int[19];
        int[] x = new int[48];
        int[] c = new int[16];

        int t;
        int loop = 1;
        int start = 0;
        int bytes = 0;

        for (int i = 0; i < 16; ++i) {
            x[i] = c[i] = 0;
        }

        int last = 0;
        int index = from;
        m[16] = m[17] = m[18] = 0;
        while (loop == 1) {
            m[0] = m[16];
            m[1] = m[17];
            m[2] = m[18];
            for (int i = 3; i < 16; i++) {
                m[i] = 0;
            }
            int i;
            for (i = start; index < length && i < 16; ++index) {
                int code = message[index];
                if (code < 0) {
                    code += 256;
                }
                m[i++] = code;
            }
            bytes += i - start;
            start = i - 16;

            if (index == length && i < 16) {
                loop = 2;
                t = 16 - (bytes & 15);
                for (; i < 16; ++i) {
                    m[i] = t;
                }
            }

            for (i = 0; i < 16; ++i) {
                c[i] ^= S[m[i] ^ last];
                last = c[i];
            }

            for (i = 0; i < loop; ++i) {
                int[] mOrC = i == 0 ? m : c;

                x[16] = mOrC[0];
                x[32] = x[16] ^ x[0];
                x[17] = mOrC[1];
                x[33] = x[17] ^ x[1];
                x[18] = mOrC[2];
                x[34] = x[18] ^ x[2];
                x[19] = mOrC[3];
                x[35] = x[19] ^ x[3];
                x[20] = mOrC[4];
                x[36] = x[20] ^ x[4];
                x[21] = mOrC[5];
                x[37] = x[21] ^ x[5];
                x[22] = mOrC[6];
                x[38] = x[22] ^ x[6];
                x[23] = mOrC[7];
                x[39] = x[23] ^ x[7];
                x[24] = mOrC[8];
                x[40] = x[24] ^ x[8];
                x[25] = mOrC[9];
                x[41] = x[25] ^ x[9];
                x[26] = mOrC[10];
                x[42] = x[26] ^ x[10];
                x[27] = mOrC[11];
                x[43] = x[27] ^ x[11];
                x[28] = mOrC[12];
                x[44] = x[28] ^ x[12];
                x[29] = mOrC[13];
                x[45] = x[29] ^ x[13];
                x[30] = mOrC[14];
                x[46] = x[30] ^ x[14];
                x[31] = mOrC[15];
                x[47] = x[31] ^ x[15];

                t = 0;
                for (int j = 0; j < 18; ++j) {
                    for (int k = 0; k < 48; ++k) {
                        x[k] = t = x[k] ^ S[t];
                    }
                    t = (t + j) & 0xFF;
                }
            }
        }

        StringBuilder result = new StringBuilder(32);
        for (int i = 0; i < 16; ++i) {
            final String hex = Integer.toHexString(x[i]);
            if (result.length() == 0) {
                if (hex.equals("0")) {
                    continue;
                }
            } else {
                if (hex.length() == 1) {
                    result.append("0");
                }
            }
            result.append(hex);
        }
        return result.toString();
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <E extends Exception> E raise(Class<E> type, Exception ex) throws E {
        throw (E) ex;
    }

    @SuppressWarnings("all")
    static Object enforceInterfaceContracts(Object sequence) {
        boolean assertions = false;
        assert assertions = true;
        if (assertions) {
            if (sequence instanceof CharSequence) {
                return enforceCharSequenceContracts((CharSequence) sequence);
            } else if (sequence != null) {
                assert sequence instanceof ByteSequence;
                return enforceByteSequenceContracts((ByteSequence) sequence);
            }
        }
        return sequence;
    }

    static ByteSequence enforceByteSequenceContracts(ByteSequence sequence) {
        if (BYTE_SEQUENCE_CLASS.isInstance(sequence)) {
            return sequence;
        } else if (sequence instanceof ByteSequenceWrapper) {
            // already wrapped
            return sequence;
        } else {
            return new ByteSequenceWrapper(sequence);
        }
    }

    static CharSequence enforceCharSequenceContracts(CharSequence sequence) {
        if (sequence instanceof String) {
            return sequence;
        } else if (sequence instanceof CharSequenceWrapper) {
            // already wrapped
            return sequence;
        } else {
            return new CharSequenceWrapper(sequence);
        }
    }

    static String findMimeType(final URL url, URLConnection connection, Set<String> validMimeTypes, Object fileSystemContext) throws IOException {
        try {
            URI uri = url.toURI();
            TruffleFile file = SourceAccessor.getTruffleFile(uri, fileSystemContext);
            String firstGuess = SourceAccessor.getMimeType(file, validMimeTypes);
            if (firstGuess != null) {
                return firstGuess;
            }
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException ex) {
            // swallow and go on
        }

        if (!ALLOW_IO || !SourceAccessor.isDefaultFileSystem(fileSystemContext)) {
            throw new SecurityException("Reading of URL " + url + " is not allowed.");
        }

        String contentType = connection.getContentType();
        if (contentType != null && (validMimeTypes == null || validMimeTypes.contains(contentType))) {
            return contentType;
        }
        return null;
    }

    static boolean isCharacterBased(String language, String mimeType) {
        if (language == null) {
            return true;
        }
        EngineSupport support = SourceAccessor.ACCESSOR.engineSupport();
        if (support == null) {
            return true;
        }
        return support.isCharacterBasedSource(language, mimeType);
    }

    static Set<String> getValidMimeTypes(String language) {
        EngineSupport support = SourceAccessor.ACCESSOR.engineSupport();
        if (support == null) {
            return null;
        }
        return support.getValidMimeTypes(language);
    }

    private static void validateMimeType(String mimeType) {
        if (mimeType == null) {
            return;
        }
        int index = mimeType.indexOf('/');
        if (index == -1 || index == 0 || index == mimeType.length() - 1) {
            throw invalidMimeType();
        }
        if (mimeType.indexOf('/', index + 1) != -1) {
            throw invalidMimeType();
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <E extends Exception> RuntimeException silenceException(Class<E> type, Exception ex) throws E {
        throw (E) ex;
    }

    private static Charset getEncoding(TruffleFile file, String mimeType) throws IOException {
        Charset encoding = SourceAccessor.getEncoding(file, mimeType);
        encoding = encoding == null ? StandardCharsets.UTF_8 : encoding;
        return encoding;
    }

    /**
     * Allows one to specify additional attribute before {@link #build() creating} new
     * {@link Source} instance.
     *
     * To load a source from disk one can use:
     * <p>
     * {@link SourceSnippets#fromFile}
     * <p>
     * To load source from a {@link URL} one can use:
     * <p>
     * {@link SourceSnippets#fromURL}
     * <p>
     * To create a source representing characters:
     * <p>
     * {@link SourceSnippets#fromAString}
     * <p>
     * or read a source from a {@link Reader}:
     * <p>
     * {@link SourceSnippets#fromReader}
     * <p>
     * To create a source representing bytes:
     * <p>
     * {@link SourceSnippets#fromBytes}
     *
     * Once your builder is configured, call {@link #build()} to perform the loading and
     * construction of new {@link Source}.
     *
     * @since 19.0
     */
    public class SourceBuilder {

        private final String language;
        private final Object origin;
        private URI uri;
        URL url;
        private String name;
        String path;
        private String mimeType;
        private Object content = CONTENT_UNSET;
        private boolean internal;
        private boolean interactive;
        private boolean cached = true;
        private Charset fileEncoding;
        private Object embedderFileSystemContext;

        SourceBuilder(String language, Object origin) {
            Objects.requireNonNull(language);
            Objects.requireNonNull(origin);
            this.language = language;
            this.origin = origin;
        }

        /**
         * Specifies a name to the {@link #build() to-be-built} {@link Source}.
         *
         * @param newName name that replaces the previously given one. If set to <code>null</code>
         *            then <code>"Unnamed"</code> will be used.
         * @return instance of <code>this</code> builder
         * @since 19.0
         */
        public SourceBuilder name(String newName) {
            this.name = newName;
            return this;
        }

        /**
         * Specifies character based content of {@link #build() to-be-built} {@link Source}. Using
         * this method one can ignore the real content of a file or URL and use already read one, or
         * completely different one. Use {@link #CONTENT_NONE} to set no content,
         * {@link Source#hasCharacters()} will be <code>false</code> then. The given characters must
         * not mutate after they were accessed for the first time. Example:
         *
         * {@link SourceSnippets#fromURLWithOwnContent}
         *
         * @param characters the code to be available via {@link Source#getCharacters()}, or
         *            {@link #CONTENT_NONE}
         * @return instance of this builder - which's {@link #build()} method no longer throws an
         *         {@link IOException}
         * @since 19.0
         */
        public LiteralBuilder content(CharSequence characters) {
            this.content = characters;
            return (LiteralBuilder) this;
        }

        /**
         * Specifies byte based content of {@link #build() to-be-built} {@link Source}. Using this
         * method one can ignore the real content of a file or URL and use already read one, or
         * completely different one. The given bytes must not mutate after they were accessed for
         * the first time. Example:
         *
         * {@link SourceSnippets#fromURLWithOwnContent}
         *
         * @param bytes the code to be available via {@link Source#getBytes()}
         * @return instance of this builder - which's {@link #build()} method no longer throws an
         *         {@link IOException}
         * @since 19.0
         */
        public LiteralBuilder content(ByteSequence bytes) {
            this.content = bytes;
            return (LiteralBuilder) this;
        }

        /**
         * Explicitly assigns a {@link Source#getMimeType() MIME type} to the {@link #build()
         * to-be-built} {@link Source}. If the MIME type is <code>null</code> then the
         * {@link Language#getDefaultMimeType() default MIME type} of the language will be used to
         * interpret the source. If set explicitly then the language needs to
         * {@link Language#getMimeTypes() support} the MIME type in order to
         * {@link com.oracle.truffle.api.TruffleLanguage.Env#parsePublic(Source, String...) parse} a
         * source. If not <code>null</code> then the MIME type will be verified containing no spaces
         * and a '/' between group and sub-group. An example for a valid MIME type is:
         * <code>text/javascript</code>.
         * <p>
         * The MIME type can be guessed by the system based on {@link #findMimeType(TruffleFile)
         * files} or {@link #findMimeType(URL) urls}.
         *
         * @see LanguageInfo#getDefaultMimeType()
         * @see LanguageInfo#getMimeTypes()
         * @see Source#findMimeType(TruffleFile)
         * @see Source#findMimeType(URL)
         * @param mimeType the new mime type to be assigned, or <code>null</code> if default MIME
         *            type should be used.
         * @return instance of <code>this</code> builder ready to {@link #build() create new source}
         * @since 19.0
         */
        public SourceBuilder mimeType(@SuppressWarnings("hiding") String mimeType) {
            validateMimeType(mimeType);
            this.mimeType = mimeType;
            return this;
        }

        /**
         * Enables or disables code caching for this source. By default code caching is enabled. If
         * <code>true</code> then the source does not require parsing every time this source is
         * evaluated. If <code>false</code> then the source requires parsing every time the source
         * is evaluated but does not remember any code. Disabling caching may be useful if the
         * source is known to only be evaluated once.
         * <p>
         * If a source instance is no longer referenced by the client then all code caches will be
         * freed automatically. Also, if the underlying context or engine is no longer referenced
         * then cached code for evaluated sources will be freed automatically.
         *
         * @return instance of <code>this</code> builder ready to {@link #build() create new source}
         * @since 19.0
         */
        public SourceBuilder cached(boolean enabled) {
            this.cached = enabled;
            return this;
        }

        /**
         * Marks the source as internal. Internal sources are those that aren't created by user, but
         * rather inherently present by the language system. Calling this method influences result
         * of create {@link Source#isInternal()}
         *
         * @return the instance of this builder
         * @since 19.0
         */
        public SourceBuilder internal(boolean enabled) {
            this.internal = enabled;
            return this;
        }

        /**
         * Marks the source as interactive. Evaluation of interactive sources by an
         * {@link com.oracle.truffle.api.TruffleLanguage.Registration#interactive() interactive
         * language} can use the {@link com.oracle.truffle.api.TruffleLanguage.Env environment}
         * streams to print the result and read an input. However, non-interactive languages are
         * expected to ignore the interactive property of sources and not use the environment
         * streams. Any desired printing of the evaluated result provided by a non-interactive
         * language needs to be handled by the caller. Calling of this method influences the result
         * of {@link Source#isInteractive()}.
         *
         * @return the instance of this builder
         * @since 19.0
         */
        public SourceBuilder interactive(boolean enabled) {
            this.interactive = enabled;
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
         * @since 19.0
         */
        public SourceBuilder uri(URI ownUri) {
            this.uri = ownUri;
            return this;
        }

        /**
         * Explicitly assigns an encoding used to read the file content. If the encoding is
         * {@code null} then the file contained encoding information is used. If the file doesn't
         * provide an encoding information the default {@code UTF-8} encoding is used.
         *
         * @param encoding the new file encoding to be used for reading the content
         * @return instance of <code>this</code> builder ready to {@link #build() create new source}
         * @since 19.0
         */
        public SourceBuilder encoding(Charset encoding) {
            this.fileEncoding = encoding;
            return this;
        }

        SourceBuilder embedderFileSystemContext(Object fileSystemContext) {
            this.embedderFileSystemContext = fileSystemContext;
            return this;
        }

        /**
         * Uses configuration of this builder to create new {@link Source} object. The method throws
         * an {@link IOException} if an error loading the source occured.
         *
         * @return the source object
         * @throws IOException if an error reading the content occurred
         * @throws SecurityException if the used {@link FileSystem filesystem} denied file reading
         * @since 19.0
         */
        public Source build() throws IOException {
            assert this.language != null;
            Source source = buildSource(this.language, this.origin, this.name, this.path, this.mimeType, this.content, this.url, this.uri, this.fileEncoding, this.internal, this.interactive,
                            this.cached, false, new FileSystemContextSupplier(embedderFileSystemContext));

            // make sure origin is not consumed again if builder is used twice
            if (source.hasBytes()) {
                this.content = source.getBytes();
            } else if (source.hasCharacters()) {
                this.content = source.getCharacters();
            }

            assert source.getName() != null;
            assert !source.hasCharacters() || source.getCharacters() != null;
            assert !source.hasBytes() || source.getBytes() != null;
            assert source.getLanguage() != null;

            return source;
        }

    }

    private static Object getSourceContent(Source source) {
        Object content = ((SourceImpl) source).toKey().content;
        if (content == CONTENT_NONE) {
            return CONTENT_UNSET;
        } else {
            return content;
        }
    }

    /**
     * Allows one to specify additional attribute before {@link #build() creating} new
     * {@link Source} instance.
     *
     * @see SourceBuilder For examples on how to use it.
     * @since 19.0
     */
    public final class LiteralBuilder extends SourceBuilder {

        LiteralBuilder(String language, Object origin) {
            super(language, origin);
        }

        LiteralBuilder(Source source) {
            super(source.getLanguage(), getSourceContent(source));
            cached(source.isCached());
            interactive(source.isInteractive());
            internal(source.isInternal());
            mimeType(source.getMimeType());
            name(source.getName());
            uri(((SourceImpl) source).toKey().uri);
            path = source.getPath();
            url = source.getURL();
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.0
         */
        @Override
        public LiteralBuilder name(String newName) {
            return (LiteralBuilder) super.name(newName);
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.0
         */
        @Override
        public LiteralBuilder mimeType(String newMimeType) {
            return (LiteralBuilder) super.mimeType(newMimeType);
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.0
         */
        @Override
        public LiteralBuilder cached(boolean cached) {
            return (LiteralBuilder) super.cached(cached);
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.0
         */
        @Override
        public LiteralBuilder internal(boolean enabled) {
            return (LiteralBuilder) super.internal(enabled);
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.0
         */
        @Override
        public LiteralBuilder interactive(boolean enabled) {
            return (LiteralBuilder) super.interactive(enabled);
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.0
         */
        @Override
        public LiteralBuilder uri(URI ownUri) {
            return (LiteralBuilder) super.uri(ownUri);
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.0
         */
        @Override
        public LiteralBuilder encoding(Charset encoding) {
            return (LiteralBuilder) super.encoding(encoding);
        }

        /**
         * Uses configuration of this builder to create new {@link Source} object.
         *
         * @return the source object
         * @throws SecurityException if the used {@link FileSystem filesystem} denied file reading
         * @since 19.0
         */
        @Override
        public Source build() {
            try {
                return super.build();
            } catch (IOException e) {
                throw silenceException(RuntimeException.class, e);
            }
        }
    }

    /**
     * @since 0.15
     * @deprecated use {@link SourceBuilder} instead.
     */
    @Deprecated
    public class Builder<E1 extends Exception, E2 extends Exception, E3 extends Exception> {
        private final Object origin;
        private URI uri;
        private String name;
        private String mime;
        private String language;
        private CharSequence characters = CONTENT_UNSET;
        private boolean internal;
        private boolean interactive;
        private boolean cached = true;

        Builder(Object origin) {
            this.origin = origin;
        }

        /**
         * @since 0.15
         * @deprecated see {@link SourceBuilder#name(String)}
         */
        @Deprecated
        @SuppressWarnings("unchecked")
        public Builder<E1, E2, RuntimeException> name(String newName) {
            Objects.requireNonNull(newName);
            this.name = newName;
            return (Builder<E1, E2, RuntimeException>) this;
        }

        /**
         * @since 0.15
         * @deprecated see {@link SourceBuilder#mimeType(String)}
         */
        @Deprecated
        @SuppressWarnings("unchecked")
        public Builder<E1, RuntimeException, E3> mimeType(String newMimeType) {
            Objects.requireNonNull(newMimeType);
            this.mime = newMimeType;
            return (Builder<E1, RuntimeException, E3>) this;
        }

        /**
         * @since 19.0
         * @deprecated see {@link SourceBuilder#cached(boolean)}
         */
        @Deprecated
        public Builder<E1, E2, E3> cached(@SuppressWarnings("hiding") boolean cached) {
            this.cached = cached;
            return this;
        }

        /**
         * @since 0.28
         * @deprecated without replacement. The language is now provided when a
         *             {@link SourceBuilder} is constructed.
         */
        @Deprecated
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
         * @since 0.15
         * @deprecated see {@link SourceBuilder#internal(boolean)}
         */
        @Deprecated
        public Builder<E1, E2, E3> internal() {
            this.internal = true;
            return this;
        }

        /**
         * @since 0.21
         * @deprecated see {@link SourceBuilder#interactive(boolean)}
         */
        @Deprecated
        public Builder<E1, E2, E3> interactive() {
            this.interactive = true;
            return this;
        }

        /**
         * @since 0.15
         * @deprecated see {@link SourceBuilder#content(CharSequence)}
         */
        @Deprecated
        public Builder<E1, E2, E3> uri(URI ownUri) {
            Objects.requireNonNull(ownUri);
            this.uri = ownUri;
            return this;
        }

        /**
         * @since 0.15
         * @deprecated see {@link SourceBuilder#content(CharSequence)}
         */
        @SuppressWarnings("unchecked")
        @Deprecated
        public Builder<RuntimeException, E2, E3> content(String code) {
            this.characters = code;
            return (Builder<RuntimeException, E2, E3>) this;
        }

        /**
         * @since 0.28
         * @deprecated see {@link SourceBuilder#content(CharSequence)}
         */
        @SuppressWarnings("unchecked")
        @Deprecated
        public Builder<RuntimeException, E2, E3> content(CharSequence code) {
            this.characters = code;
            return (Builder<RuntimeException, E2, E3>) this;
        }

        /**
         * @since 0.15
         * @throws SecurityException if the used {@link FileSystem filesystem} denied file reading
         * @deprecated see {@link SourceBuilder#build()}
         */
        @SuppressWarnings("unused")
        @Deprecated
        public Source build() throws E1, E2, E3 {
            try {
                Source source = buildSource(this.language, this.origin, this.name, null, this.mime, this.characters, null, this.uri, null, this.internal, this.interactive, this.cached, true,
                                new FileSystemContextSupplier(null));

                // legacy sources must have character sources
                assert source.hasCharacters();

                // make sure origin is not consumed again if builder is used twice
                this.characters = source.getCharacters();

                if (source.getMimeType() == null) {
                    throw raise(RuntimeException.class, new MissingMIMETypeException());
                }
                if (source.getName() == null) {
                    throw raise(RuntimeException.class, new MissingNameException());
                }
                return source;
            } catch (IOException ex) {
                throw raise(RuntimeException.class, ex);
            }
        }
    }

    private static final class FileSystemContextSupplier implements Supplier<Object> {

        private Object fileSystemContext;

        FileSystemContextSupplier(Object fileSystemContext) {
            this.fileSystemContext = fileSystemContext;
        }

        @Override
        public Object get() {
            return fileSystemContext == null ? SourceAccessor.getCurrentFileSystemContext() : fileSystemContext;
        }
    }

    static {
        // force loading source accessor
        SourceAccessor.allLoaders();
    }
}

// @formatter:off
// Checkstyle: stop
class SourceSnippets {
    public static Source fromFile(TruffleFile file) throws IOException {
        // BEGIN: SourceSnippets#fromFile
        assert file.getName().endsWith(".java") :
            "Imagine 'c:\\sources\\Example.java' file";

        String language = Source.findLanguage(file);
        Source source = Source.newBuilder(language, file).build();

        assert file.getName().equals(source.getName());
        assert file.getPath().equals(source.getPath());
        assert file.toUri().equals(source.getURI());
        // END: SourceSnippets#fromFile
        return source;
    }

    public static Source likeFileName(TruffleFile file) throws IOException {
        // BEGIN: SourceSnippets#likeFileName
        String language = Source.findLanguage(file);
        Source source = Source.newBuilder(language, file.getCanonicalFile()).
            name(file.getPath()).
            build();
        // END: SourceSnippets#likeFileName
        return source;
    }

    public static Source fromURL(Class<?> relativeClass) throws IOException, URISyntaxException {
        // BEGIN: SourceSnippets#fromURL
        URL resource = relativeClass.getResource("sample.js");
        Source source = Source.newBuilder("js", resource)
                        .build();
        assert resource.toExternalForm().equals(source.getPath());
        assert "sample.js".equals(source.getName());
        assert resource.toURI().equals(source.getURI());
        // END: SourceSnippets#fromURL
        return source;
    }

    public static Source fromURLWithOwnContent(Class<?> relativeClass) {
        // BEGIN: SourceSnippets#fromURLWithOwnContent
        URL resource = relativeClass.getResource("sample.js");
        Source source = Source.newBuilder("js", resource)
            .content("{}")
            .build();
        assert resource.toExternalForm().equals(source.getPath());
        assert "sample.js".equals(source.getName());
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
        Source source = Source.newBuilder("js", stream, "sample.js")
            .build();
        assert "sample.js".equals(source.getName());
        // END: SourceSnippets#fromReader
        return source;
    }

    public static Source fromAString() {
        // BEGIN: SourceSnippets#fromAString
        Source source = Source.newBuilder("js", "function() {\n"
                        + "  return 'Hi';\n"
                        + "}\n", "hi.js").build();
        assert "hi.js".equals(source.getName());
        // END: SourceSnippets#fromAString
        return source;
    }

    public static Source fromBytes() {
        // BEGIN: SourceSnippets#fromBytes
        byte[] bytes = new byte[] {/* Binary */};
        Source source = Source.newBuilder("llvm",
                        ByteSequence.create(bytes),
                        "<literal>").build();
        // END: SourceSnippets#fromBytes
        return source;
    }

    public static boolean loaded = true;
}
// @formatter:on
