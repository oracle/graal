/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.api;

import com.oracle.truffle.api.source.Source;

/**
 * A representation of a method.
 */
public interface MethodRef {

    /**
     * Returnes the first code index for a given line within the method.
     *
     * @param line the line number in the source code of the method
     * @return the first bci for the line
     */
    long getBCIFromLine(int line);

    /**
     * Returns the source for the method.
     *
     * @return the source
     */
    Source getSource();

    /**
     * Determines if the input line number is a valid source line for the method.
     *
     * @param lineNumber
     * @return true if line number is present in method, false otherwise
     */
    boolean hasLine(int lineNumber);

    /**
     * Returns the name of the source file that declares the method.
     *
     * @return the source file name
     */
    String getSourceFile();

    /**
     * Returns a String representation of the name of the method.
     *
     * @return the method name
     */
    String getNameAsString();

    /**
     * Returns the String representation of the signature of the method.
     *
     * @return the method signature name
     */
    String getSignatureAsString();

    /**
     * Returns the String representation of the generic signature of the method.
     *
     * @return the generic signature name
     */
    String getGenericSignatureAsString();

    /**
     * Returns the method modifiers.
     *
     * @return the bitmask for the modifiers
     */
    int getModifiers();

    /**
     * Returns the source line number for the given input code index.
     *
     * @param bci the code index.
     * @return line number at the code index
     */
    int bciToLineNumber(int bci);

    /**
     * @return true if method is native, false otherwise
     */
    boolean isMethodNative();

    /**
     * Returns the bytecode for the method in the Class file format.
     *
     * @return the byte array for the bytecode of the method
     */
    byte[] getOriginalCode();

    /**
     * @return the local variable table of the method
     */
    LocalVariableTableRef getLocalVariableTable();

    /**
     * @return the local generic variable table of the method
     */
    LocalVariableTableRef getLocalVariableTypeTable();

    /**
     * @return the line number table of the method
     */
    LineNumberTableRef getLineNumberTable();

    /**
     * Invokes the method on the input callee object with input arguments.
     *
     * @param callee guest-language object on which to execute the method
     * @param args guest-language arguments used when calling the method
     * @return the guest-language return value
     */
    Object invokeMethod(Object callee, Object[] args);

    /**
     * Determines if the declaring class has a source file attribute.
     *
     * @return true if a source file attribute is present, false otherwise
     */
    boolean hasSourceFileAttribute();

    /**
     * Determines if the code index is located in the source file on the last line of this method.
     *
     * @param codeIndex
     * @return true if last line, false otherwise
     */
    boolean isLastLine(long codeIndex);

    /**
     * Returns the klass that declares this method.
     *
     * @return the declaring klass
     */
    KlassRef getDeclaringKlassRef();

    /**
     * Returns the first line number for the method, or -1 if unknown.
     *
     * @return first line
     */
    int getFirstLine();

    /**
     * Returns the last line number for the method, or -1 if unknown.
     *
     * @return last line
     */
    int getLastLine();

    /**
     * Returns all information about potential method breakpoints set on this method.
     *
     * @return array of method breakpoint info
     */
    MethodHook[] getMethodHooks();

    /**
     * Add a new method breakpoint with the given info on this method.
     *
     * @param info the info that describes the breakpoint
     */
    void addMethodHook(MethodHook info);

    /**
     * Remove a method hook with the given info on this method.
     *
     * @param requestId the ID for the request that set the breakpoint
     */
    void removedMethodHook(int requestId);

    /**
     * Remove a method hook with the given hook on this method.
     *
     * @param hook the method hook
     */
    void removedMethodHook(MethodHook hook);

    /**
     * Determines if there are any breakpoints set on this method.
     *
     * @return true if this method has any breakpoints, false otherwise
     */
    boolean hasActiveHook();

    /**
     * Determine if this method is obsolete. A method is obsolete if it has been replaced by a
     * non-equivalent method using the RedefineClasses command. The original and redefined methods
     * are considered equivalent if their bytecodes are the same except for indices into the
     * constant pool and the referenced constants are equal.
     *
     * @return true if the method is obsolete
     */
    boolean isObsolete();

    /**
     * Returns the last bci of the method.
     *
     * @return last bci
     */
    long getLastBCI();

    /**
     * Determines if the method was compiled with a variable table.
     *
     * @return true if the method has a variable table
     */
    boolean hasVariableTable();

    /**
     * Determines if the method was compiled with a variable type table.
     *
     * @return true if the method has a variable table
     */
    boolean hasVariabletypeTable();
}
