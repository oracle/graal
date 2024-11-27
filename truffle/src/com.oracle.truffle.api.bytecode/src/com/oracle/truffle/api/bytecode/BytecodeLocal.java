/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode;

/**
 * Abstract definition of a local variable in the interpreter.
 * <p>
 * Local variables are stored in the frame. They are typically accessed in the bytecode using
 * {@code StoreLocal} and {@code LoadLocal} operations. For uncommon scenarios where locals need to
 * be accessed programmatically (e.g., in a node), locals can be accessed using accessor methods on
 * the {@link BytecodeNode}, such as {@link BytecodeNode#getLocalValue(int, Frame, int)} and
 * {@link BytecodeNode#setLocalValue(int, com.oracle.truffle.api.frame.Frame, int, Object)}.
 * <p>
 * By default a local variable is live for the extent of the block that defines it ("block
 * scoping"). Interpreters can also be configured so that locals live for the extent of the root
 * node ("root scoping"). See {@link GenerateBytecode#enableBlockScoping()} for details.
 * <p>
 * Refer to the <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/UserGuide.md">user
 * guide</a> for more details.
 *
 * @since 24.2
 */
public abstract class BytecodeLocal {

    /**
     * Internal constructor for generated code. Do not use.
     *
     * @since 24.2
     */
    public BytecodeLocal(Object token) {
        BytecodeRootNodes.checkToken(token);
    }

    /**
     * Returns the local offset to use when accessing local values with a local accessor like
     * {@link BytecodeNode#getLocalValue(int, Frame, int)}.
     *
     * @since 24.2
     */
    public abstract int getLocalOffset();

    /**
     * Returns the index when accessing into the locals table with {@link BytecodeNode#getLocals()}.
     * The local index is guaranteed to be equal to {@link #getLocalOffset()} if
     * {@link GenerateBytecode#enableBlockScoping() block scoping} is set to <code>false</code>.
     * Otherwise, the local index is distinct from the local offset and should not be used in places
     * an offset is expected.
     *
     * @since 24.2
     */
    public abstract int getLocalIndex();

}
