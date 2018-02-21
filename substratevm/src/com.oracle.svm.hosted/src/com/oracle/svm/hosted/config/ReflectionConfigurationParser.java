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

import static com.oracle.svm.core.SubstrateOptions.PrintFlags;

import java.io.File;

// Checkstyle: allow reflection

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Executable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.shadowed.com.google.gson.JsonParseException;
import com.oracle.shadowed.com.google.gson.stream.JsonReader;
import com.oracle.shadowed.com.google.gson.stream.JsonToken;
import com.oracle.svm.core.RuntimeReflection.ReflectionRegistry;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.option.HostedOptionParser;

import jdk.vm.ci.meta.MetaUtil;

/**
 * Parses JSON describing classes, methods and fields and registers them with a
 * {@link ReflectionRegistry}.
 */
public final class ReflectionConfigurationParser {
    private static final String CONSTRUCTOR_NAME = "<init>";

    private final ReflectionRegistry registry;
    private final ImageClassLoader classLoader;

    public ReflectionConfigurationParser(ReflectionRegistry registry, ImageClassLoader classLoader) {
        this.registry = registry;
        this.classLoader = classLoader;
    }

    /**
     * Parses configurations in files specified by {@code configFilesOption} and resources specified
     * by {@code configResourcesOption} and registers the parsed classes, methods and fields with
     * the {@link ReflectionRegistry} associated with this object.
     *
     * @param featureName name of the feature using the configuration (e.g., "JNI")
     */
    public void parseAndRegisterConfigurations(String featureName, HostedOptionKey<String> configFilesOption, HostedOptionKey<String> configResourcesOption) {
        String configFiles = configFilesOption.getValue();
        if (!configFiles.isEmpty()) {
            for (String path : configFiles.split(",")) {
                File file = new File(path).getAbsoluteFile();
                if (!file.exists()) {
                    throw UserError.abort("The " + featureName + " configuration file \"" + file + "\" does not exist.");
                }
                try (Reader reader = new FileReader(file)) {
                    parseAndRegister(reader, featureName, file, configFilesOption);
                } catch (IOException e) {
                    throw UserError.abort("Could not open " + file + ": " + e.getMessage());
                }
            }
        }
        String configResources = configResourcesOption.getValue();
        if (!configResources.isEmpty()) {
            for (String resource : configResources.split(",")) {
                URL url = classLoader.findResourceByName(resource);
                if (url == null) {
                    throw UserError.abort("Could not find " + featureName + " configuration resource \"" + resource + "\".");
                }
                try (Reader reader = new InputStreamReader(url.openStream())) {
                    parseAndRegister(reader, featureName, url, configResourcesOption);
                } catch (IOException e) {
                    throw UserError.abort("Could not open " + url + ": " + e.getMessage());
                }
            }
        }
    }

