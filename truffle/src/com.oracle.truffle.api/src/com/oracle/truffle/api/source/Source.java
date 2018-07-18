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
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileTypeDetector;
import java.util.Collection;
import java.util.Objects;
import java.util.ServiceLoader;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleOptions;

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

    private static final Source EMPTY = new SourceImpl.Key(null, null, null, null, null, null, null, false, false, false).toSource();
    private static final String NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE = "do not create sub sources from compiled code";
    private static final String URI_SCHEME = "truffle";

    private static final InternedSources SOURCES = new InternedSources();

    private volatile TextMap textMap;
    private volatile URI computedURI;
    volatile org.graalvm.polyglot.Source polyglotSource;

    abstract Object getSourceId();

    Source() {
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
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
     * @since 1.0
     */
    @Override
    public final int hashCode() {
        return getSourceId().hashCode();
    }

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
        CompilerAsserts.neverPartOfCompilation(NO_FASTPATH_SUBSOURCE_CREATION_MESSAGE);
        return SubSourceImpl.create(this, baseCharIndex, length);
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
        byte[] content = SourceAccessor.isTruffleFile(file) ? SourceAccessor.readTruffleFile(file) : Files.readAllBytes(file.toPath());
        return new String(content, StandardCharsets.UTF_8);
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

    abstract Source copy();

    /**
     * Returns the code sequence as {@link CharSequence}. Causes the contents of this source to be
     * loaded if they are loaded lazily.
     *
     * @since 0.28
     */
    public abstract CharSequence getCharacters();

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
     * The fully qualified name of the source. In case this source originates from a {@link File},
     * then the default path is the normalized, {@link File#getCanonicalPath() canonical path}.
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
     * One can specify whether a source is internal when {@link Builder#internal() building it}.
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
     * @since 1.0
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
     * One can specify whether a source is interactive when {@link Builder#interactive() building
     * it}.
     *
     * @return whether this source is marked as <em>interactive</em>
     * @since 0.21
     */
    public abstract boolean isInteractive();

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
     * {@link #getURI} - for example when content of a {@link Source#newBuilder(java.io.File) file
     * on a disk} changes and is re-loaded.
     *
     * @return a URI, it's never <code>null</code>
     * @since 0.14
     */
    public final URI getURI() {
        URI uri = getOriginalURI();
        if (uri == null) {
            uri = computedURI;
            if (uri == null) {
                uri = computedURI = getNamedURI(getName(), getCharacters().toString().getBytes());
            }
        }
        return uri;
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
    public abstract String getMimeType();

    /**
     * Returns the language this source was created with.
     *
     * @return the language of this source or <code>null</code>, if unknown
     * @since 0.28
     * @see Builder#language(java.lang.String)
     */
    public abstract String getLanguage();

    /**
     * Access to the source contents.
     *
     * @since 0.8 or earlier
     */
    public final Reader getReader() {
        return new CharSequenceReader(getCharacters());
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

    final TextMap getTextMap() {
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

    @SuppressWarnings({"unchecked", "unused"})
    static <E extends Exception> E raise(Class<E> type, Exception ex) throws E {
        throw (E) ex;
    }

    @SuppressWarnings("all")
    static CharSequence enforceCharSequenceContract(CharSequence sequence) {
        boolean assertions = false;
        assert assertions = true;
        if (assertions) {
            if (sequence instanceof String) {
                return sequence;
            } else if (sequence instanceof CharSequenceWrapper) {
                // already wrapped
                return sequence;
            } else {
                return new CharSequenceWrapper(sequence);
            }
        }
        return sequence;
    }

    static String findMimeType(final Path filePath) throws IOException {
        if (!TruffleOptions.AOT) {
            Collection<ClassLoader> loaders = SourceAccessor.allLoaders();
            for (ClassLoader l : loaders) {
                for (FileTypeDetector detector : ServiceLoader.load(FileTypeDetector.class, l)) {
                    String mimeType = detector.probeContentType(filePath);
                    if (mimeType != null) {
                        return mimeType;
                    }
                }
            }
        }

        String found = Files.probeContentType(filePath);
        return found == null ? "content/unknown" : found;
    }

    static String findMimeType(final URL url) throws IOException {
        Path path;
        try {
            path = Paths.get(url.toURI());
            String firstGuess = findMimeType(path);
            if (firstGuess != null) {
                return firstGuess;
            }
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException ex) {
            // swallow and go on
        }
        return url.openConnection().getContentType();
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
        private String mime;
        private String language;
        private CharSequence content;
        private boolean internal;
        private boolean interactive;
        private boolean cached = true;

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
         * @since 1.0
         */
        public Builder<E1, E2, E3> cached(@SuppressWarnings("hiding") boolean cached) {
            this.cached = cached;
            return this;
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
            try {
                String useName = this.name;
                CharSequence useContent = this.content;
                String usePath = null;
                URI useUri = this.uri;
                URL useUrl = null;
                String useMimeType = this.mime;

                if (origin instanceof File) {
                    final File file = (File) origin;
                    File absoluteFile = file.getCanonicalFile();
                    useName = useName == null ? file.getName() : useName;
                    useContent = useContent == null ? read(file) : useContent;
                    usePath = usePath == null ? absoluteFile.getPath() : usePath;
                    useUri = useUri == null ? absoluteFile.toURI() : useUri;
                    useMimeType = useMimeType == null ? findMimeType(absoluteFile.toPath()) : useMimeType;
                } else if (origin instanceof Reader) {
                    final Reader r = (Reader) origin;
                    useContent = useContent == null ? read(r) : useContent;
                } else if (origin instanceof URL) {
                    final URL url = (URL) origin;
                    String urlPath = url.getPath();
                    int lastIndex = urlPath.lastIndexOf('/');
                    useName = useName == null && lastIndex != -1 ? url.getPath().substring(lastIndex + 1) : useName;
                    useContent = useContent == null ? read(new InputStreamReader(url.openStream())) : useContent;
                    usePath = usePath == null ? url.toExternalForm() : usePath;
                    if (useUri == null) {
                        try {
                            useUri = url.toURI();
                        } catch (URISyntaxException ex) {
                            throw new IOException("Bad URL: " + url, ex);
                        }
                    }
                    useUrl = url;
                    useMimeType = useMimeType == null ? findMimeType(url) : useMimeType;
                } else {
                    assert origin instanceof CharSequence;
                    useContent = useContent == null ? (CharSequence) this.origin : useContent;
                }
                useContent = enforceCharSequenceContract(useContent);

                // make sure origin is not consumed again if builder is used twice
                this.content = useContent;

                if (useMimeType == null) {
                    throw raise(RuntimeException.class, new MissingMIMETypeException());
                }
                if (useName == null) {
                    throw raise(RuntimeException.class, new MissingNameException());
                }

                SourceImpl.Key key = new SourceImpl.Key(useContent, useMimeType, language, useUrl, useUri, useName, usePath, internal, interactive, cached);
                return SOURCES.intern(key);
            } catch (IOException ex) {
                throw raise(RuntimeException.class, ex);
            }
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
