/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.StringInternSupport;

/**
 * Performs de-duplication of String without using {@link String#intern}. Calling
 * {@link String#intern} has negative side effects on the image size because all interned strings
 * must be maintained in an array in the image heap, see {@link StringInternSupport}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class HostedStringDeduplication {

    public static HostedStringDeduplication singleton() {
        return ImageSingletons.lookup(HostedStringDeduplication.class);
    }

    /**
     * This could really be a {@link Set} instead of a {@link Map}, but we rely on
     * {@link ConcurrentMap#putIfAbsent}. The key and value are always the same.
     */
    private final ConcurrentMap<String, String> deduplicatedStrings = new ConcurrentHashMap<>();

    HostedStringDeduplication() {
    }

    /**
     * De-duplicates the provided string.
     *
     * The handling of interned strings depends on the "unintern" parameter: if it is false, then no
     * de-duplication is performed and the interned string is returned; if it is true, then
     * de-duplication is performed on a non-interned copy of the string, i.e., the result is always
     * a non-interned string.
     */
    public String deduplicate(String s, boolean unintern) {
        if (s == null) {
            return null;
        }
        String lookup;
        if (isInternedString(s)) {
            if (unintern) {
                /* Make a non-interned copy of the original interned string. */
                lookup = new String(s);
            } else {
                /*
                 * Do not process interned strings because the result might not be interned, i.e.,
                 * the de-duplication could "de-intern" the argument.
                 */
                return s;
            }
        } else {
            lookup = s;
        }

        String previous = deduplicatedStrings.putIfAbsent(lookup, lookup);
        String result = previous != null ? previous : lookup;

        assert !isInternedString(result);
        return result;
    }

    /**
     * Returns true if the provided {@link String} is interned.
     */
    @SuppressFBWarnings(value = "ES", justification = "Reference equality check needed to detect intern status")
    public static boolean isInternedString(String str) {
        /*
         * Check if we have a string that is interned in the host VM.
         *
         * We cannot just check that "str.intern() == str": if the string was not interned before,
         * then intern() returns the original object and the comparison will succeed. Instead we
         * first make a copy of the string and intern that. If the result of interning the copy
         * returns the original String, then the original String was interned before this.
         *
         * Calling intern during image generation has a side effect on the hosting HotSpot VM: the
         * string is put into the string intern table. But it does not have a side effect on the
         * generated image: We do not put all strings that are interned by HotSpot into the image
         * heap. We cannot even do that, because that would require access to HotSpot's internal
         * string table. As long as the interned string is not reachable otherwise, there is no
         * problem.
         */
        return new String(str).intern() == str;
    }
}

@AutomaticFeature
class HostedStringDeduplicationFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(HostedStringDeduplication.class, new HostedStringDeduplication());
    }
}
