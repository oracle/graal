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
package org.graalvm.profdiff.command;

import org.graalvm.profdiff.core.HotCompilationUnitPolicy;
import org.graalvm.profdiff.parser.args.ArgumentParser;
import org.graalvm.profdiff.util.Writer;

/**
 * Represents one use case of the program that can be invoked from the command line using
 * {@link #getName() the command's name}. A command may define additional arguments, which are
 * parsed using {@link #getArgumentParser() its argument parser}.
 */
public interface Command {
    /**
     * Gets the string that invokes this command from the command line.
     */
    String getName();

    /**
     * Gets the help message describing the purpose of the command.
     */
    String getDescription();

    /**
     * Gets the argument parser of the command, which parses the arguments that come just after the
     * invocation of this command in the command line.
     *
     * @return the argument parser that parses the arguments belonging to this command
     */
    ArgumentParser getArgumentParser();

    /**
     * Performs the action of the command. Called if the command was selected on the command line
     * and after the command's arguments have been parsed using {@link #getArgumentParser() its
     * argument parser}.
     *
     * @param writer the writer to use for standard output of the command
     */
    void invoke(Writer writer) throws Exception;

    /**
     * Sets the {@link HotCompilationUnitPolicy} for the command. The policy's parameters are parsed
     * in the common part of the command line.
     *
     * @param hotCompilationUnitPolicy the policy of marking hot methods to be used by this command
     */
    void setHotCompilationUnitPolicy(HotCompilationUnitPolicy hotCompilationUnitPolicy);
}
