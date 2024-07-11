/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.filters;

import java.io.IOException;
import java.net.URI;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.svm.core.configure.ConfigurationParser;

import jdk.graal.compiler.util.json.JsonParserException;
import jdk.graal.compiler.util.json.JsonWriter;

public class FilterConfigurationParser extends ConfigurationParser {
    private final ConfigurationFilter filter;

    public FilterConfigurationParser(ConfigurationFilter filter) {
        super(true);
        assert filter != null;
        this.filter = filter;
    }

    static void parseEntry(Object entryObject, BiConsumer<String, ConfigurationFilter.Inclusion> parsedEntryConsumer) {
        EconomicMap<String, Object> entry = asMap(entryObject, "Filter entries must be objects");
        Object qualified = null;
        HierarchyFilterNode.Inclusion inclusion = null;
        String exactlyOneMessage = "Exactly one of attributes 'includeClasses' and 'excludeClasses' must be specified for a filter entry";
        MapCursor<String, Object> cursor = entry.getEntries();
        while (cursor.advance()) {
            if (qualified != null) {
                throw new JsonParserException(exactlyOneMessage);
            }
            qualified = cursor.getValue();
            if ("includeClasses".equals(cursor.getKey())) {
                inclusion = ConfigurationFilter.Inclusion.Include;
            } else if ("excludeClasses".equals(cursor.getKey())) {
                inclusion = ConfigurationFilter.Inclusion.Exclude;
            } else {
                throw new JsonParserException("Unknown attribute '" + cursor.getKey() + "' (supported attributes: 'includeClasses', 'excludeClasses') in filter");
            }
        }
        if (qualified == null) {
            throw new JsonParserException(exactlyOneMessage);
        }
        parsedEntryConsumer.accept(asString(qualified), inclusion);
    }

    static void printEntry(JsonWriter writer, boolean[] isFirstRule, ConfigurationFilter.Inclusion inclusion, String rule) throws IOException {
        if (inclusion == null) {
            return;
        }
        if (!isFirstRule[0]) {
            writer.append(',').newline();
        } else {
            isFirstRule[0] = false;
        }
        writer.append('{');
        switch (inclusion) {
            case Include:
                writer.quote("includeClasses");
                break;
            case Exclude:
                writer.quote("excludeClasses");
                break;
            default:
                throw new IllegalStateException("Unsupported inclusion value: " + inclusion.name());
        }
        writer.append(':');
        writer.quote(rule);
        writer.append("}");
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        filter.parseFromJson(asMap(json, "First level of document must be an object"));
    }

}
