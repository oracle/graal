/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Objects;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceImpl;
import org.graalvm.polyglot.io.ByteSequence;

/**
 * Representation of a source code unit and its contents that can be evaluated in an execution
 * {@link Context context}. Each source is associated with the the ID of the language.
 *
 * <h3>From a file on disk</h3>
 *
 * Each file is represented as a canonical object, indexed by the absolute, canonical path name of
 * the file. File content is <em>read eagerly</em> and may be optionally <em>cached</em>. Sample
 * usage: <br>
 *
 * {@link SourceSnippets#fromFile}
 *
 * The starting point is {@link Source#newBuilder(String, java.io.File)} method.
 *
 * <h3>Read from an URL</h3>
 *
 * One can read remote or in JAR resources using the {@link Source#newBuilder(String, java.net.URL)}
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
 * {@link Source#newBuilder(String, java.lang.CharSequence, String) } factory method: <br>
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
 * the content is <em>read eagerly</em> once the {@link Builder#build()} method is called.
 *
 * <h3>Reading from bytes</h3>
 *
 * Sources can be created from bytes. Please note that all character related methods will throw an
 * {@link UnsupportedOperationException} if that is the case.
 *
 * {@link SourceSnippets#fromBytes}
 *
 * <h2>Attributes</h2>
 *
 * The source object can be associated with various attributes like {@link #getName()} and
 * {@link #getURI()}, {@link #getMimeType()} and these are immutable. The system makes the best
 * effort to derive values of these attributes from the location and/or content of the
 * {@link Source} object. However, to give the user that creates the source control over these
 * attributes, the API offers an way to alter values of these attributes.
 *
 * <h2>Character and byte based Sources</h2>
 *
 * A source is {@link #hasBytes() byte} or {@link #hasCharacters() character} based, or none of
 * those when no content is specified. For literal sources it depends on whether the byte or
 * character based factory method was used. When the source was loaded from a {@link File file} or
 * {@link URL url} then the {@link Language#getDefaultMimeType() default MIME type} of the provided
 * language will be used to determine whether bytes or characters should be loaded. The behavior can
 * be customized by specifying a {@link Builder#mimeType(String) MIME type} or
 * {@link Builder#content(ByteSequence) content} explicitly. If the specified or inferred MIME type
 * starts with <code>'text/</code> or the MIME types is <code>null</code> then it will be
 * interpreted as character based, otherwise byte based.
 *
 * @see Context#eval(Source) To evaluate sources.
 * @see Source#findLanguage(File) To detect a language using a File
 * @see Source#findLanguage(URL) To detect a language using an URL.
 * @see Source#findMimeType(File) To detect a MIME type using a File.
 * @see Source#findMimeType(URL) To detect a MIME type using an URL.
 * @since 19.0
 */
public final class Source {

    private static volatile AbstractSourceImpl IMPL;

    static AbstractSourceImpl getImpl() {
        if (IMPL == null) {
            synchronized (Engine.class) {
                if (IMPL == null) {
                    IMPL = Engine.getImpl().getSourceImpl();
                    SourceSection.IMPL = Engine.getImpl().getSourceSectionImpl();
                }
            }
        }
        return IMPL;
    }

    final String language;
    final Object impl;

    Source(String language, Object impl) {
        this.language = language;
        this.impl = impl;
    }

    /**
     * Returns the language this source created with. The string returned matches the
     * {@link Language#getId() id} of the language.
     *
     * @since 19.0
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Returns the name of this resource holding a guest language program. An example would be the
     * name of a guest language source code file. Name is supposed to be shorter than
     * {@link #getPath()}.
     *
     * @return the name of the guest language program
     * @see Builder#name(String)
     * @since 19.0
     */
    public String getName() {
        return getImpl().getName(impl);
    }

    /**
     * The fully qualified name of the source. In case this source originates from a {@link File},
     * then the path is the normalized, {@link File#getCanonicalPath() canonical path} for absolute
     * files, or the relative path otherwise. If the source originates from an {@link URL}, then
     * it's the path component of the URL.
     *
     * @since 19.0
     */
    public String getPath() {
        return getImpl().getPath(impl);
    }

