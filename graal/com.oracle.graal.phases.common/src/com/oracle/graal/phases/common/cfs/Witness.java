/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.cfs;

import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.compiler.common.type.ObjectStamp;

/**
 * <p>
 * A witness progressively tracks more detailed properties about an object value (the scrutinee);
 * the properties in question comprise whether the scrutinee has been observed:
 *
 * <ul>
 * <li>as non-null,</li>
 * <li>in a successful checkcast, or</li>
 * <li>in a successful instanceof.</li>
 * </ul>
 * </p>
 *
 * <p>
 * A witness is updated only when doing so increases the information content of the witness. For
 * example, upon visiting a {@link com.oracle.graal.nodes.java.CheckCastNode CheckCastNode} the
 * witness gets a chance to become updated.
 * </p>
 *
 * <p>
 * Therefore, at any given time, a witness represents the most detailed knowledge available so far
 * about the scrutinee, which is the knowledge most relevant for upcoming program-points.
 * </p>
 *
 * <p>
 * The availability of witnesses about both non-nullness and checkcast (for a given scrutinee)
 * promotes to an instanceof-style witness . The "levels" of knowledge a witness may exhibit are
 * captured by {@link com.oracle.graal.phases.common.cfs.Witness.LEVEL}. For conciseness, the
 * following query methods are available for a {@link com.oracle.graal.phases.common.cfs.Witness}:
 * <ul>
 * <li>{@link #atNonNull()}</li>
 * <li>{@link #atCheckCast()}</li>
 * <li>{@link #atInstanceOf()}</li>
 * </ul>
 * </p>
 *
 * <p>
 * Only non-interface object types (ie, class and array types) are tracked. Small extensions are
 * required to track interfaces, extensions that might be added after the current scheme proves
 * itself.
 * </p>
 *
 * <p>
 * Some useful facts about the Statechart implemented by {@link Witness Witness}:
 *
 * <ul>
 * <li>the start-state is "clueless"</li>
 * <li>
 * A self-transition via trackCC from whichever the current state is to itself, without information
 * gain, is always possible. Just give {@link Object java.lang.Object} as observed type.</li>
 * </ul>
 * </p>
 *
 */
public class Witness implements Cloneable {

    private static enum LEVEL {
        CLUELESS,
        NN,
        CC,
        IO,
        DN
    }

    private boolean atNonNull() {
        return level == LEVEL.NN;
    }

    private boolean atCheckCast() {
        return level == LEVEL.CC;
    }

    private boolean atInstanceOf() {
        return level == LEVEL.IO;
    }

    private boolean atDefinitelyNull() {
        return level == LEVEL.DN;
    }

    private void transition(LEVEL to, GuardingNode newAnchor) {
        this.level = to;
        this.gn = newAnchor;
        assert repOK();
    }

    public Witness() {
    }

    public Witness(Witness other) {
        level = other.level;
        seen = other.seen;
        gn = other.gn;
    }

    /**
     * Counterpart to {@link #asStamp()}
     */
    public Witness(ObjectStamp stamp, GuardingNode anchor) {
        assert stamp.isLegal();
        assert anchor != null;
        if (stamp.alwaysNull()) {
            // seen left null
            transition(LEVEL.DN, anchor);
        } else {
            boolean isNonIface = (stamp.type() != null && !stamp.type().isInterface());
            if (stamp.nonNull()) {
                if (isNonIface) {
                    seen = stamp.type();
                    transition(LEVEL.IO, anchor);
                } else {
                    seen = null;
                    transition(LEVEL.NN, anchor);
                }
            } else {
                if (isNonIface) {
                    seen = stamp.type();
                    transition(LEVEL.CC, anchor);
                } else {
                    seen = null;
                    transition(LEVEL.CLUELESS, null);
                    assert clueless();
                }
            }
        }
        assert repOK();
    }

    public boolean isNonNull() {
        return atNonNull() || atInstanceOf();
    }

    public boolean isNull() {
        return atDefinitelyNull();
    }

    /**
     * The most precise type known so far about the scrutinee. (Please notice that nothing can be
     * deduced about the non-nullness-status of the scrutinee from invoking this method alone).
     */
    public ResolvedJavaType type() {
        return seen;
    }

    public ObjectStamp asStamp() {
        boolean isKnownExact = (seen != null && seen.equals(seen.asExactType()));
        return new ObjectStamp(seen, isKnownExact, isNonNull(), isNull());
    }

    private LEVEL level = LEVEL.CLUELESS;
    private ResolvedJavaType seen = null;

