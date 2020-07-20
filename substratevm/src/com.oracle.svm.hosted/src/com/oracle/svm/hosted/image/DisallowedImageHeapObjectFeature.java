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

import java.lang.management.PlatformManagedObject;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.image.DisallowedImageHeapObjects;
import com.oracle.svm.core.jdk.management.ManagementFeature;
import com.oracle.svm.core.jdk.management.ManagementSupport;
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

    private String[] disallowedSubstrings;
    private Map<byte[], Charset> disallowedByteSubstrings;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        /*
         * Ensure that object replaced registered by ManagementFeature runs before our object
         * replacer.
         */
        return Arrays.asList(ManagementFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        classInitialization = ((FeatureImpl.DuringSetupAccessImpl) access).getHostVM().getClassInitializationSupport();
        access.registerObjectReplacer(this::replacer);

        if (SubstrateOptions.DetectUserDirectoriesInImageHeap.getValue()) {
            /*
             * We do not check for the temp directory name and the user name because they have a too
             * high chance of being short or generic terms that appear in valid strings.
             */
            disallowedSubstrings = new String[]{
                            System.getProperty("user.home"),
                            System.getProperty("user.dir"),
                            System.getProperty("java.home")};

            /* We cannot check all byte[] encodings of strings, but we want to check common ones. */
            Set<Charset> encodings = new HashSet<>(Arrays.asList(
                            StandardCharsets.UTF_8,
                            StandardCharsets.UTF_16,
                            Charset.forName(System.getProperty("sun.jnu.encoding"))));

            disallowedByteSubstrings = new IdentityHashMap<>();
            for (int i = 0; i < disallowedSubstrings.length; i++) {
                String s = disallowedSubstrings[i];
                for (Charset encoding : encodings) {
                    disallowedByteSubstrings.put(s.getBytes(encoding), encoding);
                }
            }
        }
    }

    private Object replacer(Object original) {
        if (original instanceof Thread && original instanceof ImageGeneratorThreadMarker) {
            return ((ImageGeneratorThreadMarker) original).asTerminated();
        }

        checkDisallowedMBeanObjects(original);

        if (original instanceof String && disallowedSubstrings != null) {
            String string = (String) original;
            for (String disallowedSubstring : disallowedSubstrings) {
                if (string.contains(disallowedSubstring)) {
                    throw new UnsupportedFeatureException("Detected a string in the image heap that contains a user directory. " +
                                    "This means that file system information from the native image build is persisted and available at image run time, which is most likely an error." +
                                    System.lineSeparator() + "String that is problematic: " + string + System.lineSeparator() +
                                    "Disallowed substring with user directory: " + disallowedSubstring + System.lineSeparator() +
                                    "This check can be disabled using the option " + SubstrateOptionsParser.commandArgument(SubstrateOptions.DetectUserDirectoriesInImageHeap, "-"));
                }
            }
        }

        if (original instanceof byte[] && disallowedByteSubstrings != null) {
            byte[] bytes = (byte[]) original;
            for (Map.Entry<byte[], Charset> entry : disallowedByteSubstrings.entrySet()) {
                byte[] disallowedSubstring = entry.getKey();
                if (search(bytes, disallowedSubstring)) {
                    Charset charset = entry.getValue();
                    throw new UnsupportedFeatureException("Detected a byte[] in the image heap that contains a user directory. " +
                                    "This means that file system information from the native image build is persisted and available at image run time, which is most likely an error." +
                                    System.lineSeparator() + "byte[] that is problematic: " + new String(bytes, charset) + System.lineSeparator() +
                                    "Disallowed substring with user directory: " + new String(disallowedSubstring, charset) + System.lineSeparator() +
                                    "This check can be disabled using the option " + SubstrateOptionsParser.commandArgument(SubstrateOptions.DetectUserDirectoriesInImageHeap, "-"));
                }
            }
        }

        DisallowedImageHeapObjects.check(original, this::error);
        return original;
    }

    /** See {@link ManagementSupport} for details why these objects are not allowed. */
    private void checkDisallowedMBeanObjects(Object original) {
        if (original instanceof MBeanServerConnection) {
            throw error("Detected a MBean server in the image heap. This is currently not supported, but could be changed in the future. " +
                            "Management beans are registered in many global caches that would need to be cleared and properly re-built at image build time. " +
                            "Class of disallowed object: " + original.getClass().getTypeName(),
                            original, "Try to avoid initializing the class that stores a MBean server or a MBean in a static field");

        } else if (original instanceof PlatformManagedObject && !ManagementSupport.getSingleton().isRegisteredPlatformManagedObject((PlatformManagedObject) original)) {
            throw error("Detected a PlatformManagedObject (a MXBean defined by the virtual machine) in the image heap. " +
                            "This bean is introspecting the VM that runs the image builder, i.e., a VM instance that is no longer available at image run time. " +
                            "Class of disallowed object: " + original.getClass().getTypeName(),
                            original, "Try to avoid initializing the class that stores the object in a static field");
        }
    }

    private RuntimeException error(String msg, Object obj, String initializerAction) {
        throw new UnsupportedFeatureException(msg + " " + classInitialization.objectInstantiationTraceMessage(obj, initializerAction) + " " +
                        "The object was probably created by a class initializer and is reachable from a static field. " +
                        "You can request class initialization at image run time by using the option " +
                        SubstrateOptionsParser.commandArgument(ClassInitializationFeature.Options.ClassInitialization, "<class-name>", "initialize-at-run-time") + ". " +
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
