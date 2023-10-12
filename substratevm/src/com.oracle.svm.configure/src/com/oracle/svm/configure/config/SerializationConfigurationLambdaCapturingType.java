/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.impl.ConfigurationCondition;
import com.oracle.svm.common.util.json.JsonPrintable;
import com.oracle.svm.common.util.json.JsonWriter;
import com.oracle.svm.configure.SerializationConfigurationParser;

public class SerializationConfigurationLambdaCapturingType implements JsonPrintable {
    private final ConfigurationCondition condition;
    private final String qualifiedJavaName;

    public SerializationConfigurationLambdaCapturingType(ConfigurationCondition condition, String qualifiedJavaName) {
        assert qualifiedJavaName.indexOf('/') == -1 : "Requires qualified Java name, not the internal representation";
        Objects.requireNonNull(condition);
        this.condition = condition;
        Objects.requireNonNull(qualifiedJavaName);
        this.qualifiedJavaName = qualifiedJavaName;
    }

    public ConfigurationCondition getCondition() {
        return condition;
    }

    public String getQualifiedJavaName() {
        return qualifiedJavaName;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{').indent().newline();
        ConfigurationConditionPrintable.printConditionAttribute(condition, writer);

        writer.quote(SerializationConfigurationParser.NAME_KEY).append(":").quote(qualifiedJavaName);
        writer.unindent().newline().append('}');
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SerializationConfigurationLambdaCapturingType that = (SerializationConfigurationLambdaCapturingType) o;
        return condition.equals(that.condition) &&
                        qualifiedJavaName.equals(that.qualifiedJavaName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, qualifiedJavaName);
    }

    public static final class SerializationConfigurationLambdaCapturingTypesComparator implements Comparator<SerializationConfigurationLambdaCapturingType> {

        @Override
        public int compare(SerializationConfigurationLambdaCapturingType o1, SerializationConfigurationLambdaCapturingType o2) {
            int compareName = o1.qualifiedJavaName.compareTo(o2.qualifiedJavaName);
            if (compareName != 0) {
                return compareName;
            }
            return o1.condition.compareTo(o2.condition);
        }
    }
}
