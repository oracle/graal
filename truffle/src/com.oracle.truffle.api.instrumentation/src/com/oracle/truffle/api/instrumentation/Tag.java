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
package com.oracle.truffle.api.instrumentation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AccessorInstrumentHandler;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Base class for tags used in the Truffle instrumentation framework.
 *
 * @see StandardTags For the standard set of tags
 * @see #findProvidedTag(LanguageInfo, String) to find a provided tag of a language
 * @since 0.33
 */
public abstract class Tag {

    /**
     * No instances of tags allowed. Tags are marker classes.
     *
     * @since 0.33
     */
    protected Tag() {
        throw new AssertionError("No tag instances allowed.");
    }

    /**
     * Finds a provided tag by the language using its {@link Tag.Identifier identifier}. If the
     * language implementation class is not yet loaded then this method will force the loading.
     * Therefore it is not recommended to iterate over the entire list of languages and request all
     * provided tags. It is guaranteed that there is only one provided tag class per tag identifier
     * and language. For different languages the same tag id might refer to different tag classes.
     *
     * @since 0.33
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends Tag> findProvidedTag(LanguageInfo language, String tagId) {
        Objects.requireNonNull(language);
        Objects.requireNonNull(tagId);
        EngineSupport engine = AccessorInstrumentHandler.engineAccess();
        if (engine == null) {
            return null;
        }
        Class<? extends TruffleLanguage<?>> lang = engine.getLanguageClass(language);
        ProvidedTags tags = lang.getAnnotation(ProvidedTags.class);
        if (tags != null) {
            for (Class<? extends Tag> tag : (Class<? extends Tag>[]) tags.value()) {
                String alias = getIdentifier(tag);
                if (alias != null && alias.equals(tagId)) {
                    return tag;
                }
            }
        }
        return null;
    }

    /**
     * Returns the alias of a particular tag or <code>null</code> if no alias was specified for this
     * tag.
     *
     * @param tag the tag to return the alias for.
     * @return the alias string
     * @since 0.33
     */
    public static String getIdentifier(Class<? extends Tag> tag) {
        Objects.requireNonNull(tag);
        Tag.Identifier alias = tag.getAnnotation(Tag.Identifier.class);
        if (alias != null) {
            return alias.value();
        }
        return null;
    }

    /**
     * Annotation applied to {@link Tag} subclasses to specify the tag identifier. The tag
     * identifier can be used to {@link Tag#findProvidedTag(LanguageInfo, String) find} and load tag
     * classes used by tools.
     *
     * @since 0.33
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value = TYPE)
    public @interface Identifier {

        /**
         * Returns the identifier value as string.
         *
         * @since 0.33
         */
        String value();

    }

}
