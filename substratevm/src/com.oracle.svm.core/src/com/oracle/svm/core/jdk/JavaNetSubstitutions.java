/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.net.InetAddress;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Hashtable;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

@TargetClass(java.net.URL.class)
final class Target_java_net_URL {

    @Delete private static Hashtable<?, ?> handlers;

    @Substitute
    private static URLStreamHandler getURLStreamHandler(String protocol) {
        URLStreamHandler result = Util_java_net_URL.imageHandlers.get(protocol);
        if (result == null) {
            throw VMError.unsupportedFeature("Unknown URL protocol: " + protocol);
        }
        return result;
    }
}

final class Util_java_net_URL {

    static final HashMap<String, URLStreamHandler> imageHandlers = new HashMap<>();

    static {
        addURLStreamHandler("file");
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void addURLStreamHandler(String protocol) {
        try {
            Method method = URL.class.getDeclaredMethod("getURLStreamHandler", String.class);
            method.setAccessible(true);
            imageHandlers.put(protocol, (URLStreamHandler) method.invoke(null, protocol));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            throw new Error(ex);
        }
    }
}

@TargetClass(java.net.URLStreamHandler.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_java_net_URLStreamHandler {

    @Substitute
    private InetAddress getHostAddress(URL u) {
        /*
         * This method is used for hashCode and equals of URL. Returning null is OK, but changes
         * semantics a bit.
         */
        return null;
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaNetSubstitutions {
}
