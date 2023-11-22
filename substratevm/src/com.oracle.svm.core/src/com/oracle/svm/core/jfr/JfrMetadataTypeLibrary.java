/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.options.OptionsParser;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.jfr.internal.MetadataRepository;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.TypeLibrary;

/**
 * This class caches all JFR metadata types. This is mainly necessary because
 * {@link TypeLibrary#getTypes()} isn't multi-threading safe.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class JfrMetadataTypeLibrary {
    private static final HashMap<String, Type> types = new HashMap<>();

    private static synchronized HashMap<String, Type> getTypes() {
        if (types.isEmpty()) {
            for (Type type : getTypes0()) {
                assert !types.containsKey(type.getName());
                types.put(type.getName(), type);
            }
        }
        return types;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Type> getTypes0() {
        try {
            /*
             * Initialize the MetadataRepository class, to ensure that large parts of the JFR
             * infrastructure are initialized (e.g., the TypeLibrary that we access below).
             */
            MetadataRepository.getInstance();

            Method getTypes = ReflectionUtil.lookupMethod(TypeLibrary.class, "getTypes");
            return (Collection<Type>) getTypes.invoke(null);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere("Error while calling TypeLibrary.getTypes().", e);
        }
    }

    public static int getPlatformEventCount() {
        long maxEventId = 0;
        for (Type type : getTypes().values()) {
            if (type instanceof PlatformEventType) {
                maxEventId = Math.max(maxEventId, type.getId());
            }
        }
        return NumUtil.safeToInt(maxEventId + 1);
    }

    public static long lookupPlatformEvent(String name) {
        Type type = getTypes().get(name);
        if (type instanceof PlatformEventType) {
            return type.getId();
        }
        return notFound(name);
    }

    public static long lookupType(String name) {
        Type type = getTypes().get(name);
        if (type != null) {
            return type.getId();
        }
        return notFound(name);
    }

    private static long notFound(String name) {
        String exceptionMessage = "Event/Type " + name + " was not found!";
        Type mostSimilar = getMostSimilar(name);
        if (mostSimilar != null) {
            exceptionMessage += " The most similar event/type is '" + mostSimilar.getName() + "' (" + mostSimilar.getClass() + ").";
        }
        exceptionMessage += " Take a look at 'metadata.xml' to see all available events.";
        throw VMError.shouldNotReachHere(exceptionMessage);
    }

    private static Type getMostSimilar(String missingTypeName) {
        float threshold = OptionsParser.FUZZY_MATCH_THRESHOLD;
        Type mostSimilar = null;
        for (Type type : getTypes().values()) {
            float similarity = OptionsParser.stringSimilarity(type.getName(), missingTypeName);
            if (similarity > threshold) {
                threshold = similarity;
                mostSimilar = type;
            }
        }
        return mostSimilar;
    }
}
