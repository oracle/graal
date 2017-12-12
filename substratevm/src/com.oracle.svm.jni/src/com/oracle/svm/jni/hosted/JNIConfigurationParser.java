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
package com.oracle.svm.jni.hosted;

// Checkstyle: allow reflection

import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Map.Entry;
import java.util.stream.Stream;

import com.oracle.shadowed.com.google.gson.JsonArray;
import com.oracle.shadowed.com.google.gson.JsonElement;
import com.oracle.shadowed.com.google.gson.JsonObject;
import com.oracle.shadowed.com.google.gson.JsonParseException;
import com.oracle.shadowed.com.google.gson.JsonParser;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.jni.access.JNIAccessibleMethodDescriptor;
import com.oracle.svm.jni.hosted.JNIFeature.Options;

/**
 * Parses a JNI configuration with classes, methods and fields and registers them wth
 * {@link JNIRuntimeAccess} so they are accessible via JNI at runtime.
 */
final class JNIConfigurationParser {

    public static void parseAndRegister(String file, ImageClassLoader classLoader) {
        try (Reader reader = new FileReader(file)) {
            JsonParser parser = new JsonParser();
            JsonElement root = parser.parse(reader);
            handleRoot(root, classLoader);
        } catch (Exception e) {
            throw UserError.abort("Could not parse JNI configuration file \"" + file + "\". Verify that the file exists and its contents match the expected schema (see " +
                            HostedOptionParser.HOSTED_OPTION_PREFIX + SubstrateOptionsParser.PRINT_FLAGS_OPTION_NAME + " for option " + Options.JNIConfigurationFiles.getName() + ").\n" +
                            e.toString());
        }
    }

    private static void expectOnlyMembers(JsonObject object, String... memberNames) {
        for (Entry<String, JsonElement> elements : object.entrySet()) {
            String elementName = elements.getKey();
            if (Stream.of(memberNames).noneMatch(elementName::equals)) {
                throw new JsonParseException("Unexpected element: \"" + elementName + "\" (valid elements: \"" + String.join("\", \"", memberNames) + "\")");
            }
        }
    }

    private static void handleRoot(JsonElement root, ImageClassLoader loader) {
        for (JsonElement classElement : root.getAsJsonArray()) {
            handleClass(classElement, loader);
        }
    }

    private static void handleClass(JsonElement classElement, ImageClassLoader loader) {
        JsonObject classObject = classElement.getAsJsonObject();
        expectOnlyMembers(classObject, "name", "methods", "fields");

        String name = classObject.get("name").getAsString();
        Class<?> clazz = loader.findClassByName(name, false);
        if (clazz == null) {
            throw new JsonParseException("Could not find class \"" + name + "\"");
        }
        JNIRuntimeAccess.register(clazz);

        handleFields(classObject.get("fields"), clazz);
        handleMethods(classObject.get("methods"), clazz, loader);
    }

    private static void handleFields(JsonElement fieldsElement, Class<?> clazz) {
        if (fieldsElement != null) { // optional
            for (JsonElement fieldElement : fieldsElement.getAsJsonArray()) {
                handleField(fieldElement, clazz);
            }
        }
    }

    private static void handleField(JsonElement fieldElement, Class<?> clazz) {
        JsonObject fieldObject = fieldElement.getAsJsonObject();
        expectOnlyMembers(fieldObject, "name");
        String fieldName = fieldObject.get("name").getAsString();
        try {
            Field field = clazz.getDeclaredField(fieldName);
            JNIRuntimeAccess.register(field);
        } catch (NoSuchFieldException e) {
            throw new JsonParseException("Could not find field " + "\"" + fieldName + "\" of class \"" + clazz.getName() + "\"");
        }
    }

    private static void handleMethods(JsonElement methodsElement, Class<?> clazz, ImageClassLoader loader) {
        if (methodsElement != null) { // optional
            for (JsonElement methodElement : methodsElement.getAsJsonArray()) {
                handleMethod(methodElement, clazz, loader);
            }
        }
    }

    private static void handleMethod(JsonElement methodElement, Class<?> clazz, ImageClassLoader loader) {
        JsonObject methodObject = methodElement.getAsJsonObject();
        expectOnlyMembers(methodObject, "name", "parameterTypes");
        String name = methodObject.get("name").getAsString();
        JsonElement parameterTypesElement = methodObject.get("parameterTypes");
        if (parameterTypesElement != null) {
            JsonArray array = parameterTypesElement.getAsJsonArray();
            Class<?>[] parameterTypes = new Class<?>[array.size()];
            for (int i = 0; i < array.size(); i++) {
                String typeName = array.get(i).getAsString();
                parameterTypes[i] = loader.findClassByName(typeName, false);
                if (parameterTypes[i] == null) {
                    throw new JsonParseException("Could not find type \"" + typeName + "\"");
                }
            }
            try {
                Executable method;
                if (JNIAccessibleMethodDescriptor.isConstructorName(name)) {
                    method = clazz.getDeclaredConstructor(parameterTypes);
                } else {
                    method = clazz.getDeclaredMethod(name, parameterTypes);
                }
                JNIRuntimeAccess.register(method);
            } catch (NoSuchMethodException e) {
                String parameterTypeNames = Stream.of(parameterTypes).map(Class::getSimpleName).reduce((a, b) -> a + ", " + b).orElse("");
                throw new JsonParseException("Could not find method \"" + name + "(" + parameterTypeNames + ")\" of class \"" + clazz.getName() + "\")");
            }
        } else {
            boolean found = false;
            boolean isConstructor = JNIAccessibleMethodDescriptor.isConstructorName(name);
            Executable[] methods = isConstructor ? clazz.getDeclaredConstructors() : clazz.getDeclaredMethods();
            for (Executable method : methods) {
                if (isConstructor || method.getName().equals(name)) {
                    JNIRuntimeAccess.register(method);
                    found = true;
                }
            }
            if (!found) {
                throw new JsonParseException("Could not find method \"" + name + "\" of class \"" + clazz.getName() + "\")");
            }
        }
    }
}
