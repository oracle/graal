/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.word;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.nodes.type.MethodCountersPointerStamp;
import org.graalvm.compiler.hotspot.nodes.type.MethodPointerStamp;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Extends {@link WordTypes} with information about HotSpot metaspace pointer types.
 */
public class HotSpotWordTypes extends WordTypes {

    /**
     * Resolved type for {@link MetaspacePointer}.
     */
    private final ResolvedJavaType metaspacePointerType;
    private final Class<?> metaspacePointerClass;

    /**
     * Resolved type for {@link KlassPointer}.
     */
    private final ResolvedJavaType klassPointerType;

    /**
     * Resolved type for {@link MethodPointer}.
     */
    private final ResolvedJavaType methodPointerType;

    /**
     * Resolved type for {@link MethodCountersPointer}.
     */
    private final ResolvedJavaType methodCountersPointerType;
    private final Class<?> klassPointerClass;
    private final Class<?> methodPointerClass;
    private final Class<?> methodCountersPointerClass;

    public HotSpotWordTypes(MetaAccessProvider metaAccess, JavaKind wordKind) {
        super(metaAccess, wordKind);
        this.metaspacePointerType = metaAccess.lookupJavaType(MetaspacePointer.class);
        this.metaspacePointerClass = MetaspacePointer.class;
        this.klassPointerType = metaAccess.lookupJavaType(KlassPointer.class);
        this.klassPointerClass = KlassPointer.class;
        this.methodPointerClass = MethodPointer.class;
        this.methodPointerType = metaAccess.lookupJavaType(MethodPointer.class);
        this.methodCountersPointerType = metaAccess.lookupJavaType(MethodCountersPointer.class);
        this.methodCountersPointerClass = MethodCountersPointer.class;
    }

    @Override
    public boolean isWord(JavaType type) {
        if (type instanceof ResolvedJavaType && metaspacePointerType.isAssignableFrom((ResolvedJavaType) type)) {
            return true;
        }
        assert type == null || !type.getName().equals("Lorg/graalvm/compiler/hotspot/word/KlassPointer;") : type.getClass() + " " + klassPointerType.getClass() + " " + type + " " + klassPointerType;
        return super.isWord(type);
    }

    @Override
    public boolean isWord(Class<?> clazz) {
        if (metaspacePointerClass.isAssignableFrom(clazz)) {
            return true;
        }
        return super.isWord(clazz);
    }

    @Override
    public JavaKind asKind(JavaType type) {
        if (klassPointerType.equals(type) || methodPointerType.equals(type)) {
            return getWordKind();
        }
        return super.asKind(type);
    }

    @Override
    public Stamp getWordStamp(ResolvedJavaType type) {
        if (type.equals(klassPointerType)) {
            return KlassPointerStamp.klass();
        } else if (type.equals(methodPointerType)) {
            return MethodPointerStamp.method();
        } else if (type.equals(methodCountersPointerType)) {
            return MethodCountersPointerStamp.methodCounters();
        }
        return super.getWordStamp(type);
    }

    @Override
    public Stamp getWordStamp(Class<?> type) {
        if (type.equals(klassPointerClass)) {
            return KlassPointerStamp.klass();
        } else if (type.equals(methodPointerClass)) {
            return MethodPointerStamp.method();
        } else if (type.equals(methodCountersPointerClass)) {
            return MethodCountersPointerStamp.methodCounters();
        }
        return super.getWordStamp(type);
    }
}
