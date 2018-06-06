/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

// Checkstyle: allow reflection

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;

@TargetClass(java.net.URL.class)
final class Target_java_net_URL {

    @Delete private static Hashtable<?, ?> handlers;

    @Substitute
    private static URLStreamHandler getURLStreamHandler(String protocol) {
        return JavaNetSubstitutions.getURLStreamHandler(protocol);
    }
}

@AutomaticFeature
class JavaNetFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        String optionValue = SubstrateOptions.EnableURLProtocols.getValue();
        if (optionValue.isEmpty()) {
            return;
        }
        String[] protocols = optionValue.split(",");
        for (String protocol : protocols) {
            if (JavaNetSubstitutions.defaultProtocols.contains(protocol)) {
                printWarning("The URL protocol " + protocol + " is enabled by default. " +
                                "The option " + JavaNetSubstitutions.enableProtocolsOption + protocol + " is not needed.");
            } else if (JavaNetSubstitutions.supportedProtocols.contains(protocol)) {
                JavaNetSubstitutions.addURLStreamHandler(protocol);
            } else if (JavaNetSubstitutions.unsupportedProtocols.contains(protocol)) {
                printWarning("The URL protocol " + protocol + " is not currently supported." +
                                System.lineSeparator() + JavaNetSubstitutions.supportedProtocols());
            } else {
                printWarning("The URL protocol " + protocol + " is not tested and might not work as expected." +
                                System.lineSeparator() + JavaNetSubstitutions.supportedProtocols());
                JavaNetSubstitutions.addURLStreamHandler(protocol);
            }
        }
    }

    private static void printWarning(String warningMessage) {
        // Checkstyle: stop
        System.out.println(warningMessage);
        // Checkstyle: resume}
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaNetSubstitutions {

    private static final String FILE_PROTOCOL = "file";
    private static final String HTTP_PROTOCOL = "http";
    private static final String HTTPS_PROTOCOL = "https";

    static List<String> defaultProtocols = Arrays.asList(FILE_PROTOCOL);
    static List<String> supportedProtocols = Arrays.asList(HTTP_PROTOCOL);
    static List<String> unsupportedProtocols = Arrays.asList(HTTPS_PROTOCOL);

    private static final HashMap<String, URLStreamHandler> imageHandlers = new HashMap<>();
    static final String enableProtocolsOption = SubstrateOptionsParser.commandArgument(SubstrateOptions.EnableURLProtocols, "");

    static {
        defaultProtocols.forEach(protocol -> addURLStreamHandler(protocol));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static void addURLStreamHandler(String protocol) {
        try {
            Method method = URL.class.getDeclaredMethod("getURLStreamHandler", String.class);
            method.setAccessible(true);
            imageHandlers.put(protocol, (URLStreamHandler) method.invoke(null, protocol));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            throw new Error(ex);
        }
    }

    static URLStreamHandler getURLStreamHandler(String protocol) {
        URLStreamHandler result = JavaNetSubstitutions.imageHandlers.get(protocol);
        if (result == null) {
            if (supportedProtocols.contains(protocol)) {
                unsupported("Accessing an URL protocol that was not enabled. The URL protocol " + protocol +
                                " is supported but not enabled by default. It must be enabled by adding the " + enableProtocolsOption + protocol +
                                " option to the native-image command.");
            } else if (JavaNetSubstitutions.unsupportedProtocols.contains(protocol)) {
                unsupported("The URL protocol " + protocol + " is not currently supported. " + supportedProtocols());
            } else {
                unsupported("Accessing an URL protocol that was not enabled. The URL protocol " + protocol +
                                " is not tested and might not work as expected. It can be enabled by adding the " + enableProtocolsOption + protocol +
                                " option to the native-image command.");
            }
        }
        return result;
    }

    private static void unsupported(String message) {
        throw VMError.unsupportedFeature(message);
    }

    static String supportedProtocols() {
        return "Supported URL protocols enabled by default: " + String.join(",", JavaNetSubstitutions.defaultProtocols) +
                        ". Additional supported URL protocols: " + String.join(",", JavaNetSubstitutions.supportedProtocols) + ".";
    }
}
