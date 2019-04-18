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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.util.UserError;

import jdk.vm.ci.meta.MetaUtil;

public class TypeConfiguration implements JsonPrintable {
    private final Map<String, ConfigurationType> types = new HashMap<>();

    public ConfigurationType get(String qualifiedJavaName) {
        return types.get(qualifiedJavaName);
    }

    public ConfigurationType getByInternalName(String name) {
        return types.get(MetaUtil.internalNameToJava(name, true, false));
    }

    public void add(ConfigurationType type) {
        ConfigurationType previous = types.putIfAbsent(type.getQualifiedJavaName(), type);
        UserError.guarantee(previous == null || previous == type, "Cannot replace existing type");
    }

    public ConfigurationType getOrCreateType(String qualifiedJavaName) {
        return types.computeIfAbsent(qualifiedJavaName, ConfigurationType::new);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('[');
        String prefix = "\n";
        List<ConfigurationType> list = new ArrayList<>(types.values());
        list.sort(Comparator.comparing(ConfigurationType::getQualifiedJavaName));
        for (ConfigurationType value : list) {
            writer.append(prefix);
            value.printJson(writer);
            prefix = ",\n";
        }
        writer.newline().append(']').newline();
    }
}
