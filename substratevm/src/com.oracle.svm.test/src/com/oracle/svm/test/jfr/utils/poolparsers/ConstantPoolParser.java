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

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;

import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.test.jfr.utils.JfrFileParser;
import com.oracle.svm.test.jfr.utils.RecordingInput;

public abstract class ConstantPoolParser {
    private final JfrFileParser parser;
    private final Set<Long> reservedIds = new HashSet<>();
    private final Set<Long> foundIds = new HashSet<>();
    private final Set<Long> expectedIds = new HashSet<>();

    protected ConstantPoolParser(JfrFileParser parser, long... reservedIds) {
        this.parser = parser;
        for (long reservedId : reservedIds) {
            this.reservedIds.add(reservedId);
        }
    }

    public boolean isEmpty() {
        return foundIds.isEmpty();
    }

    protected final void addFoundId(long id) {
        foundIds.add(id);
        assertFalse("ID " + id + " is reserved and must not be found in " + this + " constant pool.", reservedIds.contains(id));
    }

    protected void addExpectedId(JfrType typeId, long id) {
        ConstantPoolParser poolParser = parser.getSupportedConstantPools().get(typeId.getId());
        poolParser.expectedIds.add(id);
    }

    public void compareFoundAndExpectedIds() {
        HashSet<Long> missingIds = new HashSet<>(expectedIds);
        missingIds.removeAll(reservedIds);
        missingIds.removeAll(foundIds);

        if (!missingIds.isEmpty()) {
            Assert.fail("Error during parsing " + this + " constant pool! Missing IDs: " + missingIds + ". Expected IDs: " + expectedIds + ". Found IDs: " + foundIds);
        }
    }

    public abstract void parse(RecordingInput input) throws IOException;

    @Override
    public String toString() {
        return getClass().getName();
    }
}
