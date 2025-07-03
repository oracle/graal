/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure;

import static com.oracle.svm.configure.ConfigurationParser.DECLARING_CLASS_KEY;
import static com.oracle.svm.configure.ConfigurationParser.DECLARING_METHOD_KEY;
import static com.oracle.svm.configure.ConfigurationParser.INTERFACES_KEY;
import static com.oracle.svm.configure.ConfigurationParser.LAMBDA_KEY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import jdk.graal.compiler.util.json.JsonPrinter;
import jdk.graal.compiler.util.json.JsonWriter;

public record LambdaConfigurationTypeDescriptor(ConfigurationTypeDescriptor declaringClass, ConfigurationParser.ConfigurationMethodDescriptor declaringMethod,
                List<NamedConfigurationTypeDescriptor> interfaces) implements ConfigurationTypeDescriptor {

    public static final ConfigurationTypeDescriptor[] EMPTY_TYPE_DESCRIPTOR_ARRAY = new ConfigurationTypeDescriptor[0];

    public static LambdaConfigurationTypeDescriptor fromReflectionNames(String declaringClass, List<String> interfaces) {
        return new LambdaConfigurationTypeDescriptor(NamedConfigurationTypeDescriptor.fromReflectionName(declaringClass),
                        null, getNamedConfigurationTypeDescriptors(interfaces));
    }

    public static LambdaConfigurationTypeDescriptor fromTypeNames(String declaringClass, List<String> interfaces) {
        return new LambdaConfigurationTypeDescriptor(NamedConfigurationTypeDescriptor.fromTypeName(declaringClass), null,
                        getNamedConfigurationTypeDescriptors(interfaces));
    }

    private static List<NamedConfigurationTypeDescriptor> getNamedConfigurationTypeDescriptors(List<String> interfaces) {
        List<NamedConfigurationTypeDescriptor> reflectionInterfaces = new ArrayList<>(interfaces.size());
        for (var interfaceName : interfaces) {
            reflectionInterfaces.add(NamedConfigurationTypeDescriptor.fromReflectionName(interfaceName));
        }
        return reflectionInterfaces;
    }

    @Override
    public Kind getDescriptorType() {
        return Kind.LAMBDA;
    }

    @Override
    public Collection<String> getAllQualifiedJavaNames() {
        List<String> allNames = new ArrayList<>(declaringClass.getAllQualifiedJavaNames());
        for (ConfigurationTypeDescriptor intf : interfaces) {
            allNames.addAll(intf.getAllQualifiedJavaNames());
        }
        return allNames;
    }

    @Override
    public int compareTo(ConfigurationTypeDescriptor other) {
        if (other instanceof LambdaConfigurationTypeDescriptor otherLambda) {
            int result = declaringClass.compareTo(otherLambda.declaringClass());
            if (result != 0) {
                return result;
            }
            // Compare declaringMethod with nullsFirst
            if (this.declaringMethod() == null && otherLambda.declaringMethod() != null) {
                return -1;
            } else if (this.declaringMethod() != null && otherLambda.declaringMethod() == null) {
                return 1;
            } else if (this.declaringMethod() != null) {
                result = this.declaringMethod().compareTo(otherLambda.declaringMethod());
                if (result != 0) {
                    return result;
                }
            }

            return Arrays.compare(
                            interfaces.toArray(EMPTY_TYPE_DESCRIPTOR_ARRAY),
                            otherLambda.interfaces().toArray(EMPTY_TYPE_DESCRIPTOR_ARRAY));
        } else {
            return getDescriptorType().compareTo(other.getDescriptorType());
        }
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.appendObjectStart().quote(LAMBDA_KEY).appendFieldSeparator().appendObjectStart();

        writer.quote(DECLARING_CLASS_KEY).appendFieldSeparator();
        declaringClass.printJson(writer);
        writer.appendSeparator();

        if (declaringMethod != null) {
            writer.quote(DECLARING_METHOD_KEY).appendFieldSeparator();
            declaringMethod.printJson(writer);
            writer.appendSeparator();
        }

        writer.quote(INTERFACES_KEY).appendFieldSeparator();
        JsonPrinter.printCollection(writer, interfaces, ConfigurationTypeDescriptor::compareTo, ConfigurationTypeDescriptor::printJson);

        writer.appendObjectEnd().appendObjectEnd();
    }
}
