/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr.utils.poolparsers;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.test.jfr.utils.JfrFileParser;
import com.oracle.svm.test.jfr.utils.RecordingInput;
import org.junit.Assert;

public abstract class ConstantPoolParser {

    /**
     * Set of ids found during parsing of current constant pool.
     */
    private final Set<Long> foundIds = new HashSet<>();

    /**
     * List of ids found during parsing of other constant pools that are referencing this one.
     */
    private final Set<Long> expectedIds = new HashSet<>();

    protected ConstantPoolParser() {
        foundIds.add(0L);
    }

    protected final void addFoundId(long id) {
        foundIds.add(id);
    }

    protected static void addExpectedId(JfrType typeId, long id) {
        ConstantPoolParser poolParser = JfrFileParser.getSupportedConstantPools().get(typeId.getId());
        poolParser.expectedIds.add(id);
    }

    public void compareFoundAndExpectedIds() {
        Assert.assertTrue("Error during parsing " + this + " constant pool!" +
                        " Expected IDs: " + expectedIds +
                        ". Found IDs: " + foundIds, foundIds.size() == 0 || foundIds.containsAll(expectedIds));
    }

    public abstract void parse(RecordingInput input) throws IOException;

    @Override
    public String toString() {
        return getClass().getName();
    }
}
