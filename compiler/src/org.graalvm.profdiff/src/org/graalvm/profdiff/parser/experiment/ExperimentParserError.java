/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.parser.experiment;

import org.graalvm.profdiff.core.ExperimentId;

/**
 * Indicates that an experiment could not be parsed.
 */
@SuppressWarnings("serial")
public class ExperimentParserError extends Exception {
    /**
     * Constructs an experiment parser error.
     *
     * @param experimentId the ID of the experiment that could not be parsed
     * @param resourceName the name of the resource from the experiment that could not be parsed
     * @param message a clarification of the error
     */
    ExperimentParserError(ExperimentId experimentId, String resourceName, String message) {
        super("Failed to parse experiment " + experimentId + " in " + resourceName + ": " + message);
    }
}
