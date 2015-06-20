/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.type;

import com.oracle.jvmci.meta.ResolvedJavaType;
import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.MetaAccessProvider;
import com.oracle.jvmci.meta.Constant;
import java.util.*;

/**
 * Type describing all pointers to Java objects.
 */
public abstract class AbstractObjectStamp extends AbstractPointerStamp {

    private final ResolvedJavaType type;
    private final boolean exactType;

    protected AbstractObjectStamp(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull) {
        super(nonNull, alwaysNull);
        this.type = type;
        if (!exactType && type != null && type.isLeaf()) {
            this.exactType = true;
        } else {
            this.exactType = exactType;
        }
    }

    protected abstract AbstractObjectStamp copyWith(ResolvedJavaType newType, boolean newExactType, boolean newNonNull, boolean newAlwaysNull);

    @Override
    protected final AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        return copyWith(type, exactType, newNonNull, newAlwaysNull);
    }

    @Override
    public Stamp unrestricted() {
        return copyWith(null, false, false, false);
    }

    @Override
    public Stamp empty() {
        return copyWith(null, true, true, false);
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        JavaConstant jc = (JavaConstant) c;
        ResolvedJavaType constType = jc.isNull() ? null : meta.lookupJavaType(jc);
        return copyWith(constType, jc.isNonNull(), jc.isNonNull(), jc.isNull());
    }

    @Override
    public boolean hasValues() {
        return !exactType || (type != null && (isConcreteType(type)));
    }

    @Override
    public Kind getStackKind() {
        return Kind.Object;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        if (type != null) {
            return type;
        }
        return metaAccess.lookupJavaType(Object.class);
    }

    public ResolvedJavaType type() {
        return type;
    }

    public boolean isExactType() {
        return exactType;
    }

    protected void appendString(StringBuilder str) {
        if (this.isEmpty()) {
            str.append(" empty");
        } else {
            str.append(nonNull() ? "!" : "").append(exactType ? "#" : "").append(' ').append(type == null ? "-" : type.getName()).append(alwaysNull() ? " NULL" : "");
        }
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (this == otherStamp) {
            return this;
        }
        AbstractObjectStamp other = (AbstractObjectStamp) otherStamp;
        if (isEmpty()) {
            return other;
        } else if (other.isEmpty()) {
            return this;
        }
        ResolvedJavaType meetType;
        boolean meetExactType;
        boolean meetNonNull;
        boolean meetAlwaysNull;
        if (other.alwaysNull()) {
            meetType = type();
            meetExactType = exactType;
            meetNonNull = false;
            meetAlwaysNull = alwaysNull();
        } else if (alwaysNull()) {
            meetType = other.type();
            meetExactType = other.exactType;
            meetNonNull = false;
            meetAlwaysNull = other.alwaysNull();
        } else {
            meetType = meetTypes(type(), other.type());
            meetExactType = exactType && other.exactType;
            if (meetExactType && type != null && other.type != null) {
                // meeting two valid exact types may result in a non-exact type
                meetExactType = Objects.equals(meetType, type) && Objects.equals(meetType, other.type);
            }
            meetNonNull = nonNull() && other.nonNull();
            meetAlwaysNull = false;
        }

        if (Objects.equals(meetType, type) && meetExactType == exactType && meetNonNull == nonNull() && meetAlwaysNull == alwaysNull()) {
            return this;
        } else if (Objects.equals(meetType, other.type) && meetExactType == other.exactType && meetNonNull == other.nonNull() && meetAlwaysNull == other.alwaysNull()) {
            return other;
        } else {
            return copyWith(meetType, meetExactType, meetNonNull, meetAlwaysNull);
        }
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        return join0(otherStamp, false);
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
     * @param other the stamp this stamp should be casted to
     * @return the new improved stamp or {@code null} if this stamp cannot be improved
     */
    @Override
    public Stamp improveWith(Stamp other) {
        return join0(other, true);
    }

    private Stamp join0(Stamp otherStamp, boolean improve) {
        if (this == otherStamp) {
            return this;
        }
        AbstractObjectStamp other = (AbstractObjectStamp) otherStamp;
        if (isEmpty()) {
            return this;
        } else if (other.isEmpty()) {
            return other;
        }

        ResolvedJavaType joinType;
        boolean joinAlwaysNull = alwaysNull() || other.alwaysNull();
        boolean joinNonNull = nonNull() || other.nonNull();
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
                if (improve) {
                    joinType = type;
                    joinExactType = exactType;
                } else {
                    joinType = null;
                }

                if (joinExactType || (!isInterfaceOrArrayOfInterface(type) && !isInterfaceOrArrayOfInterface(other.type))) {
                    joinAlwaysNull = true;
                }
            }
        }
        if (joinAlwaysNull) {
            joinType = null;
            joinExactType = false;
        }
        if (joinExactType && joinType == null) {
            return StampFactory.empty(Kind.Object);
        }
        if (joinAlwaysNull && joinNonNull) {
            return StampFactory.empty(Kind.Object);
        } else if (joinExactType && !isConcreteType(joinType)) {
            return StampFactory.empty(Kind.Object);
        }
        if (Objects.equals(joinType, type) && joinExactType == exactType && joinNonNull == nonNull() && joinAlwaysNull == alwaysNull()) {
            return this;
        } else if (Objects.equals(joinType, other.type) && joinExactType == other.exactType && joinNonNull == other.nonNull() && joinAlwaysNull == other.alwaysNull()) {
            return other;
        } else {
            return copyWith(joinType, joinExactType, joinNonNull, joinAlwaysNull);
        }
    }

    private static boolean isInterfaceOrArrayOfInterface(ResolvedJavaType t) {
        return t.isInterface() || (t.isArray() && t.getElementalType().isInterface());
    }

    public static boolean isConcreteType(ResolvedJavaType type) {
        return !(type.isAbstract() && !type.isArray());
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
        result = prime * result + super.hashCode();
        result = prime * result + (exactType ? 1231 : 1237);
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
        AbstractObjectStamp other = (AbstractObjectStamp) obj;
        if (exactType != other.exactType || !Objects.equals(type, other.type)) {
            return false;
        }
        return super.equals(other);
    }
}
