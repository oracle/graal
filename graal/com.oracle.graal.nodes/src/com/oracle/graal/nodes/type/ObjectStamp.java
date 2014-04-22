/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.type;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.spi.*;
import com.oracle.graal.compiler.common.type.*;

public class ObjectStamp extends Stamp {

    private final ResolvedJavaType type;
    private final boolean exactType;
    private final boolean nonNull;
    private final boolean alwaysNull;

    public ObjectStamp(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull) {
        this.type = type;
        this.exactType = exactType;
        this.nonNull = nonNull;
        this.alwaysNull = alwaysNull;
    }

    @Override
    public Stamp unrestricted() {
        return StampFactory.object();
    }

    @Override
    public Stamp illegal() {
        return new ObjectStamp(null, true, true, false);
    }

    @Override
    public boolean isLegal() {
        return !exactType || (type != null && (isConcreteType(type)));
    }

    @Override
    public Kind getStackKind() {
        return Kind.Object;
    }

    @Override
    public PlatformKind getPlatformKind(LIRTypeTool tool) {
        return tool.getObjectKind();
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        if (type != null) {
            return type;
        }
        return metaAccess.lookupJavaType(Object.class);
    }

    public boolean nonNull() {
        return nonNull;
    }

    public boolean alwaysNull() {
        return alwaysNull;
    }

    public ResolvedJavaType type() {
        return type;
    }

    public boolean isExactType() {
        return exactType;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append('a');
        str.append(nonNull ? "!" : "").append(exactType ? "#" : "").append(' ').append(type == null ? "-" : type.getName()).append(alwaysNull ? " NULL" : "");
        return str.toString();
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (this == otherStamp) {
            return this;
        }
        if (!(otherStamp instanceof ObjectStamp)) {
            return StampFactory.illegal(Kind.Illegal);
        }
        ObjectStamp other = (ObjectStamp) otherStamp;
        ResolvedJavaType meetType;
        boolean meetExactType;
        boolean meetNonNull;
        boolean meetAlwaysNull;
        if (other.alwaysNull) {
            meetType = type();
            meetExactType = exactType;
            meetNonNull = false;
            meetAlwaysNull = alwaysNull;
        } else if (alwaysNull) {
            meetType = other.type();
            meetExactType = other.exactType;
            meetNonNull = false;
            meetAlwaysNull = other.alwaysNull;
        } else {
            meetType = meetTypes(type(), other.type());
            meetExactType = exactType && other.exactType;
            if (meetExactType && type != null && other.type != null) {
                // meeting two valid exact types may result in a non-exact type
                meetExactType = Objects.equals(meetType, type) && Objects.equals(meetType, other.type);
            }
            meetNonNull = nonNull && other.nonNull;
            meetAlwaysNull = false;
        }

        if (Objects.equals(meetType, type) && meetExactType == exactType && meetNonNull == nonNull && meetAlwaysNull == alwaysNull) {
            return this;
        } else if (Objects.equals(meetType, other.type) && meetExactType == other.exactType && meetNonNull == other.nonNull && meetAlwaysNull == other.alwaysNull) {
            return other;
        } else {
            return new ObjectStamp(meetType, meetExactType, meetNonNull, meetAlwaysNull);
        }
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        return join0(otherStamp, false);
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

    /**
     * Returns the stamp representing the type of this stamp after a cast to the type represented by
     * the {@code to} stamp. While this is very similar to a {@link #join} operation, in the case
     * where both types are not obviously related, the cast operation will prefer the type of the
     * {@code to} stamp. This is necessary as long as ObjectStamps are not able to accurately
     * represent intersection types.
     *
     * For example when joining the {@link RandomAccess} type with the {@link AbstractList} type,
     * without intersection types, this would result in the most generic type ({@link Object} ). For
     * this reason, in some cases a {@code castTo} operation is preferable in order to keep at least
     * the {@link AbstractList} type.
     *
     * @param to the stamp this stamp should be casted to
     * @return This stamp casted to the {@code to} stamp
     */
    public Stamp castTo(ObjectStamp to) {
        return join0(to, true);
    }

    private Stamp join0(Stamp otherStamp, boolean castToOther) {
        if (this == otherStamp) {
            return this;
        }
        if (!(otherStamp instanceof ObjectStamp)) {
            return StampFactory.illegal(Kind.Illegal);
        }
        ObjectStamp other = (ObjectStamp) otherStamp;
        if (!isLegal()) {
            return this;
        } else if (!other.isLegal()) {
            return other;
        }

        ResolvedJavaType joinType;
        boolean joinAlwaysNull = alwaysNull || other.alwaysNull;
        boolean joinNonNull = nonNull || other.nonNull;
        boolean joinExactType = exactType || other.exactType;
        if (Objects.equals(type, other.type)) {
            joinType = type;
        } else if (type == null && other.type == null) {
            joinType = null;
        } else if (type == null) {
            joinType = other.type;
        } else if (other.type == null) {
            joinType = type;
        } else {
            // both types are != null and different
            if (type.isAssignableFrom(other.type)) {
                joinType = other.type;
                if (exactType) {
                    joinAlwaysNull = true;
                }
            } else if (other.type.isAssignableFrom(type)) {
                joinType = type;
                if (other.exactType) {
                    joinAlwaysNull = true;
                }
            } else {
                if (castToOther) {
                    joinType = other.type;
                    joinExactType = other.exactType;
                } else {
                    joinType = null;
                }
                if (joinExactType || (!type.isInterface() && !other.type.isInterface())) {
                    joinAlwaysNull = true;
                }
            }
        }
        if (joinAlwaysNull) {
            joinType = null;
            joinExactType = false;
        }
        if (joinExactType && joinType == null) {
            return StampFactory.illegal(Kind.Object);
        }
        if (joinAlwaysNull && joinNonNull) {
            return StampFactory.illegal(Kind.Object);
        } else if (joinExactType && !isConcreteType(joinType)) {
            return StampFactory.illegal(Kind.Object);
        }
        if (Objects.equals(joinType, type) && joinExactType == exactType && joinNonNull == nonNull && joinAlwaysNull == alwaysNull) {
            return this;
        } else if (Objects.equals(joinType, other.type) && joinExactType == other.exactType && joinNonNull == other.nonNull && joinAlwaysNull == other.alwaysNull) {
            return other;
        } else {
            return new ObjectStamp(joinType, joinExactType, joinNonNull, joinAlwaysNull);
        }
    }

    public static boolean isConcreteType(ResolvedJavaType type) {
        return !(Modifier.isAbstract(type.getModifiers()) && !type.isArray());
    }

    private static ResolvedJavaType meetTypes(ResolvedJavaType a, ResolvedJavaType b) {
        if (Objects.equals(a, b)) {
            return a;
        } else if (a == null || b == null) {
            return null;
        } else {
            return a.findLeastCommonAncestor(b);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (exactType ? 1231 : 1237);
        result = prime * result + (nonNull ? 1231 : 1237);
        result = prime * result + (alwaysNull ? 1231 : 1237);
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ObjectStamp other = (ObjectStamp) obj;
        if (exactType != other.exactType || nonNull != other.nonNull || alwaysNull != other.alwaysNull) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        return true;
    }

}
