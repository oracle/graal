/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.library;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.MessageContainer;

public final class LibraryMessage extends MessageContainer {

    private final LibraryData library;
    private final String name;
    private final ExecutableElement executable;
    private final boolean isDeprecated;
    private boolean isAbstract;
    private final Set<LibraryMessage> abstractIfExported = new LinkedHashSet<>();
    private final Set<LibraryMessage> abstractIfExportedAsWarning = new LinkedHashSet<>();
    private List<LibraryMessage> deprecatedOverloads;
    private LibraryMessage deprecatedReplacement;

    public LibraryMessage(LibraryData library, String name, ExecutableElement executable, boolean isDeprecated) {
        this.library = library;
        this.name = name;
        this.executable = executable;
        this.isDeprecated = isDeprecated;
    }

    public LibraryData getLibrary() {
        return library;
    }

    public void setDeprecatedOverloads(List<LibraryMessage> deprecated) {
        this.deprecatedOverloads = deprecated;

        for (LibraryMessage message : deprecated) {
            if (!this.canBeDeprecatedFrom(message)) {
                throw new AssertionError("Undelegatable deprecated message added.");
            }
        }
    }

    public List<LibraryMessage> getDeprecatedOverloads() {
        if (deprecatedOverloads == null) {
            return Collections.emptyList();
        }
        return deprecatedOverloads;
    }

    public void setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }

    public String getSimpleName() {
        return library.getMessageElement().getSimpleName().toString() + "." + name;
    }

    public ExecutableElement getExecutable() {
        return executable;
    }

    public boolean canBeDeprecatedFrom(LibraryMessage message) {
        if (this.equals(message)) {
            return true;
        }

        if (!Objects.equals(getLibrary(), message.getLibrary())) {
            return false;
        }
        List<? extends VariableElement> otherParameters = message.getExecutable().getParameters();
        List<? extends VariableElement> parameters = this.getExecutable().getParameters();

        if (otherParameters.size() != parameters.size()) {
            // we can always safely differentiate if parameter lengths differ
            return true;
        }

        for (int i = 0; i < parameters.size(); i++) {
            VariableElement thisParamter = parameters.get(i);
            VariableElement otherParameter = otherParameters.get(i);
            if (!ElementUtils.isAssignable(otherParameter.asType(), thisParamter.asType())) {
                return false;
            }
        }
        return true;
    }

    public void setDeprecatedReplacement(LibraryMessage replacement) {
        deprecatedReplacement = replacement;
    }

    public LibraryMessage getDeprecatedReplacement() {
        return deprecatedReplacement;
    }

    @Override
    public Element getMessageElement() {
        return executable;
    }

    public Set<LibraryMessage> getAbstractIfExported() {
        return abstractIfExported;
    }

    public Set<LibraryMessage> getAbstractIfExportedAsWarning() {
        return abstractIfExportedAsWarning;
    }

    public String getName() {
        return name;
    }

    public boolean isAbstract() {
        return isAbstract || getExecutable().getModifiers().contains(Modifier.ABSTRACT);
    }

    public boolean isDeprecated() {
        return isDeprecated;
    }

    public boolean isCompatibleAssignable(List<TypeMirror> parameterTypes) {
        List<? extends VariableElement> libraryParameters = executable.getParameters();
        if (libraryParameters.size() != parameterTypes.size()) {
            return false;
        }
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (!ElementUtils.isAssignable(parameterTypes.get(i), libraryParameters.get(i).asType())) {
                return false;
            }

        }
        return true;
    }

    public boolean isCompatibleExact(List<TypeMirror> parameterTypes) {
        List<? extends VariableElement> libraryParameters = executable.getParameters();
        if (libraryParameters.size() != parameterTypes.size()) {
            return false;
        }
        // i = 1 => skip receiver
        for (int i = 1; i < parameterTypes.size(); i++) {
            if (!ElementUtils.typeEquals(parameterTypes.get(i), libraryParameters.get(i).asType())) {
                return false;
            }
        }
        return true;
    }

}
