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
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.Objects;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceImpl;

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

    final Object impl;

    public Source(Object impl) {
        this.impl = impl;
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
     * One can specify whether a source is interactive when {@link Builder#interactive() building
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
        return IMPL.getURI(impl);
    }

    /**
     * Access to the source contents.
     *
     * @since 0.8 or earlier
     */
    public Reader getReader() {
        return IMPL.getReader(impl);
    }

    /**
     * Access to the source contents. Causes the contents of this source to be loaded if they are
     * loaded lazily.
     *
     * @since 0.8 or earlier
     */
    public InputStream getInputStream() {
        return IMPL.getInputStream(impl);
    }

    /**
     * Gets the number of characters in the source. Causes the contents of this source to be loaded
     * if they are loaded lazily.
     *
     * @since 0.8 or earlier
     */
    public int getLength() {
        return IMPL.getLength(impl);
    }

    /**
     * Returns the complete text of the code. Causes the contents of this source to be loaded if
     * they are loaded lazily.
     *
     * @since 0.8 or earlier
     */
    public CharSequence getCode() {
        return IMPL.getCode(impl);
    }

    /**
     * Gets the text (not including a possible terminating newline) in a (1-based) numbered line.
     * Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @since 0.8 or earlier
     */
    public CharSequence getCode(int lineNumber) {
        return IMPL.getCode(impl, lineNumber);
    }

    /**
     * The number of text lines in the source, including empty lines; characters at the end of the
     * source without a terminating newline count as a line. Causes the contents of this source to
     * be loaded if they are loaded lazily.
     *
     * @since 0.8 or earlier
     */
    public int getLineCount() {
        return IMPL.getLineCount(impl);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the line that includes the
     * position. Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 0.8 or earlier
     */
    public int getLineNumber(int offset) throws IllegalArgumentException {
        return IMPL.getLineNumber(impl, offset);
    }

    /**
     * Given a 0-based character offset, return the 1-based number of the column at the position.
     * Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws IllegalArgumentException if the offset is outside the text contents
     * @since 0.8 or earlier
     */
    public int getColumnNumber(int offset) throws IllegalArgumentException {
        return IMPL.getColumnNumber(impl, offset);
    }

    /**
     * Given a 1-based line number, return the 0-based offset of the first character in the line.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 0.8 or earlier
     */
    public int getLineStartOffset(int lineNumber) throws IllegalArgumentException {
        return IMPL.getLineStartOffset(impl, lineNumber);
    }

    /**
     * The number of characters (not counting a possible terminating newline) in a (1-based)
     * numbered line. Causes the contents of this source to be loaded if they are loaded lazily.
     *
     * @throws IllegalArgumentException if there is no such line in the text
     * @since 0.8 or earlier
     */
    public int getLineLength(int lineNumber) throws IllegalArgumentException {
        return IMPL.getLineLength(impl, lineNumber);
    }

    @Override
    public String toString() {
        return IMPL.toString(impl);
    }

    @Override
    public int hashCode() {
        return IMPL.hashCode(impl);
    }

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

    public static final class Builder {

        private final Object origin;
        private URI uri;
        private String name;
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
        public Builder name(String newName) {
            Objects.requireNonNull(newName);
            this.name = newName;
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
        public Builder interactive() {
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
        public Builder uri(URI ownUri) {
            Objects.requireNonNull(ownUri);
            this.uri = ownUri;
            return this;
        }

        /**
         * @since 1.0
         */
        public Source build() {
            return getImpl().build(origin, uri, name, interactive);
        }

    }

    public static Builder newBuilder(CharSequence source) {
        return new Builder(source);
    }

    public static Builder newBuilder(File source) {
        return new Builder(source);
    }

    public static Builder newBuilder(URL source) {
        return new Builder(source);
    }

    public static Builder newBuilder(Reader source) {
        return new Builder(source);
    }

    public static Source create(String source) {
        return newBuilder(source).build();
    }

    public static Source create(File source) {
        return newBuilder(source).build();
    }

    public static Source create(URL source) {
        return newBuilder(source).build();
    }

    public static Source create(Reader source) {
        return newBuilder(source).build();
    }

}
