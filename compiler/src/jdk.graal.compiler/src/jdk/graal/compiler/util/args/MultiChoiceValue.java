/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.args;

import java.io.PrintWriter;

import org.graalvm.collections.EconomicMap;

/**
 * Option value with a set number of named alternatives, which are set through calls to
 * {@link #addChoice}. Intended for enum-like options, as all choice values have to be constructed
 * in advance.
 */
public class MultiChoiceValue<T> extends OptionValue<T> {

    private final EconomicMap<String, T> choices = EconomicMap.create();
    private final EconomicMap<String, String> choiceHelp = EconomicMap.create();
    private String defaultChoice = null;

    public MultiChoiceValue(String name, String help) {
        super(name, help);
    }

    public MultiChoiceValue(String name, T defaultValue, String help) {
        super(name, defaultValue, help);
    }

    @Override
    public boolean parseValue(String arg) throws InvalidArgumentException {
        if (arg == null) {
            value = defaultValue;
            return false;
        }
        value = choices.get(arg);
        if (value == null) {
            throw new InvalidArgumentException(getName(), String.format("no choice named '%s'", arg));
        }
        return true;
    }

    /**
     * Adds a choice to the set of alternatives for this option.
     *
     * @param name user-visible name for the alternative, as will be parsed from the command-line.
     * @param choiceValue value that will be returned if the given choice is selected.
     * @param help help text for the choice in question.
     * @return {@code this}
     */
    public MultiChoiceValue<T> addChoice(String name, T choiceValue, String help) {
        choices.put(name, choiceValue);
        choiceHelp.put(name, help);
        if (choiceValue.equals(defaultValue)) {
            defaultChoice = name;
        }
        return this;
    }

    @Override
    public void printUsage(PrintWriter writer, boolean detailed) {
        super.printUsage(writer, false);
        if (!detailed) {
            return;
        }
        writer.append(" {");
        String sep = "";
        for (String choice : choices.getKeys()) {
            writer.append(sep);
            writer.append(choice);
            sep = ",";
        }
        writer.append('}');
        if (defaultChoice != null) {
            writer.append(String.format(" (default: \"%s\")", defaultChoice));
        }
    }

    @Override
    public void printHelp(PrintWriter writer, int indentLevel) {
        super.printHelp(writer, indentLevel);
        var it = choiceHelp.getEntries();
        while (it.advance()) {
            String help = String.format("%s: %s", it.getKey(), it.getValue());
            OptionValue.printIndented(writer, help, indentLevel + 1);
        }
    }
}
