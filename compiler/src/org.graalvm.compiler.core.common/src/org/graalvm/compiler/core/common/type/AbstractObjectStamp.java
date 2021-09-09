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
package org.graalvm.compiler.core.common.type;

import java.util.AbstractList;
import java.util.Objects;
import java.util.RandomAccess;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Type describing all pointers to Java objects.
 */
public abstract class AbstractObjectStamp extends AbstractPointerStamp {

    /**
     * Note: the meaning of {@link #type} == null is unfortunately overloaded: it means
     * "java.lang.Object" when {@link #exactType} == false; but it means {@link #isEmpty "empty"}
     * when {@link #exactType} == true.
     */
    private final ResolvedJavaType type;
    private final boolean exactType;
    private final boolean alwaysArray;

    protected AbstractObjectStamp(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull, boolean alwaysArray) {
        super(nonNull, alwaysNull);

        /*
         * Canonicalize the non-exact java.lang.Object to null so that other places like equals do
         * not need special handling.
         */
        this.type = type == null || (!exactType && type.isJavaLangObject()) ? null : type;

        this.exactType = exactType;
        this.alwaysArray = alwaysArray || (type != null && type.isArray());
    }

    @Override
    public void accept(Visitor v) {
        super.accept(v);
        v.visitObject(type);
        v.visitBoolean(exactType);
        v.visitBoolean(alwaysArray);
    }

    protected abstract AbstractObjectStamp copyWith(ResolvedJavaType newType, boolean newExactType, boolean newNonNull, boolean newAlwaysNull, boolean newAlwaysArray);