    private void parseAndRegister(Reader reader, String featureName, Object location, HostedOptionKey<String> option) {
        /*
         * Calling toString() on an anonymous subclass of JsonReader will return
         * " at line <n> column <c>" which is useful for error messages.
         */
        JsonReader json = new JsonReader(reader) {
        };
        assert json.toString().equals(" at line 1 column 1");
        try {
            parseClassArray(json);
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = e.toString();
            }
            throw UserError.abort("Error parsing " + featureName + " configuration in " + location + json + ":\n" + errorMessage +
                            "\nVerify that the configuration matches the schema described in the " +
                            HostedOptionParser.commandArgument(PrintFlags, "+") + " output for option " + option.getName() + ".");
        }
    }

    private void parseClassArray(JsonReader reader) throws IOException {
        reader.beginArray();
        while (reader.peek() == JsonToken.BEGIN_OBJECT) {
            parseClass(reader);
        }
        reader.endArray();
    }

    private static boolean parseBoolean(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.STRING) {
            String s = reader.nextString();
            if (s.equals("true")) {
                return true;
            }
            if (!s.equals("false")) {
                throw new JsonParseException("Invalid boolean value \"" + s + "\"");
            }
            return false;
        }
        return reader.nextBoolean();
    }

    private void parseClass(JsonReader reader) throws IOException {
        reader.beginObject();
        Class<?> clazz = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("name")) {
                if (clazz != null) {
                    throw new JsonParseException("Class 'name' attribute cannot be repeated");
                }
                String className = reader.nextString();
                clazz = classLoader.findClassByName(className, false);
                if (clazz == null) {
                    throw new JsonParseException("Class " + className + " not found");
                }
                registry.register(clazz);
            } else {
                if (clazz == null) {
                    throw new JsonParseException("Class 'name' attribute must precede '" + name + "' attribute");
                }
                if (name.equals("allDeclaredMethods")) {
                    if (parseBoolean(reader)) {
                        registry.register(clazz.getDeclaredMethods());
                    }
                } else if (name.equals("allPublicMethods")) {
                    if (parseBoolean(reader)) {
                        registry.register(clazz.getMethods());
                    }
                } else if (name.equals("allDeclaredFields")) {
                    if (parseBoolean(reader)) {
                        registry.register(clazz.getDeclaredFields());
                    }
                } else if (name.equals("allPublicFields")) {
                    if (parseBoolean(reader)) {
                        registry.register(clazz.getFields());
                    }
                } else if (name.equals("methods")) {
                    parseMethods(reader, clazz);
                } else if (name.equals("fields")) {
                    parseFields(reader, clazz);
                } else {
                    throw new JsonParseException("Unknown class attribute '" + name +
                                    "' (supported attributes: allDeclaredMethods, allPublicMethods, allDeclaredFields, allPublicFields, methods, fields)");
                }
            }
        }
        reader.endObject();
    }

    private void parseFields(JsonReader reader, Class<?> clazz) throws IOException {
        reader.beginArray();
        while (reader.peek() == JsonToken.BEGIN_OBJECT) {
            parseField(reader, clazz);
        }
        reader.endArray();
    }

    private void parseField(JsonReader reader, Class<?> clazz) throws IOException {
        reader.beginObject();
        String fieldName = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("name")) {
                fieldName = reader.nextString();
            } else {
                throw new JsonParseException("Unknown field attribute '" + name + "' (supported attributes: name)");
            }
        }
        reader.endObject();
        if (fieldName == null) {
            throw new JsonParseException("Missing field 'name' attribute");
        }
        try {
            registry.register(clazz.getDeclaredField(fieldName));
        } catch (NoSuchFieldException e) {
            throw new JsonParseException("Field " + clazz.getName() + "." + fieldName + " not found");
        }
    }

    private void parseMethods(JsonReader reader, Class<?> clazz) throws IOException {
        reader.beginArray();
        while (reader.peek() == JsonToken.BEGIN_OBJECT) {
            parseMethod(reader, clazz);
        }
        reader.endArray();
    }

    private void parseMethod(JsonReader reader, Class<?> clazz) throws IOException {
        reader.beginObject();
        String methodName = null;
        Class<?>[] methodParameterTypes = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("name")) {
                if (methodName != null) {
                    throw new JsonParseException("Method 'name' attribute cannot be repeated");
                }
                methodName = reader.nextString();
            } else {
                if (methodName == null) {
                    throw new JsonParseException("Method 'name' attribute must be precede '" + name + "' attribute");
                }
                if (name.equals("parameterTypes")) {
                    if (methodParameterTypes != null) {
                        throw new JsonParseException("Method 'parameterTypes' attribute cannot be repeated");
                    }
                    methodParameterTypes = parseTypes(reader);
                } else {
                    throw new JsonParseException("Unknown method attribute '" + name + "' (supported attributes: name, parameterTypes)");
                }
            }
        }
        reader.endObject();
        if (methodName == null) {
            throw new JsonParseException("Missing method 'name' attribute");
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
                throw new JsonParseException("Method " + clazz.getName() + "." + methodName + "(" + parameterTypeNames + ") not found");
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
                throw new JsonParseException("Method " + clazz.getName() + "." + methodName + " not found");
            }
        }
    }

    private Class<?>[] parseTypes(JsonReader reader) throws IOException {
        reader.beginArray();
        List<Class<?>> types = new ArrayList<>();
        while (reader.peek() == JsonToken.STRING) {
            String originalTypeName = reader.nextString();
            String typeName = originalTypeName;
            if (typeName.indexOf('[') != -1) { // accept "int[][]", "java.lang.String[]"
                typeName = MetaUtil.internalNameToJava(MetaUtil.toInternalName(originalTypeName), true, true);
            }
            Class<?> clazz = classLoader.findClassByName(typeName, false);
            if (clazz == null) {
                throw new JsonParseException("Class " + typeName + " not found");
            }
            types.add(clazz);
        }
        reader.endArray();
        return types.toArray(new Class<?>[types.size()]);
    }
}