    /**
     * Evidence (ie, a {@link com.oracle.graal.nodes.extended.GuardingNode}) attesting to the status
     * reported by this witness.
     *
     * May be one of:
     *
     * <ul>
     * <li>
     * {@link com.oracle.graal.nodes.BeginNode BeginNode},</li>
     * <li>
     * {@link com.oracle.graal.nodes.LoopExitNode LoopExitNode},</li>
     * <li>
     * {@link com.oracle.graal.nodes.FixedGuardNode FixedGuardNode}</li>
     * <li>{@link com.oracle.graal.nodes.MergeNode MergeNode}, resulting from merging two witnesses
     * with different values for this anchor</li>
     * </ul>
     *
     * <p>
     * An {@link com.oracle.graal.nodes.calc.ObjectEqualsNode ObjectEqualsNode} test results in the
     * more-clueless of both scrutinees having its witness upgraded to that of the other (both
     * scrutinees share the same {@link Witness Witness} instance from then on). For this reason,
     * this field may also hold the anchors associated to an
     * {@link com.oracle.graal.nodes.calc.ObjectEqualsNode ObjectEqualsNode} occurrence, ie nodes
     * that can serve as {@link com.oracle.graal.nodes.extended.GuardingNode GuardingNode} for the
     * purposes of building a {@link com.oracle.graal.nodes.PiNode PiNode}.
     * </p>
     *
     */
    private GuardingNode gn = null;

    /**
     * Invariants of this class:
     * <ul>
     * <li>All fields holding null is ok, the hallmark of a {@link #clueless() clueless} witness.
     * Another way for a witness to be clueless is by recording <code>java.lang.Object</code> as the
     * seen type and nothing more.</li>
     * <li>{@link #seen seen} may be null as long as the only hint being recorded is non-nullness</li>
     * <li>A non-null value for {@link #seen seen} can't be tracked with NN, that combination
     * amounts to IO and is tracked as such</li>
     * <li>At most one of NN, CC, IO may be tracked at any given time</li>
     * <li>A non-null CC or a non-null IO force {@link #seen seen} to be non-null</li>
     * <li>{@link #seen seen} must be null or denote a non-interface reference type (ie, either a
     * class-type or an array-type)</li>
     * </ul>
     */
    private boolean repOK() {
        if (clueless()) {
            assert level == LEVEL.CLUELESS;
            return true;
        }
        if (atNonNull() || atDefinitelyNull()) {
            assert seen == null;
        }
        if (seen != null) {
            assert !seen.isInterface();
            assert !seen.isPrimitive();
        }
        assert gn instanceof BeginNode || gn instanceof LoopExitNode || gn instanceof MergeNode || gn instanceof FixedGuardNode;
        return true;
    }

    /**
     * Helper method for readability in complex conditions.
     */
    public boolean clueless() {
        return cluelessAboutType() && cluelessAboutNullity();
    }

    /**
     * Helper method for readability in complex conditions.
     */
    public boolean cluelessAboutType() {
        // TODO also clueless when `seen` is `java.lang.Object`
        return seen == null;
    }

    /**
     * Helper method for readability in complex conditions.
     */
    public boolean cluelessAboutNullity() {
        return !atNonNull() && !atInstanceOf() && !atDefinitelyNull();
    }

    /**
     * Whether this {@link Witness Witness} tracks information strictly more precise than that in
     * the given {@link com.oracle.graal.compiler.common.type.ObjectStamp}.
     */
    boolean knowsBetterThan(ObjectStamp other) {
        return FlowUtil.isMorePrecise(asStamp(), other);
    }

    /**
     * Whether this {@link Witness Witness} tracks information strictly more precise than that in
     * the {@link com.oracle.graal.compiler.common.type.ObjectStamp} of the given argument.
     */
    boolean knowsBetterThan(ValueNode other) {
        return knowsBetterThan((ObjectStamp) other.stamp());
    }

    /**
     * Porcelain method for internal use only, invoked upon a Facade method being notified about
     * checkcast or instanceof.
     *
     * @return whether the state was updated (iff the observation adds any new information)
     */
    private boolean refinesSeenType(ResolvedJavaType observed) {
        if (isNull()) {
            return false;
        }
        if (cluelessAboutType() || FlowUtil.isMorePrecise(observed, seen)) {
            seen = observed;
            return true;
        }
        return false;
    }

    /**
     * Updates this {@link Witness Witness} to account for an observation about the scrutinee being
     * non-null. In case instanceof was observed,
     * {@link #trackIO(com.oracle.graal.api.meta.ResolvedJavaType, com.oracle.graal.nodes.extended.GuardingNode)
     * <code>trackIO(ResolvedJavaType, GuardingNode)</code>} should be invoked instead
     *
     * @return whether this {@link Witness Witness} was updated (iff the observation adds any new
     *         information)
     */
    public boolean trackNN(GuardingNode anchor) {
        assert repOK();
        assert !isNull();
        try {
            if (atInstanceOf()) {
                return false;
            }
            if (atCheckCast()) {
                transition(LEVEL.IO, anchor);
                return true;
            }
            if (!atNonNull()) {
                transition(LEVEL.NN, anchor);
                return true;
            }
            return false;
        } finally {
            assert repOK();
        }
    }

    /**
     * Updates this {@link Witness Witness} to account for an observation about the scrutinee being
     * null.
     *
     * @return whether this {@link Witness Witness} was updated (iff the observation adds any new
     *         information)
     */
    public boolean trackDN(GuardingNode anchor) {
        assert repOK();
        assert !isNonNull();
        try {
            if (atDefinitelyNull()) {
                return false;
            }
            assert level == LEVEL.CLUELESS || atCheckCast();
            seen = null;
            transition(LEVEL.DN, anchor);
            return true;
        } finally {
            assert repOK();
        }
    }

