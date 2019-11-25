/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import java.util.Set;

import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.source.Source;

/**
 * Represents public information about a language.
 *
 * @since 0.25
 */
public final class LanguageInfo {

    private final String id;
    private final String name;
    private final String version;
    private final Set<String> mimeTypes;
    private final Object polyglotLanguage;
    private final String defaultMimeType;
    private final boolean internal;
    private final boolean interactive;

    LanguageInfo(Object polyglotLanguage, String id, String name, String version, String defaultMimeType, Set<String> mimeTypes, boolean internal, boolean interactive) {
        this.polyglotLanguage = polyglotLanguage;
        this.id = id;
        this.name = name;
        this.version = version;
        this.defaultMimeType = defaultMimeType;
        this.mimeTypes = mimeTypes;
        this.internal = internal;
        this.interactive = interactive;
    }

    /**
     * Returns the unique id of the language. This id is equivalent to the id returned by
     * {@link Registration#id()}.
     *
     * @since 0.27
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the unique name of the language. This name is equivalent to the name returned by
     * {@link Registration#name()}.
     *
     * @since 0.25
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the version of the language. This version is equivalent to the name returned by
     * {@link Registration#version()}.
     *
     * @since 0.25
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the default MIME type of a language or <code>null</code> if no default mime-type is
     * set. The default MIME type specifies whether a source is loaded as character or binary based
     * source by default. If no default MIME type is set all sources evaluated with that language
     * will be interpreted as {@link Source#hasCharacters() character based} sources. This set is
     * equivalent to the set provided by {@link Registration#defaultMimeType()}.
     *
     * @since 19.0
     */
    public String getDefaultMimeType() {
        return defaultMimeType;
    }

    /**
     * Returns the MIME types supported by this language. This set is equivalent to the set provided
     * by {@link Registration#characterMimeTypes()} and {@link Registration#byteMimeTypes()}.
     *
     * @since 0.25
     */
    public Set<String> getMimeTypes() {
        return mimeTypes;
    }

    Object getPolyglotLanguage() {
        return polyglotLanguage;
    }

    /**
     * @return {@code true} if the language is {@link Registration#internal() internal},
     *         {@code false} otherwise
     * @since 0.31
     */
    public boolean isInternal() {
        return internal;
    }

    /**
     * @return {@code true} if the language is {@link Registration#interactive() interactive},
     *         {@code false} otherwise
     * @since 19.0
     */
    public boolean isInteractive() {
        return interactive;
    }
}
