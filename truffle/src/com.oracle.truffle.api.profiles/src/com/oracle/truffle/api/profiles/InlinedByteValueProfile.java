/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.InlineSupport.ByteField;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.dsl.InlineSupport.StateField;
import com.oracle.truffle.api.nodes.Node;

/**
 * Specialized value profile to capture certain properties of <code>byte</code> runtime values.
 * Value profiles require a runtime check in their initialized state to verify their profiled
 * assumption. Value profiles are limited to capture monomorphic profiles only. This means that if
 * two or more values are profiled within a single profile then the profile has no effect. If the
 * value assumption is invalidated in compiled code then it is invalidated.
 *
 * <p>
 * <b> Usage example: </b>
 *
 * <pre>
 * abstract class ByteProfileNode extends Node {
 *
 *     abstract byte execute(byte input);
 *
 *     &#064;Specialization
 *     byte doDefault(byte input, &#064;Cached InlinedByteValueProfile profile) {
 *         byte profiledValue = profile.profile(this, input);
 *         // compiler may now see profiledValue as a partial evaluation constant
 *         return profiledValue;
 *     }
 * }
 * </pre>
 *
 * @since 23.0
 */
public final class InlinedByteValueProfile extends AbstractInlinedValueProfile {

    private static final InlinedByteValueProfile DISABLED;
    static {
        InlinedByteValueProfile profile = new InlinedByteValueProfile();
        DISABLED = profile;
    }

    private final ByteField cachedValue;

    private InlinedByteValueProfile() {
        super();
        this.cachedValue = null;
    }

    private InlinedByteValueProfile(InlineTarget target) {
        super(target);
        this.cachedValue = target.getPrimitive(1, ByteField.class);
    }

    /** @since 23.0 */
    public byte profile(Node node, byte value) {
        if (this.state != null) {
            int localState = this.state.get(node);
            if (localState != GENERIC) {
                if (localState == SPECIALIZED) {
                    byte v = this.cachedValue.get(node);
                    if (v == value) {
                        return v;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (localState == UNINITIALIZED) {
                    this.state.set(node, SPECIALIZED);
                    this.cachedValue.set(node, value);
                } else {
                    this.state.set(node, GENERIC);
                    this.cachedValue.set(node, (byte) 0);
                }
            }
        }
        return value;
    }

    byte getCachedValue(Node node) {
        return this.cachedValue.get(node);
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public String toString(Node node) {
        if (state == null) {
            return toStringDisabled();
        }
        return toString(ByteValueProfile.class, isUninitialized(node), isGeneric(node), //
                        String.format("value == (byte)%s", getCachedValue(node)));
    }

    /**
     * Returns an inlined version of the profile. This version is automatically used by Truffle DSL
     * node inlining.
     *
     * @since 23.0
     */
    public static InlinedByteValueProfile inline(
                    @RequiredField(value = StateField.class, bits = REQUIRED_STATE_BITS) //
                    @RequiredField(value = ByteField.class) InlineTarget target) {
        if (Profile.isProfilingEnabled()) {
            return new InlinedByteValueProfile(target);
        } else {
            return getUncached();
        }
    }

    /**
     * Returns the uncached version of the profile. The uncached version of a profile does not
     * perform any profiling.
     *
     * @since 23.0
     */
    public static InlinedByteValueProfile getUncached() {
        return DISABLED;
    }

}
