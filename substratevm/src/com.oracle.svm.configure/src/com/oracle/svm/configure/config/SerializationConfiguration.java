/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Alibaba Group Holding Limited. All rights reserved.
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.SerializationConfigurationParser;

public class SerializationConfiguration implements ConfigurationBase {

    private static final String KEY_SEPARATOR = "|";

    private final Set<String> serializations = ConcurrentHashMap.newKeySet();

    public SerializationConfiguration() {
    }

    public SerializationConfiguration(SerializationConfiguration other) {
        this.serializations.addAll(other.serializations);
    }

    public void removeAll(SerializationConfiguration other) {
        serializations.removeAll(other.serializations);
    }

    public void add(String serializationTargetClass, String customTargetConstructorClass) {
        serializations.add(mapNameAndConstructor(serializationTargetClass, customTargetConstructorClass));
    }

    public boolean contains(String serializationTargetClass, String customTargetConstructorClass) {
        return serializations.contains(mapNameAndConstructor(serializationTargetClass, customTargetConstructorClass));
    }

    private static String mapNameAndConstructor(String serializationTargetClass, String customTargetConstructorClass) {
        return serializationTargetClass + (customTargetConstructorClass != null ? KEY_SEPARATOR + customTargetConstructorClass : "");
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('[').indent();
        String prefix = "";
        for (String entry : serializations) {
            writer.append(prefix);
            writer.newline().append('{').newline();
            String[] serializationKeyValues = SubstrateUtil.split(entry, KEY_SEPARATOR, 2);
            String className = serializationKeyValues[0];
            writer.quote(SerializationConfigurationParser.NAME_KEY).append(":").quote(className);
            if (serializationKeyValues.length > 1) {
                writer.append(",").newline();
                writer.quote(SerializationConfigurationParser.CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY).append(":").quote(serializationKeyValues[1]);
            }
            writer.newline().append('}');
            prefix = ",";
        }
        writer.unindent().newline();
        writer.append(']');
    }

    @Override
    public boolean isEmpty() {
        return serializations.isEmpty();
    }

}
