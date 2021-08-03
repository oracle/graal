/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;

import jdk.jfr.internal.Type;
import jdk.jfr.internal.TypeLibrary;

/**
 * Maps JFR types against their IDs in the JDK.
 */
public enum JfrTypes {
    Class("java.lang.Class"),
    String("java.lang.String"),
    Thread("java.lang.Thread"),
    ThreadState("jdk.types.ThreadState"),
    ThreadGroup("jdk.types.ThreadGroup"),
    StackTrace("jdk.types.StackTrace"),
    ClassLoader("jdk.types.ClassLoader"),
    Method("jdk.types.Method"),
    Symbol("jdk.types.Symbol"),
    Module("jdk.types.Module"),
    Package("jdk.types.Package"),
    FrameType("jdk.types.FrameType");

    private final long id;

    JfrTypes(String name) {
        this.id = getTypeId(name);
    }

    public long getId() {
        return id;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
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

    @Platforms(Platform.HOSTED_ONLY.class)
    private static long getTypeId(String typeName) {
        for (Type type : TypeLibrary.getInstance().getTypes()) {
            if (typeName.equals(type.getName())) {
                return type.getId();
            }
        }

        String exceptionMessage = "Type " + typeName + " is not found!";
        String mostSimilarType = getMostSimilarType(typeName);
        if (mostSimilarType != null) {
            exceptionMessage += " The most similar type is " + mostSimilarType;
        }
        exceptionMessage += " Take a look at 'metadata.xml' to see all available types.";

        throw VMError.shouldNotReachHere(exceptionMessage);
    }
}
