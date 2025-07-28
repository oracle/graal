/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.image.DisallowedImageHeapObjects.CANCELLABLE_CLASS;
import static com.oracle.svm.core.image.DisallowedImageHeapObjects.MEMORY_SEGMENT_CLASS;
import static com.oracle.svm.core.image.DisallowedImageHeapObjects.NIO_CLEANER_CLASS;
import static com.oracle.svm.core.image.DisallowedImageHeapObjects.SCOPE_CLASS;

import java.io.File;
import java.io.FileDescriptor;
import java.lang.management.PlatformManagedObject;
import java.lang.ref.Cleaner;
import java.nio.Buffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.zip.ZipFile;

import javax.management.MBeanServerConnection;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.svm.core.ForeignSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.image.DisallowedImageHeapObjects;
import com.oracle.svm.core.jdk.management.ManagementSupport;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.classinitialization.ClassInitializationOptions;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

/**
 * Complain if there are types that can not move from the image generator heap to the image heap.
 */
@AutomaticallyRegisteredFeature
public class DisallowedImageHeapObjectFeature implements InternalFeature {

    private ClassInitializationSupport classInitialization;

    private String[] disallowedSubstrings;
    private Map<byte[], Charset> disallowedByteSubstrings;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;
        classInitialization = access.getHostVM().getClassInitializationSupport();
        access.registerObjectReachableCallback(MBeanServerConnection.class, (a1, obj, reason) -> onMBeanServerConnectionReachable(obj, this::error));
        access.registerObjectReachableCallback(PlatformManagedObject.class, (a1, obj, reason) -> onPlatformManagedObjectReachable(obj, this::error));
        access.registerObjectReachableCallback(Random.class, (a1, obj, reason) -> DisallowedImageHeapObjects.onRandomReachable(obj, this::error));
        access.registerObjectReachableCallback(SplittableRandom.class, (a1, obj, reason) -> DisallowedImageHeapObjects.onSplittableRandomReachable(obj, this::error));
        access.registerObjectReachableCallback(Thread.class, (a1, obj, reason) -> DisallowedImageHeapObjects.onThreadReachable(obj, this::error));
        access.registerObjectReachableCallback(DisallowedImageHeapObjects.CONTINUATION_CLASS,
                        (a1, obj, reason) -> DisallowedImageHeapObjects.onContinuationReachable(obj, this::error));
        access.registerObjectReachableCallback(FileDescriptor.class, (a1, obj, reason) -> DisallowedImageHeapObjects.onFileDescriptorReachable(obj, this::error));
        access.registerObjectReachableCallback(Buffer.class, (a1, obj, reason) -> DisallowedImageHeapObjects.onBufferReachable(obj, this::error));
        access.registerObjectReachableCallback(Cleaner.Cleanable.class, (a1, obj, reason) -> DisallowedImageHeapObjects.onCleanableReachable(obj, this::error));
        access.registerObjectReachableCallback(NIO_CLEANER_CLASS, (a1, obj, reason) -> DisallowedImageHeapObjects.onCleanableReachable(obj, this::error));
        access.registerObjectReachableCallback(Cleaner.class, (a1, obj, reason) -> DisallowedImageHeapObjects.onCleanerReachable(obj, this::error));
        access.registerObjectReachableCallback(ZipFile.class, (a1, obj, reason) -> DisallowedImageHeapObjects.onZipFileReachable(obj, this::error));
        access.registerObjectReachableCallback(CANCELLABLE_CLASS, (a1, obj, reason) -> DisallowedImageHeapObjects.onCancellableReachable(obj, this::error));

        if (ForeignSupport.isAvailable()) {
            ForeignSupport foreignSupport = ForeignSupport.singleton();
            access.registerObjectReachableCallback(MEMORY_SEGMENT_CLASS, (a1, obj, reason) -> foreignSupport.onMemorySegmentReachable(obj, this::error));
            access.registerObjectReachableCallback(SCOPE_CLASS, (a1, obj, reason) -> foreignSupport.onScopeReachable(obj, this::error));
        }

