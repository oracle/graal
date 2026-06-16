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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.svm.hosted.imagelayer.AccessImageSingletonFeature.getCrossLayerSingletonMappingInfo;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.imagelayer.AccessImageSingletonFactory;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.vm.ci.meta.MetaAccessProvider;

final class ImageSingletonSlotData {

    final Class<?> key;
    final SlotRecordKind kind;
    private boolean requiresSlot;

    ImageSingletonSlotData(Class<?> key, SlotRecordKind kind) {
        this.key = key;
        this.kind = kind;
    }

    void requireSlot() {
        assert kind == SlotRecordKind.APPLICATION_LAYER_SINGLETON : kind;
        requiresSlot = true;
    }

    boolean requiresSlot() {
        return requiresSlot;
    }

    public Class<?> getAccessType() {
        return kind == SlotRecordKind.APPLICATION_LAYER_SINGLETON ? key : key.arrayType();
    }
}

abstract class ImageSingletonDataImpl implements AccessImageSingletonFactory.ImageSingletonAccessData {

    final ImageSingletonSlotData slotData;
    private boolean applicationLayerConstant;

    ImageSingletonDataImpl(ImageSingletonSlotData slotData) {
        this.slotData = slotData;
    }

    final Class<?> getKey() {
        return slotData.key;
    }

    final SlotRecordKind getKind() {
        return slotData.kind;
    }

    @Override
    public final String getKeyName() {
        return getKey().getName();
    }

    @Override
    public AccessImageSingletonFactory.SingletonAccessInfo getAccessInfo() {
        CrossLayerSingletonMappingInfo singleton = getCrossLayerSingletonMappingInfo();
        assert singleton.singletonTableStart != null;
        CGlobalDataInfo cglobal = CGlobalDataFeature.singleton().registerAsAccessedOrGet(singleton.singletonTableStart);
        int slotNum = singleton.currentKeyToSlotInfoMap.get(getKey()).slotNum();
        return new AccessImageSingletonFactory.SingletonAccessInfo(cglobal, slotNum * singleton.referenceSize);
    }

    void setApplicationLayerConstant() {
        applicationLayerConstant = true;
    }

    @Override
    public final boolean isApplicationLayerConstant() {
        return applicationLayerConstant;
    }
}

class LoadImageSingletonDataImpl extends ImageSingletonDataImpl implements AccessImageSingletonFactory.LoadImageSingletonData {

    LoadImageSingletonDataImpl(ImageSingletonSlotData slotData) {
        super(slotData);
    }

    @Override
    public Class<?> getAccessType() {
        return slotData.getAccessType();
    }

    @Override
    public boolean isApplicationLayerOnly() {
        return getKind() == SlotRecordKind.APPLICATION_LAYER_SINGLETON;
    }

    @Override
    public AccessImageSingletonFactory.SingletonAccessInfo getAccessInfo() {
        VMError.guarantee(!isApplicationLayerConstant(), "this node should instead be constant folded");
        return super.getAccessInfo();
    }

    @Override
    public ConstantNode asApplicationLayerConstant(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflectionProvider) {
        VMError.guarantee(isApplicationLayerConstant());
        return switch (getKind()) {
            case APPLICATION_LAYER_SINGLETON -> {
                Object singleton = LayeredImageSingletonSupport.singleton().lookup(getKey(), true, false);
                yield ConstantNode.forConstant(snippetReflectionProvider.forObject(singleton), metaAccess);
            }
            case MULTI_LAYERED_SINGLETON -> {
                var multiLayerArray = ImageSingletons.lookup(AccessImageSingletonFeature.class).getMultiLayerConstant(getKey(), metaAccess, snippetReflectionProvider);
                yield ConstantNode.forConstant(multiLayerArray, 1, true, metaAccess);
            }
        };
    }
}

final class ContainsImageSingletonDataImpl extends ImageSingletonDataImpl implements AccessImageSingletonFactory.ContainsImageSingletonData {

    ContainsImageSingletonDataImpl(ImageSingletonSlotData slotData) {
        super(slotData);
    }
}
