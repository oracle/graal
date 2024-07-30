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
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDKLatest;
import com.oracle.svm.util.ReflectionUtil;

/**
 * Substitution to initialize {@link #catalog} at build time.
 *
 * JDK-8306055 introduced a built-in Catalog to JDK XML module in JDK 22. Without special treatment,
 * the initialization code would pull intermediate types (e.g. {@code CatalogReader}) into the image
 * heap. To avoid this, we initialize the catalog at build time and substitute the {@link #init}
 * method to be empty.
 *
 * Ideally, we would initialize all of {@code jdk.xml} at run time, but that is too intrusive at the
 * current point in time (GR-50683).
 */
@TargetClass(className = "jdk.xml.internal.JdkCatalog", onlyWith = JDKLatest.class)
public final class Target_jdk_xml_internal_JdkCatalog {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = JdkCatalogSupplier.class, isFinal = true) //
    public static Target_javax_xml_catalog_Catalog catalog;

    @Substitute
    @SuppressWarnings("unused")
    public static void init(String resolve) {
        // initialized at build time
    }
}

@TargetClass(className = "javax.xml.catalog.Catalog", onlyWith = JDKLatest.class)
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
    @Override
    public Object transform(Object receiver, Object originalValue) {
        // Ensure the field is initialized.
        Class<?> xmlSecurityManager = ReflectionUtil.lookupClass(false, "jdk.xml.internal.XMLSecurityManager");
        // The constructor call prepareCatalog which will call JdkCatalog#init.
        ReflectionUtil.newInstance(xmlSecurityManager);

        Class<?> jdkCatalogClass = ReflectionUtil.lookupClass(false, "jdk.xml.internal.JdkCatalog");
        Object catalog = ReflectionUtil.readStaticField(jdkCatalogClass, "catalog");

        return Objects.requireNonNull(catalog);
    }
}
