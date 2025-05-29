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

import static com.oracle.svm.hosted.xml.XMLParsersRegistration.BuiltinSchemaGrammarClasses;
import static com.oracle.svm.hosted.xml.XMLParsersRegistration.DOMImplementationRegistryClasses;
import static com.oracle.svm.hosted.xml.XMLParsersRegistration.DOMParserClasses;
import static com.oracle.svm.hosted.xml.XMLParsersRegistration.DatatypeFactoryClasses;
import static com.oracle.svm.hosted.xml.XMLParsersRegistration.SAXParserClasses;
import static com.oracle.svm.hosted.xml.XMLParsersRegistration.SchemaDVFactoryClasses;
import static com.oracle.svm.hosted.xml.XMLParsersRegistration.StAXParserClasses;
import static com.oracle.svm.hosted.xml.XMLParsersRegistration.TransformerClassesAndResources;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class JavaxXmlClassAndResourcesLoaderFeature extends JNIRegistrationUtil implements InternalFeature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(new SAXParserClasses()::registerConfigs,
                        method(access, "javax.xml.parsers.SAXParserFactory", "newInstance"));

        access.registerReachabilityHandler(new DOMParserClasses()::registerConfigs,
                        method(access, "javax.xml.parsers.DocumentBuilderFactory", "newInstance"));

        access.registerReachabilityHandler(new StAXParserClasses()::registerConfigs,
                        method(access, "javax.xml.stream.FactoryFinder", "find", Class.class, String.class));

        access.registerReachabilityHandler(new TransformerClassesAndResources()::registerConfigs,
                        method(access, "javax.xml.transform.FactoryFinder", "find", Class.class, String.class));

        access.registerReachabilityHandler(new DOMImplementationRegistryClasses()::registerConfigs,
                        method(access, "org.w3c.dom.bootstrap.DOMImplementationRegistry", "newInstance"));

        access.registerReachabilityHandler(new DatatypeFactoryClasses()::registerConfigs,
                        method(access, "javax.xml.datatype.DatatypeFactory", "newInstance"));

        access.registerReachabilityHandler(new SchemaDVFactoryClasses()::registerConfigs,
                        method(access, "com.sun.org.apache.xerces.internal.impl.dv.SchemaDVFactory", "getInstance"));

        access.registerReachabilityHandler(new BuiltinSchemaGrammarClasses()::registerConfigs,
                        constructor(access, "com.sun.org.apache.xerces.internal.impl.xs.SchemaGrammar$BuiltinSchemaGrammar", int.class, short.class));

        initializeJdkCatalog();
    }

    /**
     * Initialize the {@code CatalogHolder#catalog} field. We do this eagerly (instead of e.g. in a
     * {@link FieldValueTransformer}) to work around a race condition in
     * XMLSecurityManager#prepareCatalog (JDK-8350189).
     */
    private static void initializeJdkCatalog() {
        if (ModuleLayer.boot().findModule("java.xml").isPresent()) {
            // Ensure the JdkXmlConfig$CatalogHolder#catalog field is initialized.
            Class<?> xmlSecurityManager = ReflectionUtil.lookupClass(false, "jdk.xml.internal.JdkXmlConfig$CatalogHolder");
            // The constructor call prepareCatalog which will call JdkCatalog#init.
            ReflectionUtil.readStaticField(xmlSecurityManager, "JDKCATALOG");
        }
    }
}
