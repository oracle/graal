/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaTypeProfile.ProfiledType;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;

/**
 * Base class for checkcast and instanceof test classes.
 */
public abstract class TypeCheckTest extends GraalCompilerTest {

    protected abstract void replaceProfile(StructuredGraph graph, JavaTypeProfile profile);

    protected JavaTypeProfile currentProfile;

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod method, final StructuredGraph graph) {
        boolean forceCompile = false;
        if (currentProfile != null) {
            replaceProfile(graph, currentProfile);
            forceCompile = true;
        }
        return super.getCode(method, graph, forceCompile);
    }

    protected JavaTypeProfile profile(Class<?>... types) {
        return profile(TriState.FALSE, types);
    }

    protected JavaTypeProfile profile(TriState nullSeen, Class<?>... types) {
        if (types.length == 0) {
            return null;
        }
        ProfiledType[] ptypes = new ProfiledType[types.length];
        for (int i = 0; i < types.length; i++) {
            ptypes[i] = new ProfiledType(getMetaAccess().lookupJavaType(types[i]), 1.0D / types.length);
        }
        return new JavaTypeProfile(nullSeen, 0.0D, ptypes);
    }

    protected void test(String name, JavaTypeProfile profile, Object... args) {
        assert currentProfile == null;
        currentProfile = profile;
        try {
            super.test(name, args);
        } finally {
            currentProfile = null;
        }
    }
}
