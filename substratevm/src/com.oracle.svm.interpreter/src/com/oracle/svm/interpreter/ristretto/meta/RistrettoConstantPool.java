/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import java.lang.invoke.MethodHandle;
import java.util.List;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.interpreter.CallSiteLink;
import com.oracle.svm.interpreter.CremaLinkResolver;
import com.oracle.svm.interpreter.CremaRuntimeAccess;
import com.oracle.svm.interpreter.FailedCallSiteLink;
import com.oracle.svm.interpreter.Interpreter;
import com.oracle.svm.interpreter.ResolvedInvokeDynamicConstant;
import com.oracle.svm.interpreter.SuccessfulCallSiteLink;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.CremaMethodAccess;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedInvokeGenericJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaMethod;

/**
 * JVMCI representation of a {@link ConstantPool} used by Ristretto for compilation. Exists once per
 * {@link RistrettoMethod}. Runtime-loaded {@code invokedynamic} sites encode compiler-view state in
 * a method-specific raw operand, so this wrapper must stay bound to the compiling
 * {@link RistrettoMethod}, not just to the shared {@link InterpreterConstantPool}.
 * <p>
 * Life cycle: lives until the referencing {@link RistrettoMethod} is gc-ed.
 */
public final class RistrettoConstantPool implements ConstantPool {
    private final InterpreterConstantPool interpreterConstantPool;
    /** Compiling method whose bytecode view and call-site metadata this wrapper exposes. */
    private final RistrettoMethod compilerMethod;

    private RistrettoConstantPool(RistrettoMethod compilerMethod) {
        this.compilerMethod = compilerMethod;
        this.interpreterConstantPool = compilerMethod.getInterpreterMethod().getConstantPool();
    }

    public static RistrettoConstantPool create(RistrettoMethod compilerMethod) {
        return new RistrettoConstantPool(compilerMethod);
    }

    @Override
    public int length() {
        return interpreterConstantPool.length();
    }

    @Override
    public void loadReferencedType(int rawIndex, int opcode) {
        interpreterConstantPool.loadReferencedType(rawIndex, opcode);
    }

    @Override
    public JavaType lookupReferencedType(int rawIndex, int opcode) {
        JavaType lookupType = interpreterConstantPool.lookupReferencedType(rawIndex, opcode);
        if (lookupType instanceof InterpreterResolvedJavaType iType) {
            return RistrettoType.getOrCreate(iType);
        }
        return lookupType;
    }

    @Override
    public JavaField lookupField(int rawIndex, ResolvedJavaMethod method, int opcode) {
        JavaField javaField = interpreterConstantPool.lookupField(rawIndex, method, opcode);
        if (javaField instanceof InterpreterResolvedJavaField iField && method instanceof RistrettoMethod rMethod) {
            /*
             * Mirror the interpreter's opcode-specific field checks once a field has been resolved
             * so runtime compilation observes the same linkage errors.
             */
            InterpreterResolvedJavaMethod interpreterMethod = rMethod.getInterpreterMethod();
            CremaLinkResolver.checkFieldAccessOrThrow(CremaRuntimeAccess.getInstance(), iField, opcode, interpreterMethod.getDeclaringClass(), interpreterMethod);
        }
        /*
         * A note on wrapping AOT fields into ristretto JVMCI API: When compiling dynamically loaded
         * types with ristretto every such interpreter type is a crema resolved type. All these
         * cream resolved types are wrapped to ristretto types. However, when interfacing with AOT
         * types (in stamp intersection, optimizations etc) we need ristretto types that are
         * compatible (== have the same class) with each other. Thus, we wrap here all interpreter
         * fields (also those that are not crema resolved, i.e., AOT fields).
         */
        if (javaField instanceof InterpreterResolvedJavaField iField) {
            return RistrettoField.getOrCreate(iField);
        }
        return javaField;
    }

