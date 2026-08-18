/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.virtual.phases.ea;

import java.util.Iterator;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class maintains a set of known values, identified by base object, locations and offset.
 */
public class ReadEliminationBlockState extends EffectsBlockState<ReadEliminationBlockState> {

    final EconomicMap<CacheEntry<?>, ValueNode> readCache;

    public abstract static class CacheEntry<T> {

        public final ValueNode object;
        public final T identity;

        protected CacheEntry(ValueNode object, T identity) {
            this.object = object;
            this.identity = identity;
        }

        public abstract CacheEntry<T> duplicateWithObject(ValueNode newObject);

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CacheEntry<?>)) {
                return false;
            }
            CacheEntry<?> other = (CacheEntry<?>) obj;
            return identity.equals(other.identity) && object == other.object;
        }

        @Override
        public int hashCode() {
            int result = 31 + ((identity == null) ? 0 : identity.hashCode());
            // we need to use the identity hash code for the object since the node may not yet have
            // a valid id and thus not have a stable hash code
            return 31 * result + ((object == null) ? 0 : System.identityHashCode(object));
        }

        @Override
        public String toString() {
            return object + ":" + identity;
        }

        public abstract boolean conflicts(LocationIdentity other, ValueNode index, ValueNode array);

        public abstract LocationIdentity getIdentity();
    }

    public static final class LoadCacheEntry extends CacheEntry<LocationIdentity> {

        public LoadCacheEntry(ValueNode object, LocationIdentity identity) {
            super(object, identity);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof LoadCacheEntry) {
                return super.equals(obj);
            }
            return false;
        }

        @Override
        public CacheEntry<LocationIdentity> duplicateWithObject(ValueNode newObject) {
            return new LoadCacheEntry(newObject, identity);
        }

        @Override
        public boolean conflicts(LocationIdentity other, ValueNode index, ValueNode array) {
            return identity.equals(other);
        }

        @Override
        public LocationIdentity getIdentity() {
            return identity;
        }
    }

    /**
     * CacheEntry describing an Unsafe memory reference. The memory location and the location
     * identity are separate so both must be considered when looking for optimizable memory
     * accesses.
     */
    public static final class UnsafeLoadCacheEntry extends CacheEntry<ValueNode> {

        private final LocationIdentity locationIdentity;
        private final JavaKind kind;

        public UnsafeLoadCacheEntry(ValueNode object, ValueNode location, LocationIdentity locationIdentity, JavaKind kind) {
            super(object, location);
            assert locationIdentity != null;
            this.locationIdentity = locationIdentity;
            this.kind = kind;
        }

        @Override
        public CacheEntry<ValueNode> duplicateWithObject(ValueNode newObject) {
            return new UnsafeLoadCacheEntry(newObject, identity, locationIdentity, kind);
        }

        @Override
        public boolean conflicts(LocationIdentity other, ValueNode index, ValueNode array) {
            return locationIdentity.equals(other);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + locationIdentity.hashCode();
            result = 31 * result + kind.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UnsafeLoadCacheEntry) {
                UnsafeLoadCacheEntry other = (UnsafeLoadCacheEntry) obj;
                return super.equals(other) && locationIdentity.equals(other.locationIdentity) && kind == other.kind;
            }
            return false;
        }

        @Override
        public LocationIdentity getIdentity() {
            return locationIdentity;
        }

        @Override
        public String toString() {
            return "UNSAFE:" + super.toString() + " location:" + locationIdentity + " (" + kind + ")";
        }
    }

    /**
     * Indexed read (and array length) elimination through object clone nodes on arrays.
     *
     * Array clones are special, they create a semi-opaque view on an array.
     *
     * We can perform read elimination of indexed reads on a clone by re-routing them through the
     * clone to the original object, however a write to the original array invalidates all reads
     * cached in the cloned one. Writes to the cloned one invalidate reads cached on the other one.
     *
     *  Therefore we establish the following semantic:
     *
     *  @formatter:off
     *      write(clonee==original array)
     *          invalidates cache for clonee and clone
     *
     *      read(clonee)
     *          nothing special, must never see clone (clone is later in the cfg)
     *
     *      write(clone)
     *          invalidates cache for clone and clonee
     *
     *      read(clone)
     *          can be re-reouted to clonee if cache entry for clonee is available (means not yet written)
     *
     *  In order to have this generically correct we do the following:
     *
     *      read(arbitrary)     - check if array is cloned cache entry --> rewrite
     *      write(arbitrary)    - invalidate cache for location and index, invalidate clone and clonee cache entry
     *      clone operation     - register clone cache entry for re-routing
     *      arraylength         - check if cache entry for clonee available, re-use that one
     *                            (else write happened already or no clone available)
     *
     *  @formatter:on
     */
    public static final class ArrayCloneCacheEntry extends CacheEntry<LocationIdentity> {

        public ArrayCloneCacheEntry(ValueNode originalArray, LocationIdentity identity) {
            super(originalArray, identity);
        }

        @Override
        public CacheEntry<LocationIdentity> duplicateWithObject(ValueNode newObject) {
            return new ArrayCloneCacheEntry(newObject, identity);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ArrayCloneCacheEntry)) {
                return false;
            }
            return super.equals(obj);
        }

        @Override
        public boolean conflicts(LocationIdentity other, ValueNode index, ValueNode array) {
            return identity.equals(other);
        }

        @Override
        public LocationIdentity getIdentity() {
            return identity;
        }
    }

    /**
     * The state of a newly allocated array that has {@link NewArrayNode#fillContents()} set, i.e.,
     * all elements are initialized to the default value. A successful read from such an array
     * without an intervening side effect will yield the default value. Such reads can be
     * eliminated, but a bounds check is necessary in general.
     */
    public static final class NewInitializedArrayCacheEntry extends CacheEntry<LocationIdentity> {

        public NewInitializedArrayCacheEntry(NewArrayNode newArray) {
            this(newArray, computeLocationIdentity(newArray));
        }

        public NewInitializedArrayCacheEntry(ValueNode newArray, LocationIdentity identity) {
            super(newArray, identity);
        }

        private static LocationIdentity computeLocationIdentity(NewArrayNode newArray) {
            return NamedLocationIdentity.getArrayLocation(newArray.elementType().getJavaKind());
        }

        @Override
        public CacheEntry<LocationIdentity> duplicateWithObject(ValueNode newObject) {
            return new NewInitializedArrayCacheEntry(newObject, identity);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NewInitializedArrayCacheEntry)) {
                return false;
            }
            return super.equals(obj);
        }

        @Override
        public boolean conflicts(LocationIdentity other, ValueNode index, ValueNode array) {
            return identity.equals(other);
        }

        @Override
        public LocationIdentity getIdentity() {
            return identity;
        }
    }

    public static final class IndexedCacheEntry extends CacheEntry<LocationIdentity> {
        public final ValueNode index;
        public final JavaKind kind;

        public IndexedCacheEntry(ValueNode object, LocationIdentity identity, ValueNode index, JavaKind kind) {
            super(object, identity);
            this.index = index;
            this.kind = kind;
        }

        @Override
        public int hashCode() {
            int result = 31 * super.hashCode() + index.hashCode();
            result = 31 * result + kind.ordinal();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof IndexedCacheEntry)) {
                return false;
            }
            IndexedCacheEntry other = (IndexedCacheEntry) obj;
            return super.equals(obj) && index == other.index && kind == other.kind;
        }

        @Override
        public CacheEntry<LocationIdentity> duplicateWithObject(ValueNode newObject) {
            return new IndexedCacheEntry(newObject, identity, index, kind);
        }

        @Override
        public boolean conflicts(LocationIdentity other, ValueNode otherIndex, ValueNode otherArray) {
            return potentiallyAliasingIndexedAccesses(this.identity, other, this.object, otherArray, this.index, otherIndex);
        }

        @Override
        public LocationIdentity getIdentity() {
            return identity;
        }

    }

    public static boolean potentiallyAliasingIndexedAccesses(LocationIdentity l1, LocationIdentity l2, ValueNode o1, ValueNode o2, ValueNode i1, ValueNode i2) {
        return potentiallyAliasingIndexedAccesses(l1, l2, o1, o2, i1, i2, null);
    }

    public static boolean potentiallyAliasingIndexedAccesses(LocationIdentity l1, LocationIdentity l2, ValueNode o1, ValueNode o2, ValueNode i1, ValueNode i2, MetaAccessProvider metaAccess) {
        assert l1 != null;
        assert l2 != null;
        assert l1.isSingle() : "Single location required for aliasing analysis";
        assert l2.isSingle() : "Single location required for aliasing analysis";
        if (o1 == null || o2 == null || i1 == null || i2 == null) {
            // we do not have complete information, thus we only check the location (unsage accesses
            // for example)
            return l1.equals(l2);
        } else if (!l1.equals(l2)) {
            // We know the locations are different, the two accesses can never alias with each
            // other
            return false;
        }
        Stamp s1 = i1.stamp(NodeView.DEFAULT);
        Stamp s2 = i2.stamp(NodeView.DEFAULT);
        assert s1 instanceof IntegerStamp : "Wrong type for aliasing analysis " + s1;
        assert s2 instanceof IntegerStamp : "Wrong type for aliasing analysis" + s2;
        IntegerStamp iS1 = (IntegerStamp) s1;
        IntegerStamp iS2 = (IntegerStamp) s2;
        if (iS1.join(iS2).isEmpty()) {
            /*
             * stamps do not overlap, the accesses can never alias with each other
             */
            return false;
        }
        // we are processing accesses on the same (node identity) array
        if (o1 == o2) {
            /*
             * Allow read elimination on arbitrary arr[X +- Y] access patterns.
             * For example, in the following code:
             *
             * @formatter:off
             *      arr[x] = 1;
             *      arr[x + 1] = 2;
             *      arr[2 + x] = 3;
             *
             *      return arr[x] + arr[x + 1] + arr[2 + x];
             * @formatter:on
             *
             * all three reads should get properly eliminated. Same if (x - offset) was used.
             */
            if (isSupportedBinaryArithmeticNode(i1)) {
                BinaryArithmeticNode<?> i1bin = (BinaryArithmeticNode<?>) i1;
                if (isSupportedBinaryArithmeticNode(i2)) {
                    BinaryArithmeticNode<?> i2bin = (BinaryArithmeticNode<?>) i2;
                    BinaryOp<?> i1binOp = i1bin.getArithmeticOp();
                    BinaryOp<?> i2binOp = i2bin.getArithmeticOp();
                    if (i1binOp.equals(i2binOp)) {
                        // test all 4 possible combinations:
                        // two operands must be node-identical, while the other two must be disjoint

                        // two of the four combinations only apply if the operation is commutative
                        boolean isCommutative = i1binOp.isCommutative();

                        if (i1bin.getX() == i2bin.getX() && areValuesDisjoint(i1bin.getY(), i2bin.getY())) {
                            return false;
                        } else if (isCommutative && i1bin.getX() == i2bin.getY() && areValuesDisjoint(i1bin.getY(), i2bin.getX())) {
                            return false;
                        } else if (isCommutative && i1bin.getY() == i2bin.getX() && areValuesDisjoint(i1bin.getX(), i2bin.getY())) {
                            return false;
                        } else if (i1bin.getY() == i2bin.getY() && areValuesDisjoint(i1bin.getX(), i2bin.getX())) {
                            return false;
                        }
                    } else if (i1binOp instanceof BinaryOp.Add && i2binOp instanceof BinaryOp.Sub && mixedAddSubDisjoint(i1bin, i2bin)) {
                        // e.g. arr[x + 1], arr[x - 1]
                        return false;
                    } else if (i1binOp instanceof BinaryOp.Sub && i2binOp instanceof BinaryOp.Add && mixedAddSubDisjoint(i2bin, i1bin)) {
                        // e.g. arr[x - 1], arr[x + 1]
                        return false;
                    }
                }
                if (binopDisjointFromValue(i1bin, i2)) {
                    return false;
                }
            } else {
                if (isSupportedBinaryArithmeticNode(i2)) {
                    BinaryArithmeticNode<?> i2bin = (BinaryArithmeticNode<?>) i2;
                    if (binopDisjointFromValue(i2bin, i1)) {
                        return false;
                    }
                }
            }
        }
        if (metaAccess != null) {
            /*
             * We know nothing about the objects or the indices of the accesses: the objects we are
             * processing are not the same node, however this does not guarantee that they are not
             * the same object (i.e. two parameters where the caller supplies the same array 2
             * times), therefore we try to check for this by checking their types and ensuring they
             * are not cross assignable
             */
            Stamp o1S = o1.stamp(NodeView.DEFAULT);
            Stamp o2S = o2.stamp(NodeView.DEFAULT);
            ResolvedJavaType t1 = o1S.javaType(metaAccess);
            ResolvedJavaType t2 = o2S.javaType(metaAccess);
            if (!t1.isAssignableFrom(t2) && !t2.isAssignableFrom(t1)) {
                return false;
            }
        }
        /*
         * We do not know if the two accesses are aliasing with each other. Therefore, we have to be
         * pessimistic and assume they alias.
         */
        return true;
    }

    /**
     * Returns {@code true} if the {@link ValueNode} is a binary arithmetic operation (instance of
     * {@link BinaryArithmeticNode}) and represents a {@link #isSupportedBinaryOp(BinaryOp)
     * supported binary operation}.
     */
    private static boolean isSupportedBinaryArithmeticNode(ValueNode node) {
        if (node instanceof BinaryArithmeticNode<?>) {
            return isSupportedBinaryOp(((BinaryArithmeticNode<?>) node).getArithmeticOp());
        }
        return false;
    }

    /**
     * Returns {@code true} if the {@link BinaryOp} is a supported index for read elimination
     * removal.
     */
    private static boolean isSupportedBinaryOp(BinaryOp<?> op) {
        // Currently, only addition and subtraction are supported.
        return op instanceof BinaryOp.Add || op instanceof BinaryOp.Sub;
    }

    /**
     * Returns {@code true} if the {@link ValueNode}s can never be equal.
     */
    private static boolean areValuesDisjoint(ValueNode x, ValueNode y) {
        // values are disjoint if their stamps have empty intersection.
        return x.stamp(NodeView.DEFAULT).join(y.stamp(NodeView.DEFAULT)).isEmpty();
    }

    /**
     * Returns {@code true} if the {@link ValueNode} can never be equal to the neutral element for
     * the {@link BinaryOp}.
     */
    private static boolean valueIsNeverNeutral(ValueNode x, BinaryOp<?> op) {
        assert isSupportedBinaryOp(op) : "Op must be supported " + op;
        // this only works for addition and subtraction, which is what is currently supported
        return !((IntegerStamp) x.stamp(NodeView.DEFAULT)).contains(0);
    }

    /**
     * Returns {@code true} if the {@link BinaryArithmeticNode} is always different from the given
     * {@link ValueNode}. This is true if one of the arguments to the operation is identity-equal to
     * the value, and the other is never the neutral element for the operation (i.e.
     * {@code X + (never neutral) != X} for all {@code X})
     */
    private static boolean binopDisjointFromValue(BinaryArithmeticNode<?> binop, ValueNode z) {
        assert isSupportedBinaryArithmeticNode(binop) : "Op must be supported " + binop;
        // binop = X <op> Y
        BinaryOp<?> op = binop.getArithmeticOp();
        if (binop.getX() == z && valueIsNeverNeutral(binop.getY(), op)) {
            // X == Z && Y != e
            return true;
        } else if (op.isCommutative() && binop.getY() == z && valueIsNeverNeutral(binop.getX(), op)) {
            // Y == Z && X != e
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if an addition and subtraction with a shared (node-identical) base can never produce
     * the same value. The left-hand operand of the subtraction is considered to be the base.
     * Operations without a shared base operand conservatively return {@code false}.
     *
     * <p> The supported expressions have the form:
     * <pre>
     *  add = base + x  // or: x + base
     *  sub = base - y
     * </pre>
     *
     * <p> Considering {@code add == sub  iff  x == -y }, this method negates {@code y}'s stamp
     * and checks that it has an empty intersection with {@code x}'s stamp.
     */
    private static boolean mixedAddSubDisjoint(BinaryArithmeticNode<?> add, BinaryArithmeticNode<?> sub) {
        assert add.getArithmeticOp() instanceof BinaryOp.Add : "first argument must be add";
        assert sub.getArithmeticOp() instanceof BinaryOp.Sub : "second argument must be sub";
        ValueNode base = sub.getX();
        ValueNode addend;
        if (add.getX() == base) {
            addend = add.getY();
        } else if (add.getY() == base) {
            addend = add.getX();
        } else {
            return false;
        }

        Stamp negSubY = IntegerStamp.OPS.getNeg().foldStamp(sub.getY().stamp(NodeView.DEFAULT));
        // Check if addend is disjoint with the negated right-hand operand of the subtraction.
        // (meaning, their stamps have an empty intersection)
        return addend.stamp(NodeView.DEFAULT).join(negSubY).isEmpty();
    }

    public ReadEliminationBlockState() {
        readCache = EconomicMap.create(Equivalence.DEFAULT);
    }

    public ReadEliminationBlockState(ReadEliminationBlockState other) {
        super(other);
        readCache = EconomicMap.create(Equivalence.DEFAULT, other.readCache);
    }

    @Override
    public String toString() {
        return super.toString() + " " + readCache;
    }

    @Override
    public boolean equivalentTo(ReadEliminationBlockState other) {
        return isSubMapOf(readCache, other.readCache);
    }

    public void addCacheEntry(CacheEntry<?> identifier, ValueNode value) {
        readCache.put(identifier, value);
    }

    public ValueNode getCacheEntry(CacheEntry<?> identifier) {
        return readCache.get(identifier);
    }

    /**
     * Kill the cache for memory accesses established so far down the control flow graph.
     * {@code kill} represents a memory kill to location {@code identity}, potentially expressing an
     * array access. This method must implement Java semantic for regular fields, array accesses,
     * volatile operations etc.
     */
    public void killReadCache(@SuppressWarnings("unused") Node kill, LocationIdentity identity, ValueNode index, ValueNode array) {
        if (identity.isAny()) {
            /**
             * Kill all mutable locations.
             */
            Iterator<CacheEntry<?>> iterator = readCache.getKeys().iterator();
            while (iterator.hasNext()) {
                CacheEntry<?> entry = iterator.next();
                if (entry.getIdentity().isMutable()) {
                    iterator.remove();
                }
            }
            return;
        }
        Iterator<CacheEntry<?>> iterator = readCache.getKeys().iterator();
        while (iterator.hasNext()) {
            CacheEntry<?> entry = iterator.next();
            /*
             * We cover multiple cases here but in general index and array can only be !=null for
             * indexed nodes thus the location identity of other accesses (field and object
             * locations) will never be the same and will never alias with array accesses.
             *
             * Unsafe accesses will alias if they are writing to any location.
             */
            if (entry.conflicts(identity, index, array)) {
                iterator.remove();
            }
        }
    }

    public EconomicMap<CacheEntry<?>, ValueNode> getReadCache() {
        return readCache;
    }
}
