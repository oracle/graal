/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Comparator;
import java.util.Objects;

import org.graalvm.nativeimage.impl.UnresolvedConfigurationCondition;

import com.oracle.svm.core.configure.SerializationConfigurationParser;

import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.graal.compiler.util.json.JsonWriter;

import static com.oracle.svm.core.configure.ConfigurationParser.NAME_KEY;
import static com.oracle.svm.core.configure.ConfigurationParser.TYPE_KEY;

public class SerializationConfigurationType implements JsonPrintable, Comparable<SerializationConfigurationType> {
    private final UnresolvedConfigurationCondition condition;
    private final String qualifiedJavaName;
    private final String qualifiedCustomTargetConstructorJavaName;

    public SerializationConfigurationType(UnresolvedConfigurationCondition condition, String qualifiedJavaName, String qualifiedCustomTargetConstructorJavaName) {
        assert qualifiedJavaName.indexOf('/') == -1 : "Requires qualified Java name, not the internal representation";
        assert !qualifiedJavaName.startsWith("[") : "Requires Java source array syntax, for example java.lang.String[]";
        assert qualifiedCustomTargetConstructorJavaName == null || qualifiedCustomTargetConstructorJavaName.indexOf('/') == -1 : "Requires qualified Java name, not internal representation";
        assert qualifiedCustomTargetConstructorJavaName == null || !qualifiedCustomTargetConstructorJavaName.startsWith("[") : "Requires Java source array syntax, for example java.lang.String[]";
        Objects.requireNonNull(condition);
        this.condition = condition;
        Objects.requireNonNull(qualifiedJavaName);
        this.qualifiedJavaName = qualifiedJavaName;
        this.qualifiedCustomTargetConstructorJavaName = qualifiedCustomTargetConstructorJavaName;
    }

    public String getQualifiedJavaName() {
        return qualifiedJavaName;
    }

    public String getQualifiedCustomTargetConstructorJavaName() {
        return qualifiedCustomTargetConstructorJavaName;
    }

    public UnresolvedConfigurationCondition getCondition() {
        return condition;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        printJson(writer, true);
    }

    public void printLegacyJson(JsonWriter writer) throws IOException {
        printJson(writer, false);
    }

    private void printJson(JsonWriter writer, boolean combinedFile) throws IOException {
        writer.appendObjectStart();
        ConfigurationConditionPrintable.printConditionAttribute(condition, writer, combinedFile);
        writer.quote(combinedFile ? TYPE_KEY : NAME_KEY).appendFieldSeparator().quote(qualifiedJavaName);
        if (qualifiedCustomTargetConstructorJavaName != null) {
            writer.appendSeparator();
            writer.quote(SerializationConfigurationParser.CUSTOM_TARGET_CONSTRUCTOR_CLASS_KEY).appendFieldSeparator()
                            .quote(qualifiedCustomTargetConstructorJavaName);
        }
        writer.appendObjectEnd();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SerializationConfigurationType that = (SerializationConfigurationType) o;
        return condition.equals(that.condition) &&
                        qualifiedJavaName.equals(that.qualifiedJavaName) &&
                        Objects.equals(qualifiedCustomTargetConstructorJavaName, that.qualifiedCustomTargetConstructorJavaName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, qualifiedJavaName, qualifiedCustomTargetConstructorJavaName);
    }

    @Override
    public int compareTo(SerializationConfigurationType other) {
        int compareName = qualifiedJavaName.compareTo(other.qualifiedJavaName);
        if (compareName != 0) {
            return compareName;
        }
        int compareCondition = condition.compareTo(other.condition);
        if (compareCondition != 0) {
            return compareCondition;
        }
        Comparator<String> nullsFirstCompare = Comparator.nullsFirst(Comparator.naturalOrder());
        return nullsFirstCompare.compare(qualifiedCustomTargetConstructorJavaName, other.qualifiedCustomTargetConstructorJavaName);
    }
}