    @Override
    public JavaMethod lookupMethod(int rawIndex, int opcode, ResolvedJavaMethod caller) {
        assert caller instanceof RistrettoMethod;
        GraalError.guarantee(compilerMethod.equals(caller), "Mismatching compiler method for constant-pool lookup");
        final int cpi = rawIndex;
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            var res = lookupInvokeDynamicMethod(rawIndex);
            if (res == null) {
                int indyCpi = rawIndex >>> 16;
                VMError.guarantee(indyCpi != 0, "Unresolved invokedynamic lookup expects a compiler-visible invokedynamic CPI");
                String name = interpreterConstantPool.invokeDynamicName(indyCpi).toString();
                Signature signature = new RistrettoUnresolvedSignature(CremaMethodAccess.toJVMCI(interpreterConstantPool.invokeDynamicSignature(indyCpi),
                                SymbolsSupport.getTypes()));
                JavaType holder = RistrettoType.getOrCreate((InterpreterResolvedJavaType) DynamicHub.fromClass(MethodHandle.class).getInterpreterType());
                return new UnresolvedJavaMethod(name, signature, holder);
            }
            return res;
        }
        if (compilerMethod.getInterpreterMethod().getConstantPool().peekCachedEntry(cpi) instanceof UnresolvedJavaMethod unresolvedJavaMethod) {
            /* The interpreter has not resolved this method yet. */
            return unresolvedJavaMethod;
        }
        InterpreterResolvedJavaMethod resolvedMethod = Interpreter.resolveMethod(compilerMethod.getInterpreterMethod(), opcode, (char) cpi);
        if (resolvedMethod instanceof InterpreterResolvedInvokeGenericJavaMethod invokeGenericMethod) {
            resolvedMethod = invokeGenericMethod.getInvoker();
        }
        return RistrettoMethod.getOrCreate(resolvedMethod);
    }

    private JavaMethod lookupInvokeDynamicMethod(int rawIndex) {
        int indyCpi = rawIndex >>> 16;
        if (indyCpi == 0) {
            return null;
        }

        Object indyEntry = interpreterConstantPool.peekCachedEntry(indyCpi);
        if (indyEntry == null) {
            return null;
        }
        if (indyEntry instanceof ResolvedInvokeDynamicConstant invokeDynamicConstant) {
            SuccessfulCallSiteLink successfulCallSiteLink = getSuccessfulInvokeDynamicLink(rawIndex, invokeDynamicConstant);
            if (successfulCallSiteLink == null) {
                /*
                 * No successful runtime link is published for this exact call site yet. Keep it
                 * unresolved so parsing inserts an unresolved deopt and the interpreter can surface
                 * the eventual first-link or failed-link outcome itself.
                 */
                return null;
            }
            return RistrettoMethod.getOrCreate(successfulCallSiteLink.getInvoker());
        }
        throw GraalError.shouldNotReachHere("Unexpected INVOKEDYNAMIC constant: " + indyEntry);
    }

    /**
     * Returns the linked call-site metadata only when the runtime-loaded site has completed
     * successfully. Unlinked or failed sites remain unresolved to the parser so compilation stays
     * conservative and re-enters the interpreter to surface the real linkage outcome.
     */
    private SuccessfulCallSiteLink getSuccessfulInvokeDynamicLink(int rawIndex, ResolvedInvokeDynamicConstant invokeDynamicConstant) {
        CallSiteLink link = invokeDynamicConstant.getCallSiteLink(compilerMethod.getInterpreterMethod(), decodeCompilerIndyBci(rawIndex));
        if (link instanceof SuccessfulCallSiteLink successfulCallSiteLink) {
            return successfulCallSiteLink;
        }
        GraalError.guarantee(link == null || link instanceof FailedCallSiteLink, "Unexpected INVOKEDYNAMIC call site link: %s", link);
        return null;
    }

    /**
     * Decodes the compiler-visible low 16 bits of a rewritten runtime {@code invokedynamic} operand
     * back into the original call-site BCI.
     */
    private static int decodeCompilerIndyBci(int rawIndex) {
        int encodedBci = rawIndex & 0xFFFF;
        if (encodedBci == 0) {
            throw VMError.shouldNotReachHere("Compiler indy raw index is missing its encoded BCI: " + rawIndex);
        }
        return encodedBci - 1;
    }

    @Override
    public List<BootstrapMethodInvocation> lookupBootstrapMethodInvocations(boolean invokeDynamic) {
        return interpreterConstantPool.lookupBootstrapMethodInvocations(invokeDynamic);
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        JavaType lookupType = interpreterConstantPool.lookupType(cpi, opcode);
        if (lookupType instanceof InterpreterResolvedJavaType iType) {
            return RistrettoType.getOrCreate(iType);
        }
        return lookupType;
    }

    @Override
    public String lookupUtf8(int cpi) {
        return interpreterConstantPool.lookupUtf8(cpi);
    }

    @Override
    public Signature lookupSignature(int cpi) {
        return interpreterConstantPool.lookupSignature(cpi);
    }

    @Override
    public Object lookupConstant(int cpi) {
        return interpreterConstantPool.lookupConstant(cpi);
    }

    @Override
    public Object lookupConstant(int cpi, boolean resolve) {
        Object retVal = interpreterConstantPool.lookupConstant(cpi, resolve);
        if (retVal == null) {
            /*
             * Return null if the interpreter has not yet resolved this constant. That tells the
             * compiler to emit an unresolved deopt so execution can bounce back to the interpreter
             * and retry once the runtime world has enough metadata.
             */
            return null;
        } else if (retVal instanceof JavaConstant) {
            return retVal;
        } else if (retVal instanceof JavaType) {
            if (retVal instanceof ResolvedJavaType) {
                GraalError.guarantee(retVal instanceof InterpreterResolvedJavaType, "Must be an interpreter resolved java type but is %s", retVal);
                return RistrettoType.getOrCreate((InterpreterResolvedJavaType) retVal);
            } else {
                // unresolved entry, just return it
                return retVal;
            }
        }
        throw GraalError.shouldNotReachHere(String.format("Unknown value for constant lookup, cpi=%s resolve=%s this=%s", cpi, resolve, this));
    }

    @Override
    public JavaConstant lookupAppendix(int rawIndex, int opcode) {
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            return lookupInvokeDynamicAppendix(rawIndex);
        }
        if (opcode == Bytecodes.INVOKEVIRTUAL) {
            Object appendix = interpreterConstantPool.peekInvokeAppendix(rawIndex, opcode);
            return appendix == null ? null : SubstrateObjectConstant.forObject(appendix);
        }
        return null;
    }

    /**
     * Returns the appendix seen by the compiler for one rewritten runtime {@code invokedynamic}
     * operand without forcing first-linking of the runtime call site.
     */
    private JavaConstant lookupInvokeDynamicAppendix(int rawIndex) {
        int indyCpi = rawIndex >>> 16;
        if (indyCpi == 0) {
            return null;
        }

        Object indyEntry = interpreterConstantPool.peekCachedEntry(indyCpi);
        if (indyEntry instanceof ResolvedInvokeDynamicConstant invokeDynamicConstant) {
            SuccessfulCallSiteLink successfulCallSiteLink = getSuccessfulInvokeDynamicLink(rawIndex, invokeDynamicConstant);
            return successfulCallSiteLink == null ? null : SubstrateObjectConstant.forObject(successfulCallSiteLink.getUnboxedAppendix());
        }
        if (indyEntry != null) {
            throw GraalError.shouldNotReachHere("Unexpected INVOKEDYNAMIC constant: " + indyEntry);
        }
        return null;
    }

    @Override
    public String toString() {
        return "RistrettoConstantPool{compilerMethod=" + compilerMethod + ", interpreterConstantPool=" + interpreterConstantPool + '}';
    }
}
