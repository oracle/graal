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

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.util.VMError;

public class TypeConfiguration implements ConfigurationBase {
    private final ConcurrentMap<String, ConfigurationType> types = new ConcurrentHashMap<>();

    public TypeConfiguration() {
    }

    public TypeConfiguration(TypeConfiguration other) {
        for (ConfigurationType configurationType : other.types.values()) {
            types.put(configurationType.getQualifiedJavaName(), new ConfigurationType(configurationType));
        }
    }

    public void removeAll(TypeConfiguration other) {
        for (Map.Entry<String, ConfigurationType> typeEntry : other.types.entrySet()) {
            types.computeIfPresent(typeEntry.getKey(), (key, value) -> {
                if (value.equals(typeEntry.getValue())) {
                    return null;
                }
                assert value.getQualifiedJavaName().equals(typeEntry.getValue().getQualifiedJavaName());
                value.removeAll(typeEntry.getValue());
                return value.isEmpty() ? null : value;
            });
        }
    }

    public ConfigurationType get(String qualifiedJavaName) {
        return types.get(qualifiedJavaName);
    }

    public void add(ConfigurationType type) {
        ConfigurationType previous = types.putIfAbsent(type.getQualifiedJavaName(), type);
        if (previous != null && previous != type) {
            VMError.shouldNotReachHere("Cannot replace existing type " + previous + " with " + type);
        }
    }

    public ConfigurationType getOrCreateType(String qualifiedForNameString) {
        return types.computeIfAbsent(SignatureUtil.toInternalClassName(qualifiedForNameString), ConfigurationType::new);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('[');
        String prefix = "";
        List<ConfigurationType> list = new ArrayList<>(types.values());
        list.sort(Comparator.comparing(ConfigurationType::getQualifiedJavaName));
        for (ConfigurationType value : list) {
            writer.append(prefix).newline();
            value.printJson(writer);
            prefix = ",";
        }
        writer.newline().append(']');
    }

    @Override
    public boolean isEmpty() {
        return types.isEmpty();
    }

}
