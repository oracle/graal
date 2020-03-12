/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.image.DisallowedImageHeapObjects;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.classinitialization.ClassInitializationFeature;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.util.ImageGeneratorThreadMarker;

/**
 * Complain if there are types that can not move from the image generator heap to the image heap.
 */
@AutomaticFeature
public class DisallowedImageHeapObjectFeature implements Feature {

    private ClassInitializationSupport classInitialization;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        classInitialization = ((FeatureImpl.DuringSetupAccessImpl) access).getHostVM().getClassInitializationSupport();
        access.registerObjectReplacer(this::replacer);
    }

    private Object replacer(Object original) {
        if (original instanceof Thread && original instanceof ImageGeneratorThreadMarker) {
            return ((ImageGeneratorThreadMarker) original).asTerminated();
        }

        DisallowedImageHeapObjects.check(original, this::error);
        return original;
    }

    private RuntimeException error(String msg, Object obj, String initializerAction) {
        throw new UnsupportedFeatureException(msg + " " + classInitialization.objectInstantiationTraceMessage(obj, initializerAction) + " " +
                        "The object was probably created by a class initializer and is reachable from a static field. " +
                        "You can request class initialization at image run time by using the option " +
                        SubstrateOptionsParser.commandArgument(ClassInitializationFeature.Options.ClassInitialization, "<class-name>", "initialize-at-run-time") + ". " +
                        "Or you can write your own initialization methods and call them explicitly from your main entry point.");
    }
}
