/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

/**
 * Regression test for GR-75389.
 * <p>
 * The test covers two cases where speculative guard movement replaced {@code Objects.checkIndex}
 * checks with unsound init checks. Both cases use a zero-extended 32-bit induction variable that
 * crosses zero.
 */
public class SpeculativeGuardMovementZeroExtendIVTest extends GraalCompilerTest {

    private OptionValues opts() {
        return new OptionValues(getInitialOptions(), GraalOptions.SpeculativeGuardMovement, true);
    }

    /**
     * {@code i & 0xFFFFFFFFL} canonicalizes to {@code ZeroExtend(i)}. Masking {@code bound} gives
     * the long bound the unsigned 32-bit range accepted by the {@code fitsIn32Bit(UNSIGNED)} check
     * in speculative guard movement, so speculative guard movement attempts to move the guard.
     * Unlike {@link #basicIVSnippet}, this keeps the bound non-constant.
     * <p>
     * Before GR-75389 was fixed, the output stamp of {@code ZeroExtend(i)} made this look like a
     * valid derived IV even when the 32-bit base crossed zero. With {@code start = -4},
     * {@code end = 5}, and {@code bound = 0xFFFFFFFEL}, speculative guard movement computed
     * the init as {@code 0xFFFFFFFCL}.
     * <p>
     * The fixed code rejects this as a zero-extend converted IV because the 32-bit input can be
     * negative. The original {@code Objects.checkIndex} is not replaced by a moved guard. It throws
     * at {@code i = -2} and {@code i = -1}.
     */
    public static long derivedIVSnippet(int start, int end, long bound) {
        long b = bound & 0xFFFFFFFFL;
        long s = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i < end); i++) {
            s += Objects.checkIndex(i & 0xFFFFFFFFL, b);
        }
        return s;
    }

    @Test
    public void testDerivedIV() throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod("derivedIVSnippet"), opts());
        Assert.assertTrue(code.isValid());
        try {
            Object r = code.executeVarargs(-4, 5, 0xFFFFFFFEL);
            Assert.fail("Objects.checkIndex(0xFFFFFFFEL, 0xFFFFFFFEL) did not throw after speculative guard movement hoisted the guard; returned 0x" + Long.toHexString((Long) r));
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    /**
     * Same shape with a constant bound: {@code IntegerBelowNode(ZeroExtend(i), 0xFFFFFFFEL)}
     * canonicalizes to {@code IntegerBelowNode(i:i32, -2:i32)}.
     * <p>
     * Before GR-75389 was fixed, speculative guard movement hoisted that compare via the
     * {@code BasicInductionVariable} path. It built the init check in the 32-bit domain as
     * {@code start |<| -2}, effectively zero-extending {@code start}.
     * <p>
     * For {@code start = -4}, the incorrect init check passed as {@code 0xFFFFFFFC |<| 0xFFFFFFFE}.
     * The moved guard missed the failing {@code Objects.checkIndex} iterations at {@code i = -2}
     * and {@code i = -1}.
     * <p>
     * The fixed code sign-extends {@code start} and zero-extends the bound before building the init
     * check. The corrected check is {@code -4L |<| 0xFFFFFFFEL}, so the moved guard fails for this
     * invocation, as intended.
     */
    public static long basicIVSnippet(int start, int end) {
        long s = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i < end); i++) {
            s += Objects.checkIndex(i & 0xFFFFFFFFL, 0xFFFFFFFEL);
        }
        return s;
    }

    @Test
    public void testBasicIV() throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod("basicIVSnippet"), opts());
        Assert.assertTrue(code.isValid());
        try {
            Object r = code.executeVarargs(-4, 5);
            Assert.fail("Objects.checkIndex(0xFFFFFFFEL, 0xFFFFFFFEL) did not throw after speculative guard movement hoisted the guard; returned 0x" + Long.toHexString((Long) r));
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }
}