    @Override
    protected final AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        return copyWith(type, exactType, newNonNull, newAlwaysNull, alwaysArray);
    }

    @Override
    public Stamp unrestricted() {
        return copyWith(null, false, false, false, false);
    }

    @Override
    public Stamp empty() {
        return copyWith(null, true, true, false, false);
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        JavaConstant jc = (JavaConstant) c;
        ResolvedJavaType constType = jc.isNull() ? null : meta.lookupJavaType(jc);
        return copyWith(constType, jc.isNonNull(), jc.isNonNull(), jc.isNull(), false);
    }

    @Override
    public boolean hasValues() {
        return !exactType || (type != null && (isConcreteType(type)));
    }

    @Override
    public JavaKind getStackKind() {
        return JavaKind.Object;
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
        return exactType && type != null;
    }

    /**
     * Returns true if the stamp represents a type that is always an array. While all object arrays
     * have a common base class, there is no common base class for primitive arrays, so maintaining
     * a separate flag is more precise than just using {@link AbstractObjectStamp#type
     * type}.{@link ResolvedJavaType#isArray isArray}.
     */
    public boolean isAlwaysArray() {
        return alwaysArray;
    }

    public AbstractObjectStamp asAlwaysArray() {
        return copyWith(type, exactType, nonNull(), alwaysNull(), true);
    }

    protected void appendString(StringBuilder str) {
        if (this.isEmpty()) {
            str.append(" empty");
        } else {
            // Append "[]" for arrays, but only if it's not included in the type.
            boolean forceArrayNotation = alwaysArray && !(type != null && type.isArray());
            str.append(nonNull() ? "!" : "").//
                            append(exactType ? "#" : "").//
                            append(' ').//
                            append(type == null ? "java.lang.Object" : type.toJavaName()).//
                            append(forceArrayNotation ? "[]" : "").//
                            append(alwaysNull() ? " NULL" : "");
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
        boolean meetAlwaysArray;
        if (other.alwaysNull()) {
            meetType = type();
            meetExactType = exactType;
            meetNonNull = false;
            meetAlwaysNull = alwaysNull();
            meetAlwaysArray = alwaysArray;
        } else if (alwaysNull()) {
            meetType = other.type();
            meetExactType = other.exactType;
            meetNonNull = false;
            meetAlwaysNull = other.alwaysNull();
            meetAlwaysArray = other.alwaysArray;
        } else {
            meetType = meetTypes(type(), other.type());
            meetExactType = exactType && other.exactType;
            if (meetExactType && type != null && other.type != null) {
                // meeting two valid exact types may result in a non-exact type
                meetExactType = Objects.equals(meetType, type) && Objects.equals(meetType, other.type);
            }
            meetNonNull = nonNull() && other.nonNull();
            meetAlwaysNull = false;
            meetAlwaysArray = this.alwaysArray && other.alwaysArray;
        }

        if (Objects.equals(meetType, type) && meetExactType == exactType && meetNonNull == nonNull() && meetAlwaysNull == alwaysNull() && meetAlwaysArray == alwaysArray) {
            return this;
        } else if (Objects.equals(meetType, other.type) && meetExactType == other.exactType && meetNonNull == other.nonNull() && meetAlwaysNull == other.alwaysNull() &&
                        meetAlwaysArray == other.alwaysArray) {
            return other;
        } else {
            return copyWith(meetType, meetExactType, meetNonNull, meetAlwaysNull, meetAlwaysArray);
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
        boolean joinAlwaysArray = alwaysArray || other.alwaysArray;
        if (Objects.equals(type, other.type)) {
            joinType = type;
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
        if (joinAlwaysArray && joinType != null && !joinType.isArray() && (joinExactType || !joinType.isJavaLangObject())) {
            joinAlwaysNull = true;
            /* If we now have joinNonNull && joinAlwaysNull, it is converted to "empty" below. */
        }
        if (joinAlwaysNull) {
            joinType = null;
            joinExactType = false;
            joinAlwaysArray = false;
        }
        if (joinExactType && joinType == null) {
            return empty();
        }
        if (joinAlwaysNull && joinNonNull) {
            return empty();
        } else if (joinExactType && !isConcreteType(joinType)) {
            return empty();
        }
        if (Objects.equals(joinType, type) && joinExactType == exactType && joinNonNull == nonNull() && joinAlwaysNull == alwaysNull() && joinAlwaysArray == alwaysArray) {
            return this;
        } else if (Objects.equals(joinType, other.type) && joinExactType == other.exactType && joinNonNull == other.nonNull() && joinAlwaysNull == other.alwaysNull() &&
                        joinAlwaysArray == other.alwaysArray) {
            return other;
        } else {
            return copyWith(joinType, joinExactType, joinNonNull, joinAlwaysNull, joinAlwaysArray);
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
            // The `meetTypes` operation must be commutative. One way to achieve this is to totally
            // order the types and always call `meetOrderedNonNullTypes` in the same order. We
            // establish the order by first comparing the hash-codes for performance reasons, and
            // then comparing the internal names of the types.
            int hashA = a.getName().hashCode();
            int hashB = b.getName().hashCode();
            if (hashA < hashB) {
                return meetOrderedNonNullTypes(a, b);
            } else if (hashB < hashA) {
                return meetOrderedNonNullTypes(b, a);
            } else {
                int diff = a.getName().compareTo(b.getName());
                if (diff <= 0) {
                    return meetOrderedNonNullTypes(a, b);
                } else {
                    return meetOrderedNonNullTypes(b, a);
                }
            }
        }
    }

    private static ResolvedJavaType meetOrderedNonNullTypes(ResolvedJavaType a, ResolvedJavaType b) {
        ResolvedJavaType result = a.findLeastCommonAncestor(b);
        if (result.isJavaLangObject() && a.isInterface() && b.isInterface()) {
            // Both types are incompatible interfaces => search for first possible common
            // ancestor match among super interfaces.
            ResolvedJavaType[] interfacesA = a.getInterfaces();
            ResolvedJavaType[] interfacesB = b.getInterfaces();
            for (int i = 0; i < interfacesA.length; ++i) {
                ResolvedJavaType interface1 = interfacesA[i];
                for (int j = 0; j < interfacesB.length; ++j) {
                    ResolvedJavaType interface2 = interfacesB[j];
                    ResolvedJavaType leastCommon = meetTypes(interface1, interface2);
                    if (leastCommon.isInterface()) {
                        return leastCommon;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + super.hashCode();
        result = prime * result + (exactType ? 1231 : 1237);
        result = prime * result + (alwaysArray ? 1231 : 1237);
        result = prime * result + (type == null ? 0 : type.hashCode());
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
        if (exactType != other.exactType) {
            return false;
        }
        if (alwaysArray != other.alwaysArray) {
            return false;
        }
        if (!Objects.equals(type, other.type)) {
            return false;
        }
        return super.equals(other);
    }
}
