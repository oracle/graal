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

import com.oracle.svm.core.dcmd.DcmdOption;
import com.oracle.svm.core.jfr.JfrArgumentParser.JfrArgument;
import com.oracle.svm.core.jfr.JfrArgumentParser.JfrArgumentParsingFailed;
import com.oracle.svm.core.dcmd.AbstractDcmd;
import com.oracle.svm.core.dcmd.DcmdParseException;
import com.oracle.svm.core.util.BasedOnJDKFile;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.internal.SecuritySupport;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static com.oracle.svm.core.jfr.JfrArgumentParser.parseJfrOptions;
import static com.oracle.svm.core.jfr.JfrArgumentParser.StopArgument;
import static com.oracle.svm.core.jfr.JfrManager.resolvePath;

public class JfrStopDcmd extends AbstractDcmd {

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.jfr/share/classes/jdk/jfr/internal/dcmd/DCmdStop.java#L74-L100") //
    public JfrStopDcmd() {
        this.options = new DcmdOption[]{
                        new DcmdOption("filename", "Name of the file to which the recording is written when the\n" +
                                        "            recording is stopped. If no path is provided here or when the recording was started,\n" +
                                        "            the data from the recording is discarded.", false, null),
                        new DcmdOption("name", "Name of the recording to stop.", true, null)
        };
        this.examples = new String[]{
                        "$ jcmd <pid> JFR.stop name=1",
                        "$ jcmd <pid> JFR.stop name=benchmark filename=/directory/recordings",
                        "$ jcmd <pid> JFR.stop name=5 filename=recording.jfr"
        };
        this.name = "JFR.stop";
        this.description = "Stops a JFR recording.";
        this.impact = "low";
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.jfr/share/classes/jdk/jfr/internal/dcmd/DCmdStop.java#L44-L71") //
    @Override
    public String parseAndExecute(String[] arguments) throws DcmdParseException {
        String response;
        if (arguments.length > 3) {
            throw new DcmdParseException("Too many arguments specified");
        }

        try {
            Map<JfrArgument, String> stopArgs = parseJfrOptions(Arrays.copyOfRange(arguments, 1, arguments.length), StopArgument.values());
            String recordingName = stopArgs.get(StopArgument.Name);
            String filename = stopArgs.get(StopArgument.Filename);
            Recording target = null;

            if (recordingName == null) {
                throw new DcmdParseException("The name of the recording to stop is required but was not specified.");
            }

            for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
                if (recording.getName().equals(recordingName)) {
                    target = recording;
                    break;
                }
            }
            if (target == null) {
                throw new DcmdParseException("Could not find specified recording with name: " + recordingName);
            }

            if (filename != null) {
                SecuritySupport.SafePath safePath = resolvePath(target, filename);
                target.setDestination(safePath.toPath());
            }

            target.stop();
            target.close();
            response = "Stopped recording: " + target.getName();

        } catch (JfrArgumentParsingFailed e) {
            throw new DcmdParseException("Invalid arguments provided: " + e.getMessage());
        } catch (NumberFormatException e) {
            throw new DcmdParseException("Invalid recording name specified: " + e.getMessage());
        } catch (IOException e) {
            throw new DcmdParseException("Invalid dump path specified: " + e.getMessage());
        }
        return response + "\n";
    }
}
