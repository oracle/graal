/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

import java.util.Objects;

import jdk.graal.compiler.hotspot.HotSpotBackendFactoryDecorators;
import jdk.graal.compiler.hotspot.HotSpotSnippetMetaAccessProvider;
import jdk.graal.compiler.hotspot.SnippetObjectConstant;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A backend factory that decorates the providers during recording and replay.
 */
class HotSpotProxyBackendFactory implements HotSpotBackendFactoryDecorators {
    private final CompilationProxies proxies;

    private final ReplayCompilationSupport replayCompilationSupport;

    private final HotSpotJVMCIRuntime jvmciRuntime;

    HotSpotProxyBackendFactory(CompilationProxies proxies, ReplayCompilationSupport replayCompilationSupport, HotSpotJVMCIRuntime jvmciRuntime) {
        this.proxies = proxies;
        this.replayCompilationSupport = replayCompilationSupport;
        this.jvmciRuntime = jvmciRuntime;
    }

    @Override
    public void afterJVMCIProvidersCreated() {
        /*
         * We need the key JVMCI providers (such as meta access) available to be able to find local
         * mirrors. We must identify the local mirrors before initialization continues to avoid
         * creating duplicate proxies for equivalent local mirrors.
         */
        replayCompilationSupport.findLocalMirrors(jvmciRuntime);
    }

    @Override
    public MetaAccessProvider decorateMetaAccessProvider(MetaAccessProvider metaAccess) {
        // Do not record snippet types in libgraal - decorate the JVMCI meta access only.
        return new HotSpotSnippetMetaAccessProvider((MetaAccessProvider) proxies.proxify(metaAccess));
    }

    @Override
    public HotSpotConstantReflectionProvider decorateConstantReflectionProvider(HotSpotConstantReflectionProvider constantReflection) {
        // Only the operations invoked on the delegate are recorded and replayed.
        HotSpotConstantReflectionProvider delegate = (HotSpotConstantReflectionProvider) proxies.proxify(constantReflection);
        return new DecoratedConstantReflectionProvider(delegate);
    }

    private static final class DecoratedConstantReflectionProvider extends HotSpotConstantReflectionProvider {
        private final HotSpotConstantReflectionProvider delegate;

        private DecoratedConstantReflectionProvider(HotSpotConstantReflectionProvider delegate) {
            super(null);
            this.delegate = delegate;
        }

        @Override
        public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
            return delegate.readFieldValue(field, receiver);
        }

        @Override
        public JavaConstant forObject(Object value) {
            return delegate.forObject(value);
        }

        @Override
        public Boolean constantEquals(Constant x, Constant y) {
            if (x instanceof SnippetObjectConstant || y instanceof SnippetObjectConstant) {
                /*
                 * Equality checks for snippet constants are not recorded because snippet constants
                 * are not serializable.
                 */
                return Objects.equals(x, y);
            } else {
                return delegate.constantEquals(x, y);
            }
        }

        @Override
        public Integer readArrayLength(JavaConstant array) {
            return delegate.readArrayLength(array);
        }

        @Override
        public JavaConstant readArrayElement(JavaConstant array, int index) {
            return delegate.readArrayElement(array, index);
        }

        @Override
        public JavaConstant boxPrimitive(JavaConstant source) {
            return delegate.boxPrimitive(source);
        }

        @Override
        public JavaConstant unboxPrimitive(JavaConstant source) {
            return delegate.unboxPrimitive(source);
        }

        @Override
        public JavaConstant forString(String value) {
            return delegate.forString(value);
        }

        @Override
        public ResolvedJavaType asJavaType(Constant constant) {
            if (constant instanceof SnippetObjectConstant objectConstant) {
                /*
                 * Avoid recording an operation with the snippet constant, which is not
                 * serializable.
                 */
                return objectConstant.asObject(ResolvedJavaType.class);
            } else {
                return delegate.asJavaType(constant);
            }
        }

        @Override
        public MethodHandleAccessProvider getMethodHandleAccess() {
            return delegate.getMethodHandleAccess();
        }

        @Override
        public MemoryAccessProvider getMemoryAccessProvider() {
            return delegate.getMemoryAccessProvider();
        }

        @Override
        public JavaConstant asJavaClass(ResolvedJavaType type) {
            return delegate.asJavaClass(type);
        }

        @Override
        public Constant asObjectHub(ResolvedJavaType type) {
            return delegate.asObjectHub(type);
        }
    }

    @Override
    public HotSpotCodeCacheProvider decorateCodeCacheProvider(HotSpotCodeCacheProvider codeCacheProvider) {
        return (HotSpotCodeCacheProvider) proxies.proxify(codeCacheProvider);
    }

    @Override
    public HotSpotHostForeignCallsProvider decorateForeignCallsProvider(HotSpotHostForeignCallsProvider foreignCalls) {
        replayCompilationSupport.setForeignCallsProvider(foreignCalls);
        return foreignCalls;
    }
}
