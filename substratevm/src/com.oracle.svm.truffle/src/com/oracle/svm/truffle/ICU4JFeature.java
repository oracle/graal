/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

public final class ICU4JFeature implements Feature {

    @Override
    public String getDescription() {
        return "Provides support for ICU4J library.";
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization("com.ibm.icu", "ICU4J needs reinitialization at runtime.");
        // The ICU charsets are removed to reduce the resulting image size. ICU charsets increase
        // the image size by ~ 6 MB. Unlike the com.ibm.icu package re-initialization, which is
        // necessary for proper ICU functionality, the removal is just a performance optimization.
        Map<String, Charset> charsets = ImageSingletons.lookup(LocalizationSupport.class).charsets;
        Charset.availableCharsets().values().stream().filter(ICU4JFeature::isICUCharset).flatMap(ICU4JFeature::charsetNames).map(String::toLowerCase).distinct().forEach((String name) -> {
            Charset c = charsets.get(name);
            if (c != null && isICUCharset(c)) {
                charsets.remove(name);
                // The ICU charset could replace JDK charset because the LocalizationFeature does
                // not check for duplication. We have to restore the original JDK charset.
                Charset c2 = Charset.forName(name);
                if (!isICUCharset(c2)) {
                    charsets.put(name, c2);
                }
            }
        });
    }

    private static boolean isICUCharset(Charset c) {
        return c.getClass().getName().startsWith("com.ibm.icu.");
    }

    private static Stream<String> charsetNames(Charset c) {
        HashSet<String> names = new HashSet<>();
        names.add(c.name());
        names.addAll(c.aliases());
        return names.stream();
    }
}
