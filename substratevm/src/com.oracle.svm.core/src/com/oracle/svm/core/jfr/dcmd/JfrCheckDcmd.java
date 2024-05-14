/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.dcmd;

import com.oracle.svm.core.dcmd.AbstractDcmd;
import com.oracle.svm.core.dcmd.DcmdParseException;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

import java.util.List;

public class JfrCheckDcmd extends AbstractDcmd {

    public JfrCheckDcmd() {
        this.name = "JFR.check";
        this.description = "Checks running JFR recording(s)";
        this.impact = "low";
    }

    @Override
    public String parseAndExecute(String[] arguments) throws DcmdParseException {
        if (arguments.length > 1) {
            throw new DcmdParseException("Too many arguments specified");
        }
        StringBuilder sb = new StringBuilder();
        List<Recording> recordings = FlightRecorder.getFlightRecorder().getRecordings();

        if (recordings.isEmpty()) {
            return "No recordings.";
        }

        for (Recording recording : recordings) {
            sb.append("Recording \"").append(recording.getId()).append("\": name=").append(recording.getName());
            sb.append(" maxsize=").append(recording.getMaxSize()).append("B");
            sb.append(" (").append(recording.getState().toString()).append(")\n");
        }
        return sb.toString();
    }
}
