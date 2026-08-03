/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.loop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;

/**
 * This class describes a value node that is an induction variable in a counted loop.
 */
public abstract class InductionVariable {

    /**
     * Captures the initial and extremum values of an induction variable, together with the
     * conditions needed to use the computed endpoints safely. For example:
     * <pre>
     * for (int base = start; base &lt; limit; base += stride) {
     *     int derived = base * scale + offset;
     * }
     * </pre>
     * The endpoints for the IVs here are:
     * <pre>
     * baseInit = start
     * baseExtremum = start + stride * (tripCount - 1)
     *
     * derivedInit = baseInit * scale + offset
     * derivedExtremum = baseExtremum * scale + offset
     * </pre>
     *
     * The corresponding overflow conditions that must be guarded in order to use those
     * endpoints are:
     *
     * <pre>
     * multiplyOverflows(baseInit, scale)
     * addOverflows(baseInit * scale, offset)
     *
     * multiplyOverflows(baseExtremum, scale)
     * addOverflows(baseExtremum * scale, offset)
     * </pre>
     *
     * Since {@code base} is the loop's limit-checked IV, the loop overflow guard guarantees that
     * computing {@code basicExtremum} does not overflow, so no overflow conditions are needed
     * for its endpoints.
     *
     * <p>
     * {@link #init()} is the value from the first loop iteration. {@link #extremum()} is the value
     * from the last iteration.
     *
     * <p>
     * {@link #overflowConditions()} contains conditions that are true when the IV cannot
     * be treated as a monotonic range bounded by these endpoints, such as arithmetic overflow,
     * a narrowing conversion that wraps, or a zero extension that crosses the sign boundary.
     * The computed endpoints can only be used safely when all conditions are false.
     */
    public record Endpoints(ValueNode init, ValueNode extremum, List<LogicNode> overflowConditions) {
        public Endpoints {
            overflowConditions = List.copyOf(overflowConditions);
        }
    }

    public enum Direction {
        Up,
        Down;

