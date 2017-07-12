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

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageImpl;

/**
 * A handle for a Graal language installed in an {@link Engine engine}. The handle provides access
 * to the language's meta-data, including the language's {@link #getId() id}, {@link #getName()
 * name}, {@link #getVersion() version} and {@link #getOptions() options}.
 *
 * @see Engine#getLanguage(String) To return a single language
 * @see Engine#getLanguages() To return a map of all installed languages
 *
 * @since 1.0
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
     * @returns a language ID string.
     * @since 1.0
     */
    public String getId() {
        return impl.getId();
    }

    /**
     * Gets a human-readable name for the language (for example, "JavaScript").
     *
     * @returns the user-friendly name for this language.
     * @since 1.0
     */
    public String getName() {
        return impl.getName();
    }

    /**
     * Gets a human-readable name of the language implementation (for example, "Graal.JS"). Returns
     * <code>null</code> if no implementation name was specified.
     *
     * @since 1.0
     */
    public String getImplementationName() {
        return impl.getImplementationName();
    }

    /**
     * Gets the version information of the language in an arbitrary language-specific format.
     *
     * @since 1.0
     */
    public String getVersion() {
        return impl.getVersion();
    }

    /**
     * Returns <code>true</code> if a the language is suitable for interactive evaluation of
     * {@link Source sources}. {@link #setInteractive() Interactive} languages should be displayed
     * in interactive environments and presented to the user.
     *
     * @since 1.0
     */
    public boolean isInteractive() {
        return impl.isInteractive();
    }

    /**
     * Returns <code>true</code> if this language object represents the Java host language.
     * 
     * @since 1.0
     */
    public boolean isHost() {
        return impl.isHost();
    }

    /**
     * Returns the set of options provided by this language. Option values for languages can either
     * be provided while building an {@link Engine.Builder#setOption(String, String) engine} or a
     * {@link Context.Builder#setOption(String, String) context}. The option descriptor
     * {@link OptionDescriptor#getName() name} must be used as the option name.
     *
     * @since 1.0
     */
    public OptionDescriptors getOptions() {
        return impl.getOptions();
    }

    Engine getEngine() {
        return impl.getEngineAPI();
    }

}
