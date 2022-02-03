/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.substitutions;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Base class for references to well known types and fields.
 */
public class AbstractKnownTruffleTypes {
    protected final TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
    protected final MetaAccessProvider metaAccess;

    protected AbstractKnownTruffleTypes(MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
    }

    protected ResolvedJavaType lookupType(String className) {
        return runtime.resolveType(metaAccess, className);
    }

    protected ResolvedJavaType lookupType(Class<?> c) {
        return metaAccess.lookupJavaType(c);
    }

    static class FieldsCache {
        final ResolvedJavaType declaringClass;
        final ResolvedJavaField[] instanceFields;
        final ResolvedJavaField[] staticFields;

        FieldsCache(ResolvedJavaType declaringClass, ResolvedJavaField[] instanceFields, ResolvedJavaField[] staticFields) {
            this.declaringClass = declaringClass;
            this.instanceFields = instanceFields;
            this.staticFields = staticFields;
        }
    }

    private FieldsCache fieldsCache;

    protected ResolvedJavaField findField(ResolvedJavaType declaringClass, String name) {
        FieldsCache fc = fieldsCache;
        if (fc == null || !fc.declaringClass.equals(declaringClass)) {
            fc = new FieldsCache(declaringClass, declaringClass.getInstanceFields(false), declaringClass.getStaticFields());
            fieldsCache = fc;
        }
        for (ResolvedJavaField f : fc.instanceFields) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        for (ResolvedJavaField f : fc.staticFields) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        throw new GraalError("Could not find required field %s.%s", declaringClass.getName(), name);
    }
}
