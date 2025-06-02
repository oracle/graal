/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.time.LocalDateTime;

import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.jfr.Recording;

@TargetClass(className = "jdk.jfr.internal.JVMSupport")
final class Target_jdk_jfr_internal_JVMSupport {
    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = JfrNotAvailableTransformer.class, isFinal = true) //
    private static boolean notAvailable;

    @Substitute
    public static String makeFilename(Recording recording) {
        long pid = ProcessProperties.getProcessID();
        LocalDateTime now = LocalDateTime.now();
        String date = Target_jdk_jfr_internal_util_ValueFormatter.formatDateTime(now);
        String idText = recording == null ? "" : "-id-" + recording.getId();
        String imageName = SubstrateOptions.Name.getValue();
        return imageName + "-pid-" + pid + idText + "-" + date + ".jfr";
    }
}

final class JfrNotAvailableTransformer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return !HasJfrSupport.get();
    }
}

@TargetClass(className = "jdk.jfr.internal.util.ValueFormatter", onlyWith = HasJfrSupport.class)
final class Target_jdk_jfr_internal_util_ValueFormatter {
    @Alias
    public static native String formatDateTime(LocalDateTime time);
}
