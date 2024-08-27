/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileNotFoundException;
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
import java.util.Optional;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.util.json.JSONParser;
import org.graalvm.util.json.JSONParserException;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jdk.JavaNetSubstitutions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.LogUtils;

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
    public static final String TYPE_REACHED_KEY = "typeReached";
    public static final String NAME_KEY = "name";
    public static final String TYPE_KEY = "type";
    public static final String PROXY_KEY = "proxy";
    public static final String REFLECTION_KEY = "reflection";
    public static final String JNI_KEY = "jni";
    public static final String SERIALIZATION_KEY = "serialization";
    public static final String RESOURCES_KEY = "resources";
    public static final String BUNDLES_KEY = "bundles";
    public static final String GLOBS_KEY = "globs";
    public static final String MODULE_KEY = "module";
    public static final String GLOB_KEY = "glob";
    private final Map<String, Set<String>> seenUnknownAttributesByType = new HashMap<>();
    private final boolean strictSchema;

    protected ConfigurationParser(boolean strictConfiguration) {
        this.strictSchema = strictConfiguration;
    }

    public void parseAndRegister(URI uri) throws IOException {
        try (Reader reader = openReader(uri)) {
            parseAndRegister(new JSONParser(reader).parse(), uri);
        } catch (FileNotFoundException e) {
            /*
             * Ignore: *-config.json files can be missing when reachability-metadata.json is
             * present, and vice-versa
             */
        }
    }

    protected static BufferedReader openReader(URI uri) throws IOException {
        return new BufferedReader(new InputStreamReader(openStream(uri)));
    }

    public void parseAndRegister(Reader reader) throws IOException {
        parseAndRegister(new JSONParser(reader).parse(), null);
    }

    public abstract void parseAndRegister(Object json, URI origin) throws IOException;

    public Object getFromGlobalFile(Object json, String key) {
        EconomicMap<String, Object> map = asMap(json, "top level of reachability metadata file must be an object");
        checkAttributes(map, "reachability metadata", Collections.emptyList(), List.of(REFLECTION_KEY, JNI_KEY, SERIALIZATION_KEY, RESOURCES_KEY, BUNDLES_KEY, "reason", "comment"));
        return map.get(key);
    }

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
            warnOrFailOnSchemaError(message);
            Set<String> unknownAttributesForType = seenUnknownAttributesByType.computeIfAbsent(type, key -> new HashSet<>());
            unknownAttributesForType.addAll(unknownAttributes);
        }
    }

    public static void checkHasExactlyOneAttribute(EconomicMap<String, Object> map, String type, Collection<String> alternativeAttributes) {
        boolean attributeFound = false;
        for (String key : map.getKeys()) {
            if (alternativeAttributes.contains(key)) {
                if (attributeFound) {
                    String message = "Exactly one of [" + String.join(", ", alternativeAttributes) + "] must be set in " + type;
                    throw new JSONParserException(message);
                }
                attributeFound = true;
            }
        }
        if (!attributeFound) {
            String message = "Exactly one of [" + String.join(", ", alternativeAttributes) + "] must be set in " + type;
            throw new JSONParserException(message);
        }
    }

    /**
     * Used to warn about schema errors in configuration files. Should never be used if the type is
     * missing.
     *
     * @param message message to be displayed.
     */
    protected void warnOrFailOnSchemaError(String message) {
        if (strictSchema) {
            failOnSchemaError(message);
        } else {
            LogUtils.warning(message);
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

    private static boolean alreadyWarned = false;

    protected ConfigurationCondition parseCondition(EconomicMap<String, Object> data, boolean runtimeCondition) {
        Object conditionData = data.get(CONDITIONAL_KEY);
        if (conditionData != null) {
            EconomicMap<String, Object> conditionObject = asMap(conditionData, "Attribute 'condition' must be an object");
            if (conditionObject.containsKey(TYPE_REACHABLE_KEY) && conditionObject.containsKey(TYPE_REACHED_KEY)) {
                failOnSchemaError("condition can not have both '" + TYPE_REACHED_KEY + "' and '" + TYPE_REACHABLE_KEY + "' set.");
            } else if (conditionObject.isEmpty()) {
                failOnSchemaError("condition can not be empty");
            }

            if (conditionObject.containsKey(TYPE_REACHED_KEY)) {
                if (!runtimeCondition) {
                    failOnSchemaError("'" + TYPE_REACHED_KEY + "' condition cannot be used in older schemas. Please migrate the file to the latest schema.");
                }
                Object object = conditionObject.get(TYPE_REACHED_KEY);
                var condition = parseTypeContents(object);
                if (condition.isPresent()) {
                    if (!alreadyWarned) {
                        LogUtils.warning("Found typeReached condition in JSON configuration files. " +
                                        "The typeReached condition is not supported by this version of GraalVM and will be considered as always true. " +
                                        "Please consider upgrading to the latest GraalVM version to be able to use all the latest features.");
                        alreadyWarned = true;
                    }
                    return ConfigurationCondition.alwaysTrue();
                }
            } else if (conditionObject.containsKey(TYPE_REACHABLE_KEY)) {
                if (runtimeCondition) {
                    failOnSchemaError("'" + TYPE_REACHABLE_KEY + "' condition can not be used with the latest schema. Please use '" + TYPE_REACHED_KEY + "'.");
                }
                Object object = conditionObject.get(TYPE_REACHABLE_KEY);
                var condition = parseTypeContents(object);
                if (condition.isPresent()) {
                    String className = ((NamedConfigurationTypeDescriptor) condition.get()).name();
                    return ConfigurationCondition.create(className);
                }
            }
        }
        /*
         * Ensure forward-compatibility with condition types added in the future
         */
        return ConfigurationCondition.alwaysTrue();
    }

    private static JSONParserException failOnSchemaError(String message) {
        throw new JSONParserException(message);
    }

    protected record TypeDescriptorWithOrigin(ConfigurationTypeDescriptor typeDescriptor, boolean definedAsType) {
    }

    protected static Optional<TypeDescriptorWithOrigin> parseName(EconomicMap<String, Object> data, boolean treatAllNameEntriesAsType) {
        Object name = data.get(NAME_KEY);
        if (name != null) {
            NamedConfigurationTypeDescriptor typeDescriptor = new NamedConfigurationTypeDescriptor(asString(name));
            return Optional.of(new TypeDescriptorWithOrigin(typeDescriptor, treatAllNameEntriesAsType));
        } else {
            throw failOnSchemaError("must have type or name specified for an element");
        }
    }

    protected static Optional<ConfigurationTypeDescriptor> parseTypeContents(Object typeObject) {
        if (typeObject instanceof String stringValue) {
            return Optional.of(new NamedConfigurationTypeDescriptor(stringValue));
        } else {
            EconomicMap<String, Object> type = asMap(typeObject, "type descriptor should be a string or object");
            if (type.containsKey(PROXY_KEY)) {
                checkHasExactlyOneAttribute(type, "type descriptor object", Set.of(PROXY_KEY));
                return Optional.of(getProxyDescriptor(type.get(PROXY_KEY)));
            }
            /*
             * We return if we find a future version of a type descriptor (as a JSON object) instead
             * of failing parsing.
             */
            // TODO warn
            return Optional.empty();
        }
    }

    private static ProxyConfigurationTypeDescriptor getProxyDescriptor(Object proxyObject) {
        List<Object> proxyInterfaces = asList(proxyObject, "proxy interface content should be an interface list");
        List<String> proxyInterfaceNames = proxyInterfaces.stream().map(obj -> asString(obj, "proxy")).toList();
        return new ProxyConfigurationTypeDescriptor(proxyInterfaceNames);
    }
}
