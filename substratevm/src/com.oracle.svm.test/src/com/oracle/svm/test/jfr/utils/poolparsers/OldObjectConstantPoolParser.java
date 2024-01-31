/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.test.jfr.utils.JfrFileParser;
import com.oracle.svm.test.jfr.utils.RecordingInput;

public final class OldObjectConstantPoolParser extends AbstractRepositoryParser {
    public OldObjectConstantPoolParser(JfrFileParser parser) {
        super(parser);
    }

    @Override
    public void parse(RecordingInput input) throws IOException {
        int numOldObjects = input.readInt();
        for (int i = 0; i < numOldObjects; i++) {
            addFoundId(input.readLong());
            assertTrue("Address can't be 0", input.readLong() != 0);
            addExpectedId(JfrType.Class, input.readLong());
            input.readUTF(); // description
            input.readLong(); // GC root
        }
    }
}
