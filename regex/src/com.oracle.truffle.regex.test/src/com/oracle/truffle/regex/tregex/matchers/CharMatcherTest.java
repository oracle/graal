/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import org.junit.Assert;

public abstract class CharMatcherTest {

    public MatcherBuilder single(char i) {
        return range(i, i);
    }

    public MatcherBuilder range(char i, char j) {
        return MatcherBuilder.create(i, j);
    }

    public MatcherBuilder range(char[] i) {
        Assert.assertEquals(i.length, 2);
        return range(i[0], i[1]);
    }

    public MatcherBuilder multi(char... values) {
        assert (values.length & 1) == 0;
        return MatcherBuilder.create(values);
    }

    public void checkMatch(String errorMsg, MatcherBuilder m, char... values) {
        Assert.assertArrayEquals(matchError(errorMsg, m, values), values, m.getRanges());
    }

    private static String matchError(String errorMsg, MatcherBuilder m, char[] values) {
        StringBuilder sb = new StringBuilder(errorMsg).append(": got ").append(m.toString()).append(", expected [ ");
        for (int i = 0; i < values.length; i += 2) {
            sb.append("[").append(values[i]).append("-").append(values[i + 1]).append("] ");
        }
        return sb.append("]").toString();
    }

    public void checkContains(MatcherBuilder a, MatcherBuilder b, boolean expected) {
        boolean test = a.contains(b);
        Assert.assertEquals(a + ".contains(" + b + "): got " + test + ", expected " + expected, test, expected);
    }

    public void checkInverse(MatcherBuilder a, char... values) {
        checkMatch("inverse(" + a + ")", a.createInverse(new CompilationBuffer()), values);
    }

    public void checkIntersection(MatcherBuilder a, MatcherBuilder b, char... values) {
        checkMatch("intersection(" + a + "," + b + ")", a.createIntersectionMatcher(b, new CompilationBuffer()), values);
    }

    public void checkSubtraction(MatcherBuilder a, MatcherBuilder b, char... values) {
        checkMatch("subtraction(" + a + "," + b + ")", a.subtract(b, new CompilationBuffer()), values);
    }

    public void checkUnion(MatcherBuilder a, MatcherBuilder b, char... values) {
        checkMatch("union(" + a + "," + b + ")", a.union(b, new CompilationBuffer()), values);
    }
}
