/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.util.json.JSONParserException;

import com.oracle.svm.core.TypeResult;
import com.oracle.svm.util.LogUtils;

/**
 * Parses JSON describing classes, methods and fields and delegates their registration to a
 * {@link ReflectionConfigurationParserDelegate}.
 */
public abstract class ReflectionConfigurationParser<T> extends ConfigurationParser {
    private static final String CONSTRUCTOR_NAME = "<init>";

    protected final ConfigurationConditionResolver conditionResolver;
    protected final ReflectionConfigurationParserDelegate<T> delegate;
    private final boolean printMissingElements;

    public ReflectionConfigurationParser(ConfigurationConditionResolver conditionResolver, ReflectionConfigurationParserDelegate<T> delegate, boolean strictConfiguration,
                    boolean printMissingElements) {
        super(strictConfiguration);
        this.conditionResolver = conditionResolver;
        this.printMissingElements = printMissingElements;
        this.delegate = delegate;
    }

    public static <T> ReflectionConfigurationParser<T> create(String combinedFileKey, boolean strictMetadata, ReflectionConfigurationParserDelegate<T> delegate, boolean strictConfiguration,
                    boolean printMissingElements) {
        if (strictMetadata) {
            return new ReflectionMetadataParser<>(combinedFileKey, ConfigurationConditionResolver.identityResolver(), delegate, strictConfiguration, printMissingElements);
        } else {
            return new LegacyReflectionConfigurationParser<>(ConfigurationConditionResolver.identityResolver(), delegate, strictConfiguration, printMissingElements, false);
        }
    }

    protected void parseClassArray(List<Object> classes) {
        for (Object clazz : classes) {
            parseClass(asMap(clazz, "second level of document must be class descriptor objects"));
        }
    }

    protected abstract void parseClass(EconomicMap<String, Object> data);

    protected void registerIfNotDefault(EconomicMap<String, Object> data, boolean defaultValue, T clazz, String propertyName, Runnable register) {
        if (data.containsKey(propertyName) ? asBoolean(data.get(propertyName), propertyName) : defaultValue) {
            try {
                register.run();
            } catch (LinkageError e) {
                handleMissingElement("Could not register " + delegate.getTypeName(clazz) + ": " + propertyName + " for reflection.", e);
            }
        }
    }

    protected void parseFields(ConfigurationCondition condition, List<Object> fields, T clazz) {
        for (Object field : fields) {
            parseField(condition, asMap(field, "Elements of 'fields' array must be field descriptor objects"), clazz);
        }
    }

    private void parseField(ConfigurationCondition condition, EconomicMap<String, Object> data, T clazz) {
        checkAttributes(data, "reflection field descriptor object", Collections.singleton("name"), Arrays.asList("allowWrite", "allowUnsafeAccess"));
        String fieldName = asString(data.get("name"), "name");
        boolean allowWrite = data.containsKey("allowWrite") && asBoolean(data.get("allowWrite"), "allowWrite");

        try {
            delegate.registerField(condition, clazz, fieldName, allowWrite);
        } catch (NoSuchFieldException e) {
            handleMissingElement("Field " + formatField(clazz, fieldName) + " not found.");
        } catch (LinkageError e) {
            handleMissingElement("Could not register field " + formatField(clazz, fieldName) + " for reflection.", e);
        }
    }

    protected void parseMethods(ConfigurationCondition condition, boolean queriedOnly, List<Object> methods, T clazz) {
        for (Object method : methods) {
            parseMethod(condition, queriedOnly, asMap(method, "Elements of 'methods' array must be method descriptor objects"), clazz);
        }
    }

    private void parseMethod(ConfigurationCondition condition, boolean queriedOnly, EconomicMap<String, Object> data, T clazz) {
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
                    delegate.registerConstructor(condition, queriedOnly, clazz, methodParameterTypes);
                } else {
                    delegate.registerMethod(condition, queriedOnly, clazz, methodName, methodParameterTypes);
                }
            } catch (NoSuchMethodException e) {
                handleMissingElement("Method " + formatMethod(clazz, methodName, methodParameterTypes) + " not found.");
            } catch (LinkageError e) {
                handleMissingElement("Could not register method " + formatMethod(clazz, methodName, methodParameterTypes) + " for reflection.", e);
            }
        } else {
            try {
                boolean found;
                if (isConstructor) {
                    found = delegate.registerAllConstructors(condition, queriedOnly, clazz);
                } else {
                    found = delegate.registerAllMethodsWithName(condition, queriedOnly, clazz, methodName);
                }
                if (!found) {
                    throw new JSONParserException("Method " + formatMethod(clazz, methodName) + " not found");
                }
            } catch (LinkageError e) {
                handleMissingElement("Could not register method " + formatMethod(clazz, methodName) + " for reflection.", e);
            }
        }
    }

    private List<T> parseMethodParameters(T clazz, String methodName, List<Object> types) {
        List<T> result = new ArrayList<>();
        for (Object type : types) {
            String typeName = asString(type, "types");
            TypeResult<T> typeResult = delegate.resolveType(conditionResolver.alwaysTrue(), new NamedConfigurationTypeDescriptor(typeName), true);
            if (!typeResult.isPresent()) {
                handleMissingElement("Could not register method " + formatMethod(clazz, methodName) + " for reflection.", typeResult.getException());
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

    private void handleMissingElement(String message) {
        handleMissingElement(message, null);
    }

    protected void handleMissingElement(String msg, Throwable cause) {
        if (printMissingElements) {
            String message = msg;
            if (cause != null) {
                message += " Reason: " + formatError(cause) + '.';
            }
            LogUtils.warning(message);
        }
    }
}
