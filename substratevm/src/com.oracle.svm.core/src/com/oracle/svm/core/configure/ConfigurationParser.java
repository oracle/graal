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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.util.json.JSONParser;
import org.graalvm.util.json.JSONParserException;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jdk.JavaNetSubstitutions;
import com.oracle.svm.core.util.VMError;

public abstract class ConfigurationParser {
    public static InputStream openStream(URI uri) throws IOException {
        URL url = uri.toURL();
        if ("file".equals(url.getProtocol()) || "jar".equalsIgnoreCase(url.getProtocol()) ||
                        (!SubstrateUtil.HOSTED && JavaNetSubstitutions.RESOURCE_PROTOCOL.equals(url.getProtocol()))) {
            return url.openStream();
        }
        throw VMError.shouldNotReachHere("For security reasons, reading configurations is not supported from URIs with protocol: " + url.getProtocol());
    }

    public static final String CONDITIONAL_KEY = "condition";
    public static final String TYPE_REACHABLE_KEY = "typeReachable";
    private final Map<String, Set<String>> seenUnknownAttributesByType = new HashMap<>();
    private final boolean strictConfiguration;

    protected ConfigurationParser(boolean strictConfiguration) {
        this.strictConfiguration = strictConfiguration;
    }

    public void parseAndRegister(URI uri) throws IOException {
        try (Reader reader = openReader(uri)) {
            parseAndRegister(new JSONParser(reader).parse(), uri);
        }
    }

    protected static BufferedReader openReader(URI uri) throws IOException {
        return new BufferedReader(new InputStreamReader(openStream(uri)));
    }

    public void parseAndRegister(Reader reader) throws IOException {
        parseAndRegister(new JSONParser(reader).parse(), null);
    }

    public abstract void parseAndRegister(Object json, URI origin) throws IOException;

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object data, String errorMessage) {
        if (data instanceof List) {
            return (List<Object>) data;
        }
        throw new JSONParserException(errorMessage);
    }

    @SuppressWarnings("unchecked")
    public static EconomicMap<String, Object> asMap(Object data, String errorMessage) {
        if (data instanceof EconomicMap) {
            return (EconomicMap<String, Object>) data;
        }
        throw new JSONParserException(errorMessage);
    }

    protected void checkAttributes(EconomicMap<String, Object> map, String type, Collection<String> requiredAttrs, Collection<String> optionalAttrs) {
        Set<String> unseenRequired = new HashSet<>(requiredAttrs);
        for (String key : map.getKeys()) {
            unseenRequired.remove(key);
        }
        if (!unseenRequired.isEmpty()) {
            throw new JSONParserException("Missing attribute(s) [" + String.join(", ", unseenRequired) + "] in " + type);
        }
        Set<String> unknownAttributes = new HashSet<>();
        for (String key : map.getKeys()) {
            unknownAttributes.add(key);
        }
        unknownAttributes.removeAll(requiredAttrs);
        unknownAttributes.removeAll(optionalAttrs);

        if (seenUnknownAttributesByType.containsKey(type)) {
            unknownAttributes.removeAll(seenUnknownAttributesByType.get(type));
        }

        if (unknownAttributes.size() > 0) {
            String message = "Unknown attribute(s) [" + String.join(", ", unknownAttributes) + "] in " + type;
            warnOrFail(message);
            Set<String> unknownAttributesForType = seenUnknownAttributesByType.computeIfAbsent(type, key -> new HashSet<>());
            unknownAttributesForType.addAll(unknownAttributes);
        }
    }

    protected void warnOrFail(String message) {
        if (strictConfiguration) {
            throw new JSONParserException(message);
        } else {
            System.err.println("Warning: " + message);
        }
    }

    protected void checkAttributes(EconomicMap<String, Object> map, String type, Collection<String> requiredAttrs) {
        checkAttributes(map, type, requiredAttrs, Collections.emptyList());
    }

    public static String asString(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        throw new JSONParserException("Invalid string value \"" + value + "\".");
    }

    protected static String asString(Object value, String propertyName) {
        if (value instanceof String) {
            return (String) value;
        }
        throw new JSONParserException("Invalid string value \"" + value + "\" for element '" + propertyName + "'");
    }

    protected static String asNullableString(Object value, String propertyName) {
        return (value == null) ? null : asString(value, propertyName);
    }

    protected static boolean asBoolean(Object value, String propertyName) {
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        throw new JSONParserException("Invalid boolean value '" + value + "' for element '" + propertyName + "'");
    }

    protected static long asLong(Object value, String propertyName) {
        if (value instanceof Long) {
            return (long) value;
        }
        if (value instanceof Integer) {
            return (int) value;
        }
        throw new JSONParserException("Invalid long value '" + value + "' for element '" + propertyName + "'");
    }

    protected ConfigurationCondition parseCondition(EconomicMap<String, Object> data) {
        Object conditionData = data.get(CONDITIONAL_KEY);
        if (conditionData != null) {
            EconomicMap<String, Object> conditionObject = asMap(conditionData, "Attribute 'condition' must be an object");
            Object conditionType = conditionObject.get(TYPE_REACHABLE_KEY);
            if (conditionType instanceof String) {
                return ConfigurationCondition.create((String) conditionType);
            } else {
                warnOrFail("'" + TYPE_REACHABLE_KEY + "' should be of type string");
            }
        }
        return ConfigurationCondition.alwaysTrue();
    }

}
