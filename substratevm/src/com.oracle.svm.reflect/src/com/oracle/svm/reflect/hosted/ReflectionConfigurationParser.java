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
package com.oracle.svm.reflect.hosted;

// Checkstyle: allow reflection

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.core.common.SuppressFBWarnings;

import com.oracle.shadowed.com.google.gson.Gson;
import com.oracle.shadowed.com.google.gson.GsonBuilder;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;

public class ReflectionConfigurationParser {

    private static final String CONSTRUCTOR_NAME = "<init>";

    static class ErrorContext {
        String file;
        String classContext;
        String memberContext;

        @Override
        public String toString() {
            List<String> context = new ArrayList<>();
            if (file != null) {
                context.add(file);
            }
            if (classContext != null) {
                context.add(classContext);
            }
            if (memberContext != null) {
                context.add(memberContext);
            }
            if (context.isEmpty()) {
                return "";
            }
            return "In " + String.join(", ", context) + ": ";
        }
    }

    private ErrorContext errorContext = new ErrorContext();

    protected void loadFiles(ReflectionDataBuilder reflectionData, ImageClassLoader imageClassLoader) {
        for (String substitutionFileName : ReflectionFeature.Options.ReflectionConfigurationFiles.getValue().split(",")) {
            if (!substitutionFileName.isEmpty()) {
                errorContext.file = substitutionFileName;

                try {
                    loadFile(reflectionData, imageClassLoader, new FileReader(substitutionFileName));
                } catch (FileNotFoundException ex) {
                    throw error("file not found");
                }
            }
        }
        for (String substitutionResourceName : ReflectionFeature.Options.ReflectionConfigurationResources.getValue().split(",")) {
            if (!substitutionResourceName.isEmpty()) {
                errorContext.file = substitutionResourceName;

                InputStream substitutionStream = imageClassLoader.findResourceByName(substitutionResourceName);
                if (substitutionStream != null) {
                    loadFile(reflectionData, imageClassLoader, new InputStreamReader(substitutionStream));
                } else {
                    throw error("file not found");
                }
            }
        }
        errorContext.file = null;
    }

    private void loadFile(ReflectionDataBuilder reflectionData, ImageClassLoader imageClassLoader, Reader reader) {
        Gson gson = new GsonBuilder().create();
        ClassDescriptor[] classDescriptors = gson.fromJson(reader, ClassDescriptor[].class);

        int classIndex = 0;
        for (ClassDescriptor classDescriptor : classDescriptors) {
            if (classDescriptor == null) {
                /* Empty or trailing array elements are parsed to null. */
                continue;
            }
            errorContext.classContext = "class " + (classDescriptor.name == null ? classIndex : classDescriptor.name);

            if (classDescriptor.name == null) {
                throw error("class name missing");

            }
            Class<?> clazz = imageClassLoader.findClassByName(classDescriptor.name);
            reflectionData.register(clazz);

            if (classDescriptor.allDeclaredMethods) {
                reflectionData.register(clazz.getDeclaredMethods());
            }
            if (classDescriptor.allPublicMethods) {
                reflectionData.register(clazz.getMethods());
            }
            if (classDescriptor.allDeclaredFields) {
                reflectionData.register(clazz.getDeclaredFields());
            }
            if (classDescriptor.allPublicFields) {
                reflectionData.register(clazz.getFields());
            }

            int index = 0;
            for (MethodDescriptor methodDescriptor : classDescriptor.methods) {
                if (methodDescriptor == null) {
                    /* Empty or trailing array elements are parsed to null. */
                    continue;
                }
                errorContext.memberContext = "method " + (methodDescriptor.name == null ? index : methodDescriptor.name);
                if (methodDescriptor.parameterTypes != null) {
                    reflectionData.register(findMethod(imageClassLoader, clazz, methodDescriptor.name, methodDescriptor.parameterTypes));
                } else {
                    reflectionData.register(findMethod(clazz, methodDescriptor.name));
                }
                index++;
            }
            index = 0;
            for (FieldDescriptor fieldDescriptor : classDescriptor.fields) {
                errorContext.memberContext = "field " + (fieldDescriptor.name == null ? index : fieldDescriptor.name);
                reflectionData.register(findField(clazz, fieldDescriptor.name));
                index++;
            }
            errorContext.classContext = null;
            errorContext.memberContext = null;
            classIndex++;
        }
    }

    private Executable findMethod(Class<?> declaringClass, String methodName) {
        Executable result = null;
        if (methodName.equals(CONSTRUCTOR_NAME)) {
            for (Constructor<?> c : declaringClass.getDeclaredConstructors()) {
                if (result != null) {
                    throw error("two constructors found: " + result + ", " + c + ". Use property \'parameterTypes\' to disambiguate.");
                }
                result = c;
            }
            if (result == null) {
                throw error("no constructor found: " + declaringClass);
            }
        } else {
            for (Method m : declaringClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    if (result != null) {
                        throw error("two methods with same name found: " + result + ", " + m + ". Use property \'parameterTypes\' to disambiguate.");
                    }
                    result = m;
                }
            }
            if (result == null) {
                throw error("method not found: " + declaringClass + ", method name " + methodName);
            }
        }
        result.setAccessible(true);
        return result;
    }

    private Executable findMethod(ImageClassLoader imageClassLoader, Class<?> declaringClass, String methodName, String[] parameterTypeNames) {
        Class<?>[] parameterTypes = new Class<?>[parameterTypeNames.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = imageClassLoader.findClassByName(parameterTypeNames[i]);
        }
        try {
            Executable result;
            if (methodName == null) {
                throw error("method name missing");
            }
            if (methodName.equals(CONSTRUCTOR_NAME)) {
                result = declaringClass.getDeclaredConstructor(parameterTypes);
            } else {
                result = declaringClass.getDeclaredMethod(methodName, parameterTypes);
            }
            result.setAccessible(true);
            return result;
        } catch (NoSuchMethodException e) {
            throw error("method not found: " + declaringClass + ", method name " + methodName + ", parameter types " + Arrays.toString(parameterTypeNames));
        }
    }

    private Field findField(Class<?> declaringClass, String fieldName) {
        try {
            Field result = declaringClass.getDeclaredField(fieldName);
            result.setAccessible(true);
            return result;
        } catch (NoSuchFieldException e) {
            throw error("field not found: " + declaringClass + ", field name " + fieldName);
        }
    }

    private RuntimeException error(String msg) {
        throw UserError.abort(errorContext + msg);
    }
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class ClassDescriptor {
    String name;

    boolean allDeclaredMethods;
    boolean allPublicMethods;
    boolean allDeclaredFields;
    boolean allPublicFields;

    MethodDescriptor[] methods = new MethodDescriptor[0];
    FieldDescriptor[] fields = new FieldDescriptor[0];
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class MethodDescriptor {
    String name;
    String[] parameterTypes;
}

@SuppressFBWarnings(value = "UwF", justification = "Fields written by GSON using reflection")
class FieldDescriptor {
    String name;
}
