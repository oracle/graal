/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import com.oracle.truffle.compiler.TruffleCompiler;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.vm.ci.code.InstalledCode;

import java.util.Objects;

public class TruffleInstalledCodeTest extends PartialEvaluationTest {

    /**
     * Creates a string of length {@code len} whose prefix is repeated 'X's and whose suffix is
     * {@code suffix}.
     */
    private static String makeLongName(int len, String suffix) {
        return "X".repeat(len - suffix.length()) + suffix;
    }

    /**
     * Tests that {@link TruffleCompilerImpl#asInstalledCodeName(String)} returns a valid name for
     * {@link InstalledCode#InstalledCode(String)}.
     */
    @Test
    public void testLongName() {
        String[] suffixes = {TruffleCompiler.FIRST_TIER_COMPILATION_SUFFIX, TruffleCompiler.SECOND_TIER_COMPILATION_SUFFIX};
        for (String suffix : suffixes) {
            String[] names = {
                            makeLongName(TruffleCompilerImpl.MAX_NAME_LENGTH - 1, suffix),
                            makeLongName(TruffleCompilerImpl.MAX_NAME_LENGTH, suffix),
                            makeLongName(TruffleCompilerImpl.MAX_NAME_LENGTH + 1, suffix)
            };
            for (String name : names) {
                String installedCodeName = TruffleCompilerImpl.asInstalledCodeName(name);
                Assert.assertTrue(installedCodeName, installedCodeName.endsWith(suffix));
                if (name.length() <= TruffleCompilerImpl.MAX_NAME_LENGTH) {
                    Assert.assertEquals("unexpected truncation", installedCodeName, name);
                } else {
                    Assert.assertNotEquals("unexpected truncation", installedCodeName, name);
                }

                // Must not throw an exception
                InstalledCode obj = new InstalledCode(installedCodeName);
                Objects.requireNonNull(obj);
            }
        }
    }
}
