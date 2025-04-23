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

package org.graalvm.visualizer.source.java.impl;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.java.JavaLocation;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TreePathHandle;
import org.netbeans.modules.parsing.api.Snapshot;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.EnumSet;

/**
 * Provides additional java-related information. Placed into ProcessingLocation.getLookup()
 */
public final class JavaLocationInfo implements JavaLocation {
    private Location location;
    /**
     * Bytecode index as reported in the IGV dump
     */
    private final int bytecodeIndex;

    private final String className;
    /**
     * The current method name
     */
    private final String methodName;

    /**
     * The current method signature, if known
     */
    private final String methodSignature;

    /**
     * FQN of the referenced type
     */
    private String targetClass;

    /**
     * name referenced (invoked) method
     */
    private String invokedMethod;

    /**
     * name of the referenced (read, written) field
     */
    private String variableName;

    /**
     * Caches the handle, if resolved.
     */
    private TreePathHandle cachedHandle;

    private boolean handleResolved;

    JavaLocationInfo(int bytecodeIndex, String className, String methodName, String methodSignature) {
        this.bytecodeIndex = bytecodeIndex;
        this.className = className;
        this.methodName = methodName;
        this.methodSignature = methodSignature;
    }

    JavaLocationInfo withLocation(Location loc) {
        this.location = loc;
        return this;
    }

    public FileObject getFile() {
        return location.getOriginFile();
    }

    public int getLine() {
        return location.getLine();
    }

    void callToMethod(String classQN, String methodName) {
        this.invokedMethod = methodName;
        this.targetClass = classQN;
    }

    void referToField(String classQN, String field) {
        this.variableName = field;
        this.targetClass = classQN;
    }

    void referToVariable(String varName) {
        this.variableName = varName;
        this.targetClass = null;
    }

    public int getBytecodeIndex() {
        return bytecodeIndex;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getClassName() {
        return className;
    }

    public boolean isResolved() {
        return location.isResolved();
    }

    public Location getLocation() {
        return location;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public String getInvokedMethod() {
        return invokedMethod;
    }

    public String getVariableName() {
        return variableName;
    }

    private static final EnumSet LOCAL_KINDS = EnumSet.of(ElementKind.EXCEPTION_PARAMETER, ElementKind.PARAMETER,
            ElementKind.LOCAL_VARIABLE, ElementKind.RESOURCE_VARIABLE);

    private TreePathHandle setResolvedHandle(TreePathHandle handle) {
        synchronized (this) {
            if (!handleResolved) {
                handleResolved = true;
                cachedHandle = handle;
            }
            return cachedHandle;
        }
    }

    /**
     * Attempts to find a handle for the specific tree. Do not call in EDT.
     */
    public TreePathHandle findTreePath() {
        synchronized (this) {
            if (handleResolved) {
                return cachedHandle;
            }
        }
        TreePathHandle[] result = new TreePathHandle[1];
        FileObject f = location.getOriginFile();
        if (f == null) {
            // not resolved
            return null;
        }
        try {
            JavaSource js = JavaSource.forFileObject(f);
            if (js == null) {
                return setResolvedHandle(null);
            }
            js.runUserActionTask(new Task<CompilationController>() {
                int offset;
                int endOffset;
                TreePath foundPath;

                @Override
                public void run(CompilationController parameter) throws Exception {
                    Snapshot ss = parameter.getSnapshot();
                    CharSequence contents = ss.getText();
                    offset = -1;
                    endOffset = contents.length();
                    int curLine = 0;
                    for (int index = 0; index < contents.length(); index++) {
                        if (contents.charAt(index) == '\n') {
                            if (++curLine == location.getLine()) {
                                offset = index;
                            } else if (curLine == location.getLine() + 1) {
                                endOffset = index;
                                break;
                            }
                        }
                    }
                    if (offset == -1) {
                        // line not found
                        return;
                    }
                    SourcePositions spos = parameter.getTrees().getSourcePositions();
                    CompilationUnitTree cut = parameter.getCompilationUnit();
                    String dottedQN = targetClass == null ? null : targetClass.replace('$', '.');

                    new TreePathScanner() {
                        private boolean containsLine;
                        private boolean parentNotIncluded;

                        @Override
                        public Object scan(Tree tree, Object p) {
                            if (foundPath != null) {
                                return null;
                            }
                            boolean saveParent = parentNotIncluded;
                            boolean saveLine = containsLine;

                            int tStart = (int) spos.getStartPosition(cut, tree);
                            int tEnd = (int) spos.getEndPosition(cut, tree);
                            if (tStart >= offset && tEnd < endOffset) {
                                saveLine = true;
                            }
                            Object o = super.scan(tree, p);
                            this.containsLine = saveLine;
                            this.parentNotIncluded = saveParent;
                            return o;
                        }

                        @Override
                        public Object visitIdentifier(IdentifierTree node, Object p) {
                            Object o = super.visitIdentifier(node, p);
                            if (!containsLine) {
                                return o;
                            }
                            Element el = parameter.getTrees().getElement(getCurrentPath());
                            if (el != null) {
                                String sn = el.getSimpleName().toString();
                                if (sn.equals(variableName)) {
                                    if (targetClass == null) {
                                        if (LOCAL_KINDS.contains(el.getKind())) {
                                            // got it:
                                            foundPath = getCurrentPath();
                                            return o;
                                        }
                                    } else if (!(el.getKind() == ElementKind.ENUM_CONSTANT || el.getKind() == ElementKind.FIELD)) {
                                        return o;
                                    }
                                } else if (sn.equals(methodName)) {
                                    if (!(el.getKind() == ElementKind.METHOD || el.getKind() == ElementKind.CONSTRUCTOR)) {
                                        return o;
                                    }
                                } else {
                                    return o;
                                }
                                Element clazzEl = el.getEnclosingElement();
                                if (clazzEl.getKind().isInterface() || clazzEl.getKind().isClass()) {
                                    String fqn = ((TypeElement) clazzEl).getQualifiedName().toString();
                                    if (fqn.equals(dottedQN)) {
                                        foundPath = getCurrentPath();
                                        return o;
                                    }
                                }
                            }
                            return o;
                        }

                    }.scan(new TreePath(parameter.getCompilationUnit()), null);
                    if (foundPath != null) {
                        result[0] = TreePathHandle.create(foundPath, parameter);
                    }
                }
            }, true);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return setResolvedHandle(result[0]);
    }

    public String toString() {
        return new StringBuilder("javaloc[class = ").append(className).
                append(", method = ").append(methodName).
                append(", handle = ").append(cachedHandle).
                append(", targetClass = ").append(targetClass).
                append(", callMethod = ").append(invokedMethod).
                append(", variable = ").append(variableName).
                append("]").toString();
    }
}
