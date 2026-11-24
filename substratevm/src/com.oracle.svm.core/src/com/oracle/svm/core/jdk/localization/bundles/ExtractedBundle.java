/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.localization.bundles;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

public final class ExtractedBundle extends AbstractMap<String, Object> implements StoredBundle, ConcurrentMap<String, Object> {
    private final EconomicMap<String, Object> content;

    public ExtractedBundle(Map<String, Object> lookup) {
        if (lookup.isEmpty()) {
            this.content = EconomicMap.emptyMap();
        } else {
            EconomicMap<String, Object> copiedContent = EconomicMap.create(lookup.size());
            lookup.forEach(copiedContent::put);
            this.content = copiedContent;
        }
    }

    /// Builds instance contents using the same map implementation as non-empty prepared bundles.
    public ExtractedBundle(Object[][] entries) {
        EconomicMap<String, Object> instanceContent = EconomicMap.create(entries.length);
        for (Object[] entry : entries) {
            String key = (String) entry[0];
            Object value = entry[1];
            if (key == null || value == null) {
                throw new NullPointerException();
            }
            instanceContent.put(key, value);
        }
        this.content = instanceContent;
    }

    @Override
    public Map<String, Object> getContent(Object bundle) {
        return this;
    }

    @Override
    public int size() {
        return content.size();
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof String stringKey && content.containsKey(stringKey);
    }

    @Override
    public Object get(Object key) {
        return key instanceof String stringKey ? content.get(stringKey) : null;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> entries = new LinkedHashSet<>(content.size());
        MapCursor<String, Object> cursor = content.getEntries();
        while (cursor.advance()) {
            entries.add(Map.entry(cursor.getKey(), cursor.getValue()));
        }
        return Collections.unmodifiableSet(entries);
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replace(String key, Object oldValue, Object newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object replace(String key, Object value) {
        throw new UnsupportedOperationException();
    }
}
