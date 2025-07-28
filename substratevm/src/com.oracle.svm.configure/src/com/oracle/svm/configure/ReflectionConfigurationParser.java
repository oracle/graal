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
package com.oracle.svm.configure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.util.json.JsonParserException;

/**
 * Parses JSON describing classes, methods and fields and delegates their registration to a
 * {@link ReflectionConfigurationParserDelegate}.
 */
public abstract class ReflectionConfigurationParser<C, T> extends ConditionalConfigurationParser {
    private static final String CONSTRUCTOR_NAME = "<init>";

    protected final ConfigurationConditionResolver<C> conditionResolver;
    protected final ReflectionConfigurationParserDelegate<C, T> delegate;

    public ReflectionConfigurationParser(ConfigurationConditionResolver<C> conditionResolver, ReflectionConfigurationParserDelegate<C, T> delegate, EnumSet<ConfigurationParserOption> parserOptions) {
        super(parserOptions);
        this.conditionResolver = conditionResolver;
        this.delegate = delegate;
    }

    @Override
    protected EnumSet<ConfigurationParserOption> supportedOptions() {
        EnumSet<ConfigurationParserOption> base = super.supportedOptions();
        base.add(ConfigurationParserOption.PRINT_MISSING_ELEMENTS);
        base.add(ConfigurationParserOption.JNI_PARSER);
        return base;
    }

    public static <C, T> ReflectionConfigurationParser<C, T> create(boolean combinedFileSchema,
                    ConfigurationConditionResolver<C> conditionResolver, ReflectionConfigurationParserDelegate<C, T> delegate,
                    EnumSet<ConfigurationParserOption> parserOptions) {
        if (combinedFileSchema) {
            return new ReflectionMetadataParser<>(conditionResolver, delegate, parserOptions);
        } else {
            return new LegacyReflectionConfigurationParser<>(conditionResolver, delegate, parserOptions);
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

    protected void parseFields(C condition, List<Object> fields, T clazz, boolean jniAccessible) {
        for (Object field : fields) {
            parseField(condition, asMap(field, "Elements of 'fields' array must be field descriptor objects"), clazz, jniAccessible);
        }
    }

    private void parseField(C condition, EconomicMap<String, Object> data, T clazz, boolean jniAccessible) {
        checkAttributes(data, "reflection field descriptor object", Collections.singleton("name"), Arrays.asList("allowWrite", "allowUnsafeAccess"));
        String fieldName = asString(data.get("name"), "name");
        boolean allowWrite = data.containsKey("allowWrite") && asBoolean(data.get("allowWrite"), "allowWrite");

        try {
            delegate.registerField(condition, clazz, fieldName, allowWrite, jniAccessible);
        } catch (NoSuchFieldException e) {
            handleMissingElement("Field " + formatField(clazz, fieldName) + " not found.");
        } catch (LinkageError e) {
            handleMissingElement("Could not register field " + formatField(clazz, fieldName) + " for reflection.", e);
        }
    }

    protected void parseMethods(C condition, boolean queriedOnly, List<Object> methods, T clazz, boolean jniAccessible) {
        for (Object method : methods) {
            parseMethod(condition, queriedOnly, asMap(method, "Elements of 'methods' array must be method descriptor objects"), clazz, jniAccessible);
        }
    }

    private void parseMethod(C condition, boolean queriedOnly, EconomicMap<String, Object> data, T clazz, boolean jniAccessible) {
        String methodName = asString(data.get(NAME_KEY), NAME_KEY);
        List<T> methodParameterTypes = null;
        Object parameterTypes = data.get(PARAMETER_TYPES_KEY);
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
                    delegate.registerConstructor(condition, queriedOnly, clazz, methodParameterTypes, jniAccessible);
                } else {
                    delegate.registerMethod(condition, queriedOnly, clazz, methodName, methodParameterTypes, jniAccessible);
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
                    found = delegate.registerAllConstructors(condition, queriedOnly, jniAccessible, clazz);
                } else {
                    found = delegate.registerAllMethodsWithName(condition, queriedOnly, jniAccessible, clazz, methodName);
                }
                if (!found) {
                    throw new JsonParserException("Method " + formatMethod(clazz, methodName) + " not found");
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
            TypeResult<T> typeResult = delegate.resolveType(conditionResolver.alwaysTrue(), NamedConfigurationTypeDescriptor.fromJSONName(typeName), true, false);
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
        if (checkOption(ConfigurationParserOption.PRINT_MISSING_ELEMENTS)) {
            String message = msg;
            if (cause != null) {
                message += " Reason: " + formatError(cause) + '.';
            }
            LogUtils.warning(message);
        }
    }
}
