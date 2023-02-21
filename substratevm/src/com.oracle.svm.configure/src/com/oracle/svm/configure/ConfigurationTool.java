/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.oracle.svm.configure.command.ConfigurationCommand;
import com.oracle.svm.configure.command.ConfigurationCommandFileCommand;
import com.oracle.svm.configure.command.ConfigurationGenerateCommand;
import com.oracle.svm.configure.command.ConfigurationGenerateConditionalsCommand;
import com.oracle.svm.configure.command.ConfigurationGenerateFiltersCommand;
import com.oracle.svm.configure.command.ConfigurationHelpCommand;
import com.oracle.svm.configure.command.ConfigurationProcessTraceCommand;
import com.oracle.svm.configure.command.ConfigurationUnknownCommand;

public class ConfigurationTool {
    private static final int USAGE_ERROR_CODE = 2;
    private static final int INTERNAL_ERROR_CODE = 1;

    protected static final Map<String, ConfigurationCommand> commands = new HashMap<>();
    private static final ConfigurationUnknownCommand unknownCommand = new ConfigurationUnknownCommand();
    private static final ConfigurationCommandFileCommand commandFileCommand = new ConfigurationCommandFileCommand();

    static {
        ConfigurationCommand helpCommand = new ConfigurationHelpCommand();
        ConfigurationCommand generateCommand = new ConfigurationGenerateCommand();
        ConfigurationCommand processTraceCommand = new ConfigurationProcessTraceCommand();
        ConfigurationCommand generateFiltersCommand = new ConfigurationGenerateFiltersCommand();
        ConfigurationCommand conditionalsCommand = new ConfigurationGenerateConditionalsCommand();

        commands.put(helpCommand.getName(), helpCommand);
        commands.put(generateCommand.getName(), generateCommand);
        commands.put(commandFileCommand.getName(), commandFileCommand);
        commands.put(processTraceCommand.getName(), processTraceCommand);
        commands.put(conditionalsCommand.getName(), conditionalsCommand);
        commands.put(generateFiltersCommand.getName(), generateFiltersCommand);
    }

    public static Collection<ConfigurationCommand> getCommands() {
        return commands.values();
    }

    public static void main(String[] arguments) {
        try {
            if (arguments.length == 0) {
                throw new ConfigurationUsageException("No arguments provided.");
            }
            Iterator<String> argumentsIterator = Arrays.asList(arguments).iterator();
            String command = argumentsIterator.next();

            if (command.equals(commandFileCommand.getName())) {
                argumentsIterator = ConfigurationCommandFileCommand.handleCommandFile(argumentsIterator);
                if (!argumentsIterator.hasNext()) {
                    throw new ConfigurationUsageException("No arguments provided in the command file.");
                }
                command = argumentsIterator.next();
            }
            commands.getOrDefault(command, unknownCommand).apply(argumentsIterator);
        } catch (ConfigurationUsageException e) {
            System.err.println(e.getMessage() + System.lineSeparator() +
                            "Use 'native-image-configure help' for usage.");
            System.exit(USAGE_ERROR_CODE);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(INTERNAL_ERROR_CODE);
        }
    }
}
