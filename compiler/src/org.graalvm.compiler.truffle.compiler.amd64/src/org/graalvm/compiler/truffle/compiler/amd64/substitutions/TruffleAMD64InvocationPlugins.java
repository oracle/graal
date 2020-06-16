/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.amd64.substitutions;

import static org.graalvm.compiler.truffle.common.TruffleCompilerRuntime.getRuntime;

import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

@ServiceProvider(TruffleInvocationPluginProvider.class)
public class TruffleAMD64InvocationPlugins implements TruffleInvocationPluginProvider {

    @Override
    public void registerInvocationPlugins(Providers providers, Architecture architecture, InvocationPlugins plugins, boolean canDelayIntrinsification) {
        if (architecture instanceof AMD64) {
            registerArrayUtilsPlugins(plugins, providers.getMetaAccess(), providers.getReplacements());
        }
    }

    private static void registerArrayUtilsPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, Replacements replacements) {
        final ResolvedJavaType arrayUtilsType = getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.ArrayUtils");
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, new InvocationPlugins.ResolvedJavaSymbol(arrayUtilsType), replacements);
        r.registerMethodSubstitution(AMD64ArrayUtilsSubstitutions.class, "runIndexOf", String.class, int.class, int.class, char[].class);
        r.registerMethodSubstitution(AMD64ArrayUtilsSubstitutions.class, "runIndexOf", char[].class, int.class, int.class, char[].class);
        r.registerMethodSubstitution(AMD64ArrayUtilsSubstitutions.class, "runIndexOf", byte[].class, int.class, int.class, byte[].class);
        r.registerMethodSubstitution(AMD64ArrayUtilsSubstitutions.class, "runRegionEquals", byte[].class, int.class, byte[].class, int.class, int.class);
        r.registerMethodSubstitution(AMD64ArrayUtilsSubstitutions.class, "runRegionEquals", char[].class, int.class, char[].class, int.class, int.class);
        r.registerMethodSubstitution(AMD64ArrayUtilsSubstitutions.class, "runRegionEquals", String.class, int.class, String.class, int.class, int.class);
    }
}
