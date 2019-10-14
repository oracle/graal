/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.nodes.LanguageInfo;
import java.util.Set;

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
        EngineSupport engine = InstrumentAccessor.engineAccess();
        if (engine == null) {
            return null;
        }
        for (Class<? extends Tag> tag : (Set<? extends Class<? extends Tag>>) engine.getProvidedTags(language)) {
            String alias = getIdentifier(tag);
            if (alias != null && alias.equals(tagId)) {
                return tag;
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
