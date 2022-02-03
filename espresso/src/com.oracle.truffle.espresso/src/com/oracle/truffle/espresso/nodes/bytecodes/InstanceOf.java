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
package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.analysis.hierarchy.AssumptionGuardedValue;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyAssumption;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * INSTANCEOF bytecode helper nodes.
 *
 * <p>
 * Provides specialized instanceof checks for: array classes, {@link Object}, final classes,
 * interfaces, primitives and regular classes. Also includes a cached (inline cache) implementation.
 *
 * <p>
 * If the type to check is known in advance e.g. INSTANCEOF and CHECKCAST bytecodes, use
 * {@link InstanceOf} via {@link InstanceOf#create(Klass, boolean)}, which creates a specialized
 * {@link InstanceOf} node for that particular type.
 *
 * If the type to check is not known in advance, or there can be multiple, use
 * {@link InstanceOf.Dynamic} which also takes the type to check as parameter.
 *
 * For un-cached nodes use the stateless {@link InstanceOf.Dynamic}.
 */
@NodeInfo(shortName = "INSTANCEOF constant class")
public abstract class InstanceOf extends Node {

    public abstract boolean execute(Klass maybeSubtype);

    /**
     * Dynamic instanceof check. Takes the type to check as parameter.
     */
    @GenerateUncached
    @NodeInfo(shortName = "INSTANCEOF dynamic check")
    public abstract static class Dynamic extends Node {
        protected static final int LIMIT = 4;

        public abstract boolean execute(Klass maybeSubtype, Klass superType);

        protected static InstanceOf createInstanceOf(Klass superType) {
            return InstanceOf.create(superType, true);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "superType == cachedSuperType", limit = "LIMIT")
        boolean doCached(Klass maybeSubType, Klass superType,
                        @Cached("superType") Klass cachedSuperType,
                        @Cached("createInstanceOf(cachedSuperType)") InstanceOf instanceOf) {
            return instanceOf.execute(maybeSubType);
        }

        @Specialization(replaces = "doCached")
        protected boolean doGeneric(Klass maybeSubType, Klass superType) {
            return superType.isAssignableFrom(maybeSubType);
        }
    }

    /**
     * Creates a specialized {@link InstanceOf} node for the given type.
     *
     * @param superType the type to check
     * @param useInlineCache uses an inline cache, then fallback to specialized nodes.
     */
    public static InstanceOf create(Klass superType, boolean useInlineCache) {
        // Prefer an inline cache for non-trivial checks.
        if (useInlineCache) {
            return InstanceOfFactory.InlineCacheNodeGen.create(superType);
        }

        // Cheap checks first.
        if (superType.isJavaLangObject()) {
            return ObjectClass.INSTANCE;
        }
        if (superType.isPrimitive()) {
            return new PrimitiveClass((PrimitiveKlass) superType);
        }

        if (!superType.isArray() && superType.isFinalFlagSet()) {
            return new FinalClass(superType);
        }

        if (superType.isInstanceClass()) {
            return InstanceOfFactory.ConstantClassNodeGen.create((ObjectKlass) superType);
        }

        // Non-trivial checks.
        if (superType.isArray()) {
            return InstanceOfFactory.ArrayClassNodeGen.create((ArrayKlass) superType);
        }
        if (superType.isInterface()) {
            return InstanceOfFactory.ConstantInterfaceNodeGen.create((ObjectKlass) superType);
        }

        throw EspressoError.shouldNotReachHere();
    }

    @NodeInfo(shortName = "INSTANCEOF primitive")
    private static final class PrimitiveClass extends InstanceOf {

        private final PrimitiveKlass superType;

        PrimitiveClass(PrimitiveKlass superType) {
            this.superType = superType;
        }

        @Override
        public boolean execute(Klass maybeSubtype) {
            return superType == maybeSubtype;
        }
    }

    @NodeInfo(shortName = "INSTANCEOF Ljava/lang/Object;")
    private static final class ObjectClass extends InstanceOf {

        private ObjectClass() {
            // single instance
        }

        static final InstanceOf INSTANCE = new ObjectClass();

        @Override
        public boolean execute(Klass maybeSubtype) {
            // Faster than: return maybeSubtype.isPrimitive();
            return !(maybeSubtype instanceof PrimitiveKlass);
        }
    }

    @NodeInfo(shortName = "INSTANCEOF final non-array class")
    private static final class FinalClass extends InstanceOf {

        private final Klass superType;

        FinalClass(Klass superType) {
            assert superType.isFinalFlagSet() && !superType.isArray();
            this.superType = superType;
        }

        @Override
        public boolean execute(Klass maybeSubtype) {
            return superType == maybeSubtype;
        }
    }

    @NodeInfo(shortName = "INSTANCEOF array class")
    abstract static class ArrayClass extends InstanceOf {

        private final ArrayKlass superType;
        @Child InstanceOf elementalInstanceOf;

        protected ArrayClass(ArrayKlass superType) {
            this.superType = superType;
            this.elementalInstanceOf = InstanceOf.create(superType.getElementalType(), true);
        }

        @Specialization
        boolean doArray(ArrayKlass maybeSubtype,
                        @Cached BranchProfile nonTrivialProfile,
                        @Cached BranchProfile elementalCheckProfile,
                        @Cached BranchProfile simpleSubTypeCheckProfile) {
            if (superType == maybeSubtype) {
                return true;
            }

            nonTrivialProfile.enter();
            int comparison = Integer.compare(superType.getDimension(), maybeSubtype.getDimension());
            if (comparison == 0) {
                elementalCheckProfile.enter();
                return elementalInstanceOf.execute(maybeSubtype.getElementalType());
            }

            if (comparison < 0) {
                simpleSubTypeCheckProfile.enter();
                Klass elemental = superType.getElementalType();
                Meta meta = EspressoContext.get(this).getMeta();
                return elemental == meta.java_lang_Object || elemental == meta.java_io_Serializable || elemental == meta.java_lang_Cloneable;
            }

            return false; // if (comparison > 0)
        }

        @Fallback
        boolean doFallback(Klass maybeSubtype) {
            assert !maybeSubtype.isArray();
            return false;
        }
    }

    @NodeInfo(shortName = "INSTANCEOF class")
    abstract static class ConstantClass extends InstanceOf {

        private final ObjectKlass superType;

        ConstantClass(ObjectKlass superType) {
            this.superType = superType;
        }

        protected ClassHierarchyAssumption getNoImplementorsAssumption() {
            return EspressoContext.get(this).getClassHierarchyOracle().hasNoImplementors(superType);
        }

        protected AssumptionGuardedValue<ObjectKlass> readSingleImplementor() {
            return EspressoContext.get(this).getClassHierarchyOracle().readSingleImplementor(superType);
        }

        @Specialization(assumptions = "noImplementors")
        public boolean doNoImplementors(@SuppressWarnings("unused") Klass maybeSubtype,
                        @SuppressWarnings("unused") @Cached("getNoImplementorsAssumption().getAssumption()") Assumption noImplementors) {
            return false;
        }

        /**
         * If {@code superType} has a single implementor (itself if {@code superType} is a concrete
         * class or a single concrete child, if {@code superType} is an abstract class),
         * {@code maybeSubtype} is its subtype iff it's equal to the implementing class.
         */
        @Specialization(assumptions = "maybeSingleImplementor.hasValue()", guards = "implementor != null")
        public boolean doSingleImplementor(ObjectKlass maybeSubtype,
                        @SuppressWarnings("unused") @Cached("readSingleImplementor()") AssumptionGuardedValue<ObjectKlass> maybeSingleImplementor,
                        @Cached("maybeSingleImplementor.get()") ObjectKlass implementor) {
            return maybeSubtype == implementor;
        }

        @Specialization
        public boolean doObjectKlass(ObjectKlass maybeSubtype) {
            return superType == maybeSubtype || superType.checkOrdinaryClassSubclassing(maybeSubtype);
        }

        @Fallback
        public boolean doFallback(@SuppressWarnings("unused") Klass maybeSubtype) {
            return false;
        }
    }

    @NodeInfo(shortName = "INSTANCEOF interface")
    abstract static class ConstantInterface extends InstanceOf {

        private final ObjectKlass superType;

        ConstantInterface(ObjectKlass superType) {
            this.superType = superType;
            assert superType.isInterface();
        }

        protected ClassHierarchyAssumption getNoImplementorsAssumption() {
            return EspressoContext.get(this).getClassHierarchyOracle().hasNoImplementors(superType);
        }

        protected AssumptionGuardedValue<ObjectKlass> readSingleImplementor() {
            return EspressoContext.get(this).getClassHierarchyOracle().readSingleImplementor(superType);
        }

        @Specialization(assumptions = "noImplementors")
        public boolean doNoImplementors(@SuppressWarnings("unused") Klass maybeSubtype,
                        @SuppressWarnings("unused") @Cached("getNoImplementorsAssumption().getAssumption()") Assumption noImplementors) {
            return false;
        }

        @Specialization(assumptions = "maybeSingleImplementor.hasValue()", guards = "implementor != null", replaces = "doNoImplementors")
        public boolean doSingleImplementor(ObjectKlass maybeSubtype,
                        @SuppressWarnings("unused") @Cached("readSingleImplementor()") AssumptionGuardedValue<ObjectKlass> maybeSingleImplementor,
                        @Cached("maybeSingleImplementor.get()") ObjectKlass implementor) {
            return maybeSubtype == implementor;
        }

        @Specialization(replaces = "doSingleImplementor")
        public boolean doObjectKlass(ObjectKlass maybeSubtype) {
            // This check can be expensive.
            return superType.checkInterfaceSubclassing(maybeSubtype);
        }

        @Specialization
        public boolean doArrayKlass(@SuppressWarnings("unused") ArrayKlass maybeSubtype) {
            Meta meta = EspressoContext.get(this).getMeta();
            return superType == meta.java_lang_Cloneable || superType == meta.java_io_Serializable;
        }

        @Fallback
        public boolean doFallback(@SuppressWarnings("unused") Klass maybeSubtype) {
            return false;
        }
    }

    @NodeInfo(shortName = "INSTANCEOF + inline cache")
    abstract static class InlineCache extends InstanceOf {

        protected static final int LIMIT = 4;

        protected final Klass superType;

        protected InlineCache(Klass superType) {
            this.superType = superType;
        }

        @Specialization(guards = "cachedMaybeSubtype == maybeSubtype", limit = "LIMIT")
        boolean doCached(@SuppressWarnings("unused") Klass maybeSubtype,
                        @SuppressWarnings("unused") @Cached("maybeSubtype") Klass cachedMaybeSubtype,
                        @Cached("superType.isAssignableFrom(maybeSubtype)") boolean result) {
            return result;
        }

        @Specialization(replaces = "doCached")
        public boolean doGeneric(Klass maybeSubtype, @Cached("createGeneric(superType)") InstanceOf genericInstanceOf) {
            return genericInstanceOf.execute(maybeSubtype);
        }

        protected static InstanceOf createGeneric(Klass superType) {
            return InstanceOf.create(superType, false);
        }
    }
}
