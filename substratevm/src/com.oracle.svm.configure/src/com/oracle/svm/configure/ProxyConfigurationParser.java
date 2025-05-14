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
package com.oracle.svm.configure;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.util.json.JsonParserException;

/**
 * Parses JSON describing lists of interfaces and passes them to the given consumer (e.g., to add
 * them to a registry).
 */
public final class ProxyConfigurationParser<C> extends ConditionalConfigurationParser {

    private final ConfigurationConditionResolver<C> conditionResolver;

    private final BiConsumer<C, List<String>> proxyConfigConsumer;

    public ProxyConfigurationParser(ConfigurationConditionResolver<C> conditionResolver, EnumSet<ConfigurationParserOption> parserOptions, BiConsumer<C, List<String>> proxyConfigConsumer) {
        super(parserOptions);
        this.proxyConfigConsumer = proxyConfigConsumer;
        this.conditionResolver = conditionResolver;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        parseTopLevelArray(asList(json, "First-level of document must be an array of interface lists"));
    }

    private void parseTopLevelArray(List<Object> proxyConfiguration) {
        boolean foundInterfaceLists = false;
        boolean foundProxyConfigurationObjects = false;
        for (Object proxyConfigurationObject : proxyConfiguration) {
            if (proxyConfigurationObject instanceof List) {
                foundInterfaceLists = true;
                parseInterfaceList(conditionResolver.alwaysTrue(), asList(proxyConfigurationObject, "<shouldn't reach here>"));
            } else if (proxyConfigurationObject instanceof EconomicMap) {
                foundProxyConfigurationObjects = true;
                parseWithConditionalConfig(asMap(proxyConfigurationObject, "<shouldn't reach here>"));
            } else {
                throw new JsonParserException("Second-level must be composed of either interface lists or proxy configuration objects");
            }
            if (foundInterfaceLists && foundProxyConfigurationObjects) {
                throw new JsonParserException("Second-level can only be populated of either interface lists or proxy configuration objects, but these cannot be mixed");
            }
        }
    }

    private void parseInterfaceList(C condition, List<?> data) {
        List<String> interfaces = data.stream().map(ConfigurationParser::asString).collect(Collectors.toList());

        try {
            proxyConfigConsumer.accept(condition, interfaces);
        } catch (Exception e) {
            throw new JsonParserException(e.toString());
        }
    }

    private void parseWithConditionalConfig(EconomicMap<String, Object> proxyConfigObject) {
        checkAttributes(proxyConfigObject, "proxy descriptor object", Collections.singleton("interfaces"), Collections.singletonList(CONDITIONAL_KEY));
        UnresolvedConfigurationCondition condition = parseCondition(proxyConfigObject, false);
        TypeResult<C> resolvedCondition = conditionResolver.resolveCondition(condition);
        if (resolvedCondition.isPresent()) {
            parseInterfaceList(resolvedCondition.get(), asList(proxyConfigObject.get("interfaces"), "The interfaces property must be an array of fully qualified interface names"));
        }
    }
}
