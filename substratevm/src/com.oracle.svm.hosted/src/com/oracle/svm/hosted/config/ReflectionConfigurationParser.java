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
package com.oracle.svm.hosted.config;

import static com.oracle.svm.core.SubstrateOptions.PrintFlags;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.impl.ReflectionRegistry;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.json.JSONParser;
import com.oracle.svm.hosted.json.JSONParserException;

import jdk.vm.ci.meta.MetaUtil;

// Checkstyle: allow reflection

/**
 * Parses JSON describing classes, methods and fields and registers them with a
 * {@link ReflectionRegistry}.
 */
public final class ReflectionConfigurationParser extends ConfigurationParser {
    private static final String CONSTRUCTOR_NAME = "<init>";

    private final ReflectionRegistry registry;

    public ReflectionConfigurationParser(ReflectionRegistry registry, ImageClassLoader classLoader) {
        super(classLoader);
        this.registry = registry;
    }

    @Override
    protected void parseAndRegister(Reader reader, String featureName, Object location, HostedOptionKey<String> option) {
        try {
            JSONParser parser = new JSONParser(reader);
            Object json = parser.parse();
            parseClassArray(asList(json, "first level of document must be an array of class descriptors"));
        } catch (IOException | JSONParserException e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = e.toString();
            }
            throw UserError.abort("Error parsing " + featureName + " configuration in " + location + ":\n" + errorMessage +
                            "\nVerify that the configuration matches the schema described in the " +
                            SubstrateOptionsParser.commandArgument(PrintFlags, "+") + " output for option " + option.getName() + ".");
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
            throw new JSONParserException("Missing atrribute 'name' in class descriptor object");
        }
        String className = asString(classObject, "name");

        Class<?> clazz = classLoader.findClassByName(className, false);
        if (clazz == null) {
            throw new JSONParserException("Class " + className + " not found");
        }

        registry.register(clazz);

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (name.equals("name")) {
                /* Already handled. */
            } else if (name.equals("allDeclaredConstructors")) {
                if (asBoolean(value, "allDeclaredConstructors")) {
                    registry.register(clazz.getDeclaredConstructors());
                }
            } else if (name.equals("allPublicConstructors")) {
                if (asBoolean(value, "allPublicConstructors")) {
                    registry.register(clazz.getConstructors());
                }
            } else if (name.equals("allDeclaredMethods")) {
                if (asBoolean(value, "allDeclaredMethods")) {
                    registry.register(clazz.getDeclaredMethods());
                }
            } else if (name.equals("allPublicMethods")) {
                if (asBoolean(value, "allPublicMethods")) {
                    registry.register(clazz.getMethods());
                }
            } else if (name.equals("allDeclaredFields")) {
                if (asBoolean(value, "allDeclaredFields")) {
                    registry.register(clazz.getDeclaredFields());
                }
            } else if (name.equals("allPublicFields")) {
                if (asBoolean(value, "allPublicFields")) {
                    registry.register(clazz.getFields());
                }
            } else if (name.equals("methods")) {
                parseMethods(asList(value, "Attribute 'methods' must be an array of method descriptors"), clazz);
            } else if (name.equals("fields")) {
                parseFields(asList(value, "Attribute 'fields' must be an array of field descriptors"), clazz);
            } else {
                throw new JSONParserException("Unknown attribute '" + name +
                                "' (supported attributes: allDeclaredConstructors, allPublicConstructors, allDeclaredMethods, allPublicMethods, allDeclaredFields, allPublicFields, methods, fields) in defintion of class " +
                                clazz.getTypeName());
            }
        }
    }

    private void parseFields(List<Object> fields, Class<?> clazz) {
        for (Object field : fields) {
            parseField(asMap(field, "Elements of 'fields' array must be field descriptor objects"), clazz);
        }
    }

    private void parseField(Map<String, Object> data, Class<?> clazz) {
        String fieldName = null;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String propertyName = entry.getKey();
            if (propertyName.equals("name")) {
                fieldName = asString(entry.getValue(), "name");
            } else {
                throw new JSONParserException("Unknown attribute '" + propertyName + "' (supported attributes: 'name') in definition of field for class '" + clazz.getTypeName() + "'");
            }
        }

        if (fieldName == null) {
            throw new JSONParserException("Missing atribute 'name' in definition of field for class " + clazz.getTypeName());
        }

        try {
            registry.register(clazz.getDeclaredField(fieldName));
        } catch (NoSuchFieldException e) {
            throw new JSONParserException("Field " + clazz.getTypeName() + "." + fieldName + " not found");
        }
    }

    private void parseMethods(List<Object> methods, Class<?> clazz) {
        for (Object method : methods) {
            parseMethod(asMap(method, "Elements of 'methods' array must be method descriptor objects"), clazz);
        }
    }

    private void parseMethod(Map<String, Object> data, Class<?> clazz) {
        String methodName = null;
        Class<?>[] methodParameterTypes = null;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String propertyName = entry.getKey();
            if (propertyName.equals("name")) {
                methodName = asString(entry.getValue(), "name");
            } else if (propertyName.equals("parameterTypes")) {
                methodParameterTypes = parseTypes(asList(entry.getValue(), "Attribute 'parameterTypes' must be a list of type names"));
            } else {
                throw new JSONParserException(
                                "Unknown attribute '" + propertyName + "' (supported attributes: 'name', 'parameterTypes') in definition of method for class '" + clazz.getTypeName() + "'");
            }
        }

        if (methodName == null) {
            throw new JSONParserException("Missing attribute 'name' in definition of method for class '" + clazz.getTypeName() + "'");
        }

        if (methodParameterTypes != null) {
            try {
                Executable method;
                if (CONSTRUCTOR_NAME.equals(methodName)) {
                    method = clazz.getDeclaredConstructor(methodParameterTypes);
                } else {
                    method = clazz.getDeclaredMethod(methodName, methodParameterTypes);
                }
                registry.register(method);
            } catch (NoSuchMethodException e) {
                String parameterTypeNames = Stream.of(methodParameterTypes).map(Class::getSimpleName).collect(Collectors.joining(", "));
                throw new JSONParserException("Method " + clazz.getTypeName() + "." + methodName + "(" + parameterTypeNames + ") not found");
            }
        } else {
            boolean found = false;
            boolean isConstructor = CONSTRUCTOR_NAME.equals(methodName);
            Executable[] methods = isConstructor ? clazz.getDeclaredConstructors() : clazz.getDeclaredMethods();
            for (Executable method : methods) {
                if (isConstructor || method.getName().equals(methodName)) {
                    registry.register(method);
                    found = true;
                }
            }
            if (!found) {
                throw new JSONParserException("Method " + clazz.getTypeName() + "." + methodName + " not found");
            }
        }
    }

    private Class<?>[] parseTypes(List<Object> types) {
        List<Class<?>> result = new ArrayList<>();
        for (Object type : types) {
            String typeName = asString(type, "types");
            if (typeName.indexOf('[') != -1) {
                /* accept "int[][]", "java.lang.String[]" */
                typeName = MetaUtil.internalNameToJava(MetaUtil.toInternalName(typeName), true, true);
            }
            Class<?> clazz = classLoader.findClassByName(typeName, false);
            if (clazz == null) {
                throw new JSONParserException("Class " + typeName + " not found");
            }
            result.add(clazz);
        }
        return result.toArray(new Class<?>[result.size()]);
    }

}
