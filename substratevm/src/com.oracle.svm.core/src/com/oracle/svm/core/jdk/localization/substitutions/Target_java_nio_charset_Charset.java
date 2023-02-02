/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK19OrLater;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;

@TargetClass(java.nio.charset.Charset.class)
@SuppressWarnings({"unused"})
public final class Target_java_nio_charset_Charset {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static volatile Object[] cache1;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static volatile Object[] cache2;

    @Substitute
    private static Charset defaultCharset() {
        return ImageSingletons.lookup(LocalizationSupport.class).defaultCharset;
    }

    @Substitute
    private static SortedMap<String, Charset> availableCharsets() {
        TreeMap<String, Charset> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Charset> charsets = ImageSingletons.lookup(LocalizationSupport.class).charsets;
        for (Charset charset : charsets.values()) {
            result.put(charset.name(), charset);
        }
        return Collections.unmodifiableSortedMap(result);
    }

    @Alias
    @TargetElement(onlyWith = JDK19OrLater.class)
    public static native Charset forName(String charsetName, Charset fallback);

    @Substitute
    private static Charset lookup2(String charsetName) {
        Object[] a;
        if ((a = cache2) != null && charsetName.equals(a[0])) {
            cache2 = cache1;
            cache1 = a;
            return (Charset) a[1];
        }

        Map<String, Charset> charsets = ImageSingletons.lookup(LocalizationSupport.class).charsets;
        Charset cs = charsets.get(charsetName.toLowerCase());
        if (cs != null) {
            cache(charsetName, cs);
            return cs;
        }

        /* Only need to check the name if we didn't find a charset for it */
        checkName(charsetName);
        return null;
    }

    @Alias
    private static native void checkName(String s);

    @Alias
    private static native void cache(String charsetName, Charset cs);
}
