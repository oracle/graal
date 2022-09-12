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
package org.graalvm.profdiff.parser.args;

/**
 * Builds and parses program arguments.
 */
public class ProgramArgumentParser extends ArgumentParser {
    /**
     * The name of the program.
     */
    private final String prog;

    /**
     * The description of the program.
     */
    private final String description;

    /**
     * Constructs an argument parser.
     *
     * @param prog the name of the program
     * @param description the description of the program
     */
    public ProgramArgumentParser(String prog, String description) {
        this.prog = prog;
        this.description = description;
    }

    /**
     * Creates a usage string describing the program arguments.
     *
     * @return the usage string
     */
    public String createUsage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage: ").append(prog);
        if (!optionArguments.values().isEmpty()) {
            sb.append(" [options]");
        }
        for (Argument argument : positionalArguments) {
            sb.append(' ').append(argument.getName());
        }
        sb.append("\n\n").append(description).append("\n\nOptions:\n");
        for (Argument argument : optionArguments.values()) {
            sb.append(String.format("  %-20s ", argument.getName())).append(argument.getHelp()).append('\n');
        }
        if (getSubparserGroup().isPresent()) {
            sb.append("\n\n").append(getSubparserGroup().get().createUsage());
        }
        return sb.toString();
    }
}
