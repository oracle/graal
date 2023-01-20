/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.profiles;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.InlineSupport.StateField;
import com.oracle.truffle.api.nodes.Node;

/**
 * A profile is a Truffle utility class that uses the {@link CompilerDirectives Truffle compiler
 * directives} to guard for and/or forward runtime information to the compiler. Whenever Truffle DSL
 * can be used {@link InlinedProfile inlined profiles} subclasses should be used instead of regular
 * {@link Profile profile} subclasses.
 * <p>
 * <b>Usage:</b> Inlined profiles are used using the {@link Cached} annotation in specialization
 * methods. See the individual profile subclasses for further usage examples. Profiles are intended
 * for local speculation only. For global speculations use {@link Assumption assumptions} instead.
 * <p>
 * <b>Compilation:</b> Some profiles like {@link InlinedBranchProfile branch} profiles do not induce
 * additional overhead in compiled code. Others like {@link InlinedByteValueProfile value} profiles
 * might require a runtime check to verify their local speculation. Even if profiles do not induce
 * direct overhead in compiled code it still might get invalidated as a result of using profiles.
 * Invalidating profiles will result in the invalidation of compiled code. It is therefore essential
 * to place these profiles in way that is neither too aggressive nor too conservative, ideally based
 * on measurements in real world applications.
 * <p>
 * <b>Footprint:</b> Inlined versions of profiles have a significantly reduced memory footprint
 * compared to their {@link Profile allocated} counterparts, however they do rely on their usage
 * being inlined to have the same performance characteristics. Whether profiling information can be
 * forwarded to the compiler depends on the capabilities of the {@link TruffleRuntime runtime
 * system}. If the runtime returns <code>true</code> in {@link TruffleRuntime#isProfilingEnabled()}
 * then runtime information will get collected. This comes at at the cost of additional overhead and
 * footprint in interpreted mode. Thats why the factory methods of profiles can return
 * implementations where profiling is disabled. Using disabled profiles makes sense for runtimes
 * that are unable to use the collected profiling information. Even runtime implementations that are
 * able to use this information might decide to turn off profiling for benchmarking purposes.
 * <p>
 * Inlined profile subclasses:
 * <ul>
 * <li>{@link InlinedBranchProfile} to profile on unlikely branches like errors.</li>
 * <li>{@link InlinedConditionProfile} to profile on conditionals or boolean values.</li>
 * <li>{@link InlinedCountingConditionProfile} to profile on conditionals or boolean values using
 * counters.</li>
 * <li>{@link InlinedLoopConditionProfile} to profile on conditionals of loops with special support
 * for counted loops.</li>
 * <li>{@link InlinedByteValueProfile} to profile on <code>byte</code> values.</li>
 * <li>{@link InlinedIntValueProfile} to profile on <code>int</code> values.</li>
 * <li>{@link InlinedLongValueProfile} to profile on <code>long</code> values.</li>
 * <li>{@link InlinedFloatValueProfile} to profile on <code>float</code> values.</li>
 * <li>{@link InlinedDoubleValueProfile} to profile on <code>double</code> values.</li></li>
 * </ul>
 *
 * @see InlinedProfile
 * @see Assumption
 * @since 23.0
 */
public abstract class InlinedProfile {

    InlinedProfile() {
    }

    /**
     * Disables this profile by setting it to its generic state. After disabling it is guaranteed to
     * never {@link CompilerDirectives#transferToInterpreterAndInvalidate() deoptimize} on any
     * invocation of a profile method.
     * <p>
     * This method must not be called on compiled code paths. Note that disabling the profile will
     * not invalidate existing compiled code that uses this profile.
     *
     * @since 23.0
     */
    public abstract void disable(Node node);

    /**
     * Resets this profile to its uninitialized state. Has no effect if this profile is already in
     * its uninitialized state or a disabled version of this profile is used.
     * <p>
     * This method must not be called on compiled code paths. Note that disabling the profile will
     * not invalidate existing compiled code that uses this profile.
     *
     * @since 23.0
     */
    public abstract void reset(Node node);

    /**
     * Prints a string representation of this inlined profile given a target node.
     *
     * @since 23.0
     */
    public abstract String toString(Node node);

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public final String toString() {
        return String.format("%s(INLINED)", getClass().getSimpleName());
    }

    static int getStateInt(StateField field, Node node) {
        return field.get(node);
    }

    static byte getStateByte(StateField field, Node node) {
        return (byte) (field.get(node) & 0xFF);
    }

    static void setStateByte(StateField field, Node node, byte b) {
        field.set(node, (b & 0xFF));
    }

    static float getStateFloat(StateField field, Node node) {
        return Float.intBitsToFloat(field.get(node));
    }

    static void setStateFloat(StateField field, Node node, float f) {
        field.set(node, Float.floatToRawIntBits(f));
    }

    static boolean getStateBoolean(StateField field, Node node) {
        return field.get(node) == 1;
    }

    static void setStateInt(StateField field, Node node, int value) {
        field.set(node, value);
    }

    static void setStateBoolean(StateField field, Node node, boolean value) {
        field.set(node, value ? 1 : 0);
    }

    final String toStringDisabled() {
        return String.format("%s(DISABLED)", getClass().getSimpleName());
    }

    final String toString(Class<?> profileClass, boolean uninitialized, boolean generic, String specialization) {
        String s;
        if (uninitialized) {
            s = "UNINITIALIZED";
        } else if (generic) {
            s = "GENERIC";
        } else {
            s = specialization == null ? "" : specialization;
        }
        return String.format("%s(%s)@%s", profileClass.getSimpleName(), s, Integer.toHexString(this.hashCode()));
    }
}
