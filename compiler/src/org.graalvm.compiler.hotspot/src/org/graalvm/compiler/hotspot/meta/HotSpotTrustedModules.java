/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import static org.graalvm.compiler.serviceprovider.JDK9Method.JAVA_SPECIFICATION_VERSION;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;

/**
 * Builds the result for {@link HotSpotInvocationPlugins#initTrustedModules(CompilerConfiguration)}.
 *
 * This version of the class must be used on JDK 8.
 *
 * @see "https://docs.oracle.com/javase/9/docs/specs/jar/jar.html#Multi-release"
 */
public final class HotSpotTrustedModules {

    @SuppressWarnings("unused")
    static EconomicSet<Object> build(CompilerConfiguration compilerConfiguration) {
        throw new InternalError("Cannot use API introduced in Java 9 or later on Java " + JAVA_SPECIFICATION_VERSION);
    }
}
