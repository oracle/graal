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

import java.io.FileDescriptor;

import org.graalvm.nativeimage.Feature;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.hosted.ClassInitializationFeature;

/**
 * Complain if there are types that can not move from the image generator heap to the image heap.
 */
@AutomaticFeature
public class DisallowedImageHeapObjectFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(DisallowedImageHeapObjectFeature::replacer);
    }

    private static Object replacer(Object original) {
        String message = "This is not supported. The object was probaby created by a class initializer and is reachable from a static field. " +
                        "By default, all class initialization is done during native image building." +
                        "You can manually delay class initialization to runtime by using the option " +
                        SubstrateOptionsParser.commandArgument(ClassInitializationFeature.Options.DelayClassInitialization, "<class-name>") + "." +
                        "Or you can write your own initialization methods and call them explicitly from your main entry point.";

        /* Started Threads can not be in the image heap. */
        if (original instanceof Thread) {
            final Thread asThread = (Thread) original;
            if (asThread.getState() != Thread.State.NEW) {
                throw new UnsupportedFeatureException("Detected a started Thread in the image heap. " + message);
            }
        }
        /* FileDescriptors can not be in the image heap. */
        if (original instanceof FileDescriptor) {
            final FileDescriptor asFileDescriptor = (FileDescriptor) original;
            /* Except for a few well-known FileDescriptors. */
            if (!((asFileDescriptor == FileDescriptor.in) || (asFileDescriptor == FileDescriptor.out) || (asFileDescriptor == FileDescriptor.err) || (!asFileDescriptor.valid()))) {
                throw new UnsupportedFeatureException("Detected a FileDescriptor in the image heap. " + message);
            }
        }

        /* ZipFiles can not be in the image heap. */
        if (original instanceof java.util.zip.ZipFile) {
            throw new UnsupportedFeatureException("Detected a ZipFile object in the image heap. " + message);
        }

        return original;
    }
}