    /**
     * Updates this {@link Witness Witness} to account for an observation about the scrutinee
     * conforming to the provided type. In case instanceof was observed,
     * {@link #trackIO(com.oracle.graal.api.meta.ResolvedJavaType, com.oracle.graal.nodes.extended.GuardingNode)
     * <code>trackIO(ResolvedJavaType, GuardingNode)</code>} should be invoked instead.
     *
     * @return true iff information was gained.
     */
    public boolean trackCC(ResolvedJavaType observed, GuardingNode anchor) {
        assert !observed.isInterface();
        assert anchor != null;
        assert repOK();
        try {
            boolean anchorObsolete = refinesSeenType(observed);
            if (atInstanceOf()) {
                /*
                 * Statechart: self-transition at IO, potential information gain.
                 */
                if (anchorObsolete) {
                    transition(LEVEL.IO, anchor);
                    return true;
                }
                return false;
            }
            if (anchorObsolete) {
                if (!atNonNull()) {
                    /* Statechart: transition from clueless to CC. */
                    transition(LEVEL.CC, anchor);
                    return true;
                } else {
                    /* Statechart: transition from NN to IO. */
                    transition(LEVEL.IO, anchor);
                    return true;
                }
            }
            /*
             * Statechart: self-transition from whichever the current state is to itself, without
             * information gain.
             */
            return false;
        } finally {
            assert repOK();
        }
    }

    /**
     * Updates this {@link Witness Witness} to account for an observation about the non-null
     * scrutinee conforming to a type.
     *
     * @return whether this {@link Witness Witness} was updated (iff the observation adds any new
     *         information)
     */
    public boolean trackIO(ResolvedJavaType observed, GuardingNode anchor) {
        assert repOK();
        assert !isNull();
        try {
            boolean gotMorePreciseType = refinesSeenType(observed);
            if (!atInstanceOf() || gotMorePreciseType) {
                transition(LEVEL.IO, anchor);
                return true;
            }
            return gotMorePreciseType;
        } finally {
            assert repOK();
        }
    }

    /**
     * Shallow cloning is enough because what's reachable from {@link Witness} is primitive or used
     * read-only when merging states.
     */
    @Override
    public Witness clone() {
        return new Witness(this);
    }

    /**
     * @return null for a clueless method, non-null otherwise.
     */
    GuardingNode guard() {
        return gn;
    }

    /**
     * Merges another state into this one, by mutating this object, the other is left as is.
     */
    public void merge(Witness that, MergeNode merge) {
        assert this.repOK();
        assert that.repOK();

        if (clueless()) {
            return;
        }

        // preserve guarding node only if matches other, otherwise settle on `merge`
        final GuardingNode resultGuard = (guard() == that.guard()) ? guard() : merge;

        if (isNull()) {
            switch (that.level) {
                case CLUELESS:
                case NN:
                    // lose is-null status, gain nothing
                    level = LEVEL.CLUELESS;
                    seen = null;
                    break;
                case CC:
                case IO:
                    // demote to check-cast
                    level = LEVEL.CC;
                    seen = that.seen;
                    break;
                case DN:
                    // keep is-null status
            }
            gn = resultGuard;
            return;
        }

        if (that.isNull()) {
            /*
             * Same decisions as above (based on this-being-null and that-level) are now made based
             * on (that-being-null and this-level). Because merge is commutative.
             */
            switch (level) {
                case CLUELESS:
                case NN:
                    // lose is-null status, gain nothing
                    level = LEVEL.CLUELESS;
                    seen = null;
                    break;
                case CC:
                    break;
                case IO:
                    // demote to check-cast
                    level = LEVEL.CC;
                    // keep this.seen as-is
                    break;
                case DN:
                    // keep is-null status
            }
            gn = resultGuard;
            return;
        }

        // by now we know neither this nor that are known-to-be-null
        assert !isNull() && !that.isNull();

        // umbrella type over the observations from both witnesses
        ResolvedJavaType newSeen = (seen == null || that.seen == null) ? null : FlowUtil.widen(seen, that.seen);

        /*
         * guarantee on (both conformance and non-nullness) required from each input in order for
         * the result to be able to offer such guarantee
         */
        final boolean resultIO = atInstanceOf() && that.atInstanceOf();
        /* failing that, attempt to rescue type-conformance */
        final boolean resultCC = !resultIO && (!cluelessAboutType() && !that.cluelessAboutType());
        /* if all else fails, attempt to rescue non-nullness */
        final boolean resultNN = !resultIO && !resultCC && (isNonNull() && that.isNonNull());

        seen = newSeen;
        level = resultIO ? LEVEL.IO : resultCC ? LEVEL.CC : resultNN ? LEVEL.NN : LEVEL.CLUELESS;
        gn = resultGuard;

        assert repOK();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(cluelessAboutType() ? "seen=?" : "seen=" + seen);
        sb.append(";");
        sb.append("gn=" + gn);
        return sb.toString();
    }

}
