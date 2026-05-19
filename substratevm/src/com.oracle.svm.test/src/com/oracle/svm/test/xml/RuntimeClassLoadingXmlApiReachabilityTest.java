/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.xml;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.transform.TransformerFactory;

import org.graalvm.nativeimage.hosted.Feature;
import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "--add-modules=java.xml",
                "-H:+UnlockExperimentalVMOptions",
                "-H:+AllowJRTFileSystem",
                "-H:+RuntimeClassLoading",
                "--initialize-at-run-time=jdk.internal.loader.ClassLoaders",
                "--features=com.oracle.svm.test.xml.RuntimeClassLoadingXmlApiReachabilityTest$TestFeature"
})
public class RuntimeClassLoadingXmlApiReachabilityTest {
    private static final String XML_CATALOG_HOLDER_CLASS = "jdk.xml.internal.JdkXmlConfig$CatalogHolder";
    private static final String[] XML_IMPLEMENTATION_CLASSES = {
                    "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl",
                    "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl",
                    "com.sun.xml.internal.stream.events.XMLEventFactoryImpl",
                    "com.sun.xml.internal.stream.XMLInputFactoryImpl",
                    "com.sun.xml.internal.stream.XMLOutputFactoryImpl",
                    "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl",
                    "com.sun.org.apache.xerces.internal.dom.DOMXSImplementationSourceImpl",
                    "com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl",
                    "com.sun.org.apache.xerces.internal.impl.dv.xs.SchemaDVFactoryImpl",
                    "com.sun.org.apache.xerces.internal.impl.dv.xs.ExtendedSchemaDVFactoryImpl"
    };

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            Class<?> catalogHolder = access.findClassByName(XML_CATALOG_HOLDER_CLASS);
            if (catalogHolder == null) {
                throw new AssertionError(XML_CATALOG_HOLDER_CLASS + " is not available to the image build");
            }
            access.registerReachabilityHandler(_ -> {
                throw new AssertionError(XML_CATALOG_HOLDER_CLASS + " was made reachable by XML API reachability");
            }, catalogHolder);

            for (String className : XML_IMPLEMENTATION_CLASSES) {
                Class<?> implementationClass = access.findClassByName(className);
                if (implementationClass == null) {
                    throw new AssertionError(className + " is not available to the image build");
                }
                access.registerReachabilityHandler(_ -> {
                    throw new AssertionError(className + " was made reachable by XML API reachability");
                }, implementationClass);
            }
        }
    }

    @Test
    public void testXmlFactoryApiReachabilityDoesNotRegisterImplementationMetadataOrInitializeCatalog() throws Exception {
        if (System.nanoTime() == Long.MIN_VALUE) {
            SAXParserFactory.newInstance();
            DocumentBuilderFactory.newInstance();
            XMLEventFactory.newInstance();
            XMLInputFactory.newInstance();
            XMLOutputFactory.newInstance();
            TransformerFactory.newInstance();
            DatatypeFactory.newInstance();
        }
    }
}
