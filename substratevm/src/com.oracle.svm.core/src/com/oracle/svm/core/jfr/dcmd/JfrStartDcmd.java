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
import com.oracle.svm.core.dcmd.AbstractDcmd;
import com.oracle.svm.core.dcmd.DcmdParseException;
import java.util.Arrays;
import com.oracle.svm.core.util.BasedOnJDKFile;

import com.oracle.svm.core.jfr.JfrManager;

public class JfrStartDcmd extends AbstractDcmd {

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+2/src/jdk.jfr/share/classes/jdk/jfr/internal/dcmd/DCmdStart.java#L348-L461") //
    public JfrStartDcmd() {
        this.options = new DcmdOption[]{
                        new DcmdOption("delay", "Length of time to wait before starting to record\n" +
                                        "                  (INTEGER followed by 's' for seconds 'm' for minutes or h' for\n" +
                                        "                  hours, 0s)", false, "0s"),
                        new DcmdOption("disk", "Flag for also writing the data to disk while recording\n" +
                                        "                  (BOOLEAN)", false, "true"),
                        new DcmdOption("dumponexit", "Flag for writing the recording to disk when the Java\n" +
                                        "                  Virtual Machine (JVM) shuts down. If set to 'true' and no value\n" +
                                        "                  is given for filename, the recording is written to a file in the\n" +
                                        "                  directory where the process was started. The file name is a\n" +
                                        "                  system-generated name that contains the process ID, the recording\n" +
                                        "                  ID and the current time stamp. (For example:\n" +
                                        "                  id-1-2021_09_14_09_00.jfr) (BOOLEAN)", false, "false"),
                        new DcmdOption("duration", "Length of time to record. Note that 0s means forever\n" +
                                        "                  (INTEGER followed by 's' for seconds 'm' for minutes or 'h' for\n" +
                                        "                  hours)", false, "0s"),
                        new DcmdOption("filename", "Name of the file to which the flight recording data is\n" +
                                        "                  written when the recording is stopped. If no filename is given, a\n" +
                                        "                  filename is generated from the PID and the current date and is\n" +
                                        "                  placed in the directory where the process was started. The\n" +
                                        "                  filename may also be a directory in which case, the filename is\n" +
                                        "                  generated from the PID and the current date in the specified\n" +
                                        "                  directory.", false, null),
                        new DcmdOption("maxage", "Maximum time to keep the recorded data on disk. This\n" +
                                        "                  parameter is valid only when the disk parameter is set to true.\n" +
                                        "                  Note 0s means forever. (INTEGER followed by 's' for seconds 'm'\n" +
                                        "                  for minutes or 'h' for hours, 0s)", false, "No max age."),
                        new DcmdOption("maxsize", "Maximum size of the data to keep on disk in bytes if\n" +
                                        "                  one of the following suffixes is not used: 'm' or 'M' for\n" +
                                        "                  megabytes OR 'g' or 'G' for gigabytes. This parameter is valid\n" +
                                        "                  only when the disk parameter is set to 'true'. The value must not\n" +
                                        "                  be less than the value for the maxchunksize parameter set with\n" +
                                        "                  the JFR.configure command.", false, "No max size"),
                        new DcmdOption("name", "Name of the recording. If no name is provided, a name\n" +
                                        "                  is generated. Make note of the generated name that is shown in\n" +
                                        "                  the response to the command so that you can use it with other\n" +
                                        "                  commands.", false, "System-generated default name"),
                        new DcmdOption("path-to-gc-root", "Flag for saving the path to garbage collection (GC)\n" +
                                        "                  roots at the end of a recording. The path information is useful\n" +
                                        "                  for finding memory leaks but collecting it is time consuming.\n" +
                                        "                  Turn on this flag only when you have an application that you\n" +
                                        "                  suspect has a memory leak. If the settings parameter is set to\n" +
                                        "                  'profile', then the information collected includes the stack\n" +
                                        "                  trace from where the potential leaking object wasallocated. (BOOLEAN)", false, "false"),
                        new DcmdOption("settings", " Name of the settings file that identifies which events\n" +
                                        "                  to record. To specify more than one file, use the settings\n" +
                                        "                  parameter repeatedly. Include the path if the file is not in\n" +
                                        "                  JAVA-HOME/lib/jfr. The following profiles are included with the\n" +
                                        "                  JDK in the JAVA-HOME/lib/jfr directory: 'default.jfc': collects a\n" +
                                        "                  predefined set of information with low overhead, so it has minimal\n" +
                                        "                  impact on performance and can be used with recordings that run\n" +
                                        "                  continuously; 'profile.jfc': Provides more data than the\n" +
                                        "                  'default.jfc' profile, but with more overhead and impact on\n" +
                                        "                  performance. Use this configuration for short periods of time\n" +
                                        "                  when more information is needed. Use none to start a recording\n" +
                                        "                  without a predefined configuration file. (STRING)", false, "JAVA-HOME/lib/jfr/default.jfc")
        };
        this.examples = new String[]{
                        "$ jcmd <pid> JFR.start",
                        "$ jcmd <pid> JFR.start filename=dump.jfr",
                        "$ jcmd <pid> JFR.start filename=/directory/recordings",
                        "$ jcmd <pid> JFR.start maxage=1h maxsize=1000M",
                        "$ jcmd <pid> JFR.start delay=5m settings=my.jfc"
        };
        this.name = "JFR.start";
        this.description = "Starts a new JFR recording.";
        this.impact = "medium";
    }

    @Override
    public String parseAndExecute(String[] arguments) throws DcmdParseException {
        String recordingName = JfrManager.initRecording(Arrays.copyOfRange(arguments, 1, arguments.length));
        return "Started recording " + recordingName + "\n";
    }

    @Override
    public String getName() {
        return "JFR.start";
    }

    @Override
    public String getImpact() {
        return "Medium";
    }
}
