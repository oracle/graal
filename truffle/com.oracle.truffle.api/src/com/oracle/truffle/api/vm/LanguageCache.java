/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.vm.TruffleVM.Language;
import java.util.HashMap;
import java.util.Map;

/**
 * Ahead-of-time initialization. If the JVM is started with -Dcom.oracle.truffle.aot=true, it
 * populates cache with languages found in application classloader.
 */
final class LanguageCache {
    private static final Map<String, TruffleLanguage<?>> CACHE;
    static {
        Map<String, TruffleLanguage<?>> map = null;
        if (Boolean.getBoolean("com.oracle.truffle.aot")) { // NOI18N
            map = new HashMap<>();
            for (Language description : TruffleVM.newVM().build().getLanguages().values()) {
                TruffleLanguage<?> language = description.getImpl(false);
                map.put(language.getClass().getName(), language);
            }
        }
        CACHE = map;
    }

    static TruffleLanguage<?> find(String name) {
        return CACHE == null ? null : CACHE.get(name);
    }
}
