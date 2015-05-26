/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.hotspot.*;
import com.oracle.jvmci.hotspot.*;

public class HotSpotSnippetReflectionProvider implements SnippetReflectionProvider {

    private final HotSpotGraalRuntimeProvider runtime;

    public HotSpotSnippetReflectionProvider(HotSpotGraalRuntimeProvider runtime) {
        this.runtime = runtime;
    }

    @Override
    public JavaConstant forObject(Object object) {
        return HotSpotObjectConstantImpl.forObject(object);
    }

    @Override
    public Object asObject(ResolvedJavaType type, JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        HotSpotObjectConstant hsConstant = (HotSpotObjectConstant) constant;
        return hsConstant.asObject(type);
    }

    @Override
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        HotSpotObjectConstant hsConstant = (HotSpotObjectConstant) constant;
        return hsConstant.asObject(type);
    }

    @Override
    public JavaConstant forBoxed(Kind kind, Object value) {
        return HotSpotObjectConstantImpl.forBoxedValue(kind, value);
    }

    public Object getSubstitutionGuardParameter(Class<?> type) {
        if (type.isInstance(runtime)) {
            return runtime;
        }
        if (type.isInstance(runtime.getConfig())) {
            return runtime.getConfig();
        }
        return null;
    }

    // Lazily initialized
    private ResolvedJavaType wordTypesType;
    private ResolvedJavaType runtimeType;
    private ResolvedJavaType configType;

    public Object getInjectedNodeIntrinsicParameter(ResolvedJavaType type) {
        // Need to test all fields since there no guarantee under the JMM
        // about the order in which these fields are written.
        if (configType == null || wordTypesType == null || configType == null) {
            MetaAccessProvider metaAccess = runtime.getHostProviders().getMetaAccess();
            wordTypesType = metaAccess.lookupJavaType(runtime.getHostProviders().getWordTypes().getClass());
            runtimeType = metaAccess.lookupJavaType(runtime.getClass());
            configType = metaAccess.lookupJavaType(runtime.getConfig().getClass());
        }

        if (type.isAssignableFrom(wordTypesType)) {
            return runtime.getHostProviders().getWordTypes();
        }
        if (type.isAssignableFrom(runtimeType)) {
            return runtime;
        }
        if (type.isAssignableFrom(configType)) {
            return runtime.getConfig();
        }
        return null;
    }
}
