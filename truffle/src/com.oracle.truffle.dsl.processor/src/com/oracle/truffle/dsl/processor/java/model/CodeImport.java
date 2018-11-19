/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.model;

import java.util.Objects;

public class CodeImport implements Comparable<CodeImport> {

    private final String packageName;
    private final String symbolName;
    private final boolean staticImport;

    public CodeImport(String packageName, String symbolName, boolean staticImport) {
        this.packageName = packageName;
        this.symbolName = symbolName;
        this.staticImport = staticImport;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSymbolName() {
        return symbolName;
    }

    public boolean isStaticImport() {
        return staticImport;
    }

    @Override
    public int compareTo(CodeImport o) {
        if (staticImport && !o.staticImport) {
            return 1;
        } else if (!staticImport && o.staticImport) {
            return -1;
        } else {
            int result = getPackageName().compareTo(o.getPackageName());
            if (result == 0) {
                return getSymbolName().compareTo(o.getSymbolName());
            }
            return result;
        }
    }

    public <P> void accept(CodeElementScanner<?, P> s, P p) {
        s.visitImport(this, p);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, symbolName, staticImport);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CodeImport) {
            CodeImport otherImport = (CodeImport) obj;
            return getPackageName().equals(otherImport.getPackageName()) && getSymbolName().equals(otherImport.getSymbolName()) //
                            && staticImport == otherImport.staticImport;
        }
        return super.equals(obj);
    }
}
