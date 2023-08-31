/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.redefinition.plugins.jdkcaches.JDKCacheRedefinitionPlugin;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions
public final class Target_java_beans_ThreadGroupContext {

    @Substitution(hasReceiver = true, methodName = "<init>")
    abstract static class Init extends SubstitutionNode {

        abstract void execute(@JavaType(Class.class) StaticObject context);

        @Specialization
        void doCached(
                        @JavaType(Class.class) StaticObject context,
                        @Bind("getContext()") EspressoContext espressoContext,
                        @Cached("create(espressoContext.getMeta().java_beans_ThreadGroupContext_init.getCallTargetNoSubstitution())") DirectCallNode original) {
            // for class redefinition we need to collect details about beans
            JDKCacheRedefinitionPlugin plugin = espressoContext.lookup(JDKCacheRedefinitionPlugin.class);
            if (plugin != null) {
                plugin.registerThreadGroupContext(context);
            }
            // call original method
            original.call(context);
        }
    }
}
