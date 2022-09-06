/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.UnknownObjectField;

public final class StringInternSupport {

    /** The String intern table at run time. */
    private final ConcurrentHashMap<String, String> internedStrings;

    /**
     * The native image contains a lot of interned strings. All Java String literals, and all class
     * names, are interned per Java specification. We don't want the memory overhead of an hash
     * table entry, so we store them (sorted) in this String[] array. When a string is interned at
     * run time, it is added to the real hash map, so we pay the (logarithmic) cost of the array
     * access only once per string.
     *
     * The field is set late during image generation, so the value is not available during static
     * analysis and compilation.
     */
    @UnknownObjectField(types = {String[].class}) private String[] imageInternedStrings;

    @Platforms(Platform.HOSTED_ONLY.class)
    public StringInternSupport() {
        this.internedStrings = new ConcurrentHashMap<>(16, 0.75f, 1);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setImageInternedStrings(String[] newImageInternedStrings) {
        this.imageInternedStrings = newImageInternedStrings;
    }

    protected String intern(String str) {
        String result = internedStrings.get(str);
        if (result != null) {
            return result;
        } else {
            return doIntern(str);
        }
    }

    private String doIntern(String str) {
        String result = str;
        int imageIdx = Arrays.binarySearch(imageInternedStrings, str);
        if (imageIdx >= 0) {
            result = imageInternedStrings[imageIdx];
        }
        String oldValue = internedStrings.putIfAbsent(result, result);
        if (oldValue != null) {
            result = oldValue;
        }
        return result;
    }
}
