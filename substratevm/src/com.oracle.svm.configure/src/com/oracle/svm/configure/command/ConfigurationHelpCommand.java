/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.configure.command;

import java.util.Collection;
import java.util.Iterator;

import com.oracle.svm.configure.ConfigurationTool;

public class ConfigurationHelpCommand extends ConfigurationCommand {
    @Override
    public String getName() {
        return "help";
    }

    @Override
    public void apply(Iterator<String> argumentsIterator) {
        Collection<ConfigurationCommand> commands = ConfigurationTool.getCommands();
        System.out.println();
        System.out.println(getToolName());
        System.out.println();
        System.out.println(getToolDescription());
        System.out.println();
        System.out.print("Usage: ");
        commands.forEach(command -> System.out.println(command.getUsage()));
        System.out.println();
        commands.forEach(command -> System.out.println(command.getDescription()));
    }

    @Override
    public String getUsage() {
        return "native-image-configure help";
    }

    @Override
    protected String getDescription0() {
        return "                      prints this help message." + System.lineSeparator();
    }

    protected String getToolName() {
        return "GraalVM native-image-configure tool";
    }

    protected String getToolDescription() {
        return "This tool can be used to prepare a configuration of JNI, reflection and" + System.lineSeparator() +
                        "resources for a native-image build.";
    }
}
