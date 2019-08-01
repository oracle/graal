/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.core.util.json.JSONParserException;

// Checkstyle: allow reflection

/**
 * Parses JSON describing classes, methods and fields and delegates their registration to a
 * {@link ReflectionConfigurationParserDelegate}.
 */
public final class ReflectionConfigurationParser<T> extends ConfigurationParser {
    private static final String CONSTRUCTOR_NAME = "<init>";

    private final ReflectionConfigurationParserDelegate<T> delegate;
    private final boolean allowIncompleteClasspath;

    public ReflectionConfigurationParser(ReflectionConfigurationParserDelegate<T> delegate) {
        this(delegate, false);
    }

    public ReflectionConfigurationParser(ReflectionConfigurationParserDelegate<T> delegate, boolean allowIncompleteClasspath) {
        this.delegate = delegate;
        this.allowIncompleteClasspath = allowIncompleteClasspath;
    }

    @Override
    public void parseAndRegister(Reader reader) throws IOException {
        try {
            JSONParser parser = new JSONParser(reader);
            Object json = parser.parse();
            parseClassArray(asList(json, "first level of document must be an array of class descriptors"));
        } catch (NoClassDefFoundError e) {
            throw e;
        }
    }

    private void parseClassArray(List<Object> classes) {
        for (Object clazz : classes) {
            parseClass(asMap(clazz, "second level of document must be class descriptor objects"));
        }
    }

