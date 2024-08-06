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

package jdk.graal.compiler.debug.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.debug.StandardPathUtilitiesProvider;

public class StandardPathUtilitiesProviderTest {

    @Test
    public void sanitizesPathName() {
        StandardPathUtilitiesProvider sut = new StandardPathUtilitiesProvider();
        String sanitized = sut.sanitizeFileName("\0null");
        Assert.assertEquals("_null", sanitized);

        sanitized = sut.sanitizeFileName("path/with/slashes");
        Assert.assertEquals("path_with_slashes", sanitized);
    }

    @Test
    public void sanitizesPathNameOnWindows() {
        Assume.assumeTrue(System.getProperty("os.name", "").startsWith("Windows"));

        // Windows is pickier regarding path names
        StandardPathUtilitiesProvider sut = new StandardPathUtilitiesProvider();
        String sanitized = sut.sanitizeFileName("<lessthan>greaterthan:colon|bar*asterisk?question");
        Assert.assertEquals("_lessthan_greaterthan_colon_bar_asterisk_question", sanitized);
    }
}
