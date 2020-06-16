/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.xml;

import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.classinitialization.ConfigurableClassInitialization;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class XMLParsersRegistration extends JNIRegistrationUtil {

    public void registerConfigs(Feature.DuringAnalysisAccess access) {
        List<String> parserClasses = xmlParserClasses();
        registerReflectionClasses((FeatureImpl.DuringAnalysisAccessImpl) access, parserClasses);
        registerResources();
        access.requireAnalysisIteration();
    }

    abstract List<String> xmlParserClasses();

    void registerResources() {
    }

    private static void registerReflectionClasses(FeatureImpl.DuringAnalysisAccessImpl access, List<String> parserClasses) {
        for (String className : parserClasses) {
            RuntimeReflection.register(clazz(access, className));
            RuntimeReflection.register(constructor(access, className));
        }
    }

    static class DatatypeFactoryClasses extends XMLParsersRegistration {

        @Override
        List<String> xmlParserClasses() {
            return Collections.singletonList("com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl");
        }
    }

    static class DOMImplementationRegistryClasses extends XMLParsersRegistration {

        @Override
        List<String> xmlParserClasses() {
            return Collections.singletonList("com.sun.org.apache.xerces.internal.dom.DOMXSImplementationSourceImpl");
        }
    }

    static class DOMParserClasses extends XMLParsersRegistration {

        @Override
        List<String> xmlParserClasses() {
            return Collections.singletonList("com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
        }
    }

    static class SAXParserClasses extends XMLParsersRegistration {

        @Override
        List<String> xmlParserClasses() {
            return Collections.singletonList("com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
        }
    }

    static class StAXParserClasses extends XMLParsersRegistration {

        @Override
        List<String> xmlParserClasses() {
            return Arrays.asList(
                            "com.sun.xml.internal.stream.events.XMLEventFactoryImpl",
                            "com.sun.xml.internal.stream.XMLInputFactoryImpl",
                            "com.sun.xml.internal.stream.XMLOutputFactoryImpl");
        }
    }

    static class TransformerClassesAndResources extends XMLParsersRegistration {

        @Override
        void registerResources() {
            /*
             * To allow register new resource bundle classes during analysis phase
             */
            ConfigurableClassInitialization classInitializationSupport = (ConfigurableClassInitialization) ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
            classInitializationSupport.setConfigurationSealed(false);

            ResourcesRegistry resourcesRegistry = ImageSingletons.lookup(ResourcesRegistry.class);
            resourcesRegistry.addResourceBundles("com.sun.org.apache.xml.internal.serializer.utils.SerializerMessages");
            resourcesRegistry.addResources("com.sun.*.properties");

            classInitializationSupport.setConfigurationSealed(true);
        }

        @Override
        List<String> xmlParserClasses() {
            return Collections.singletonList("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
        }
    }
}