    /**
     * The URL if the source is retrieved via URL.
     *
     * @return URL or <code>null</code>
     * @since 19.0
     */
    public URL getURL() {
        return getImpl().getURL(impl);
    }

    /**
     * Get the URI of the source. Every source has an associated {@link URI}, which can be used as a
     * persistent identification of the source. The {@link URI} returned by this method should be as
     * unique as possible, yet it can happen that different {@link Source sources} return the same
     * {@link #getURI} - for example when content of a
     * {@link Source#newBuilder(String, java.io.File) file on a disk} changes and is re-loaded.
     *
     * @return a URI, never <code>null</code>
     * @since 19.0
     */
    public URI getURI() {
        return getImpl().getURI(impl);
    }

    /**
     * Check whether this source has been marked as <em>interactive</em>. Interactive sources are
     * provided by an entity which is able to interactively read output and provide an input during
     * the source execution; that can be a user I/O through an interactive shell for instance.
     * <p>
     * One can specify whether a source is interactive when {@link Builder#interactive(boolean)
     * building it}.
     *
     * @return whether this source is marked as <em>interactive</em>
     * @since 19.0
     */
    public boolean isInteractive() {
        return getImpl().isInteractive(impl);
    }

    /**
     * Gets whether this source has been marked as <em>internal</em>, meaning that it has been
     * provided by the infrastructure, language implementation, or system library. <em>Internal</em>
     * sources are presumed to be irrelevant to guest language programmers, as well as possibly
     * confusing and revealing of language implementation details.
     * <p>
     * On the other hand, tools should be free to make <em>internal</em> sources visible in
     * (possibly privileged) modes that are useful for language implementors.
     * <p>
     * One can specify whether a source is internal when {@link Builder#internal(boolean) building
     * it}.
     *
     * @return whether this source is marked as <em>internal</em>
     * @since 19.0
     */
    public boolean isInternal() {
        return getImpl().isInternal(impl);
    }

    /**
     * Returns a new reader that reads from the {@link #getCharacters() characters} provided by this
     * source.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @since 19.0
     */
    public Reader getReader() {
        return getImpl().getReader(impl);
    }

    /**
     * @since 19.0
     * @deprecated use {@link #getReader()}, {@link #getCharacters()} or {@link #getBytes()}
     *             instead. The implementation is inefficient and can not distinguish byte and
     *             character based sources.
     */
    @Deprecated
    public InputStream getInputStream() {
        return getImpl().getInputStream(impl);
    }

    /**
     * Gets the number of characters or bytes of the source.
     *
     * @since 19.0
     */
    public int getLength() {
        return getImpl().getLength(impl);
    }

