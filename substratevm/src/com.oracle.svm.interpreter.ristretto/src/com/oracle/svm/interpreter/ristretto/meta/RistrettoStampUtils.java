/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import com.oracle.svm.core.graal.nodes.SubstrateNarrowOopStamp;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.type.NarrowOopStamp;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Ristretto runtime compilation needs to see only Ristretto JVMCI types. These helpers rewrite
 * {@link SubstrateType} stamps to Ristretto ones and leave only metadata stamps unchanged.
 */
public final class RistrettoStampUtils {
    public static Stamp normalizeStamp(Stamp stamp) {
        if (stamp instanceof AbstractObjectStamp objectStamp) {
            ResolvedJavaType type = objectStamp.type();
            if (type instanceof SubstrateType substrateType && !(type instanceof RistrettoType) && !isHubStampType(type)) {
                ObjectStamp normalized = new ObjectStamp(RistrettoUtils.toRType(substrateType), objectStamp.isExactType(), objectStamp.nonNull(), objectStamp.alwaysNull(),
                                objectStamp.isAlwaysArray());
                if (objectStamp instanceof NarrowOopStamp narrowOopStamp) {
                    return SubstrateNarrowOopStamp.compressed(normalized, narrowOopStamp.getEncoding());
                }
                return normalized;
            }
        }
        return stamp;
    }

    public static boolean isValidRistrettoObjectStampType(ResolvedJavaType type) {
        return type == null || type instanceof RistrettoType || isHubStampType(type);
    }

    /**
     * Hub stamps are shared VM metadata used to represent class mirrors and hub loads, not
     * runtime-loaded Java payload types, so the Ristretto verifier allows them unchanged.
     */
    private static boolean isHubStampType(ResolvedJavaType type) {
        return isTypeName(type, "com.oracle.svm.core.hub.DynamicHub") || isTypeName(type, Class.class.getName());
    }

    private static boolean isTypeName(ResolvedJavaType type, String className) {
        return type.toJavaName().equals(className) || type.toJavaName(true).equals(className) || type.toClassName().equals(className) || type.toString().equals("SubstrateType<" + className + ">");
    }
}
