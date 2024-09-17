/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.localization.substitutions;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import sun.util.locale.BaseLocale;
import sun.util.locale.InternalLocaleBuilder;
import sun.util.locale.LanguageTag;
import sun.util.locale.LocaleSyntaxException;
import sun.util.locale.LocaleUtils;
import sun.util.locale.StringTokenIterator;

@TargetClass(InternalLocaleBuilder.class)
public final class Target_sun_util_locale_InternalLocaleBuilder {

    @Alias
    public native Target_sun_util_locale_InternalLocaleBuilder clearExtensions();

    @Alias
    private native Target_sun_util_locale_InternalLocaleBuilder setExtensions(List<String> bcpExtensions, String privateuse);

    /**
     * This method replicates the JDK implementation of
     * {@link InternalLocaleBuilder#setExtensions(String)}, with a minor adjustment. Instead of
     * using {@link String#replaceAll(String, String)}, which would pull a lot of JDK code, we use a
     * custom implementation.
     */
    @Substitute
    public Target_sun_util_locale_InternalLocaleBuilder setExtensions(String subtagsParam) throws LocaleSyntaxException {
        if (LocaleUtils.isEmpty(subtagsParam)) {
            clearExtensions();
            return this;
        }
        String subtags = InternalLocaleBuilderUtil.replaceAll(subtagsParam, BaseLocale.SEP.charAt(0), LanguageTag.SEP.charAt(0));
        StringTokenIterator itr = new StringTokenIterator(subtags, LanguageTag.SEP);

        List<String> extensions = null;
        String privateuse = null;

        int parsed = 0;
        int start;

        // Make a list of extension subtags
        while (!itr.isDone()) {
            String s = itr.current();
            if (LanguageTag.isExtensionSingleton(s)) {
                start = itr.currentStart();
                String singleton = s;
                StringBuilder sb = new StringBuilder(singleton);

                itr.next();
                while (!itr.isDone()) {
                    s = itr.current();
                    if (LanguageTag.isExtensionSubtag(s)) {
                        sb.append(LanguageTag.SEP).append(s);
                        parsed = itr.currentEnd();
                    } else {
                        break;
                    }
                    itr.next();
                }

                if (parsed < start) {
                    throw new LocaleSyntaxException("Incomplete extension '" + singleton + "'",
                                    start);
                }

                if (extensions == null) {
                    extensions = new ArrayList<>(4);
                }
                extensions.add(sb.toString());
            } else {
                break;
            }
        }
        if (!itr.isDone()) {
            String s = itr.current();
            if (LanguageTag.isPrivateusePrefix(s)) {
                start = itr.currentStart();
                StringBuilder sb = new StringBuilder(s);

                itr.next();
                while (!itr.isDone()) {
                    s = itr.current();
                    if (!LanguageTag.isPrivateuseSubtag(s)) {
                        break;
                    }
                    sb.append(LanguageTag.SEP).append(s);
                    parsed = itr.currentEnd();

                    itr.next();
                }
                if (parsed <= start) {
                    throw new LocaleSyntaxException("Incomplete privateuse:" + subtags.substring(start),
                                    start);
                } else {
                    privateuse = sb.toString();
                }
            }
        }

        if (!itr.isDone()) {
            throw new LocaleSyntaxException("Ill-formed extension subtags:" + subtags.substring(itr.currentStart()),
                            itr.currentStart());
        }

        return setExtensions(extensions, privateuse);
    }
}

class InternalLocaleBuilderUtil {

    public static String replaceAll(String input, char toReplace, char replaceWith) {
        if (input == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);
            if (currentChar == toReplace) {
                result.append(replaceWith);
            } else {
                result.append(currentChar);
            }
        }

        return result.toString();
    }
}
