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

import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

import com.oracle.svm.configure.ResourcesRegistry;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.util.JVMCIReflectionUtil;

@AutomaticallyRegisteredFeature
public class JavaxXmlClassAndResourcesLoaderFeature extends JNIRegistrationUtil implements InternalFeature {

    private static final String XERCES_MESSAGES = "com.sun.org.apache.xerces.internal.impl.msg.";
    private static final String SERIALIZER = "com.sun.org.apache.xml.internal.serializer.";

    /**
     * The classes that load resource bundles, with the bundles each one loads. A parser formats its
     * errors with the Xerces message bundles, and the serializer of a transformer or of an
     * {@code LSSerializer} escapes characters with its entity bundles. Both can be created without
     * any factory lookup (for example with {@code DocumentBuilderFactory.newDefaultInstance()},
     * {@code TransformerFactory.newDefaultInstance()} or {@code DOMImplementationLS}).
     */
    private static final Map<String, List<String>> MESSAGE_BUNDLE_LOADERS = Map.ofEntries(
                    Map.entry("com.sun.org.apache.xerces.internal.impl.msg.XMLMessageFormatter", List.of(XERCES_MESSAGES + "XMLMessages")),
                    Map.entry("com.sun.org.apache.xerces.internal.dom.DOMMessageFormatter",
                                    List.of(XERCES_MESSAGES + "DOMMessages", XERCES_MESSAGES + "XMLSerializerMessages", XERCES_MESSAGES + "XMLMessages")),
                    Map.entry("com.sun.org.apache.xerces.internal.util.SAXMessageFormatter", List.of(XERCES_MESSAGES + "SAXMessages")),
                    Map.entry("com.sun.org.apache.xerces.internal.impl.xs.XSMessageFormatter", List.of(XERCES_MESSAGES + "XMLSchemaMessages")),
                    Map.entry("com.sun.org.apache.xerces.internal.impl.dv.DatatypeException", List.of(XERCES_MESSAGES + "XMLSchemaMessages")),
                    Map.entry("com.sun.org.apache.xerces.internal.jaxp.validation.JAXPValidationMessageFormatter", List.of(XERCES_MESSAGES + "JAXPValidationMessages")),
                    Map.entry("com.sun.org.apache.xerces.internal.util.DatatypeMessageFormatter", List.of(XERCES_MESSAGES + "DatatypeMessages")),
                    Map.entry("com.sun.org.apache.xerces.internal.xinclude.XIncludeMessageFormatter", List.of(XERCES_MESSAGES + "XIncludeMessages")),
                    Map.entry("com.sun.org.apache.xerces.internal.xpointer.XPointerMessageFormatter", List.of(XERCES_MESSAGES + "XPointerMessages")),
                    Map.entry("com.sun.org.apache.xerces.internal.impl.xpath.regex.RegexParser", List.of("com.sun.org.apache.xerces.internal.impl.xpath.regex.message")),
                    Map.entry(SERIALIZER + "ToXMLStream", List.of(SERIALIZER + "XMLEntities")),
                    Map.entry(SERIALIZER + "ToHTMLStream", List.of(SERIALIZER + "HTMLEntities")),
                    Map.entry(SERIALIZER + "Encodings", List.of(SERIALIZER + "Encodings")),
                    Map.entry(SERIALIZER + "utils.Utils", List.of(SERIALIZER + "utils.SerializerMessages")));

    /** The java.xml module is optional: without it, there are no XML classes to register. */
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JVMCIReflectionUtil.bootModuleLayer().findModule("java.xml").isPresent();
    }

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
        }
        MESSAGE_BUNDLE_LOADERS.forEach((loader, bundles) -> access.registerReachabilityHandler(_ -> registerMessageBundles(bundles), type(access, loader)));
    }

    private static void registerMessageBundles(List<String> bundles) {
        /* Registering resource bundles during analysis needs an unsealed configuration. */
        ClassInitializationSupport.singleton().withUnsealedConfiguration(() -> {
            for (String bundle : bundles) {
                ResourcesRegistry.singleton().addResourceBundles(AccessCondition.unconditional(), false, bundle);
            }
        });
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
}
