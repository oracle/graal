/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import static com.oracle.svm.hosted.pltgot.GOTEntryAllocator.GOT_NO_ENTRY;
import static com.oracle.svm.interpreter.InterpreterUtil.traceInterpreter;
import static com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod.EST_NO_ENTRY;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.pltgot.GOTAccess;
import com.oracle.svm.core.pltgot.GOTHeapSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterUniverse;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@InternalVMMethod
final class InterpreterDirectivesSupportImpl implements InterpreterDirectivesSupport {
    final Map<InterpreterResolvedJavaMethod, Long> rememberCompiledEntry = new HashMap<>();

    @Override
    public boolean forceInterpreterExecution(Object method) {
        InterpreterResolvedJavaMethod interpreterMethod = getInterpreterMethod(method);

        if (interpreterMethod == null) {
            return false;
        }
        if (interpreterMethod.getGotOffset() == GOT_NO_ENTRY) {
            return false;
        }
        if (interpreterMethod.getEnterStubOffset() == EST_NO_ENTRY) {
            return false;
        }

        /* arguments to Log methods might have side-effects */
        if (InterpreterOptions.InterpreterTraceSupport.getValue()) {
            traceInterpreter("[forceInterpreterExecution] ").string(interpreterMethod.toString()).newline();
        }

        int estOffset = ConfigurationValues.getTarget().wordSize * interpreterMethod.getEnterStubOffset();
        Pointer estBase = InterpreterStubTable.getBaseForEnterStubTable();
        UnsignedWord estEntry = estBase.add(estOffset).readWord(0);

        WordBase previousEntry = GOTAccess.readFromGotEntry(interpreterMethod.getGotOffset());
        rememberCompiledEntry.put(interpreterMethod, previousEntry.rawValue());
        writeGOTHelper(interpreterMethod, estEntry);

        return true;
    }

    private static void writeGOTHelper(InterpreterResolvedJavaMethod interpreterMethod, UnsignedWord estEntry) {
        GOTHeapSupport.get().makeGOTWritable();
        GOTAccess.writeToGotEntry(interpreterMethod.getGotOffset(), estEntry);
        GOTHeapSupport.get().makeGOTReadOnly();
    }

    @Override
    public void resetInterpreterExecution(Object method) {
        InterpreterResolvedJavaMethod interpreterMethod = getInterpreterMethod(method);

        if (interpreterMethod == null) {
            // Either forceInterpreterExecution was never called or we don't have an interpreter
            // method
            return;
        }

        if (!rememberCompiledEntry.containsKey(interpreterMethod)) {
            return;
        }
        long previousEntry = rememberCompiledEntry.get(interpreterMethod);
        writeGOTHelper(interpreterMethod, Word.pointer(previousEntry));
    }

    private class InterpreterOpToken {
        List<InterpreterResolvedJavaMethod> changedExecutionState;
        boolean valid = true;

        InterpreterOpToken() {
            changedExecutionState = new ArrayList<>();
        }
    }

    @Override
    public Object ensureInterpreterExecution(Object method) {
        InterpreterResolvedJavaMethod interpreterMethod = getInterpreterMethod(method);
        InterpreterOpToken token = new InterpreterOpToken();

        if (interpreterMethod == null) {
            return null;
        }

        /* arguments to Log methods might have side-effects */
        if (InterpreterOptions.InterpreterTraceSupport.getValue()) {
            traceInterpreter("[ensureInterpreterExecution] ").string(interpreterMethod.toString()).newline();
        }

        for (InterpreterResolvedJavaMethod inliner : interpreterMethod.getInlinedBy()) {
            if (forceInterpreterExecution(inliner)) {
                token.changedExecutionState.add(0, inliner);
            }
        }

        if (forceInterpreterExecution(interpreterMethod)) {
            token.changedExecutionState.add(0, interpreterMethod);
        }
        return token;
    }

    @Override
    public void undoExecutionOperation(Object t) {
        if (t == null) {
            return;
        }
        InterpreterOpToken token = (InterpreterOpToken) t;
        if (!token.valid) {
            throw new IllegalStateException("Token is expired.");
        }
        VMError.guarantee(token.valid);
        VMError.guarantee(token.changedExecutionState != null);
        for (InterpreterResolvedJavaMethod m : token.changedExecutionState) {
            VMError.guarantee(m != null);
            resetInterpreterExecution(m);
        }
        token.valid = false;
    }

    @Override
    public void markKlass(Class<?> targetKlass) {
        GraalError.unimplemented("should be replaced");
    }

    @Override
    public Object callIntoInterpreter(Object method, Object... args) {
        InterpreterResolvedJavaMethod interpreterMethod = getInterpreterMethod(method);
        return Interpreter.execute(interpreterMethod, args, true);
    }

    @Override
    public Object callIntoUnknown(Object method, Object... args) {
        InterpreterResolvedJavaMethod interpreterMethod = getInterpreterMethod(method);
        MethodPointer calleeFtnPtr = interpreterMethod.getNativeEntryPoint();
        return InterpreterStubSection.leaveInterpreter(calleeFtnPtr, interpreterMethod, interpreterMethod.getDeclaringClass(), args);
    }

    private static String getDescriptor(Class<?> returnType, Class<?>... parameterTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (Class<?> param : parameterTypes) {
            sb.append(param.descriptorString());
        }
        sb.append(")");
        sb.append(returnType.descriptorString());
        return sb.toString();
    }

    private static InterpreterResolvedJavaMethod getInterpreterMethod(Object testMethod) {
        VMError.guarantee(testMethod != null);
        VMError.guarantee(testMethod instanceof InterpreterResolvedJavaMethod);
        return (InterpreterResolvedJavaMethod) testMethod;
    }

    static InterpreterResolvedJavaType getInterpreterType(Class<?> clazz) {
        VMError.guarantee(clazz != null);

        return findInterpreterResolvedJavaType(clazz);
    }

    private static InterpreterResolvedJavaType findInterpreterResolvedJavaType(Class<?> clazz) {
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        return (InterpreterResolvedJavaType) universe.lookupType(clazz);
    }

    static InterpreterResolvedJavaMethod getInterpreterMethod(InterpreterResolvedJavaType clazz, String targetName, Class<?> returnType, Class<?>[] parameterTypes) {
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();

        Collection<? extends ResolvedJavaMethod> allDeclaredMethods = universe.getAllDeclaredMethods(clazz);

        String targetDescriptor = returnType == null ? null : getDescriptor(returnType, parameterTypes);
        for (ResolvedJavaMethod m : allDeclaredMethods) {
            if (targetName.equals(m.getName()) && (targetDescriptor == null || targetDescriptor.equals(m.getSignature().toMethodDescriptor()))) {
                return (InterpreterResolvedJavaMethod) m;
            }
        }

        return null;
    }
}
