/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.configure.ExceptionConfigurationParser;
import com.oracle.svm.configure.json.JsonWriter;

public class ExceptionConfiguration extends ConfigurationBase<ExceptionConfiguration, ExceptionConfiguration.Predicate> {
    private final ConcurrentMap<String, List<String>> exceptions = new ConcurrentHashMap<>();

    public ExceptionConfiguration() {
    }

    public ExceptionConfiguration(ExceptionConfiguration other) {
        exceptions.putAll(other.exceptions);
    }

    public void add(String clazz, String exception) {
        List<String> exceptionsOfClass = exceptions.computeIfAbsent(clazz, key -> new ArrayList<>());
        exceptionsOfClass.add(exception);
    }

    @Override
    public boolean isEmpty() {
        return exceptions.isEmpty();
    }

    @Override
    public ExceptionConfiguration copy() {
        return new ExceptionConfiguration(this);
    }

    @Override
    protected void merge(ExceptionConfiguration other) {
        exceptions.putAll(other.exceptions);
    }

    @Override
    public void mergeConditional(ConfigurationCondition condition, ExceptionConfiguration other) {
    }

    @Override
    public ConfigurationParser createParser() {
        return new ExceptionConfigurationParser(true);
    }

    @Override
    protected void subtract(ExceptionConfiguration other) {
        exceptions.keySet().removeAll(other.exceptions.keySet());
    }

    @Override
    protected void intersect(ExceptionConfiguration other) {
        exceptions.keySet().retainAll(other.exceptions.keySet());
    }

    @Override
    protected void removeIf(Predicate predicate) {
        exceptions.keySet().removeIf(predicate::testExceptionType);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('[').indent().newline();
        writer.append('{').indent();
        String prefix = "";
        for (Map.Entry<String, List<String>> entry : exceptions.entrySet()) {
            writer.append(prefix).newline();
            writer.quote("class").append(":").quote(entry.getKey()).append(",").newline();
            writer.quote("exceptions").append(":[").indent();
            prefix = "";
            for (String exception : entry.getValue()) {
                writer.append(prefix).newline().quote(exception);
                prefix = ",";
            }
            writer.unindent().newline().append(']');
            prefix = ",";
        }
        writer.unindent().newline().append('}');
        writer.unindent().newline().append(']').newline();
    }

    public interface Predicate {
        public boolean testExceptionType(String className);
    }
}
