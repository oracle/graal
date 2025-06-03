/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.configure.ResourcesRegistry;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

public abstract class XMLParsersRegistration extends JNIRegistrationUtil {

    public void registerConfigs(Feature.DuringAnalysisAccess a) {
        FeatureImpl.DuringAnalysisAccessImpl access = (FeatureImpl.DuringAnalysisAccessImpl) a;
        List<String> parserClasses = xmlParserClasses();
        registerReflectionClasses(access, parserClasses);
        registerResources();
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

    static class SchemaDVFactoryClasses extends XMLParsersRegistration {

        @Override
        List<String> xmlParserClasses() {
            return Collections.singletonList("com.sun.org.apache.xerces.internal.impl.dv.xs.SchemaDVFactoryImpl");
        }
    }

    static class BuiltinSchemaGrammarClasses extends XMLParsersRegistration {

        @Override
        List<String> xmlParserClasses() {
            return Collections.singletonList("com.sun.org.apache.xerces.internal.impl.dv.xs.ExtendedSchemaDVFactoryImpl");
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
        @SuppressWarnings("try")
        void registerResources() {
            /*
             * To allow register new resource bundle classes during analysis phase
             */
            ClassInitializationSupport classInitializationSupport = (ClassInitializationSupport) ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
            classInitializationSupport.withUnsealedConfiguration(() -> {
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xml.internal.serializer.utils.SerializerMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xalan.internal.xsltc.compiler.util.ErrorMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xml.internal.serializer.Encodings");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xml.internal.serializer.HTMLEntities");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xml.internal.serializer.XMLEntities");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.xpath.regex.message");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.msg.DOMMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.msg.DatatypeMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.msg.JAXPValidationMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.msg.SAXMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.msg.XIncludeMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.msg.XMLMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.msg.XMLSchemaMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.msg.XMLSerializerMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xerces.internal.impl.msg.XPointerMessages");
                ResourcesRegistry.singleton().addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.org.apache.xalan.internal.res.XSLTInfo");
            });
        }

        @Override
        List<String> xmlParserClasses() {
            return Collections.singletonList("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
        }
    }
}
