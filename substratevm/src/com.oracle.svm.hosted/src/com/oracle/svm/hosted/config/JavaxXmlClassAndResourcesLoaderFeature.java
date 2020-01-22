/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.config;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.classinitialization.ConfigurableClassInitialization;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

@AutomaticFeature
public class JavaxXmlClassAndResourcesLoaderFeature extends JNIRegistrationUtil implements Feature {

    private static String[] xmlClasses = {
                    "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl",
                    "com.sun.org.apache.xerces.internal.dom.DOMXSImplementationSourceImpl",
                    "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl",
                    "com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl",
                    "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl",
                    "com.sun.xml.internal.stream.events.XMLEventFactoryImpl",
                    "com.sun.xml.internal.stream.XMLInputFactoryImpl",
                    "com.sun.xml.internal.stream.XMLOutputFactoryImpl"
    };

    private static String[] bundles = {
                    "com.sun.org.apache.xml.internal.serializer.utils.SerializerMessages"
    };

    private static String[] resources = {
                    "com.sun.*.properties"
    };

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Object[] xmlMethods = {
                        method(access, "javax.xml.parsers.FactoryFinder", "find", Class.class, String.class),
                        method(access, "javax.xml.transform.FactoryFinder", "find", Class.class, String.class),
                        method(access, "javax.xml.stream.FactoryFinder", "find", Class.class, String.class)
        };
        access.registerReachabilityHandler(JavaxXmlClassAndResourcesLoaderFeature::registerJavaxXmlConfigs, xmlMethods);
    }

    private static void registerJavaxXmlConfigs(DuringAnalysisAccess a) {
        registerReflectionClasses((FeatureImpl.DuringAnalysisAccessImpl) a);
        registerResources();
        a.requireAnalysisIteration();
    }

    private static void registerResources() {
        /*
         * To allow register new resource bundle classes during analysis phase
         */
        ConfigurableClassInitialization classInitializationSupport = (ConfigurableClassInitialization) ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        classInitializationSupport.setConfigurationSealed(false);

        ResourcesRegistry resourcesRegistry = ImageSingletons.lookup(ResourcesRegistry.class);
        for (String resourceBundle : bundles) {
            resourcesRegistry.addResourceBundles(resourceBundle);
        }
        for (String resource : resources) {
            resourcesRegistry.addResources(resource);
        }

        classInitializationSupport.setConfigurationSealed(true);
    }

    private static void registerReflectionClasses(FeatureImpl.DuringAnalysisAccessImpl access) {
        for (String className : xmlClasses) {
            RuntimeReflection.register(clazz(access, className));
            RuntimeReflection.register(constructor(access, className));
        }
    }
}
