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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.configure.ConditionalElement;

public class ProxyConfiguration implements ConfigurationBase {
    private final Set<ConditionalElement<List<String>>> interfaceLists = ConcurrentHashMap.newKeySet();

    public ProxyConfiguration() {
    }

    public ProxyConfiguration(ProxyConfiguration other) {
        for (ConditionalElement<List<String>> interfaceList : other.interfaceLists) {
            interfaceLists.add(new ConditionalElement<>(interfaceList.getCondition(), new ArrayList<>(interfaceList.getElement())));
        }
    }

    public void add(ConfigurationCondition condition, List<String> interfaceList) {
        interfaceLists.add(new ConditionalElement<>(condition, interfaceList));
    }

    public boolean contains(ConfigurationCondition condition, List<String> interfaceList) {
        return interfaceLists.contains(new ConditionalElement<>(condition, interfaceList));
    }

    public boolean contains(ConfigurationCondition condition, String... interfaces) {
        return contains(condition, Arrays.asList(interfaces));
    }

    public void removeAll(ProxyConfiguration other) {
        interfaceLists.removeAll(other.interfaceLists);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        List<ConditionalElement<List<String>>> lists = new ArrayList<>(interfaceLists.size());
        lists.addAll(interfaceLists);
        lists.sort(ConditionalElement.comparator(ProxyConfiguration::compareList));

        writer.append('[');
        writer.indent();
        String prefix = "";
        for (ConditionalElement<List<String>> list : lists) {
            writer.append(prefix).newline();
            writer.append('{').indent().newline();
            ConfigurationConditionPrintable.printConditionAttribute(list.getCondition(), writer);
            writer.quote("interfaces").append(":").append('[');
            String typePrefix = "";
            for (String type : list.getElement()) {
                writer.append(typePrefix).quote(type);
                typePrefix = ",";
            }
            writer.append(']');
            writer.append('}').unindent().newline();
            prefix = ",";
        }
        writer.unindent().newline();
        writer.append(']');
    }

    @Override
    public boolean isEmpty() {
        return interfaceLists.isEmpty();
    }

    private static <T extends Comparable<T>> int compareList(List<T> l1, List<T> l2) {
        for (int i = 0; i < l1.size() && i < l2.size(); i++) {
            int c = l1.get(i).compareTo(l2.get(i));
            if (c != 0) {
                return c;
            }
        }
        return l1.size() - l2.size();
    }

}
