/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.HashMap;
import java.util.zip.ZipFile;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(value = ZipFile.class, innerClass = "Source")
final class Target_java_util_zip_ZipFile_Source {

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = HashMap.class, isFinal = true)//
    private static HashMap<Target_java_util_zip_ZipFile_Source_Key, Target_java_util_zip_ZipFile_Source> files;
}

@TargetClass(value = ZipFile.class, innerClass = {"Source", "Key"})
final class Target_java_util_zip_ZipFile_Source_Key {
}

/* Dummy class to have the same name as the file. */
public class Target_java_util_zip_ZipFile {
}
