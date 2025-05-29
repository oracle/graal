/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.js;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.webimage.api.JSObject;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.Digest;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Dynamically generates methods for accesses to {@link JSObject} fields.
 *
 * @see JSObjectAccessMethod
 */
@AutomaticallyRegisteredImageSingleton
public class JSObjectAccessMethodSupport {
    public static JSObjectAccessMethodSupport singleton() {
        return ImageSingletons.lookup(JSObjectAccessMethodSupport.class);
    }

    record AccessorDescription(AnalysisField field, boolean isLoad) {
    }

    private final Map<AccessorDescription, JSObjectAccessMethod> accessMethods = new ConcurrentHashMap<>();

    public AnalysisMethod lookupLoadMethod(AnalysisMetaAccess aMetaAccess, AnalysisField field) {
        return lookup(aMetaAccess, field, true);
    }

    public AnalysisMethod lookupStoreMethod(AnalysisMetaAccess aMetaAccess, AnalysisField field) {
        return lookup(aMetaAccess, field, false);
    }

    private AnalysisMethod lookup(AnalysisMetaAccess aMetaAccess, AnalysisField field, boolean isLoad) {
        GraalError.guarantee(JSObject.class.isAssignableFrom(field.getDeclaringClass().getJavaClass()), "Field must be in JSObject class: %s", field);
        GraalError.guarantee(!field.isStatic(), "Field must not be static: %s", field);
        UserError.guarantee(!field.isFinal(), "Instance fields in subclasses of %s must not be final: %s", JSObject.class.getSimpleName(), field.format("%H.%n"));
        UserError.guarantee(field.isPublic() || field.isProtected(), "Only public and protected instance fields in subclasses of %s are allowed: %s", JSObject.class.getSimpleName(),
                        field.format("%H.%n"));
        JSObjectAccessMethod accessMethod = accessMethods.computeIfAbsent(
                        new AccessorDescription(field, isLoad),
                        key -> createAccessMethod(aMetaAccess, field, isLoad));

        return aMetaAccess.getUniverse().lookup(accessMethod);
    }

    private static JSObjectAccessMethod createAccessMethod(AnalysisMetaAccess metaAccess, AnalysisField field, boolean isLoad) {
        ResolvedJavaType unwrappedFieldDeclaringClass = field.getDeclaringClass().getWrapped();
        ResolvedJavaType unwrappedFieldType = field.getType().getWrapped();

        Signature signature;
        if (isLoad) {
            signature = ResolvedSignature.fromArray(new ResolvedJavaType[]{unwrappedFieldDeclaringClass}, unwrappedFieldType);
        } else {
            signature = ResolvedSignature.fromArray(new ResolvedJavaType[]{unwrappedFieldDeclaringClass, unwrappedFieldType}, metaAccess.lookupJavaType(void.class).getWrapped());
        }

        // Just use some constant pool. Should not actually be meaningfully accessed
        ConstantPool pool = unwrappedFieldDeclaringClass.getDeclaredConstructors(false)[0].getConstantPool();

        String name = accessMethodName(field, isLoad);
        ResolvedJavaType unwrappedDeclaringClass = metaAccess.lookupJavaType(JSObjectAccessMethodHolder.class).getWrapped();
        return new JSObjectAccessMethod(name, field, isLoad, unwrappedDeclaringClass, signature, pool);
    }

    /**
     * The method the {@link JSObjectAccessMethod} gets. The only requirement here is that all
     * method names are unique.
     */
    private static String accessMethodName(AnalysisField field, boolean isLoad) {
        String sb = field.getDeclaringClass().toClassName() + "." + field.getName();
        return (isLoad ? "get" : "set") + "_" + field.getDeclaringClass().getUnqualifiedName() + "_" + field.getName() + "_" + Digest.digest(sb);
    }
}
