/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;

final class ContextReference<C> {
    private static Map<TruffleLanguage<?>, Integer> ids = new HashMap<>();
    private final ContextStoreProfile profile;
    private final TruffleLanguage<C> language;
    private final int languageId;

    private ContextReference(ContextStoreProfile profile, TruffleLanguage<C> language, int languageId) {
        this.profile = profile;
        this.language = language;
        this.languageId = languageId;
    }

    @SuppressWarnings("unchecked")
    public C get() {
        final ContextStore store = profile.get();
        Object context = store.getContext(languageId);
        if (context == this) {
            return null;
        }
        if (context == null) {
            context = ExecutionImpl.findContext(store.vm, language.getClass());
            store.setContext(languageId, context == null ? this : context);
        }
        return (C) context;
    }

    public static synchronized <C> ContextReference<C> create(TruffleLanguage<C> language) {
        Integer id = ids.get(language);
        if (id == null) {
            ids.put(language, id = ids.size());
        }
        return new ContextReference<>(ExecutionImpl.sharedProfile(), language, id);
    }
}
