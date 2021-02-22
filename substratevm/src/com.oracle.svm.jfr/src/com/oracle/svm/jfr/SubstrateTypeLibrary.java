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

// Checkstyle: allow reflection
import com.oracle.svm.core.util.VMError;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.TypeLibrary;
import jdk.internal.org.xml.sax.helpers.DefaultHandler;
import jdk.internal.util.xml.SAXParser;
import jdk.internal.util.xml.impl.SAXParserImpl;

@Platforms(Platform.HOSTED_ONLY.class)
final class SubstrateTypeLibrary {

    static void installSubstrateTypeLibrary() {
        try {
            Field instance = TypeLibrary.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, getInstance());
        } catch (Exception ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static TypeLibrary getInstance() throws NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        List<Type> jvmTypes;
        try {
            jvmTypes = createTypes();
            Collections.sort(jvmTypes, (a, b) -> Long.compare(a.getId(), b.getId()));
        } catch (IOException | ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere("JFR: Could not read metadata", ex);
        }

        Constructor<TypeLibrary> c = TypeLibrary.class.getDeclaredConstructor(List.class);
        c.setAccessible(true);
        return c.newInstance(jvmTypes);
    }

    @SuppressWarnings("unchecked")
    private static List<Type> createTypes()
                    throws IOException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        SAXParser parser = new SAXParserImpl();
        // Checkstyle: stop
        Class<?> metadataHandler = Class.forName("jdk.jfr.internal.MetadataHandler");
        // Checkstyle: resume
        Method buildTypes = metadataHandler.getDeclaredMethod("buildTypes");
        buildTypes.setAccessible(true);

        Constructor<?> c = metadataHandler.getDeclaredConstructor();
        c.setAccessible(true);

        DefaultHandler t = (DefaultHandler) c.newInstance();
        // TODO: at the moment, we ship our own metadata.xml file. However, it probably makes more
        // sense to just use the JDK version-specific metadata.xml file that ships with GraalVM.
        try (InputStream is = new BufferedInputStream(SubstrateTypeLibrary.class.getResourceAsStream("/com/oracle/svm/jfr/types/metadata.xml"))) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.DEBUG, "Parsing metadata.xml");
            try {
                parser.parse(is, t);
                return (List<Type>) buildTypes.invoke(t);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
