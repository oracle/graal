/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.stack.SubstrateStackIntrospection;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.stack.StackIntrospection;

public class SubstrateGraalRuntime implements GraalRuntime, RuntimeProvider {

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateGraalRuntime() {
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == RuntimeProvider.class) {
            return (T) this;
        } else if (clazz == SnippetReflectionProvider.class) {
            return (T) GraalSupport.getRuntimeConfig().getSnippetReflection();
        } else if (clazz == StackIntrospection.class) {
            return (T) SubstrateStackIntrospection.SINGLETON;
        }
        return null;
    }

    @Override
    public Backend getHostBackend() {
        return GraalSupport.getRuntimeConfig().getBackendForNormalMethod();
    }

    @Override
    public <T extends Architecture> Backend getBackend(Class<T> arch) {
        assert arch.isInstance(GraalSupport.getRuntimeConfig().getBackendForNormalMethod().getTarget().arch);
        return GraalSupport.getRuntimeConfig().getBackendForNormalMethod();
    }
}
