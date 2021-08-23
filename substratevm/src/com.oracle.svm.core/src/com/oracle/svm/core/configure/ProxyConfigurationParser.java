/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.core.util.json.JSONParserException;

// Checkstyle: allow reflection

/**
 * Parses JSON describing lists of interfaces and register them in the {@link DynamicProxyRegistry}.
 */
public final class ProxyConfigurationParser extends ConfigurationParser {
    private final Consumer<String[]> interfaceListConsumer;

    public ProxyConfigurationParser(Consumer<String[]> interfaceListConsumer, boolean strictConfiguration) {
        super(strictConfiguration);
        this.interfaceListConsumer = interfaceListConsumer;
    }

    @Override
    public void parseAndRegister(Reader reader) throws IOException {
        JSONParser parser = new JSONParser(reader);
        Object json = parser.parse();
        parseTopLevelArray(asList(json, "first level of document must be an array of interface lists"));
    }

    private void parseTopLevelArray(List<Object> proxyConfiguration) {
        boolean foundInterfaceLists = false;
        boolean foundProxyConfigurationObjects = false;
        for (Object proxyConfigurationObject : proxyConfiguration) {
            if (proxyConfigurationObject instanceof List) {
                foundInterfaceLists = true;
                parseInterfaceList(asList(proxyConfigurationObject, "<shouldn't reach here>"));
            } else if (proxyConfigurationObject instanceof Map) {
                foundProxyConfigurationObjects = true;
                parseWithPredicatedConfig(asMap(proxyConfigurationObject, "<shouldn't reach here>"));
            } else {
                throw new JSONParserException("second level must be composed of either interface lists or proxy configuration objects");
            }
            if (foundInterfaceLists && foundProxyConfigurationObjects) {
                throw new JSONParserException("second level can only be populated of either interface lists or proxy configuration objects, but these cannot be mixed");
            }
        }
    }

    private void parseInterfaceList(List<?> data) {
        String[] interfaces = new String[data.size()];
        int i = 0;
        for (Object value : data) {
            interfaces[i] = asString(value);
            i++;
        }
        try {
            interfaceListConsumer.accept(interfaces);
        } catch (Exception e) {
            throw new JSONParserException(e.toString());
        }
    }

    private void parseWithPredicatedConfig(Map<String, Object> proxyConfigObject) {
        checkAttributes(proxyConfigObject, "proxy descriptor object", Collections.singleton("interfaces"));
        parseInterfaceList(asList(proxyConfigObject.get("interfaces"), "\"interfaces\" must be an array of fully qualified interface names"));
    }

}
