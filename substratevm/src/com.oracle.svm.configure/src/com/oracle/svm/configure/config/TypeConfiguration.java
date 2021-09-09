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
package com.oracle.svm.configure.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.configure.ConditionalElement;
import com.oracle.svm.core.util.VMError;

public class TypeConfiguration implements ConfigurationBase {
    private final ConcurrentMap<ConditionalElement<String>, ConfigurationType> types = new ConcurrentHashMap<>();

    public TypeConfiguration() {
    }

    public TypeConfiguration(TypeConfiguration other) {
        for (Map.Entry<ConditionalElement<String>, ConfigurationType> entry : other.types.entrySet()) {
            types.put(entry.getKey(), new ConfigurationType(entry.getValue()));
        }
    }

    public void removeAll(TypeConfiguration other) {
        for (Map.Entry<ConditionalElement<String>, ConfigurationType> typeEntry : other.types.entrySet()) {
            types.computeIfPresent(typeEntry.getKey(), (key, value) -> {
                if (value.equals(typeEntry.getValue())) {
                    return null;
                }
                assert value.getCondition().equals(typeEntry.getValue().getCondition());
                assert value.getQualifiedJavaName().equals(typeEntry.getValue().getQualifiedJavaName());
                value.removeAll(typeEntry.getValue());
                return value.isEmpty() ? null : value;
            });
        }
    }

    public ConfigurationType get(ConfigurationCondition condition, String qualifiedJavaName) {
        return types.get(new ConditionalElement<>(condition, qualifiedJavaName));
    }

    public void add(ConfigurationType type) {
        ConfigurationType previous = types.putIfAbsent(new ConditionalElement<>(type.getCondition(), type.getQualifiedJavaName()), type);
        if (previous != null && previous != type) {
            VMError.shouldNotReachHere("Cannot replace existing type " + previous + " with " + type);
        }
    }

    public ConfigurationType getOrCreateType(ConfigurationCondition condition, String qualifiedForNameString) {
        return types.computeIfAbsent(new ConditionalElement<>(condition, qualifiedForNameString), p -> new ConfigurationType(p.getCondition(), p.getElement()));
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        List<ConfigurationType> typesList = new ArrayList<>(this.types.values());
        typesList.sort(Comparator.comparing(ConfigurationType::getQualifiedJavaName).thenComparing(ConfigurationType::getCondition));

        writer.append('[');
        String prefix = "";
        for (ConfigurationType type : typesList) {
            writer.append(prefix).newline();
            type.printJson(writer);
            prefix = ",";
        }
        writer.newline().append(']');
    }

    @Override
    public boolean isEmpty() {
        return types.isEmpty();
    }

}
