/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.configure;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.oracle.svm.core.reflect.proxy.DynamicProxySupport;

import jdk.graal.compiler.util.json.JsonPrinter;
import jdk.graal.compiler.util.json.JsonWriter;

public record ProxyConfigurationTypeDescriptor(List<String> interfaceNames) implements ConfigurationTypeDescriptor {

    public ProxyConfigurationTypeDescriptor(List<String> interfaceNames) {
        this.interfaceNames = interfaceNames.stream().map(ConfigurationTypeDescriptor::checkQualifiedJavaName).toList();
    }

    @Override
    public Kind getDescriptorType() {
        return Kind.PROXY;
    }

    @Override
    public String toString() {
        return DynamicProxySupport.proxyTypeDescriptor(interfaceNames.toArray(String[]::new));
    }

    @Override
    public Collection<String> getAllQualifiedJavaNames() {
        return interfaceNames;
    }

    @Override
    public int compareTo(ConfigurationTypeDescriptor other) {
        if (other instanceof ProxyConfigurationTypeDescriptor proxyOther) {
            return Arrays.compare(interfaceNames.toArray(String[]::new), proxyOther.interfaceNames.toArray(String[]::new));
        } else {
            return getDescriptorType().compareTo(other.getDescriptorType());
        }
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.appendObjectStart();
        writer.quote("proxy").appendFieldSeparator();
        JsonPrinter.printCollection(writer, interfaceNames, null, (String p, JsonWriter w) -> w.quote(p));
        writer.appendObjectEnd();
    }
}
