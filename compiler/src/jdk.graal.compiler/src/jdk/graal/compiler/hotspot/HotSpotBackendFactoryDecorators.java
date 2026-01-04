/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Decorators for the providers created by a backend factory.
 */
public interface HotSpotBackendFactoryDecorators {
    /**
     * Decorates the given MetaAccessProvider.
     *
     * @param metaAccess the MetaAccessProvider to decorate
     * @return the decorated MetaAccessProvider
     */
    default MetaAccessProvider decorateMetaAccessProvider(MetaAccessProvider metaAccess) {
        return metaAccess;
    }

    /**
     * Decorates the given constant reflection provider.
     *
     * @param constantReflection the constant reflection provider to decorate
     * @return the decorated constant reflection provider
     */
    default HotSpotConstantReflectionProvider decorateConstantReflectionProvider(HotSpotConstantReflectionProvider constantReflection) {
        return constantReflection;
    }

    /**
     * Decorates the given code cache provider.
     *
     * @param codeCacheProvider the code cache provider to decorate
     * @return the decorated code cache provider
     */
    default HotSpotCodeCacheProvider decorateCodeCacheProvider(HotSpotCodeCacheProvider codeCacheProvider) {
        return codeCacheProvider;
    }

    /**
     * Decorates the given foreign calls provider.
     *
     * @param foreignCalls the foreign calls provider to decorate
     * @return the decorated foreign calls provider
     */
    default HotSpotHostForeignCallsProvider decorateForeignCallsProvider(HotSpotHostForeignCallsProvider foreignCalls) {
        return foreignCalls;
    }

    /**
     * Called after JVMCI providers have been created.
     */
    default void afterJVMCIProvidersCreated() {
    }
}