        public Direction opposite() {
            switch (this) {
                case Up:
                    return Down;
                case Down:
                    return Up;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(this); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    public abstract StructuredGraph graph();

    protected final Loop loop;

    public InductionVariable(Loop loop) {
        this.loop = loop;
    }

    public Loop getLoop() {
        return loop;
    }

    /**
     * Returns the direction of the induction variable, or {@code null} when it cannot be
     * statically determined (such as when the stride is a runtime variable).
     */
    public abstract Direction direction();

    /**
     * Returns the value node that is described by this induction variable.
     */
    public abstract ValueNode valueNode();

    /**
     * Returns the node that gives the initial value of this induction variable.
     */
    public abstract ValueNode initNode();

    /**
     * Returns the stride of the induction variable. The stride is the value that is added to the
     * induction variable at each iteration.
     */
    public abstract ValueNode strideNode();

    /**
     * Returns whether the initial value of the induction variable is a constant.
     */
    public abstract boolean isConstantInit();

    /**
     * Returns whether the stride of the induction variable is a constant.
     */
    public abstract boolean isConstantStride();

    /**
     * Returns the constant initial value of an induction variable as a {@code long}.
     */
    public abstract long constantInit();

    /**
     * Returns the constant stride of an induction variable as a {@code long}.
     */
    public abstract long constantStride();

    /**
     * Returns the extremum value of the induction variable. The extremum value is the value of the
     * induction variable in the loop body of the last iteration, only taking into account the main
     * loop limit test. It's possible for the loop to exit before this value if
     * {@link CountedLoopInfo#isExactTripCount()} returns false for the containing loop.
     */
    public ValueNode extremumNode() {
        return extremumNode(false, valueNode().stamp(NodeView.DEFAULT));
    }

    public abstract ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp);

    /**
     * Returns the extremum value of the induction variable computed in the given stamp.
     *
     * @param assumeLoopEntered whether the caller guarantees that the loop executes at least once
     * @param stamp stamp to use for the computation
     * @param maxTripCount maximum trip count to use when computing the last-iteration value
     */
    public abstract ValueNode extremumNode(boolean assumeLoopEntered, Stamp stamp, ValueNode maxTripCount);

    /**
     * Computes the initial value and extremum values of this IV, together with the conditions required to use
     * them safely. The initial value is computed in the IV's stamp, and the extremum in {@code extremumStamp},
     * <p>
     * Each overflow condition is emitted in the native arithmetic width of the IV step that can overflow,
     * so the condition matches that step's real overflow semantics. Both endpoints need checks,
     * because derived IV operations may overflow while producing the initial value, or the extremum
     * value, or both.
     * <p>
     * For example, for the derived IV in this loop:
     * <pre>
     * for (int i = start; i < limit; i++) {
     *     int iv = i * 8;
     * }
     * </pre>
     * The returned init value is the {@code int} expression {@code start * 8}, but with a requested
     * {@code extremumStamp = long}, the extremum is the {@code long} expression
     * {@code (((long) limit) - 1L) * 8L}.
     * The returned overflow conditions check both {@code start * 8} and {@code (limit - 1) * 8}
     * in {@code int} arithmetic.
     *
     * @param assumeLoopEntered if the caller guarantees that the loop executes at least once
     * @param effectiveMaxTripCount maximum trip count to use when computing the last-iteration value
     * @param extremumStamp stamp to use for the returned extremum expression
     * @param bodyIV counted loop's body IV, used to identify the protected basic counter
     * @param limitCheckedIV counted loop's limit checked IV, used to identify the protected basic
     *            counter
     */
    public Endpoints computeEndpoints(boolean assumeLoopEntered, ValueNode effectiveMaxTripCount, Stamp extremumStamp, InductionVariable bodyIV,
                    InductionVariable limitCheckedIV) {
        /*
         * Follow this IV's base chain to the root/basic IV, compute that root extremum and its
         * overflow conditions first, then rebuild the derived IV initial values, endpoints, and
         * overflow conditions on the way back out.
         */
        ArrayList<DerivedInductionVariable> derivedIVs = null;
        InductionVariable current = this;
        while (current instanceof DerivedInductionVariable derived) {
            if (derivedIVs == null) {
                derivedIVs = new ArrayList<>();
            }
            derivedIVs.add(derived);
            current = derived.getBase();
        }
        GraalError.guarantee(current instanceof BasicInductionVariable, "Expected basic induction variable but got %s", current);

        ArrayList<LogicNode> overflowConditions = new ArrayList<>();
        /*
         * The root/basic IV contributes overflow conditions in the width of its own IV stamp.
         */
        Stamp ivStamp = current.valueNode().stamp(NodeView.DEFAULT);
        ValueNode init = current.initNode();
        ValueNode currentExtremum = current.extremumNode(assumeLoopEntered, extremumStamp, effectiveMaxTripCount);
        ValueNode currentIvExtremum;
        /*
         * The body and limit checked IVs are already covered by the counted loop's overflow guard
         * or the guarantee that the counter does not overflow.
         */
        if (extremumOverflowCoveredByCountedLoop(current, bodyIV, limitCheckedIV)) {
            currentIvExtremum = current.extremumNode(assumeLoopEntered, ivStamp, effectiveMaxTripCount);
        } else {
            currentIvExtremum = current.collectLocalEndpointOverflowConditions(assumeLoopEntered, ivStamp, effectiveMaxTripCount, null, overflowConditions);
        }

        if (derivedIVs != null) {
            for (int i = derivedIVs.size() - 1; i >= 0; i--) {
                DerivedInductionVariable derived = derivedIVs.get(i);
                /*
                 * Each derived IV likewise contributes overflow conditions in the width of its own
                 * IV stamp, not in the caller-requested final extremum stamp.
                 */
                Stamp derivedIVStamp = derived.valueNode().stamp(NodeView.DEFAULT);
                if (derived instanceof DerivedConvertedInductionVariable converted) {
                    /*
                     * Conversion IVs contribute conditions that ensure they don't introduce
                     * discontinuities across the range.
                     */
                    converted.collectRangeEndpointConditions(init, currentIvExtremum, overflowConditions);
                }
                /*
                 * Checking both init and extremum for overflow can be unnecessary. For example, for an ascending
                 * base IV, checking only `init - 50` and not `extremum - 50` is sufficient, but which of the two
                 * conditions is redundant in the general offset IV case depends on the sign of the offset and
                 * the IV direction.
                 * TODO skip these redundant conditions when possible
                 */
                init = derived.collectLocalEndpointOverflowConditions(assumeLoopEntered, derivedIVStamp, effectiveMaxTripCount, init, overflowConditions);
                if (extremumOverflowCoveredByCountedLoop(derived, bodyIV, limitCheckedIV)) {
                    currentIvExtremum = derived.extremumNode(assumeLoopEntered, derivedIVStamp, effectiveMaxTripCount);
                } else {
                    currentIvExtremum = derived.collectLocalEndpointOverflowConditions(assumeLoopEntered, derivedIVStamp, effectiveMaxTripCount, currentIvExtremum, overflowConditions);
                }
                currentExtremum = derived.extremumNode(assumeLoopEntered, extremumStamp, effectiveMaxTripCount);
            }
        }
        return new Endpoints(init, currentExtremum, overflowConditions);
    }

    /**
     * Returns whether the counted loop's no-overflow guarantee covers the extremum
     * computation for {@code iv}. It directly covers a basic body or limit-checked IV. It also
     * covers body/limit-checked offset IVs, because it preserves the base stride: after the
     * initial operation has been checked for overflow, a later overflow would imply that the
     * loop counter itself wraps.
     * <p>
     * This does not cover the initial endpoint. A derived IV can overflow while producing its
     * initial value even though the resulting counter advances without overflow. It
     * also excludes {@code offset - base} and scaled IVs, since negation or multiplication can
     * overflow.
     *
     * @param iv IV whose extremum is being computed
     * @param bodyIV counted loop's body IV
     * @param limitCheckedIV counted loop's limit-checked IV
     */
    private static boolean extremumOverflowCoveredByCountedLoop(InductionVariable iv, InductionVariable bodyIV, InductionVariable limitCheckedIV) {
        if (iv == bodyIV || iv == limitCheckedIV) {
            if (iv instanceof BasicInductionVariable) {
                return true;
            }
            if (iv instanceof DerivedOffsetInductionVariable offsetIV && !offsetIV.baseIsSubtrahend) {
                /* match offsetIV = `base +/- offset` */
                return true;
            }
        }
        return false;
    }

    /**
     * Produces one endpoint and adds the safety conditions contributed by this induction
     * variable's local arithmetic and conversions. If any added condition evaluates to {@code true},
     * the corresponding endpoint computation cannot be used safely. This method is used for both
     * the initial and extremum (last-iteration) endpoints.
     * <p>
     * The supplied {@code stamp} is the stamp that the resulting endpoint value should have.
     * <p>
     * For derived IVs, {@code baseEndpoint} is the already computed endpoint of the base IV as
     * produced by the preceding step of this iterative computation. For a basic IV,
     * {@code baseEndpoint} is unused and can be {@code null}.
     *
     * @param conditions the collection to which endpoint-safety conditions are added
     * @return this IV's endpoint in {@code stamp}, as threaded through the iterative endpoint
     *         computation
     */
    protected abstract ValueNode collectLocalEndpointOverflowConditions(boolean assumeLoopEntered, Stamp stamp, ValueNode effectiveMaxTripCount, ValueNode baseEndpoint,
                    Collection<LogicNode> conditions);

    /**
     * @return whether the extremum of this induction variable is a constant
     */
    public abstract boolean isConstantExtremum();

    /**
     * @return the constant extremum of this induction variable as a {@code long}
     */
    public abstract long constantExtremum();

    /**
     * Returns the exit value of the induction variable. The exit value is the value of the
     * induction variable at the loop exit.
     */
    public abstract ValueNode exitValueNode();

    /**
     * Deletes any nodes created within the scope of this object that have no usages.
     */
    public abstract void deleteUnusedNodes();

    /*
     * Range check predication support.
     */

    /**
     * Is this = C * ref + n, C a constant?
     */
    public boolean isConstantScale(InductionVariable ref) {
        return this == ref;
    }

    /**
     * this = C * ref + n, returns C.
     */
    public long constantScale(InductionVariable ref) {
        assert this == ref : this + "!=" + ref;
        return 1;
    }

    /**
     * Is this = n * ref + 0?
     */
    public boolean offsetIsZero(InductionVariable ref) {
        return this == ref;
    }

    /**
     * If this = n * ref + offset, returns offset or null otherwise.
     */
    public ValueNode offsetNode(InductionVariable ref) {
        assert !offsetIsZero(ref);
        return null;
    }

    /**
     * Duplicate this iv including all (non-constant) nodes.
     */
    public abstract InductionVariable duplicate();

    /**
     * Duplicate this IV with a new init node.
     */
    public abstract InductionVariable duplicateWithNewInit(ValueNode newInit);

    /**
     * Return the value of this iv upon the first entry of the loop.
     */
    public abstract ValueNode entryTripValue();

    /**
     * Return the root induction variable of this IV. The root induction variable is a
     * {@link BasicInductionVariable} directly representing a loop phi and a stride. It is computed
     * by following {@link DerivedInductionVariable#getBase()} until the
     * {@link BasicInductionVariable} is found.
     */
    public BasicInductionVariable getRootIV() {
        if (this instanceof BasicInductionVariable) {
            return (BasicInductionVariable) this;
        }
        assert this instanceof DerivedInductionVariable : this;
        return ((DerivedInductionVariable) this).getBase().getRootIV();
    }

    public enum IVToStringVerbosity {
        /**
         * Print a full representation of the induction variable including all nodes and its type.
         */
        FULL,
        /*
         * Only print the operations in a numeric form.
         */
        NUMERIC
    }

    /**
     * Determines if the components of this IV are structurally intact, i.e., part of a graph, not
     * deleted etc.
     */
    public abstract boolean structuralIntegrityValid();

    public abstract String toString(IVToStringVerbosity verbosity);

    @Override
    public String toString() {
        return toString(IVToStringVerbosity.NUMERIC);
    }

}