        if (SubstrateOptions.DetectUserDirectoriesInImageHeap.getValue()) {
            access.registerObjectReachableCallback(String.class, this::onStringReachable);
            access.registerObjectReachableCallback(byte[].class, this::onByteArrayReachable);

            /*
             * We do not check for the temp directory name and the user name because they have a too
             * high chance of being short or generic terms that appear in valid strings.
             */
            disallowedSubstrings = getDisallowedSubstrings(
                            System.getProperty("user.home"),
                            System.getProperty("user.dir"),
                            System.getProperty("java.home"));

            /* We cannot check all byte[] encodings of strings, but we want to check common ones. */
            Set<Charset> encodings = new HashSet<>(Arrays.asList(
                            StandardCharsets.UTF_8,
                            StandardCharsets.UTF_16,
                            Charset.forName(System.getProperty("sun.jnu.encoding"))));

            disallowedByteSubstrings = new IdentityHashMap<>();
            for (String s : disallowedSubstrings) {
                for (Charset encoding : encodings) {
                    disallowedByteSubstrings.put(s.getBytes(encoding), encoding);
                }
            }
        }
    }

    private static String[] getDisallowedSubstrings(String... substrings) {
        return Arrays.stream(substrings).filter(s -> {
            /*
             * To avoid false positives when detecting user directories in the image heap, we
             * disallow substrings only if they have at least two name-separator characters.
             */
            return s.indexOf(File.separatorChar, s.indexOf(File.separatorChar) + 1) != -1;
        }).toArray(String[]::new);
    }

    @SuppressWarnings("unused")
    private void onStringReachable(DuringAnalysisAccess a, String string, ObjectScanner.ScanReason reason) {
        if (disallowedSubstrings != null) {
            for (String disallowedSubstring : disallowedSubstrings) {
                if (string.contains(disallowedSubstring)) {
                    throw new UnsupportedFeatureException("Detected a string in the image heap that contains a user directory. " +
                                    "This means that file system information from the native image build is persisted and available at image runtime, which is most likely an error." +
                                    System.lineSeparator() + "String that is problematic: " + string + System.lineSeparator() +
                                    "Disallowed substring with user directory: " + disallowedSubstring + System.lineSeparator() +
                                    "This check can be disabled using the option " + SubstrateOptionsParser.commandArgument(SubstrateOptions.DetectUserDirectoriesInImageHeap, "-"));
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private void onByteArrayReachable(DuringAnalysisAccess a, byte[] bytes, ObjectScanner.ScanReason reason) {
        if (disallowedByteSubstrings != null) {
            for (Map.Entry<byte[], Charset> entry : disallowedByteSubstrings.entrySet()) {
                byte[] disallowedSubstring = entry.getKey();
                if (search(bytes, disallowedSubstring)) {
                    Charset charset = entry.getValue();
                    throw new UnsupportedFeatureException("Detected a byte[] in the image heap that contains a user directory. " +
                                    "This means that file system information from the native image build is persisted and available at image runtime, which is most likely an error." +
                                    System.lineSeparator() + "byte[] that is problematic: " + new String(bytes, charset) + System.lineSeparator() +
                                    "Disallowed substring with user directory: " + new String(disallowedSubstring, charset) + System.lineSeparator() +
                                    "This check can be disabled using the option " + SubstrateOptionsParser.commandArgument(SubstrateOptions.DetectUserDirectoriesInImageHeap, "-"));
                }
            }
        }
    }

    /**
     * See {@link ManagementSupport} for details why these objects are not allowed.
     */
    private static void onMBeanServerConnectionReachable(MBeanServerConnection serverConnection, DisallowedImageHeapObjects.DisallowedObjectReporter reporter) {
        throw reporter.raise("Detected a MBean server in the image heap. This is currently not supported, but could be changed in the future. " +
                        "Management beans are registered in many global caches that would need to be cleared and properly re-built at image build time. " +
                        "Class of disallowed object: " + serverConnection.getClass().getTypeName(),
                        serverConnection, "Try to avoid initializing the class that stores a MBean server or a MBean in a static field");
    }

    /**
     * See {@link ManagementSupport} for details why these objects are not allowed.
     */
    private static void onPlatformManagedObjectReachable(PlatformManagedObject platformManagedObject, DisallowedImageHeapObjects.DisallowedObjectReporter reporter) {
        if (!ManagementSupport.getSingleton().isAllowedPlatformManagedObject(platformManagedObject)) {
            throw reporter.raise("Detected a PlatformManagedObject (a MXBean defined by the virtual machine) in the image heap. " +
                            "This bean is introspecting the VM that runs the image builder, i.e., a VM instance that is no longer available at image runtime. " +
                            "Class of disallowed object: " + platformManagedObject.getClass().getTypeName(),
                            platformManagedObject, "Try to avoid initializing the class that stores the object in a static field");
        }
    }

    private RuntimeException error(String msg, Object obj, String initializerAction) {
        throw new UnsupportedFeatureException(msg + " " + classInitialization.objectInstantiationTraceMessage(obj, "", action -> initializerAction) +
                        "The object was probably created by a class initializer and is reachable from a static field. " +
                        "You can request class initialization at image runtime by using the option " +
                        SubstrateOptionsParser.commandArgument(ClassInitializationOptions.ClassInitialization, "<class-name>", "initialize-at-run-time") + ". " +
                        "Or you can write your own initialization methods and call them explicitly from your main entry point.");
    }

    private static boolean search(byte[] haystack, byte[] needle) {
        byte first = needle[0];
        for (int start = 0; start < haystack.length - needle.length; start++) {
            if (haystack[start] == first) {
                boolean same = true;
                for (int i = 1; i < needle.length; i++) {
                    if (haystack[start + i] != needle[i]) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    return true;
                }
            }
        }
        return false;
    }
}
