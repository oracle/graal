/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java.decompiler.test;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.java.decompiler.test.example.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.runtime.*;

public class Test {

    /**
     * @param args
     * @throws SecurityException
     * @throws NoSuchMethodException
     */
    public static void main(String[] args) throws NoSuchMethodException, SecurityException {
        DebugEnvironment.initialize(System.out);
        MetaAccessProvider metaAccess = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders().getMetaAccess();
        Method method = Example.class.getDeclaredMethod("loop7", new Class[]{int.class, int.class});
        final ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(method);
        TestUtil.compileMethod(javaMethod);
    }
}
