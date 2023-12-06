/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.introspection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BytecodeIntrospection {

    public interface Provider {
        default BytecodeIntrospection getIntrospectionData() {
            throw new UnsupportedOperationException();
        }

        static BytecodeIntrospection create(Object... data) {
            return new BytecodeIntrospection(data);
        }
    }

    private final Object[] data;

    // format: [int 0, Object[] instructions, Object[] exHandlers, Object[] sourceInfo or null]
    // instruction: [int index, String name, short[] bytes, Object[] argumentValues]
    // argumentValue: [ArgumentKind kind, Object value]
    // exHandler: [int startIndex, int endIndex, int handlerIndex, int exVariable]
    // sourceInfo: [int startIndex, int endIndex, SourceSection ss]

    private BytecodeIntrospection(Object[] data) {
        if (data.length == 0 || (int) data[0] != 0) {
            throw new UnsupportedOperationException("Illegal operation introspection version");
        }

        this.data = data;
    }

    public List<Instruction> getInstructions() {
        Object[] instructions = (Object[]) data[1];
        List<Instruction> result = new ArrayList<>(instructions.length);
        for (int i = 0; i < instructions.length; i++) {
            result.add(new Instruction((Object[]) instructions[i]));
        }
        return Collections.unmodifiableList(result);
    }

    public List<ExceptionHandler> getExceptionHandlers() {
        Object[] handlers = (Object[]) data[2];
        List<ExceptionHandler> result = new ArrayList<>(handlers.length);
        for (int i = 0; i < handlers.length; i++) {
            result.add(new ExceptionHandler((Object[]) handlers[i]));
        }
        return Collections.unmodifiableList(result);
    }

    public List<SourceInformation> getSourceInformation() {
        Object[] sourceInfo = (Object[]) data[3];
        if (sourceInfo == null) {
            return null;
        }
        List<SourceInformation> result = new ArrayList<>(sourceInfo.length);
        for (int i = 0; i < sourceInfo.length; i++) {
            result.add(new SourceInformation((Object[]) sourceInfo[i]));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public String toString() {
        List<Instruction> instructions = getInstructions();
        List<ExceptionHandler> exceptions = getExceptionHandlers();
        List<SourceInformation> sourceInformation = getSourceInformation();
        return String.format("""
                        BytecodeIntrospection[
                            instructions(%s) = %s
                            exceptionHandlers(%s) = %s
                            sourceInformation(%s) = %s
                        ]""",
                        instructions.size(),
                        formatList(instructions),
                        exceptions.size(),
                        formatList(exceptions),
                        sourceInformation != null ? sourceInformation.size() : "-",
                        formatList(sourceInformation));

    }

    private static String formatList(List<? extends Object> list) {
        if (list == null) {
            return "Not Available";
        } else if (list.isEmpty()) {
            return "Empty";
        }
        String sep = "\n        ";
        return sep + String.join(sep, list.stream().map(element -> element.toString()).toArray(String[]::new));
    }

}
