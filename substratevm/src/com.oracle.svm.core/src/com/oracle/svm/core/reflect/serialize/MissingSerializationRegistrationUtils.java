/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.serialize;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.core.MissingRegistrationUtils;

public final class MissingSerializationRegistrationUtils extends MissingRegistrationUtils {

    public static void reportSerialization(Class<?> cl) {
        reportSerialization(cl, typeDescriptor(cl));
    }

    public static void reportSerialization(Class<?> cl, String serializedType) {
        ConfigurationType type = getConfigurationType(cl);
        type.setSerializable();

        MissingSerializationRegistrationError exception = new MissingSerializationRegistrationError(serializationMessage(elementToJSON(type), serializedType), cl);
        StackTraceElement responsibleClass = getResponsibleClass(exception);
        MissingRegistrationUtils.report(exception, responsibleClass);
    }

    private static String serializationMessage(String json, String typeDescriptor) {
        return registrationMessage("serialize or deserialize", typeDescriptor, json, "", "reflection", "reflection");
    }

    /*
     * This is a list of all public JDK methods that end up potentially throwing missing
     * registration errors. This should be implemented using wrapping substitutions once they are
     * available.
     */
    private static final Map<String, Set<String>> serializationEntryPoints = Map.of(
                    ObjectOutputStream.class.getTypeName(), Set.of("writeObject", "writeUnshared"),
                    ObjectInputStream.class.getTypeName(), Set.of("readObject", "readUnshared"),
                    ObjectStreamClass.class.getTypeName(), Set.of("lookup"),
                    "sun.reflect.ReflectionFactory", Set.of("newConstructorForSerialization"),
                    "jdk.internal.reflect.ReflectionFactory", Set.of("newConstructorForSerialization"));

    private static StackTraceElement getResponsibleClass(MissingSerializationRegistrationError t) {
        StackTraceElement[] stackTrace = t.getStackTrace();
        boolean previous = false;
        StackTraceElement lastElem = null;
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (previous) {
                previous = false;
                lastElem = stackTraceElement;
            }
            if (serializationEntryPoints.getOrDefault(stackTraceElement.getClassName(), Set.of())
                            .contains(stackTraceElement.getMethodName())) {
                previous = true;
            }
        }
        return lastElem;
    }
}
