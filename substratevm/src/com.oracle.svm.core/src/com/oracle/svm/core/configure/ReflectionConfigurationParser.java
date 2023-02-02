/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.util.json.JSONParserException;

import com.oracle.svm.core.TypeResult;

/**
 * Parses JSON describing classes, methods and fields and delegates their registration to a
 * {@link ReflectionConfigurationParserDelegate}.
 */
public final class ReflectionConfigurationParser<T> extends ConfigurationParser {
    private static final String CONSTRUCTOR_NAME = "<init>";

    private final ReflectionConfigurationParserDelegate<T> delegate;
    private static final List<String> OPTIONAL_REFLECT_CONFIG_OBJECT_ATTRS = Arrays.asList("allDeclaredConstructors", "allPublicConstructors",
                    "allDeclaredMethods", "allPublicMethods", "allDeclaredFields", "allPublicFields",
                    "allDeclaredClasses", "allPermittedSubclasses", "allPublicClasses", "methods", "queriedMethods", "fields", CONDITIONAL_KEY,
                    "queryAllDeclaredConstructors", "queryAllPublicConstructors", "queryAllDeclaredMethods", "queryAllPublicMethods", "unsafeAllocated");

    public ReflectionConfigurationParser(ReflectionConfigurationParserDelegate<T> delegate) {
        this(delegate, true);
    }

    public ReflectionConfigurationParser(ReflectionConfigurationParserDelegate<T> delegate, boolean strictConfiguration) {
        super(strictConfiguration);
        this.delegate = delegate;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        parseClassArray(asList(json, "first level of document must be an array of class descriptors"));
    }

    private void parseClassArray(List<Object> classes) {
        for (Object clazz : classes) {
            parseClass(asMap(clazz, "second level of document must be class descriptor objects"));
        }
    }

    private void parseClass(EconomicMap<String, Object> data) {
        checkAttributes(data, "reflection class descriptor object", Collections.singleton("name"), OPTIONAL_REFLECT_CONFIG_OBJECT_ATTRS);

        Object classObject = data.get("name");
        String className = asString(classObject, "name");

        TypeResult<ConfigurationCondition> conditionResult = delegate.resolveCondition(parseCondition(data).getTypeName());
        if (!conditionResult.isPresent()) {
            return;
        }
        ConfigurationCondition condition = conditionResult.get();

        TypeResult<T> result = delegate.resolveType(condition, className, false);
        if (!result.isPresent()) {
            handleError("Could not resolve class " + className + " for reflection configuration.", result.getException());
            return;
        }
        T clazz = result.get();
        delegate.registerType(clazz);

        MapCursor<String, Object> cursor = data.getEntries();
        while (cursor.advance()) {
            String name = cursor.getKey();
            Object value = cursor.getValue();
            try {
                switch (name) {
                    case "allDeclaredConstructors":
                        if (asBoolean(value, "allDeclaredConstructors")) {
                            delegate.registerDeclaredConstructors(false, clazz);
                        }
                        break;
                    case "allPublicConstructors":
                        if (asBoolean(value, "allPublicConstructors")) {
                            delegate.registerPublicConstructors(false, clazz);
                        }
                        break;
                    case "allDeclaredMethods":
                        if (asBoolean(value, "allDeclaredMethods")) {
                            delegate.registerDeclaredMethods(false, clazz);
                        }
                        break;
                    case "allPublicMethods":
                        if (asBoolean(value, "allPublicMethods")) {
                            delegate.registerPublicMethods(false, clazz);
                        }
                        break;
                    case "allDeclaredFields":
                        if (asBoolean(value, "allDeclaredFields")) {
                            delegate.registerDeclaredFields(clazz);
                        }
                        break;
                    case "allPublicFields":
                        if (asBoolean(value, "allPublicFields")) {
                            delegate.registerPublicFields(clazz);
                        }
                        break;
                    case "allDeclaredClasses":
                        if (asBoolean(value, "allDeclaredClasses")) {
                            delegate.registerDeclaredClasses(clazz);
                        }
                        break;
                    case "allPermittedSubclasses":
                        if (asBoolean(value, "allPermittedSubclasses")) {
                            delegate.registerPermittedSubclasses(clazz);
                        }
                        break;
                    case "allPublicClasses":
                        if (asBoolean(value, "allPublicClasses")) {
                            delegate.registerPublicClasses(clazz);
                        }
                        break;
                    case "queryAllDeclaredConstructors":
                        if (asBoolean(value, "queryAllDeclaredConstructors")) {
                            delegate.registerDeclaredConstructors(true, clazz);
                        }
                        break;
                    case "queryAllPublicConstructors":
                        if (asBoolean(value, "queryAllPublicConstructors")) {
                            delegate.registerPublicConstructors(true, clazz);
                        }
                        break;
                    case "queryAllDeclaredMethods":
                        if (asBoolean(value, "queryAllDeclaredMethods")) {
                            delegate.registerDeclaredMethods(true, clazz);
                        }
                        break;
                    case "queryAllPublicMethods":
                        if (asBoolean(value, "queryAllPublicMethods")) {
                            delegate.registerPublicMethods(true, clazz);
                        }
                        break;
                    case "unsafeAllocated":
                        if (asBoolean(value, "unsafeAllocated")) {
                            delegate.registerUnsafeAllocated(clazz);
                        }
                        break;
                    case "methods":
                        parseMethods(false, asList(value, "Attribute 'methods' must be an array of method descriptors"), clazz);
                        break;
                    case "queriedMethods":
                        parseMethods(true, asList(value, "Attribute 'queriedMethods' must be an array of method descriptors"), clazz);
                        break;
                    case "fields":
                        parseFields(asList(value, "Attribute 'fields' must be an array of field descriptors"), clazz);
                        break;
                }
            } catch (LinkageError e) {
                handleError("Could not register " + delegate.getTypeName(clazz) + ": " + name + " for reflection.", e);
            }
        }
    }

