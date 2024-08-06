/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.type;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.LIRKindTool;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

public class ObjectStamp extends AbstractObjectStamp {

    public ObjectStamp(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull, boolean alwaysArray) {
        super(type, exactType, nonNull, alwaysNull, alwaysArray);
    }

    @Override
    protected ObjectStamp copyWith(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull, boolean alwaysArray) {
        return new ObjectStamp(type, exactType, nonNull, alwaysNull, alwaysArray);
    }

    @Override
    public Stamp unrestricted() {
        return StampFactory.object();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append('a');
        appendString(str);
        return str.toString();
    }

    @Override
    public boolean isCompatible(Stamp other) {
        if (this == other) {
            return true;
        }
        if (other instanceof ObjectStamp) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        if (constant instanceof JavaConstant) {
            return ((JavaConstant) constant).getJavaKind().isObject();
        }
        return false;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getObjectKind();
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        try {
            return provider.readObjectConstant(base, displacement);
        } catch (IllegalArgumentException e) {
            /*
             * It's possible that the base and displacement aren't valid together so simply return
             * null.
             */
            return null;
        }
    }

    /**
     * Convert an ObjectStamp into a representation that can be resolved symbolically into the
     * original stamp.
     */
    @Override
    public SymbolicJVMCIReference<ObjectStamp> makeSymbolic() {
        if (type() == null) {
            return null;
        }
        return new SymbolicObjectStamp(this);
    }

    static class SymbolicObjectStamp implements SymbolicJVMCIReference<ObjectStamp> {
        UnresolvedJavaType type;
        private final boolean exactType;
        private final boolean nonNull;
        private final boolean alwaysNull;
        private final boolean alwaysArray;

        SymbolicObjectStamp(ObjectStamp stamp) {
            if (stamp.type() != null) {
                type = UnresolvedJavaType.create(stamp.type().getName());
            }
            exactType = stamp.isExactType();
            nonNull = stamp.nonNull();
            alwaysNull = stamp.alwaysNull();
            alwaysArray = stamp.isAlwaysArray();
        }

        @Override
        public ObjectStamp resolve(ResolvedJavaType accessingClass) {
            ResolvedJavaType resolvedType = null;
            if (type != null) {
                resolvedType = this.type.resolve(accessingClass);
                if (resolvedType == null) {
                    throw new NoClassDefFoundError("Can't resolve " + type.getName() + " with " + accessingClass.getName());
                }
            }
            return new ObjectStamp(resolvedType, exactType, nonNull, alwaysNull, alwaysArray);
        }

        @Override
        public String toString() {
            return "SymbolicObjectStamp{" +
                            "declaringType=" + type +
                            ", exactType=" + exactType +
                            ", nonNull=" + nonNull +
                            ", alwaysNull=" + alwaysNull +
                            ", alwaysArray=" + alwaysArray +
                            '}';
        }
    }

}
