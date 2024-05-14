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

package com.oracle.svm.core.dcmd;

public abstract class AbstractDcmd implements Dcmd {
    protected DcmdOption[] options;
    protected String[] examples;
    protected String name;
    protected String description;
    protected String impact;

    @Override
    public DcmdOption[] getOptions() {
        return options;
    }

    @Override
    public String[] getExample() {
        return examples;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getImpact() {
        return impact;
    }

    @Override
    public String printHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append("\n");
        if (getDescription() != null) {
            sb.append(getDescription()).append("\n");
        }
        sb.append("Impact: ").append(this.getImpact()).append("\n");
        sb.append("Syntax: ").append(getName());

        if (getOptions() != null) {
            sb.append(" [options]\n");
            sb.append("Options:\n");
            for (DcmdOption option : this.getOptions()) {
                sb.append("\t").append(option.getName()).append(": ");
                if (option.isRequired()) {
                    sb.append("[Required] ");
                } else {
                    sb.append("[Optional] ");
                }
                sb.append(option.getDescription());
                if (option.getDefaultValue() != null) {
                    sb.append(" Default value: ").append(option.getDefaultValue());
                }
                sb.append("\n");
            }
        }
        sb.append("\n");

        if (getExample() != null) {
            sb.append("Examples:\n");
            for (String example : this.getExample()) {
                sb.append("\t").append(example).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
