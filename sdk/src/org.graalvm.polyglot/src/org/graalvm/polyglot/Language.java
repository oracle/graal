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
package org.graalvm.polyglot;

import java.util.Set;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageImpl;

/**
 * A handle for a Graal language installed in an {@link Engine engine}. The handle provides access
 * to the language's meta-data, including the language's {@link #getId() id}, {@link #getName()
 * name}, {@link #getVersion() version} and {@link #getOptions() options}.
 *
 * @see Engine#getLanguages()
 * @since 19.0
 */
public final class Language {

    final AbstractLanguageImpl impl;

    Language(AbstractLanguageImpl impl) {
        this.impl = impl;
    }

    /**
     * Gets the primary identification string of this language. The language id is used as the
     * primary way of identifying languages in the polyglot API. (eg. <code>js</code>)
     *
     * @return a language ID string.
     * @since 19.0
     */
    public String getId() {
        return impl.getId();
    }

    /**
     * Gets a human-readable name for the language (for example, "JavaScript").
     *
     * @return the user-friendly name for this language.
     * @since 19.0
     */
    public String getName() {
        return impl.getName();
    }

    /**
     * Gets a human-readable name of the language implementation (for example, "Graal.JS"). Returns
     * <code>null</code> if no implementation name was specified.
     *
     * @since 19.0
     */
    public String getImplementationName() {
        return impl.getImplementationName();
    }

    /**
     * Gets the version information of the language in an arbitrary language-specific format.
     *
     * @since 19.0
     */
    public String getVersion() {
        return impl.getVersion();
    }

    /**
     * Returns <code>true</code> if a the language is suitable for interactive evaluation of
     * {@link Source sources}. {@link #isInteractive() Interactive} languages should be displayed in
     * interactive environments and presented to the user.
     *
     * @since 19.0
     */
    public boolean isInteractive() {
        return impl.isInteractive();
    }

    /**
     * Returns the set of options provided by this language. Option values for languages can either
     * be provided while building an {@link Engine.Builder#option(String, String) engine} or a
     * {@link Context.Builder#option(String, String) context}. The option descriptor
     * {@link OptionDescriptor#getName() name} must be used as the option name.
     *
     * @since 19.0
     */
    public OptionDescriptors getOptions() {
        return impl.getOptions();
    }

    /**
     * Returns the default MIME type that is in use by a language. The default MIME type specifies
     * whether a source is loaded as character or binary based source by default. Returns
     * <code>null</code> if the language does not specify a default MIME type.
     *
     * @see Source#hasBytes()
     * @see Source#getMimeType()
     * @since 19.0
     */
    public String getDefaultMimeType() {
        return impl.getDefaultMimeType();
    }

    /**
     * Returns the MIME types supported by this language.
     *
     * @see Source#getMimeType()
     * @since 19.0
     */
    public Set<String> getMimeTypes() {
        return impl.getMimeTypes();
    }

}
