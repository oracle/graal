/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

// Checkstyle: allow reflection

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.shadowed.com.google.gson.JsonArray;
import com.oracle.shadowed.com.google.gson.JsonElement;
import com.oracle.shadowed.com.google.gson.JsonObject;
import com.oracle.shadowed.com.google.gson.JsonParseException;
import com.oracle.shadowed.com.google.gson.JsonParser;
import com.oracle.shadowed.com.google.gson.JsonPrimitive;
import com.oracle.svm.core.RuntimeReflection.ReflectionRegistry;
import com.oracle.svm.hosted.ImageClassLoader;

import jdk.vm.ci.meta.MetaUtil;

/**
 * Parses a reflection configuration with classes, methods and fields and registers them wth
 * {@link ReflectionRegistry} so they are accessible via JNI at runtime.
 */
public final class ReflectionConfigurationParser {
    private static final String CONSTRUCTOR_NAME = "<init>";

    private static class ParseContext {
        String className;
        String memberName;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (className != null) {
                sb.append("class ").append(className).append(": ");
                if (memberName != null) {
                    sb.append("member ").append(memberName).append(": ");
                }
            }
            return sb.toString();
        }
    }

    private final ReflectionRegistry registry;
    private final ImageClassLoader classLoader;

    public ReflectionConfigurationParser(ReflectionRegistry registry, ImageClassLoader classLoader) {
        this.registry = registry;
        this.classLoader = classLoader;
    }

    public void parseAndRegister(String file) throws IOException {
        try (Reader reader = new FileReader(file)) {
            parseAndRegister(reader);
        }
    }

    public void parseAndRegister(Reader reader) {
        ParseContext context = new ParseContext();
        try {
            JsonParser parser = new JsonParser();
            JsonElement root = parser.parse(reader);
            handleRoot(context, root);
        } catch (Exception e) {
            throw new JsonParseException(context.toString() + e.getMessage(), e);
        }
    }

    private static JsonParseException bailout(String message) {
        throw new JsonParseException(message);
    }

    private static void expectMembers(JsonObject object, String required, String... optionals) {
        if (!object.has(required)) {
            throw bailout("missing required element: \"" + required + '"');
        }
        for (Entry<String, JsonElement> elements : object.entrySet()) {
            String elementName = elements.getKey();
            if (!required.equals(elementName) && Stream.of(optionals).noneMatch(elementName::equals)) {
                throw bailout("unexpected element: \"" + elementName + "\" (valid elements: \"" + String.join("\", \"", optionals) + "\")");
            }
        }
    }

    private void handleRoot(ParseContext context, JsonElement root) {
        for (JsonElement classElement : root.getAsJsonArray()) {
            handleClass(context, classElement);
        }
    }

    private void handleClass(ParseContext context, JsonElement classElement) {
        JsonObject classObject = classElement.getAsJsonObject();
        String name = classObject.get("name").getAsString();
        context.className = name;
        expectMembers(classObject, "name", "methods", "fields", "allDeclaredMethods", "allPublicMethods", "allDeclaredFields", "allPublicFields");
        Class<?> clazz = classLoader.findClassByName(name, false);
        if (clazz == null) {
            throw new JsonParseException("class not found");
        }
        registry.register(clazz);
        if (getAsBoolean(classObject, "allDeclaredMethods", false)) {
            registry.register(clazz.getDeclaredMethods());
        }
        if (getAsBoolean(classObject, "allPublicMethods", false)) {
            registry.register(clazz.getMethods());
        }
        if (getAsBoolean(classObject, "allDeclaredFields", false)) {
            registry.register(clazz.getDeclaredFields());
        }
        if (getAsBoolean(classObject, "allPublicFields", false)) {
            registry.register(clazz.getFields());
        }
        handleFields(context, classObject.get("fields"), clazz);
        handleMethods(context, classObject.get("methods"), clazz);
        context.className = null;
    }

    private static boolean getAsBoolean(JsonObject object, String name, boolean forNull) {
        JsonPrimitive primitive = object.getAsJsonPrimitive(name);
        return (primitive == null) ? forNull : primitive.getAsBoolean();
    }

    private void handleFields(ParseContext context, JsonElement fieldsElement, Class<?> clazz) {
        if (fieldsElement != null) { // optional
            for (JsonElement fieldElement : fieldsElement.getAsJsonArray()) {
                handleField(context, fieldElement, clazz);
            }
        }
    }

    private void handleField(ParseContext context, JsonElement fieldElement, Class<?> clazz) {
        JsonObject fieldObject = fieldElement.getAsJsonObject();
        expectMembers(fieldObject, "name");
        String fieldName = fieldObject.get("name").getAsString();
        context.memberName = fieldName;
        try {
            Field field = clazz.getDeclaredField(fieldName);
            registry.register(field);
        } catch (NoSuchFieldException e) {
            throw bailout("field not found");
        }
        context.memberName = null;
    }

    private void handleMethods(ParseContext context, JsonElement methodsElement, Class<?> clazz) {
        if (methodsElement != null) { // optional
            for (JsonElement methodElement : methodsElement.getAsJsonArray()) {
                handleMethod(context, methodElement, clazz);
            }
        }
    }

    private void handleMethod(ParseContext context, JsonElement methodElement, Class<?> clazz) {
        JsonObject methodObject = methodElement.getAsJsonObject();
        String name = methodObject.get("name").getAsString();
        context.memberName = name;
        expectMembers(methodObject, "name", "parameterTypes");
        JsonElement parameterTypesElement = methodObject.get("parameterTypes");
        if (parameterTypesElement != null) {
            JsonArray array = parameterTypesElement.getAsJsonArray();
            Class<?>[] parameterTypes = new Class<?>[array.size()];
            for (int i = 0; i < array.size(); i++) {
                String originalTypeName = array.get(i).getAsString();
                String typeName = originalTypeName;
                if (typeName.indexOf('[') != -1) { // accept "int[][]", "java.lang.String[]"
                    typeName = MetaUtil.internalNameToJava(MetaUtil.toInternalName(originalTypeName), true, true);
                }
                parameterTypes[i] = classLoader.findClassByName(typeName, false);
                if (parameterTypes[i] == null) {
                    throw bailout("parameter type " + originalTypeName + " not found");
                }
            }
            try {
                Executable method;
                if (CONSTRUCTOR_NAME.equals(name)) {
                    method = clazz.getDeclaredConstructor(parameterTypes);
                } else {
                    method = clazz.getDeclaredMethod(name, parameterTypes);
                }
                registry.register(method);
            } catch (NoSuchMethodException e) {
                String parameterTypeNames = Stream.of(parameterTypes).map(Class::getSimpleName).collect(Collectors.joining(", "));
                throw bailout("method with parameter types (" + parameterTypeNames + ") not found");
            }
        } else {
            boolean found = false;
            boolean isConstructor = CONSTRUCTOR_NAME.equals(name);
            Executable[] methods = isConstructor ? clazz.getDeclaredConstructors() : clazz.getDeclaredMethods();
            for (Executable method : methods) {
                if (isConstructor || method.getName().equals(name)) {
                    registry.register(method);
                    found = true;
                }
            }
            if (!found) {
                throw bailout("method not found");
            }
        }
        context.memberName = null;
    }
}
