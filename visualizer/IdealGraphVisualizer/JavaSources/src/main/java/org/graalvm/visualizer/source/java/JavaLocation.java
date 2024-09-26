/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.source.java;

import org.graalvm.visualizer.source.SpecificLocationInfo;
import org.netbeans.api.java.source.TreePathHandle;

/**
 * Specific information on the java location
 */
public interface JavaLocation extends SpecificLocationInfo {
    /**
     * Bytecode index represented by the node. -1, if the bytecode index cannot
     * be determined
     *
     * @return bytecode index within method, or -1 if unknown
     */
    public int getBytecodeIndex();

    /**
     * @return name of the hosting method
     */
    public String getMethodName();

    /**
     * @return name of the hosting class
     */
    public String getClassName();

    /**
     * @return Signature of the hosting method
     */
    public String getMethodSignature();

    /**
     * FQN of class which declares the referenced method or field.
     *
     * @return FQN of target class
     */
    public String getTargetClass();

    /**
     * Method name, if the node invokes a method on the target class. Null otherwise
     *
     * @return target method's name or {@code null}
     */
    public String getInvokedMethod();

    /**
     * Name of the referenced variable (of the target class). Null otherwise. Variable
     * can be read or written.
     *
     * @return name of variable on the target class or {@code null}
     */
    public String getVariableName();

    /**
     * Attempts to find a location for this info. May return {@code null}
     *
     * @return target location
     */
    public TreePathHandle findTreePath();
}
