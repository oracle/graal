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
import static com.oracle.svm.hosted.xml.XMLParsersRegistration.XMLCryptoTransformServiceClassesAndResources;

import java.io.IOException;
import java.lang.module.ModuleReader;
import java.lang.module.ResolvedModule;
import java.util.Optional;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.JVMCIReflectionUtil;

@AutomaticallyRegisteredFeature
public class JavaxXmlClassAndResourcesLoaderFeature extends JNIRegistrationUtil implements InternalFeature {
    private static final String JDK_CATALOG_RESOURCE_PREFIX = "jdk/xml/internal/jdkcatalog/";

    private static boolean jdkCatalogResourcesRegistered;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (RuntimeClassLoading.isSupported()) {
            /*
             * With runtime class loading, XML implementation classes can be loaded dynamically from
             * the runtime JDK. Do not make them AOT-reachable just because a public XML factory API
             * is reachable. If an XML implementation class becomes AOT-reachable independently, add
             * the same reflection metadata and resources that ordinary Native Image builds use.
             */
            registerImplementationClassReachabilityHandlers(access);
        } else {
            registerApiReachabilityHandlers(access);
            initializeJdkCatalog();
        }
    }

    private static void registerApiReachabilityHandlers(BeforeAnalysisAccess access) {
        access.registerReachabilityHandler(new SAXParserClasses()::registerConfigs,
                        method(access, "javax.xml.parsers.SAXParserFactory", "newInstance"));

        access.registerReachabilityHandler(new DOMParserClasses()::registerConfigs,
                        method(access, "javax.xml.parsers.DocumentBuilderFactory", "newInstance"));

        access.registerReachabilityHandler(new StAXParserClasses()::registerConfigs,
                        method(access, "javax.xml.stream.FactoryFinder", "find", Class.class, String.class));

        access.registerReachabilityHandler(new TransformerClassesAndResources()::registerConfigs,
                        method(access, "javax.xml.transform.FactoryFinder", "find", Class.class, String.class));

        // TransformService of java.xml.crypto module
        optionalMethod(access, "com.sun.org.apache.xml.internal.security.utils.I18n", "init", String.class, String.class)
                        .ifPresent(method -> access.registerReachabilityHandler(new XMLCryptoTransformServiceClassesAndResources()::registerConfigs, method));

        access.registerReachabilityHandler(new DOMImplementationRegistryClasses()::registerConfigs,
                        method(access, "org.w3c.dom.bootstrap.DOMImplementationRegistry", "newInstance"));

        access.registerReachabilityHandler(new DatatypeFactoryClasses()::registerConfigs,
                        method(access, "javax.xml.datatype.DatatypeFactory", "newInstance"));

        access.registerReachabilityHandler(new SchemaDVFactoryClasses()::registerConfigs,
                        method(access, "com.sun.org.apache.xerces.internal.impl.dv.SchemaDVFactory", "getInstance"));

        access.registerReachabilityHandler(new BuiltinSchemaGrammarClasses()::registerConfigs,
                        constructor(access, "com.sun.org.apache.xerces.internal.impl.xs.SchemaGrammar$BuiltinSchemaGrammar", int.class, short.class));
    }

    private static void registerImplementationClassReachabilityHandlers(BeforeAnalysisAccess access) {
        registerOnImplementationClassReachable(access, new SAXParserClasses());
        registerOnImplementationClassReachable(access, new DOMParserClasses());
        registerOnImplementationClassReachable(access, new StAXParserClasses());
        registerOnImplementationClassReachable(access, new TransformerClassesAndResources());
        registerOnImplementationClassReachable(access, new DOMImplementationRegistryClasses());
        registerOnImplementationClassReachable(access, new DatatypeFactoryClasses());
        registerOnImplementationClassReachable(access, new SchemaDVFactoryClasses());
        registerOnImplementationClassReachable(access, new BuiltinSchemaGrammarClasses());

        // TransformService of java.xml.crypto module
        optionalMethod(access, "com.sun.org.apache.xml.internal.security.utils.I18n", "init", String.class, String.class)
                        .ifPresent(method -> access.registerReachabilityHandler(new XMLCryptoTransformServiceClassesAndResources()::registerConfigs, method));
    }

    private static void registerOnImplementationClassReachable(BeforeAnalysisAccess access, XMLParsersRegistration registration) {
        for (String className : registration.xmlParserClasses()) {
            access.registerReachabilityHandler(a -> registration.registerConfig(a, className), type(access, className));
        }
    }

    static synchronized void registerJdkCatalogResources() {
        if (jdkCatalogResourcesRegistered) {
            return;
        }

        var javaXmlResolvedModule = JVMCIReflectionUtil.bootModuleLayer().findModule("java.xml");
        if (javaXmlResolvedModule.isEmpty()) {
            jdkCatalogResourcesRegistered = true;
            return;
        }

        String javaXmlModuleName = javaXmlResolvedModule.get().getName();
        Module javaXmlModule = ModuleLayer.boot().findModule(javaXmlModuleName).orElseThrow(() -> VMError.shouldNotReachHere("Could not resolve hosted java.xml module"));
        Optional<ResolvedModule> resolvedModule = ModuleLayer.boot().configuration().findModule(javaXmlModuleName);
        VMError.guarantee(resolvedModule.isPresent());

        try (ModuleReader reader = resolvedModule.get().reference().open()) {
            reader.list()
                            .filter(entry -> entry.startsWith(JDK_CATALOG_RESOURCE_PREFIX))
                            .forEach(entry -> RuntimeResourceAccess.addResource(javaXmlModule, entry));
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }

        jdkCatalogResourcesRegistered = true;
    }

    /**
     * Initialize the {@code CatalogHolder#catalog} field. We do this eagerly (instead of e.g. in a
     * {@link FieldValueTransformer}) to work around a race condition in
     * XMLSecurityManager#prepareCatalog (JDK-8350189).
     */
    private static void initializeJdkCatalog() {
        if (JVMCIReflectionUtil.bootModuleLayer().findModule("java.xml").isPresent()) {
            registerJdkCatalogResources();
            // Ensure the JdkXmlConfig$CatalogHolder#catalog field is initialized.
            Class<?> xmlSecurityManager = ReflectionUtil.lookupClass(false, "jdk.xml.internal.JdkXmlConfig$CatalogHolder");
            // The constructor call prepareCatalog which will call JdkCatalog#init.
            ReflectionUtil.readStaticField(xmlSecurityManager, "JDKCATALOG");
        }
    }
}
