/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Set;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageDispatch;

/**
 * A handle for a Graal language installed in an {@link Engine engine}. The handle provides access
 * to the language's meta-data, including the language's {@link #getId() id}, {@link #getName()
 * name}, {@link #getVersion() version} and {@link #getOptions() options}.
 *
 * @see Engine#getLanguages()
 * @since 19.0
 */
public final class Language {

    final AbstractLanguageDispatch dispatch;
    final Object receiver;
    /**
     * Strong reference to {@link Engine} to prevent it from being garbage collected and closed
     * while {@link Language} is still reachable.
     */
    final Engine engine;

    Language(AbstractLanguageDispatch dispatch, Object receiver, Engine engine) {
        this.dispatch = dispatch;
        this.receiver = receiver;
        this.engine = Objects.requireNonNull(engine);
    }

    /**
     * Gets the primary identification string of this language. The language id is used as the
     * primary way of identifying languages in the polyglot API. (eg. <code>js</code>)
     *
     * @return a language ID string.
     * @since 19.0
     */
    public String getId() {
        return dispatch.getId(receiver);
    }

    /**
     * Gets a human-readable name for the language (for example, "JavaScript").
     *
     * @return the user-friendly name for this language.
     * @since 19.0
     */
    public String getName() {
        return dispatch.getName(receiver);
    }

    /**
     * Gets a human-readable name of the language implementation (for example, "Graal.JS"). Returns
     * <code>null</code> if no implementation name was specified.
     *
     * @since 19.0
     */
    public String getImplementationName() {
        return dispatch.getImplementationName(receiver);
    }

    /**
     * Gets the version information of the language in an arbitrary language-specific format.
     *
     * @since 19.0
     */
    public String getVersion() {
        return dispatch.getVersion(receiver);
    }

    /**
     * Returns <code>true</code> if a the language is suitable for interactive evaluation of
     * {@link Source sources}. {@link #isInteractive() Interactive} languages should be displayed in
     * interactive environments and presented to the user.
     *
     * @since 19.0
     */
    public boolean isInteractive() {
        return dispatch.isInteractive(receiver);
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
        return dispatch.getOptions(receiver);
    }

    /**
     * Returns the source options descriptors available for sources of this language.
     *
     * @see #getOptions()
     * @see Source.Builder#option(String, String)
     * @since 25.0
     */
    public OptionDescriptors getSourceOptions() {
        return dispatch.getSourceOptions(receiver);
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
        return dispatch.getDefaultMimeType(receiver);
    }

    /**
     * Returns the MIME types supported by this language.
     *
     * @see Source#getMimeType()
     * @since 19.0
     */
    public Set<String> getMimeTypes() {
        return dispatch.getMimeTypes(receiver);
    }

    /**
     * Get the URL for the language website.
     *
     * @since 21.1.0
     */
    public String getWebsite() {
        return dispatch.getWebsite(receiver);
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public int hashCode() {
        return this.dispatch.hashCode(this.receiver);
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public boolean equals(Object obj) {
        Object otherImpl;
        if (obj instanceof Language) {
            otherImpl = ((Language) obj).receiver;
        } else {
            return false;
        }
        return dispatch.equals(receiver, otherImpl);
    }

}
