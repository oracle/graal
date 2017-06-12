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
package com.oracle.truffle.api.nodes;

import java.util.Set;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;

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
    private final Object engineObject;
    volatile TruffleLanguage<?> spi;

    LanguageInfo(Object engineObject, String id, String name, String version, Set<String> mimeTypes) {
        this.engineObject = engineObject;
        this.id = id;
        this.name = name;
        this.version = version;
        this.mimeTypes = mimeTypes;
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
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#getName()}.
     *
     * @since 0.25
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the version of the language. This version is equivalent to the name returned by
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#getVersion()}.
     *
     * @since 0.25
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the MIME types supported by this language. This set is equivalent to the set returned
     * by {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#getMimeTypes()}.
     *
     * @since 0.25
     */
    public Set<String> getMimeTypes() {
        return mimeTypes;
    }

    Object getEngineObject() {
        return engineObject;
    }

    void setSpi(TruffleLanguage<?> spi) {
        this.spi = spi;
    }

    TruffleLanguage<?> getSpi() {
        return spi;
    }
}
