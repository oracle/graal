/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;

import org.graalvm.collections.Pair;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that the cache used by the compiler tests properly handles different options.
 */
public class CacheResetTest extends GraalCompilerTest {

    public static void snippet() {
        // do nothing
    }

    private static final String SNIPPET_NAME = "snippet";

    @Test
    public void testSameOptionsReuse() {
        final ResolvedJavaMethod installedCodeOwner = getResolvedJavaMethod(SNIPPET_NAME);

        OptionValues opt = new OptionValues(getInitialOptions(), HighTier.Options.Inline, true);

        test(opt, SNIPPET_NAME);
        InstalledCode firstInstalled = getCachedCode(installedCodeOwner);

        test(opt, SNIPPET_NAME);
        InstalledCode secondInstalled = getCachedCode(installedCodeOwner);

        assert firstInstalled.isValid();
        assert secondInstalled.isValid();

        Assert.assertEquals(firstInstalled.getStart(), secondInstalled.getStart());
    }

    @Test
    public void testSameOptionsDeepReuse() {
        final ResolvedJavaMethod installedCodeOwner = getResolvedJavaMethod(SNIPPET_NAME);

        OptionValues opt = new OptionValues(getInitialOptions(), HighTier.Options.Inline, true);
        test(opt, SNIPPET_NAME);
        InstalledCode firstInstalled = getCachedCode(installedCodeOwner);

        OptionValues opt1 = new OptionValues(getInitialOptions(), HighTier.Options.Inline, true);
        test(opt1, SNIPPET_NAME);
        InstalledCode secondInstalled = getCachedCode(installedCodeOwner);

        assert firstInstalled.isValid();
        assert secondInstalled.isValid();

        Assert.assertEquals(firstInstalled.getStart(), secondInstalled.getStart());
    }

    private static InstalledCode getCachedCode(ResolvedJavaMethod method) {
        HashMap<ResolvedJavaMethod, Pair<OptionValues, InstalledCode>> tlCache = cache.get();
        Pair<OptionValues, InstalledCode> cached = tlCache.get(method);
        return cached.getRight();
    }

    @Test
    public void testDifferentOptionsNoReuse() {
        final ResolvedJavaMethod installedCodeOwner = getResolvedJavaMethod(SNIPPET_NAME);

        OptionValues opt = new OptionValues(getInitialOptions(), HighTier.Options.Inline, true);
        test(opt, SNIPPET_NAME);
        InstalledCode firstInstalled = getCachedCode(installedCodeOwner);

        OptionValues opt1 = new OptionValues(getInitialOptions(), HighTier.Options.Inline, false);
        test(opt1, SNIPPET_NAME);
        InstalledCode secondInstalled = getCachedCode(installedCodeOwner);

        assert firstInstalled.isValid();
        assert secondInstalled.isValid();

        Assert.assertNotEquals(firstInstalled.getStart(), secondInstalled.getStart());
    }
}
