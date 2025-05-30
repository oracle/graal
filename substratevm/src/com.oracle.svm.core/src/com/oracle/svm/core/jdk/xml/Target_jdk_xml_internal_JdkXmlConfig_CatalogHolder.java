/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.xml;

import java.util.Objects;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substitution to initialize {@link #JDKCATALOG} at build time.
 *
 * JDK-8306055 introduced a built-in Catalog to JDK XML module in JDK 22. Without special treatment,
 * the initialization code would pull intermediate types (e.g. {@code CatalogReader}) into the image
 * heap. To avoid this, we initialize the catalog at build time.
 *
 * Ideally, we would initialize all of {@code jdk.xml} at run time, but that is too intrusive at the
 * current point in time (GR-50683).
 */
@TargetClass(className = "jdk.xml.internal.JdkXmlConfig$CatalogHolder")
public final class Target_jdk_xml_internal_JdkXmlConfig_CatalogHolder {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = JdkCatalogSupplier.class, isFinal = true) //
    public static Target_javax_xml_catalog_Catalog JDKCATALOG;
}

@TargetClass(className = "javax.xml.catalog.Catalog")
final class Target_javax_xml_catalog_Catalog {
}

@TargetClass(className = "javax.xml.catalog.CatalogImpl")
final class Target_javax_xml_catalog_CatalogImpl {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Target_javax_xml_parsers_SAXParser parser;
}

@TargetClass(className = "javax.xml.parsers.SAXParser")
final class Target_javax_xml_parsers_SAXParser {
}

final class JdkCatalogSupplier implements FieldValueTransformer {

    /**
     * Verifies that {@link Target_jdk_xml_internal_JdkXmlConfig_CatalogHolder#JDKCATALOG} is
     * non-null. The initialization is triggered in
     * {@code com.oracle.svm.hosted.xml.JavaxXmlClassAndResourcesLoaderFeature#initializeJdkCatalog()}
     */
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return Objects.requireNonNull(originalValue, "JdkCatalog initialization failed");
    }
}
