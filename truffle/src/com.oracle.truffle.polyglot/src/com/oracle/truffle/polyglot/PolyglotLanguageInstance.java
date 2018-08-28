/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.VMAccessor.LANGUAGE;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;

final class PolyglotLanguageInstance {

    final PolyglotLanguage language;
    final TruffleLanguage<?> spi;

    private final PolyglotSourceCache sourceCache;

    private volatile OptionValuesImpl firstOptionValues;

    PolyglotLanguageInstance(PolyglotLanguage language) {
        this.language = language;
        try {
            this.spi = language.cache.loadLanguage();
            LANGUAGE.initializeLanguage(spi, language.info, language);
            if (!language.engine.singleContext.isValid()) {
                initializeMultiContext();
            }
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Error initializing language '%s' using class '%s'.", language.cache.getId(), language.cache.getClassName()), e);
        }
        this.sourceCache = new PolyglotSourceCache();
    }

    boolean areOptionsCompatible(OptionValuesImpl newOptionValues) {
        OptionValuesImpl firstOptions = this.firstOptionValues;
        if (firstOptionValues == null) {
            return true;
        } else {
            return VMAccessor.LANGUAGE.areOptionsCompatible(spi, firstOptions, newOptionValues);
        }
    }

    void claim(OptionValuesImpl optionValues) {
        assert Thread.holdsLock(language.engine);
        if (this.firstOptionValues == null) {
            this.firstOptionValues = optionValues;
        }
    }

    void initializeMultiContext() {
        assert !language.engine.singleContext.isValid();
        if (language.cache.getPolicy() != ContextPolicy.EXCLUSIVE) {
            LANGUAGE.initializeMultiContext(spi);
        }
    }

    PolyglotSourceCache getSourceCache() {
        return sourceCache;
    }

}
