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

import java.util.HashMap;

import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;

import jdk.jfr.internal.Type;
import jdk.jfr.internal.TypeLibrary;

/**
 * This class caches all JFR metadata types. This is mainly necessary because
 * {@link TypeLibrary#getTypes()} isn't multi-threading safe.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class JfrMetadataTypeLibrary {
    private static final HashMap<String, Type> types = new HashMap<>();

    public static void initialize() {
        for (Type type : TypeLibrary.getInstance().getTypes()) {
            types.put(type.getName(), type);
        }
    }

    public static long lookup(String typeName) {
        Type type = types.get(typeName);
        if (type != null) {
            return type.getId();
        }

        String exceptionMessage = "Type " + typeName + " is not found!";
        String mostSimilarType = getMostSimilarType(typeName);
        if (mostSimilarType != null) {
            exceptionMessage += " The most similar type is " + mostSimilarType;
        }
        exceptionMessage += " Take a look at 'metadata.xml' to see all available types.";

        throw VMError.shouldNotReachHere(exceptionMessage);
    }

    private static String getMostSimilarType(String missingTypeName) {
        float threshold = OptionsParser.FUZZY_MATCH_THRESHOLD;
        String mostSimilar = null;
        for (Type type : TypeLibrary.getInstance().getTypes()) {
            float similarity = OptionsParser.stringSimilarity(type.getName(), missingTypeName);
            if (similarity > threshold) {
                threshold = similarity;
                mostSimilar = type.getName();
            }
        }
        return mostSimilar;
    }
}