    /**
     * Returns all characters of the source.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @since 19.0
     */
    public CharSequence getCharacters() {
        return getImpl().getCharacters(impl);
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
     * The MIME type can be guessed by the system based on {@link #findMimeType(File) files} or
     * {@link #findMimeType(URL) urls}
     *
     * @see Language#getDefaultMimeType()
     * @see Language#getMimeTypes()
     * @see Source#findMimeType(File)
     * @see Source#findMimeType(URL)
     * @return MIME type of this source or <code>null</code>, if not explicitly set.
     * @since 19.0
     */
    public String getMimeType() {
        return getImpl().getMimeType(impl);
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     * Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @see #hasCharacters()
     * @since 19.0
     */
    public CharSequence getCharacters(int lineNumber) {
        return getImpl().getCharacters(impl, lineNumber);
    }

    /**
     * Returns the bytes of the source if it is a {@link #hasBytes() byte based source}.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasBytes() bytes}
     *             .
     * @see #hasBytes()
     * @since 19.0
     */
    public ByteSequence getBytes() {
        return getImpl().getBytes(impl);
    }

    /**
     * Returns <code>true</code> if this source represents a character based source, else
     * <code>false</code>. A source is either a byte based, a character based, or with no content,
     * but never both byte and character based at the same time.
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
    public boolean hasCharacters() {
        return getImpl().hasCharacters(impl);
    }

    /**
     * Returns <code>true</code> if this source represents a byte based source, else
     * <code>false</code>. A source is either a byte based, a character based, or with no content,
     * but never both byte and character based at the same time.
     * <p>
     * The method {@link #getBytes()} is only supported if this method returns <code>true</code>.
     *
     * @see #getBytes()
     * @since 19.0
     */
    public boolean hasBytes() {
        return getImpl().hasBytes(impl);
    }

    /**
     * The number of text lines of a character based source, including empty lines; characters at
     * the end of the source without a terminating newline count as a line. Causes the contents of
     * this source to be loaded if they are loaded lazily.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @since 19.0
     */
    public int getLineCount() {
        return getImpl().getLineCount(impl);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position. Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 19.0
     */
    public int getLineNumber(int offset) throws IllegalArgumentException {
        return getImpl().getLineNumber(impl, offset);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the column at the position.
     * Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 19.0
     */
    public int getColumnNumber(int offset) throws IllegalArgumentException {
        return getImpl().getColumnNumber(impl, offset);
    }

    /**
     * Given a 1-based line number, return the 0-based offset of the first character in the line.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 19.0
     */
    public int getLineStartOffset(int lineNumber) throws IllegalArgumentException {
        return getImpl().getLineStartOffset(impl, lineNumber);
    }

    /**
     * The number of characters (not counting a possible terminating newline) in a (1-based)
     * numbered line. Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws UnsupportedOperationException if this source cannot contain {@link #hasCharacters()
     *             characters}.
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 19.0
     */
    public int getLineLength(int lineNumber) throws IllegalArgumentException {
        return getImpl().getLineLength(impl, lineNumber);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String toString() {
        return getImpl().toString(impl);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public int hashCode() {
        return getImpl().hashCode(impl);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public boolean equals(Object obj) {
        Object otherImpl;
        if (obj instanceof Source) {
            otherImpl = ((Source) obj).impl;
        } else {
            return false;
        }
        return getImpl().equals(impl, otherImpl);
    }

    /**
     * Creates a new character based literal source from a character sequence. The given characters
     * must not mutate after they were accessed for the first time.
     * <p>
     * Use this method for sources that do originate from a literal. For file or URL sources use the
     * appropriate builder constructor and {@link Builder#content(CharSequence)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromAString}
     *
     * @param language the language id, must not be <code>null</code>
     * @param characters the character sequence or string, must not be <code>null</code>
     * @param name the name of the source, if <code>null</code> then <code>"Unnamed"</code> will be
     *            used as name.
     * @since 19.0
     */
    public static Builder newBuilder(String language, CharSequence characters, String name) {
        return EMPTY.new Builder(language, characters).name(name);
    }

    /**
     * Creates a new byte based literal source from a byte sequence. The given bytes must not mutate
     * after they were accessed for the first time.
     * <p>
     * Use this method for sources that do originate from a literal. For file or URL sources use the
     * appropriate builder constructor and {@link Builder#content(CharSequence)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromBytes}
     *
     * @param language the language id, must not be <code>null</code>
     * @param bytes the byte sequence or string, must not be <code>null</code>
     * @param name the name of the source, if <code>null</code> then <code>"Unnamed"</code> will be
     *            used as name.
     * @since 19.0
     */
    public static Builder newBuilder(String language, ByteSequence bytes, String name) {
        return EMPTY.new Builder(language, bytes).name(name);
    }

    /**
     * Creates a new file based source builder from a given file. A file based source is either
     * interpreted as {@link Source#hasBytes() binary} or {@link Source#hasCharacters() character}
     * source depending on the {@link Language#getDefaultMimeType() default MIME type} of the
     * language, the {@link Builder#content(ByteSequence) contents} or the specified
     * {@link Builder#mimeType(String) MIME type}. A language may be detected from an existing file
     * using {@link #findLanguage(File)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromFile}
     *
     * @param language the language id, must not be <code>null</code>
     * @param file the file to use, must not be <code>null</code>
     * @since 19.0
     */
    public static Builder newBuilder(String language, File file) {
        return EMPTY.new Builder(language, file);
    }

    /**
     * Creates a new URL based source builder from a given URL. A URL based source is either
     * interpreted as {@link Source#hasBytes() binary} or {@link Source#hasCharacters() character}
     * source depending on the {@link Language#getDefaultMimeType() default MIME type} of the
     * language, the {@link Builder#content(ByteSequence) contents} or the specified
     * {@link Builder#mimeType(String) MIME type}. A language may be detected from an existing file
     * using {@link #findLanguage(URL)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromURL}
     *
     * @param language the language id, must not be <code>null</code>
     * @param url the URL to use and load, must not be <code>null</code>
     * @since 19.0
     */
    public static Builder newBuilder(String language, URL url) {
        return EMPTY.new Builder(language, url);
    }

    /**
     * Creates new character based literal source from a reader.
     * <p>
     * Use this method for sources that do originate from a literal. For file or URL sources use the
     * appropriate builder constructor and {@link Builder#content(CharSequence)}.
     * <p>
     * Example usage: {@link SourceSnippets#fromReader}
     *
     * @since 19.0
     */
    public static Builder newBuilder(String language, Reader source, String name) {
        return EMPTY.new Builder(language, source).name(name);
    }

    /**
     * Shortcut for creating a source object from a language and char sequence. The given characters
     * must not mutate after they were accessed for the first time.
     * <p>
     * Use for sources that do not come from a file, or URL. If they do, use the appropriate builder
     * and {@link Builder#content(CharSequence)}.
     *
     * @since 19.0
     */
    public static Source create(String language, CharSequence source) {
        return newBuilder(language, source, "Unnamed").buildLiteral();
    }

    /**
     * Returns the id of the first language that supports evaluating the probed mime type of a given
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
    public static String findLanguage(File file) throws IOException {
        return getImpl().findLanguage(file);
    }

    /**
     * Returns the id of the first language that supports evaluating the probed mime type of a given
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
        return getImpl().findLanguage(url);
    }

    /**
     * Returns the probed MIME type for a given file, or <code>null</code> if no MIME type could be
     * resolved. Typically the MIME type is identified using the file extension and/or using its
     * contents. Probing the MIME type of an {@link File} may require to opening the file.
     *
     * @throws IOException if an error opening the file occurred.
     * @see #findLanguage(File)
     * @since 19.0
     */
    public static String findMimeType(File file) throws IOException {
        return getImpl().findMimeType(file);
    }

    /**
     * Returns the probed MIME type for a given url, or <code>null</code> if no MIME type could be
     * resolved. Typically the MIME type is identified using the file extension, connection
     * meta-data and/or using it contents. Returns <code>null</code> if the language of the given
     * file could not be detected. Probing the language of an URL may require to open a new URL
     * connection.
     *
     * @throws IOException if an error opening the url occurred.
     * @see #findLanguage(URL)
     * @since 19.0
     */
    public static String findMimeType(URL url) throws IOException {
        return getImpl().findMimeType(url);
    }

    /**
     * Returns the first installed language that supports evaluating sources for a given MIME type.
     * Returns <code>null</code> if no language was found that supports a given MIME type. The
     * languages are queried in the same order as returned by {@link Engine#getLanguages()}.
     * Mime-types don't adhere to the MIME type format will return <code>null</code>.
     *
     * @since 19.0
     */
    public static String findLanguage(String mimeType) {
        return getImpl().findLanguage(mimeType);
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <E extends Exception> RuntimeException silenceException(Class<E> type, Exception ex) throws E {
        throw (E) ex;
    }

    private static void validateMimeType(String mimeType) {
        if (mimeType == null) {
            return;
        }
        int index = mimeType.indexOf('/');
        if (index == -1 || index == 0 || index == mimeType.length() - 1) {
            throw invalidMimeType(mimeType);
        }
        if (mimeType.indexOf('/', index + 1) != -1) {
            throw invalidMimeType(mimeType);
        }
    }

    private static IllegalArgumentException invalidMimeType(String mimeType) {
        return new IllegalArgumentException(String.format("Invalid MIME type '%s' provided. A MIME type consists of a type and a subtype separated by '/'.", mimeType));
    }

    private static final Source EMPTY = new Source(null, null);

    /**
     * Represents a builder to build {@link Source} objects.
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
    public class Builder {

        private final String language;
        private final Object origin;
        private URI uri;
        private String name;
        private boolean interactive;
        private boolean internal;
        private boolean cached = true;
        private Object content;
        private String mimeType;
        private Charset fileEncoding;

        Builder(String language, Object origin) {
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
        public Builder name(String newName) {
            this.name = newName;
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
         * @return instance of this builder
         * @since 19.0
         */
        public Builder content(String code) {
            return content((CharSequence) code);
        }

        /**
         * Specifies character based content of {@link #build() to-be-built} {@link Source}. Using
         * this method one can ignore the real content of a file or URL and use already read one, or
         * completely different one. The given characters must not mutate after they were accessed
         * for the first time. Example:
         *
         * {@link SourceSnippets#fromURLWithOwnContent}
         *
         * @param characters the code to be available via {@link Source#getCharacters()}
         * @return instance of this builder - which's {@link #build()} method no longer throws an
         *         {@link IOException}
         * @since 19.0
         */
        public Builder content(CharSequence characters) {
            Objects.requireNonNull(characters);
            this.content = characters;
            return this;
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
        public Builder content(ByteSequence bytes) {
            Objects.requireNonNull(bytes);
            this.content = bytes;
            return this;
        }

        /**
         * Explicitly assigns a {@link Source#getMimeType() MIME type} to the {@link #build()
         * to-be-built} {@link Source}. If the MIME type is <code>null</code> then the
         * {@link Language#getDefaultMimeType() default MIME type} of the language will be used to
         * interpret the source. If set explicitly then the language needs to
         * {@link Language#getMimeTypes() support} the MIME type in order to
         * {@link Context#eval(Source) evaluate} the source. If not <code>null</code> the MIME type
         * is already verified containing no spaces and a '/' between group and sub-group. An
         * example for a valid MIME type is: <code>text/javascript</code>.
         * <p>
         * The MIME type can be guessed by the system based on {@link #findMimeType(File) files} or
         * {@link #findMimeType(URL) urls}. If a source is {@link Source#hasBytes() binary} based
         * then the MIME type must also be a binary based MIME type. All MIME types starting with
         * 'text/' will be interpreted as character based MIME types.
         *
         * @see Language#getDefaultMimeType()
         * @see Language#getMimeTypes()
         * @see Source#findMimeType(File)
         * @see Source#findMimeType(URL)
         * @param mimeType the new mime type to be assigned, or <code>null</code> if default MIME
         *            type should be used.
         * @return instance of <code>this</code> builder ready to {@link #build() create new source}
         * @since 19.0
         */
        public Builder mimeType(@SuppressWarnings("hiding") String mimeType) {
            validateMimeType(mimeType);
            this.mimeType = mimeType;
            return this;
        }

        /**
         * Marks the source as interactive. {@link Context#eval Evaluation} of interactive sources
         * by an {@link Language#isInteractive() interactive language} can use the {@link Context}
         * output streams to print the result and read an input. However, non-interactive languages
         * are expected to ignore the interactive property of sources and not use the polyglot
         * engine streams. Any desired printing of the evaluated result provided by a
         * non-interactive language needs to be handled by the caller. Calling of this method
         * influences the result of {@link Source#isInteractive()}.
         *
         * @return the instance of this builder
         * @since 19.0
         */
        public Builder interactive(@SuppressWarnings("hiding") boolean interactive) {
            this.interactive = interactive;
            return this;
        }

        /**
         * Set whether this source has been marked as <em>internal</em>, meaning that it has been
         * provided by the infrastructure, language implementation, or system library.
         * <em>Internal</em> sources are presumed to be irrelevant to guest language programmers, as
         * well as possibly confusing and revealing of language implementation details.
         * <p>
         * On the other hand, tools should be free to make <em>internal</em> sources visible in
         * (possibly privileged) modes that are useful for language implementors.
         * <p>
         *
         * @return the instance of this builder
         * @since 19.0
         */
        public Builder internal(@SuppressWarnings("hiding") boolean internal) {
            this.internal = internal;
            return this;
        }

        /**
         * Enables or disables code caching for this source. By default code caching is enabled. If
         * <code>true</code> then the source does not require parsing every time this source is
         * {@link Context#eval(Source) evaluated}. If <code>false</code> then the source requires
         * parsing every time the source is evaluated but does not remember any state. Disabling
         * caching may be useful if the source is known to only be evaluated once.
         * <p>
         * If a source instance is no longer referenced by the client then all code caches will be
         * freed automatically. Also, if the underlying context or engine is no longer referenced
         * then cached code for evaluated sources will be freed automatically.
         *
         * @return instance of <code>this</code> builder ready to {@link #build() create new source}
         * @since 19.0
         */
        public Builder cached(@SuppressWarnings("hiding") boolean cached) {
            this.cached = cached;
            return this;
        }

        /**
         * Assigns new {@link URI} to the {@link #build() to-be-created} {@link Source}. Each source
         * provides {@link Source#getURI()} as a persistent identification of its location. A
         * default value for the method is deduced from the location or content, but one can change
         * it by using this method
         *
         * @param newUri the URL to use instead of default one, cannot be <code>null</code>
         * @return the instance of this builder
         * @since 19.0
         */
        public Builder uri(URI newUri) {
            Objects.requireNonNull(newUri);
            this.uri = newUri;
            return this;
        }

        /**
         * Assigns an encoding used to read the file content. If the encoding is {@code null} then
         * the file contained encoding information is used. If the file doesn't provide an encoding
         * information the default {@code UTF-8} encoding is used.
         *
         * @param encoding the new file encoding to be used for reading the content
         * @return instance of <code>this</code> builder ready to {@link #build() create new source}
         * @since 19.0
         */
        public Builder encoding(Charset encoding) {
            this.fileEncoding = encoding;
            return this;
        }

        /**
         * Uses configuration of this builder to create new {@link Source} object. The method throws
         * an {@link IOException} if an error loading the source occurred.
         *
         * @return the source object
         * @since 19.0
         */
        public Source build() throws IOException {
            Source source = getImpl().build(language, origin, uri, name, mimeType, content, interactive, internal, cached, fileEncoding);

            // make sure origin is not consumed again if builder is used twice
            if (source.hasBytes()) {
                this.content = source.getBytes();
            } else {
                assert source.hasCharacters();
                this.content = source.getCharacters();
            }

            return source;
        }

        /**
         * Uses configuration of this builder to create new {@link Source} object. This method can
         * only be used if the builder was created as
         * {@link Source#newBuilder(String, CharSequence, String) string literal} builder and
         * otherwise throws an {@link UnsupportedOperationException}.
         *
         * @return the source object
         * @since 19.0
         */
        public Source buildLiteral() {
            try {
                return build();
            } catch (IOException e) {
                throw new AssertionError("No error expected.", e);
            }
        }
    }
}

//@formatter:off
//Checkstyle: stop
class SourceSnippets {
 public static Source fromFile(File dir, String name) throws IOException  {
     // BEGIN: SourceSnippets#fromFile
     File file = new File(dir, name);
     assert name.endsWith(".java") : "Imagine proper file";

     String language = Source.findLanguage(file);
     Source source = Source.newBuilder(language, file).build();

     assert file.getName().equals(source.getName());
     assert file.getPath().equals(source.getPath());
     assert file.toURI().equals(source.getURI());
     // END: SourceSnippets#fromFile
     return source;
 }

 public static Source likeFileName(String fileName) throws IOException {
     // BEGIN: SourceSnippets#likeFileName
     File file = new File(fileName);
     String language = Source.findLanguage(file);
     Source source = Source.newBuilder(language, file.getCanonicalFile()).
         name(file.getPath()).
         build();
     // END: SourceSnippets#likeFileName
     return source;
 }

 public static Source fromURL(Class<?> relativeClass) throws URISyntaxException, IOException {
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
         .buildLiteral();
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
         + "}\n", "hi.js").buildLiteral();
     assert "hi.js".equals(source.getName());
     // END: SourceSnippets#fromAString
     return source;
 }

 public static Source fromBytes() {
     // BEGIN: SourceSnippets#fromBytes
     byte[] bytes = new byte[] {/* Binary */};
     Source source = Source.newBuilder("llvm",
                     ByteSequence.create(bytes),
                     "<literal>").buildLiteral();
     // END: SourceSnippets#fromBytes
     return source;
 }

 public static boolean loaded = true;
}
//@formatter:on
