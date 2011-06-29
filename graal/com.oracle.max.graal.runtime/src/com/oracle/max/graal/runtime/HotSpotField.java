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

package com.oracle.max.graal.runtime;

import java.lang.reflect.*;

import com.oracle.max.graal.compiler.*;
import com.sun.cri.ci.CiConstant;
import com.sun.cri.ci.CiKind;
import com.sun.cri.ri.RiField;
import com.sun.cri.ri.RiType;

/**
 * Represents a field in a HotSpot type.
 */
public class HotSpotField extends CompilerObject implements RiField {

    private final RiType holder;
    private final String name;
    private final RiType type;
    private final int offset;
    private final int accessFlags;
    private CiConstant constant;

    public HotSpotField(Compiler compiler, RiType holder, String name, RiType type, int offset, int accessFlags) {
        super(compiler);
        this.holder = holder;
        this.name = name;
        this.type = type;
        this.offset = offset;
        this.accessFlags = accessFlags;
    }

    @Override
    public int accessFlags() {
        return accessFlags;
    }

    @Override
    public CiConstant constantValue(CiConstant receiver) {
        if (receiver == null) {
            if (constant == null && holder.isResolved() && holder.isSubtypeOf(compiler.getVMEntries().getType(GraalOptions.class))) {
                Field f;
                try {
                    f = GraalOptions.class.getField(name);
                } catch (SecurityException e1) {
                    return null;
                } catch (NoSuchFieldException e1) {
                    return null;
                }
                f.setAccessible(true);
                if (Modifier.isStatic(f.getModifiers())) {
                    CiKind kind = CiKind.fromJavaClass(f.getType());
                    Object value;
                    try {
                        value = f.get(null);
                    } catch (IllegalArgumentException e) {
                        return null;
                    } catch (IllegalAccessException e) {
                        return null;
                    }
                    constant = CiConstant.forBoxed(kind, value);
                }
            }

            // Constant part only valid for static fields.
            return constant;
        }
        return null;
    }

    @Override
    public RiType holder() {
        return holder;
    }

//    @Override
//    public boolean equals(Object obj) {
//        if (obj instanceof HotSpotField) {
//            HotSpotField other = (HotSpotField) obj;
//            return other.offset == offset && other.holder.equals(holder());
//        }
//        return false;
//    }

    @Override
    public boolean isResolved() {
        return holder.isResolved();
    }

    @Override
    public CiKind kind() {
        return type().kind();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public RiType type() {
        return type;
    }

    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return "HotSpotField<" + holder.name() + "." + name + ":" + offset + ">";
    }

}
