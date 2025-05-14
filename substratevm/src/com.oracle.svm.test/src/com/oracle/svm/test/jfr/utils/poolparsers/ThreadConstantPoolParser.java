/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Red Hat Inc. All rights reserved.
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

import org.junit.Assert;

import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.test.jfr.utils.JfrFileParser;
import com.oracle.svm.test.jfr.utils.RecordingInput;

public class ThreadConstantPoolParser extends AbstractRepositoryParser {
    public ThreadConstantPoolParser(JfrFileParser parser) {
        super(parser);
    }

    @Override
    public void parse(RecordingInput input) throws IOException {
        int numberOfThread = input.readInt();
        for (int i = 0; i < numberOfThread; i++) {
            addFoundId(input.readLong()); // ThreadId.
            input.readUTF(); // OSThreadName.
            Assert.assertTrue("OSThreadId is not correct!", input.readLong() >= 0); // OSThreadId.
            input.readUTF(); // JavaThreadName.
            Assert.assertTrue("JavaThreadId is not correct!", input.readLong() >= 0); // JavaThreadId.
            addExpectedId(JfrType.ThreadGroup, input.readLong()); // ThreadGroupId.
            input.readBoolean();
        }
    }
}
