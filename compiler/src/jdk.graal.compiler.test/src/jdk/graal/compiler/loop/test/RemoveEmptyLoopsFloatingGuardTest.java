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
package jdk.graal.compiler.loop.test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests the propagation of a speculative type check from an inlined helper.
 */
public class RemoveEmptyLoopsFloatingGuardTest extends GraalCompilerTest {

    /**
     * Supplies a force inlined cast with a monomorphic warmup profile.
     */
    private static final class ProfiledCharsetCast {
        /**
         * Performs the type check whose profile is used when this helper is inlined.
         */
        @BytecodeParserForceInline
        static int cast(Charset value) {
            Charset charset = Objects.requireNonNull(value);
            return charset.getClass() == StandardCharsets.ISO_8859_1.getClass() ? 1 : 2;
        }
    }

    /**
     * Models the loop and uncommon exit preceding the call in {@code java.util.jar.Attributes.read}.
     */
    private static int profiledCharsetCastAfterLoop(byte[] buffer, int length, int lines) {
        int result = 0;
        for (int line = 0; line < lines; line++) {
            GraalDirectives.sideEffect();
            int i = 0;
            /*
             * Keep both uncommon exits as explicit deoptimizations. This models the two
             * UnreachedCode paths in the profiled Attributes.read graph and preserves their
             * interaction with the TypeCheckedInliningViolated guard.
             */
            while (GraalDirectives.injectBranchProbability(0.9, buffer[i++] != ':')) {
                if (GraalDirectives.injectBranchProbability(0.0, i >= length)) {
                    GraalDirectives.deoptimize(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.UnreachedCode, false);
                    throw new IllegalArgumentException("invalid header field before colon");
                }
            }
            if (GraalDirectives.injectBranchProbability(1.0, buffer[i++] != ' ')) {
                GraalDirectives.deoptimize(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.UnreachedCode, false);
                throw new IllegalArgumentException("invalid header field after colon");
            }
            int castValue = ProfiledCharsetCast.cast(StandardCharsets.UTF_8);
            result += i + castValue;
        }
        return result;
    }

    @Test
    public void testRemoveEmptyLoopWithFloatingGuard() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.GuardPriorities, false);
        ResolvedJavaMethod cast = getResolvedJavaMethod(ProfiledCharsetCast.class, "cast");
        for (int i = 0; i < 10_000; i++) {
            ProfiledCharsetCast.cast(StandardCharsets.ISO_8859_1);
        }
        getCode(cast, null, true, false, options);
        ResolvedJavaMethod method = getResolvedJavaMethod("profiledCharsetCastAfterLoop");
        getCode(method, options);

        test(options, "profiledCharsetCastAfterLoop", new byte[]{'x', ':', ' '}, 3, 1);
    }
}
