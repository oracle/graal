/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider.AfterHostedUniverse;
import com.oracle.svm.core.heap.UnknownObjectField;

/**
 * Runtime module support singleton, containing the runtime boot module layer. The boot module layer
 * is synthesized by a feature during native image generation, after analysis (as module layer
 * synthesizing requires analysis information). For convenience, this singleton also contains
 * hosted-only hosted-to-runtime module mappers used by other parts of the module system during the
 * image build. These are important, as every hosted module has its own synthesized runtime
 * counterpart. The lookup function is implemented inside the module layer synthesis feature. See
 * {@code ModuleLayerFeature} for more information.
 */
public final class RuntimeModuleSupport {

    public static RuntimeModuleSupport instance() {
        return ImageSingletons.lookup(RuntimeModuleSupport.class);
    }

    @UnknownObjectField(availability = AfterHostedUniverse.class) //
    private ModuleLayer bootLayer;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private Function<Module, Module> hostedToRuntimeModuleMapper;

    @Platforms(Platform.HOSTED_ONLY.class) //
    public void setBootLayer(ModuleLayer bootLayer) {
        this.bootLayer = bootLayer;
    }

    public ModuleLayer getBootLayer() {
        return bootLayer;
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public void setHostedToRuntimeModuleMapper(Function<Module, Module> hostedToRuntimeModuleMapper) {
        this.hostedToRuntimeModuleMapper = hostedToRuntimeModuleMapper;
    }

    public Module getRuntimeModuleForHostedModule(Module hostedModule) {
        return hostedToRuntimeModuleMapper.apply(hostedModule);
    }

}