    private void parseFields(List<Object> fields, T clazz) {
        for (Object field : fields) {
            parseField(asMap(field, "Elements of 'fields' array must be field descriptor objects"), clazz);
        }
    }

    private void parseField(EconomicMap<String, Object> data, T clazz) {
        checkAttributes(data, "reflection field descriptor object", Collections.singleton("name"), Arrays.asList("allowWrite", "allowUnsafeAccess"));
        String fieldName = asString(data.get("name"), "name");
        boolean allowWrite = data.containsKey("allowWrite") && asBoolean(data.get("allowWrite"), "allowWrite");

        try {
            delegate.registerField(clazz, fieldName, allowWrite);
        } catch (NoSuchFieldException e) {
            handleError("Field " + formatField(clazz, fieldName) + " not found.");
        } catch (LinkageError e) {
            handleError("Could not register field " + formatField(clazz, fieldName) + " for reflection.", e);
        }
    }

    private void parseMethods(boolean queriedOnly, List<Object> methods, T clazz) {
        for (Object method : methods) {
            parseMethod(queriedOnly, asMap(method, "Elements of 'methods' array must be method descriptor objects"), clazz);
        }
    }

    private void parseMethod(boolean queriedOnly, EconomicMap<String, Object> data, T clazz) {
        checkAttributes(data, "reflection method descriptor object", Collections.singleton("name"), Collections.singleton("parameterTypes"));
        String methodName = asString(data.get("name"), "name");
        List<T> methodParameterTypes = null;
        Object parameterTypes = data.get("parameterTypes");
        if (parameterTypes != null) {
            methodParameterTypes = parseMethodParameters(clazz, methodName, asList(parameterTypes, "Attribute 'parameterTypes' must be a list of type names"));
            if (methodParameterTypes == null) {
                return;
            }
        }

        boolean isConstructor = CONSTRUCTOR_NAME.equals(methodName);
        if (methodParameterTypes != null) {
            try {
                if (isConstructor) {
                    delegate.registerConstructor(queriedOnly, clazz, methodParameterTypes);
                } else {
                    delegate.registerMethod(queriedOnly, clazz, methodName, methodParameterTypes);
                }
            } catch (NoSuchMethodException e) {
                handleError("Method " + formatMethod(clazz, methodName, methodParameterTypes) + " not found.");
            } catch (LinkageError e) {
                handleError("Could not register method " + formatMethod(clazz, methodName, methodParameterTypes) + " for reflection.", e);
            }
        } else {
            try {
                boolean found;
                if (isConstructor) {
                    found = delegate.registerAllConstructors(queriedOnly, clazz);
                } else {
                    found = delegate.registerAllMethodsWithName(queriedOnly, clazz, methodName);
                }
                if (!found) {
                    throw new JSONParserException("Method " + formatMethod(clazz, methodName) + " not found");
                }
            } catch (LinkageError e) {
                handleError("Could not register method " + formatMethod(clazz, methodName) + " for reflection.", e);
            }
        }
    }

    private List<T> parseMethodParameters(T clazz, String methodName, List<Object> types) {
        List<T> result = new ArrayList<>();
        for (Object type : types) {
            String typeName = asString(type, "types");
            TypeResult<T> typeResult = delegate.resolveType(ConfigurationCondition.alwaysTrue(), typeName, true);
            if (!typeResult.isPresent()) {
                handleError("Could not register method " + formatMethod(clazz, methodName) + " for reflection.", typeResult.getException());
                return null;
            }
            result.add(typeResult.get());
        }
        return result;
    }

    private static String formatError(Throwable e) {
        return e.getClass().getTypeName() + ": " + e.getMessage();
    }

    private String formatField(T clazz, String fieldName) {
        return delegate.getTypeName(clazz) + '.' + fieldName;
    }

    private String formatMethod(T clazz, String methodName) {
        return formatMethod(clazz, methodName, Collections.emptyList());
    }

    private String formatMethod(T clazz, String methodName, List<T> paramTypes) {
        String parameterTypeNames = paramTypes.stream().map(delegate::getSimpleName).collect(Collectors.joining(", "));
        return delegate.getTypeName(clazz) + '.' + methodName + '(' + parameterTypeNames + ')';
    }

    private static void handleError(String message) {
        handleError(message, null);
    }

    private static void handleError(String msg, Throwable cause) {
        String message = msg;
        if (cause != null) {
            message += " Reason: " + formatError(cause) + '.';
        }
        System.err.println("Warning: " + message);
    }
}
