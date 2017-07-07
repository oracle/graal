/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceImpl;

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
 * {@link Source#newBuilder(java.lang.CharSequence) } factory method: <br>
 *
 * {@link SourceSnippets#fromAString}
 *
 * the created {@link Source} doesn't have associated {@link #getMimeType() mime type}. One has to
 * explicitly attach via {@link Builder#mimeType(java.lang.String)} method. The created
 * {@link Source} doesn't have associated {@link #getName() name}, one has to attach it via
 * {@link Builder#setName(java.lang.String)} method.
 *
 * <h3>Reading from a stream</h3>
 *
 * If one has a {@link Reader} one can convert its content into a {@link Source} via
 * {@link Source#newBuilder(java.io.Reader)} method: <br>
 *
 * {@link SourceSnippets#fromReader}
 *
 * the content is <em>read eagerly</em> once the {@link Builder#build()} method is called. It
 * doesn't have associated {@link #getName() name}. The name should be explicitly provided by
 * {@link Builder#setName(java.lang.String) } method.
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
 * {@link Builder#setName(java.lang.String)}, {@link Builder#setURI(java.net.URI)} methods.
 * </p>
 * <p>
 * While {@link Source} is immutable, the world around it is changing. The content of a file from
 * which a {@link Source#newBuilder(java.io.File) source has been read} may change few seconds
 * later. How can we balance the immutability with ability to see real state of the world? In this
 * case, one can load of a new version of the {@link Source#newBuilder(java.io.File) source for the
 * same file}. The newly loaded {@link Source} will be different than the previous one, however it
 * will have the same attributes ({@link #getName()}. There isn't much to do about this - just keep
 * in mind that there can be multiple different {@link Source} objects representing the same
 * {@link #getURI() source origin}.
 * </p>
 *
 * @since 1.0
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

    // non final to support legacy code
    String language;
    final Object impl;

    Source(String language, Object impl) {
        this.language = language;
        this.impl = impl;
    }

    /**
     * Returns the language this source created with.
     *
     * @since 1.0
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
     * @since 1.0
     */
    public String getName() {
        return IMPL.getName(impl);
    }

    /**
     * The fully qualified name of the source. In case this source originates from a {@link File},
     * then the default path is the normalized, {@link File#getCanonicalPath() canonical path}.
     *
     * @since 1.0
     */
    public String getPath() {
        return IMPL.getPath(impl);
    }

    /**
     * Check whether this source has been marked as <em>interactive</em>. Interactive sources are
     * provided by an entity which is able to interactively read output and provide an input during
     * the source execution; that can be a user I/O through an interactive shell for instance.
     * <p>
     * One can specify whether a source is interactive when {@link Builder#setInteractive() building
     * it}.
     *
     * @return whether this source is marked as <em>interactive</em>
     * @since 1.0
     */
    public boolean isInteractive() {
        return IMPL.isInteractive(impl);
    }

    /**
     * The URL if the source is retrieved via URL.
     *
     * @return URL or <code>null</code>
     * @since 1.0
     */
    public URL getURL() {
        return IMPL.getURL(impl);
    }

    /**
     * Get the URI of the source. Every source has an associated {@link URI}, which can be used as a
     * persistent identification of the source. The {@link URI} returned by this method should be as
     * unique as possible, yet it can happen that different {@link Source sources} return the same
     * {@link #getURI} - for example when content of a {@link Source#newBuilder(java.io.File) file
     * on a disk} changes and is re-loaded.
     *
     * @return a URI, never <code>null</code>
     * @since 1.0
     */
    public URI getURI() {
        return IMPL.getURI(impl);
    }

    /**
     * Access to the source contents.
     *
     * @since 1.0
     */
    public Reader getReader() {
        return IMPL.getReader(impl);
    }

    /**
     * Access to the source contents. Causes the contents of this source to be loaded if they are
     * loaded lazily.
     *
     * @since 1.0
     */
    public InputStream getInputStream() {
        return IMPL.getInputStream(impl);
    }

    /**
     * Gets the number of characters in the source. Causes the contents of this source to be loaded
     * if they are loaded lazily.
     *
     * @since 1.0
     */
    public int getLength() {
        return IMPL.getLength(impl);
    }

    /**
     * Returns the complete text of the code. Causes the contents of this source to be loaded if
     * they are loaded lazily.
     *
     * @since 1.0
     */
    public CharSequence getCode() {
        return IMPL.getCode(impl);
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     * Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @since 1.0
     */
    public CharSequence getCode(int lineNumber) {
        return IMPL.getCode(impl, lineNumber);
    }

    /**
     * The number of text lines in the source, including empty lines; characters at the end of the
     * source without a terminating newline count as a line. Causes the contents of this source to
     * be loaded if they are loaded lazily.
     *
     * @since 1.0
     */
    public int getLineCount() {
        return IMPL.getLineCount(impl);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position. Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 1.0
     */
    public int getLineNumber(int offset) throws IllegalArgumentException {
        return IMPL.getLineNumber(impl, offset);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the column at the position.
     * Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 1.0
     */
    public int getColumnNumber(int offset) throws IllegalArgumentException {
        return IMPL.getColumnNumber(impl, offset);
    }

    /**
     * Given a 1-based line number, return the 0-based offset of the first character in the line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 1.0
     */
    public int getLineStartOffset(int lineNumber) throws IllegalArgumentException {
        return IMPL.getLineStartOffset(impl, lineNumber);
    }

    /**
     * The number of characters (not counting a possible terminating newline) in a (1-based)
     * numbered line. Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 1.0
     */
    public int getLineLength(int lineNumber) throws IllegalArgumentException {
        return IMPL.getLineLength(impl, lineNumber);
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
     * One can specify whether a source is internal when {@link Builder#setInternal() building it}.
     *
     * @return whether this source is marked as <em>internal</em>
     * @since 1.0
     */
    public boolean isInternal() {
        return IMPL.isInternal(impl);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public String toString() {
        return IMPL.toString(impl);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public int hashCode() {
        return IMPL.hashCode(impl);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public boolean equals(Object obj) {
        Object otherImpl;
        if (obj instanceof Source) {
            otherImpl = ((Source) obj).impl;
        } else {
            return false;
        }
        return IMPL.equals(impl, otherImpl);
    }

    public static Builder newBuilder(String language, CharSequence source, String name) {
        return new Builder(language, source).name(name);
    }

    public static Builder newBuilder(String language, File source) {
        return new Builder(language, source);
    }

    public static Builder newBuilder(String language, URL source, String name) {
        return new Builder(language, source).name(name);
    }

    public static Builder newBuilder(String language, Reader source, String name) {
        return new Builder(language, source).name(name);
    }

    /**
     * @deprecated use {@link #newBuilder(String, CharSequence)}
     */
    @Deprecated
    public static Builder newBuilder(CharSequence source) {
        return new Builder(source);
    }

    /**
     * @deprecated use {@link #newBuilder(String, File)}
     */
    @Deprecated
    public static Builder newBuilder(File source) {
        return new Builder(source);
    }

    /**
     * @deprecated use {@link #newBuilder(String, File)}
     */
    @Deprecated
    public static Source create(CharSequence source) {
        try {
            return newBuilder(source).build();
        } catch (IOException e) {
            throw new AssertionError("Should not reach here");
        }

    }

    public static Source create(String language, CharSequence source) {
        return newBuilder(language, source, "Unnamed").buildLiteral();
    }

    /**
     * Creates a new
     *
     * @throws IOException if an error occured during loading of hte file.
     * @since 1.0
     */
    public static Source create(String language, File source) throws IOException {
        return newBuilder(language, source).build();
    }

    /**
     * Finds a language for a given {@link File file} instance. Typically the language is identified
     * using the file extension and/or using it contents. Returns <code>null</code> if the language
     * of the given file could not be detected.
     *
     * @since 1.0
     */
    public static String findLanguage(File file) {
        return IMPL.findLanguage(file);
    }

    /**
     * Finds an installed language using a given mime-type. Returns <code>null</code> if no language
     * was found for a given mime-type.
     *
     * @since 1.0
     */
    public static String findLanguage(String mimeType) {
        return IMPL.findLanguage(mimeType);
    }

    public static class Builder {

        private final String language;
        private final Object origin;
        private URI uri;
        private String name;
        private boolean interactive;
        private boolean internal;
        private String content;

        Builder(String language, Object origin) {
            Objects.requireNonNull(language);
            Objects.requireNonNull(origin);
            this.language = language;
            this.origin = origin;
        }

        // legacy constructor
        Builder(Object origin) {
            this.language = null;
            this.origin = origin;
        }

        public Builder name(String newName) {
            Objects.requireNonNull(newName);
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
         * @param code the code to be available via {@link Source#getCode()}
         * @return instance of this builder
         * @since 1.0
         */
        public Builder content(String code) {
            Objects.requireNonNull(code);
            this.content = code;
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
         * @since 1.0
         */
        public Builder interactive(@SuppressWarnings("hiding") boolean interactive) {
            this.interactive = interactive;
            return this;
        }

        /**
         * @deprecated use {@link #interactive(boolean)}
         */
        @Deprecated
        public Builder interactive() {
            return interactive(true);
        }

        /**
         * Marks the source as internal. Internal sources are those that aren't created by user, but
         * rather inherently present by the language system. Calling this method influences result
         * of create {@link Source#isInternal()}
         *
         * @return the instance of this builder
         * @since 1.0
         */
        public Builder internal(@SuppressWarnings("hiding") boolean internal) {
            this.internal = internal;
            return this;
        }

        /**
         * @deprecated use {@link #internal(boolean)}
         */
        @Deprecated
        public Builder internal() {
            return internal(true);
        }

        /**
         * Assigns new {@link URI} to the {@link #build() to-be-created} {@link Source}. Each source
         * provides {@link Source#getURI()} as a persistent identification of its location. A
         * default value for the method is deduced from the location or content, but one can change
         * it by using this method
         *
         * @param ownUri the URL to use instead of default one, cannot be <code>null</code>
         * @return the instance of this builder
         * @since 1.0
         */
        public Builder uri(URI newUri) {
            Objects.requireNonNull(newUri);
            this.uri = newUri;
            return this;
        }

        /**
         * Uses configuration of this builder to create new {@link Source} object. This method
         * throws can throw an {@link IOException} independent of whether
         *
         * @return the source object
         * @since 1.0
         */
        public Source build() throws IOException {
            return getImpl().build(language, origin, uri, name, content, interactive, internal);
        }

        /**
         * Uses configuration of this builder to create new {@link Source} object.
         *
         * @return the source object
         * @since 1.0
         */
        public Source buildLiteral() {
            if (!(origin instanceof CharSequence)) {
                throw new UnsupportedOperationException("This method is only supported for string literal. Use build() instead.");
            }
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
     Source source = Source.create(language, file);

     assert file.getName().equals(source.getName());
     assert file.getPath().equals(source.getPath());
     assert file.toURI().equals(source.getURI());
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

 public static Source fromURL(Class<?> relativeClass) throws URISyntaxException, IOException {
     // BEGIN: SourceSnippets#fromURL
     URL resource = relativeClass.getResource("sample.js");
     Source source = Source.newBuilder("js", resource, "sample.js")
                     .build();
     assert resource.toExternalForm().equals(source.getPath());
     assert "sample.js".equals(source.getName());
     assert resource.toURI().equals(source.getURI());
     // END: SourceSnippets#fromURL
     return source;
 }

 public static Source fromURLWithOwnContent(Class<?> relativeClass) throws IOException {
     // BEGIN: SourceSnippets#fromURLWithOwnContent
     URL resource = relativeClass.getResource("sample.js");
     Source source = Source.newBuilder("js", resource, "sample.js")
         .content("{}")
         .build();
     assert resource.toExternalForm().equals(source.getPath());
     assert "sample.js".equals(source.getName());
     assert resource.toExternalForm().equals(source.getURI().toString());
     assert "{}".equals(source.getCode());
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
         + "}\n", "<literal>").buildLiteral();
     // END: SourceSnippets#fromAString
     return source;
 }

 public static boolean loaded = true;
}
//@formatter:on
