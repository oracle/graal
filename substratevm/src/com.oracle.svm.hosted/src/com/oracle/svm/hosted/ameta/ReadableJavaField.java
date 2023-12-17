/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.ameta;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.HostedField;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

public interface ReadableJavaField extends ResolvedJavaField {

    static JavaConstant readFieldValue(ClassInitializationSupport classInitializationSupport, ResolvedJavaField field, JavaConstant receiver) {
        assert !(field instanceof AnalysisField) && !(field instanceof HostedField) : "must have been unwrapped";

        if (field instanceof ReadableJavaField readableField) {
            /*
             * A ReadableJavaField is able to provide a field value even when the class is
             * initialized at run time, so this check must be before the class initialization check
             * below.
             */
            assert readableField.isValueAvailable() : "Field " + readableField.format("%H.%n") + " value not available for reading.";
            return readableField.readValue(classInitializationSupport, receiver);

        } else if (!classInitializationSupport.maybeInitializeAtBuildTime(field.getDeclaringClass())) {
            /*
             * The class is initialized at image run time. We must not use any field value from the
             * image builder VM, even if the class is already initialized there. We need to return
             * the value expected before running the class initializer.
             *
             * Note that we cannot rely on field.getDeclaringClass().isInitialized() for the class
             * initialization check: we are already in the HotSpot universe here, and a class that
             * is initialized in the hosting HotSpot VM can still be initialized at run time.
             */
            if (field.isStatic()) {
                /*
                 * Use the value from the constant pool attribute for the static field. That is the
                 * value before the class initializer is executed.
                 */
                JavaConstant constantValue = field.getConstantValue();
                if (constantValue != null) {
                    return constantValue;
                } else {
                    return JavaConstant.defaultForKind(field.getJavaKind());
                }

            } else {
                /*
                 * Classes that are initialized at run time must not have instances in the image
                 * heap. Invoking instance methods would miss the class initialization checks. Image
                 * generation should have been aborted earlier with a user-friendly message, this is
                 * just a safeguard.
                 */
                throw VMError.shouldNotReachHere("Cannot read instance field of a class that is initialized at run time: " + field.format("%H.%n"));
            }

        } else {
            return GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(field, receiver);
        }
    }

    JavaConstant readValue(ClassInitializationSupport classInitializationSupport, JavaConstant receiver);

    boolean isValueAvailable();

    boolean injectFinalForRuntimeCompilation();

    static boolean injectFinalForRuntimeCompilation(ResolvedJavaField original) {
        if (original instanceof ReadableJavaField) {
            return ((ReadableJavaField) original).injectFinalForRuntimeCompilation();
        } else {
            return false;
        }
    }
}
