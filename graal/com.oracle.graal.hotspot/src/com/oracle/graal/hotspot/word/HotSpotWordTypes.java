/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.word;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.word.*;

/**
 * Extends {@link WordTypes} with information about HotSpot metaspace pointer types.
 */
public class HotSpotWordTypes extends WordTypes {

    /**
     * Resolved type for {@link MetaspacePointer}.
     */
    private final ResolvedJavaType metaspacePointerType;

    /**
     * Resolved type for {@link KlassPointer}.
     */
    private final ResolvedJavaType klassPointerType;

    /**
     * Resolved type for {@link MethodPointer}.
     */
    private final ResolvedJavaType methodPointerType;

    private final Stamp klassPointerStamp;

    private final Stamp methodPointerStamp;

    public HotSpotWordTypes(MetaAccessProvider metaAccess, Kind wordKind, Stamp klassPointerStamp, Stamp methodPointerStamp) {
        super(metaAccess, wordKind);
        this.metaspacePointerType = metaAccess.lookupJavaType(MetaspacePointer.class);
        this.klassPointerType = metaAccess.lookupJavaType(KlassPointer.class);
        this.methodPointerType = metaAccess.lookupJavaType(MethodPointer.class);
        this.klassPointerStamp = klassPointerStamp;
        this.methodPointerStamp = methodPointerStamp;
    }

    @Override
    public boolean isWord(ResolvedJavaType type) {
        if (type != null && metaspacePointerType.isAssignableFrom(type)) {
            return true;
        }
        return super.isWord(type);
    }

    @Override
    public Kind asKind(JavaType type) {
        if (klassPointerType.equals(type) || methodPointerType.equals(type)) {
            return getWordKind();
        }
        return super.asKind(type);
    }

    @Override
    public Stamp getWordStamp(ResolvedJavaType type) {
        if (type.equals(klassPointerType)) {
            return klassPointerStamp;
        } else if (type.equals(methodPointerType)) {
            return methodPointerStamp;
        }
        return super.getWordStamp(type);
    }

    public Stamp getKlassPointerStamp() {
        return klassPointerStamp;
    }

    public Stamp getMethodPointerStamp() {
        return methodPointerStamp;
    }
}