    private void parseClass(Map<String, Object> data) {
        Object classObject = data.get("name");
        if (classObject == null) {
            throw new JSONParserException("Missing attribute 'name' in class descriptor object");
        }
        String className = asString(classObject, "name");

        T clazz = delegate.resolveType(className);
        if (clazz == null) {
            handleError("Could not resolve " + className + " for reflection configuration.");
            return;
        }
        delegate.registerType(clazz);

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            try {
                if (name.equals("name")) {
                    /* Already handled. */
                } else if (name.equals("allDeclaredConstructors")) {
                    if (asBoolean(value, "allDeclaredConstructors")) {
                        delegate.registerDeclaredConstructors(clazz);
                    }
                } else if (name.equals("allPublicConstructors")) {
                    if (asBoolean(value, "allPublicConstructors")) {
                        delegate.registerPublicConstructors(clazz);
                    }
                } else if (name.equals("allDeclaredMethods")) {
                    if (asBoolean(value, "allDeclaredMethods")) {
                        delegate.registerDeclaredMethods(clazz);
                    }
                } else if (name.equals("allPublicMethods")) {
                    if (asBoolean(value, "allPublicMethods")) {
                        delegate.registerPublicMethods(clazz);
                    }
                } else if (name.equals("allDeclaredFields")) {
                    if (asBoolean(value, "allDeclaredFields")) {
                        delegate.registerDeclaredFields(clazz);
                    }
                } else if (name.equals("allPublicFields")) {
                    if (asBoolean(value, "allPublicFields")) {
                        delegate.registerPublicFields(clazz);
                    }
                } else if (name.equals("allDeclaredClasses")) {
                    if (asBoolean(value, "allDeclaredClasses")) {
                        delegate.registerDeclaredClasses(clazz);
                    }
                } else if (name.equals("allPublicClasses")) {
                    if (asBoolean(value, "allPublicClasses")) {
                        delegate.registerPublicClasses(clazz);
                    }
                } else if (name.equals("methods")) {
                    parseMethods(asList(value, "Attribute 'methods' must be an array of method descriptors"), clazz);
                } else if (name.equals("fields")) {
                    parseFields(asList(value, "Attribute 'fields' must be an array of field descriptors"), clazz);
                } else {
                    throw new JSONParserException("Unknown attribute '" + name +
                                    "' (supported attributes: allDeclaredConstructors, allPublicConstructors, allDeclaredMethods, allPublicMethods, allDeclaredFields, allPublicFields, methods, fields) in defintion of class " +
                                    delegate.getTypeName(clazz));
                }
            } catch (NoClassDefFoundError e) {
                handleError("Could not register " + delegate.getTypeName(clazz) + ": " + name + " for reflection. Reason: " + formatError(e) + ".");
            }
        }
    }

    private void parseFields(List<Object> fields, T clazz) {
        for (Object field : fields) {
            parseField(asMap(field, "Elements of 'fields' array must be field descriptor objects"), clazz);
        }
    }

    private void parseField(Map<String, Object> data, T clazz) {
        String fieldName = null;
        boolean allowWrite = false;
        boolean allowUnsafeAccess = false;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String propertyName = entry.getKey();
            if (propertyName.equals("name")) {
                fieldName = asString(entry.getValue(), "name");
            } else if (propertyName.equals("allowWrite")) {
                allowWrite = asBoolean(entry.getValue(), "allowWrite");
            } else if (propertyName.equals("allowUnsafeAccess")) {
                allowUnsafeAccess = asBoolean(entry.getValue(), "allowUnsafeAccess");
            } else {
                throw new JSONParserException("Unknown attribute '" + propertyName + "' (supported attributes: 'name') in definition of field for class '" + delegate.getTypeName(clazz) + "'");
            }
        }

        if (fieldName == null) {
            throw new JSONParserException("Missing attribute 'name' in definition of field for class " + delegate.getTypeName(clazz));
        }

        try {
            delegate.registerField(clazz, fieldName, allowWrite, allowUnsafeAccess);
        } catch (NoSuchFieldException e) {
            handleError("Field " + delegate.getTypeName(clazz) + "." + fieldName + " not found.");
        } catch (NoClassDefFoundError e) {
            handleError("Could not register field " + delegate.getTypeName(clazz) + "." + fieldName + " for reflection. Reason: " + formatError(e) + ".");
        }
    }

    private void parseMethods(List<Object> methods, T clazz) {
        for (Object method : methods) {
            parseMethod(asMap(method, "Elements of 'methods' array must be method descriptor objects"), clazz);
        }
    }

    private void parseMethod(Map<String, Object> data, T clazz) {
        String methodName = null;
        List<T> methodParameterTypes = null;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String propertyName = entry.getKey();
            if (propertyName.equals("name")) {
                methodName = asString(entry.getValue(), "name");
            } else if (propertyName.equals("parameterTypes")) {
                methodParameterTypes = parseTypes(asList(entry.getValue(), "Attribute 'parameterTypes' must be a list of type names"));
            } else {
                throw new JSONParserException(
                                "Unknown attribute '" + propertyName + "' (supported attributes: 'name', 'parameterTypes') in definition of method for class '" + delegate.getTypeName(clazz) + "'");
            }
        }

        if (methodName == null) {
            throw new JSONParserException("Missing attribute 'name' in definition of method for class '" + delegate.getTypeName(clazz) + "'");
        }

        boolean isConstructor = CONSTRUCTOR_NAME.equals(methodName);
        if (methodParameterTypes != null) {
            try {
                if (isConstructor) {
                    delegate.registerConstructor(clazz, methodParameterTypes);
                } else {
                    delegate.registerMethod(clazz, methodName, methodParameterTypes);
                }
            } catch (NoSuchMethodException e) {
                handleError("Method " + formatMethod(clazz, methodName, methodParameterTypes) + " not found.");
            } catch (NoClassDefFoundError e) {
                handleError("Could not register method " + formatMethod(clazz, methodName, methodParameterTypes) + " for reflection. Reason: " + formatError(e) + ".");
            }
        } else {
            try {
                boolean found;
                if (isConstructor) {
                    found = delegate.registerAllConstructors(clazz);
                } else {
                    found = delegate.registerAllMethodsWithName(clazz, methodName);
                }
                if (!found) {
                    throw new JSONParserException("Method " + delegate.getTypeName(clazz) + "." + methodName + " not found");
                }
            } catch (NoClassDefFoundError e) {
                handleError("Could not register method " + delegate.getTypeName(clazz) + "." + methodName + " for reflection. Reason: " + formatError(e) + ".");
            }
        }
    }

    private List<T> parseTypes(List<Object> types) {
        List<T> result = new ArrayList<>();
        for (Object type : types) {
            String typeName = asString(type, "types");
            T clazz = delegate.resolveType(typeName);
            if (clazz == null) {
                throw new JSONParserException("Class " + typeName + " not found");
            }
            result.add(clazz);
        }
        return result;
    }

    private static String formatError(Error e) {
        return e.getClass().getTypeName() + ": " + e.getMessage();
    }

    private String formatMethod(T clazz, String methodName, List<T> paramTypes) {
        String parameterTypeNames = paramTypes.stream().map(delegate::getSimpleName).collect(Collectors.joining(", "));
        return delegate.getTypeName(clazz) + "." + methodName + "(" + parameterTypeNames + ")";
    }

    private void handleError(String message) {
        // Checkstyle: stop
        if (this.allowIncompleteClasspath) {
            System.out.println("WARNING: " + message);
        } else {
            throw new JSONParserException(message + " To allow unresolvable reflection configuration, use option -H:+AllowIncompleteClasspath");
        }
        // Checkstyle: resume
    }
}
