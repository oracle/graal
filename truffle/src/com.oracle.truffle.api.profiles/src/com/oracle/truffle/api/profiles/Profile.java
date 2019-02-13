/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCloneable;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * <p>
 * A profile is a Truffle utility class that uses the {@link CompilerDirectives Truffle compiler
 * directives} to guard for and/or forward runtime information to the compiler.
 * </p>
 *
 * <p>
 * <b>Usage:</b> Profiles should be stored in <code>final</code> or {@link CompilationFinal
 * compilation final} fields of node classes to ensure that they can get optimized properly.
 * Profiles must not be shared between ASTs. Using the same profile multiple times in a single
 * {@link Node node} or in multiple {@link Node nodes} which {@link Node#getRootNode() link} to the
 * same {@link RootNode root} is allowed. <b>Never</b> store profiles inside runtime values that
 * leave the scope of the originating AST. This limitation exists because the used mechanism to
 * invalidate compiled code performs local invalidations only. For global speculations use
 * {@link Assumption assumptions} instead.
 * </p>
 *
 * <p>
 * <b>Compilation:</b> Some profiles like {@link BranchProfile branch} profiles do not induce
 * additional overhead in compiled code. Others like {@link ValueProfile value} profiles might
 * require a runtime check to verify their assumptions which are forwarded to the compiler. Even if
 * profiles do not induce direct overhead in compiled code it still might get invalidated as a
 * result of using profiles. Invalidating profiles will result in the invalidation of compiled code.
 * It is therefore essential to place these profiles in way that is neither too aggressive nor too
 * conservative.
 * </p>
 *
 * <p>
 * <b>Footprint:</b> Whether profiling information can be forwarded to the compiler depends on the
 * capabilities of the {@link TruffleRuntime runtime system}. If the runtime returns
 * <code>true</code> in {@link TruffleRuntime#isProfilingEnabled()} then runtime information will
 * get collected. This comes at at the cost of additional overhead and footprint in interpreted
 * mode. Thats why the factory methods of profiles can return implementations where profiling is
 * disabled. Using disabled profiles makes sense for runtimes that are unable to use the collected
 * profiling information. Even runtime implementations that are able to use this information might
 * decide to turn off profiling for benchmarking purposes.
 * </p>
 *
 * <p>
 * Profile subclasses:
 * <ul>
 * <li>{@link BranchProfile} to profile on unlikely branches like errors.</li>
 * <li>{@link ConditionProfile} to profile on conditionals or boolean values.</li>
 * <li>{@link LoopConditionProfile} to profile on conditionals of loops with special support for
 * counted loops.</li>
 * <li>{@link ValueProfile} to profile on properties like type and identity of values.</li>
 * <li>{@link ByteValueProfile} to profile on <code>byte</code> values.</li>
 * <li>{@link IntValueProfile} to profile on <code>int</code> values.</li>
 * <li>{@link LongValueProfile} to profile on <code>long</code> values.</li>
 * <li>{@link FloatValueProfile} to profile on <code>float</code> values.</li>
 * <li>{@link DoubleValueProfile} to profile on <code>double</code> values.</li>
 * <li>{@link PrimitiveValueProfile} to profile on objects by identity and on primitives by value.
 * </li>
 * </ul>
 * </p>
 *
 * @see Assumption
 * @since 0.10
 */
public abstract class Profile extends NodeCloneable {
    static boolean isProfilingEnabled() {
        boolean enabled;
        try {
            enabled = Truffle.getRuntime().isProfilingEnabled();
        } catch (LinkageError ex) {
            // running on old version of Graal
            enabled = true;
        }
        return enabled;
    }

    Profile() {
        /* We don't to allow custom profiles. We want to evolve this API further first. Sorry. */
    }

    String toStringDisabled(Class<?> profileClass) {
        return String.format("%s(DISABLED)", profileClass.getSimpleName());
    }

    String toString(Class<?> profileClass, boolean uninitialized, boolean generic, String specialization) {
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
