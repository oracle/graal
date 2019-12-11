/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.meta.SharedField;

/**
 * Static fields are represented as two arrays in the native image heap: one for Object fields and
 * one for all primitive fields. The byte-offset into these arrays is stored in
 * {@link SharedField#getLocation}.
 * <p>
 * Implementation notes: The arrays are created after static analysis, but before compilation. We
 * need to know how many static fields are reachable in order to compute the appropriate size for
 * the arrays, which is only available after static analysis.
 */
public final class StaticFieldsSupport {

    @UnknownObjectField(types = {Object[].class}) private Object[] staticObjectFields;
    @UnknownObjectField(types = {byte[].class}) private byte[] staticPrimitiveFields;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected StaticFieldsSupport() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setData(Object[] staticObjectFields, byte[] staticPrimitiveFields) {
        StaticFieldsSupport support = ImageSingletons.lookup(StaticFieldsSupport.class);
        support.staticObjectFields = staticObjectFields;
        support.staticPrimitiveFields = staticPrimitiveFields;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Object[] getStaticObjectFields() {
        Object[] result = ImageSingletons.lookup(StaticFieldsSupport.class).staticObjectFields;
        assert result != null;
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static byte[] getStaticPrimitiveFields() {
        byte[] result = ImageSingletons.lookup(StaticFieldsSupport.class).staticPrimitiveFields;
        assert result != null;
        return result;
    }
}

@AutomaticFeature
class StaticFieldsFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(StaticFieldsSupport.class, new StaticFieldsSupport());
    }
}
