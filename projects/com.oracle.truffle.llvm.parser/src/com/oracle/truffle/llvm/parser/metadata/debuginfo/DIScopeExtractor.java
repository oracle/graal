/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDBasicType;
import com.oracle.truffle.llvm.parser.metadata.MDCompileUnit;
import com.oracle.truffle.llvm.parser.metadata.MDCompositeType;
import com.oracle.truffle.llvm.parser.metadata.MDDerivedType;
import com.oracle.truffle.llvm.parser.metadata.MDFile;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariableExpression;
import com.oracle.truffle.llvm.parser.metadata.MDLexicalBlock;
import com.oracle.truffle.llvm.parser.metadata.MDLexicalBlockFile;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MDModule;
import com.oracle.truffle.llvm.parser.metadata.MDNamespace;
import com.oracle.truffle.llvm.parser.metadata.MDReference;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDSubroutine;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceFile;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

final class DIScopeExtractor {

    private final Map<MDBaseNode, LLVMSourceLocation> scopes = new HashMap<>();

    private final ScopeVisitor extractor = new ScopeVisitor();
    private final DITypeIdentifier typeIdentifier;

    DIScopeExtractor(DITypeIdentifier typeIdentifier) {
        this.typeIdentifier = typeIdentifier;
    }

    LLVMSourceLocation resolve(MDBaseNode node) {
        if (node == null || node == MDReference.VOID) {
            return null;

        } else if (scopes.containsKey(node)) {
            return scopes.get(node);

        } else {
            node.accept(extractor);
            return scopes.get(node);
        }
    }

    private final class ScopeVisitor implements MetadataVisitor {

        private void linkToParent(LLVMSourceLocation child, MDBaseNode parentRef) {
            final LLVMSourceLocation parent = resolve(parentRef);
            child.setParent(parent);
        }

        private void copyFile(LLVMSourceLocation scope, MDBaseNode fileRef) {
            if (fileRef != MDReference.VOID) {
                final LLVMSourceLocation fileScope = resolve(fileRef);
                if (fileScope != null) {
                    scope.copyFile(fileScope);
                }
            }
        }

        private void visit(MDBaseNode scopeRef, MDBaseNode parentRef, MDBaseNode fileRef, long line, long column) {
            if (scopes.containsKey(scopeRef)) {
                return;
            }

            final LLVMSourceLocation scope = new LLVMSourceLocation(line, column);
            scopes.put(scopeRef, scope);

            copyFile(scope, fileRef);
            linkToParent(scope, parentRef);
        }

        private void visit(MDBaseNode scopeRef, MDBaseNode parentRef, MDBaseNode fileRef, long line) {
            visit(scopeRef, parentRef, fileRef, line, -1L);
        }

        private void visit(MDBaseNode scopeRef, MDBaseNode parentRef, MDBaseNode fileRef) {
            visit(scopeRef, parentRef, fileRef, -1L);
        }

        @Override
        public void visit(MDLocation md) {
            if (scopes.containsKey(md)) {
                return;

            } else if (md.getInlinedAt() != MDReference.VOID) {
                final LLVMSourceLocation actualLoc = resolve(md.getInlinedAt());
                scopes.put(md, actualLoc);
                return;
            }

            final LLVMSourceLocation scope = new LLVMSourceLocation(md.getLine(), md.getColumn());
            scopes.put(md, scope);
            linkToParent(scope, md.getScope());
        }

        @Override
        public void visit(MDFile md) {
            if (scopes.containsKey(md)) {
                return;
            }

            final LLVMSourceLocation scope = new LLVMSourceLocation(-1L, -1L);
            scopes.put(md, scope);

            String file = null;
            final MDReference fileRef = md.getFile();
            if (fileRef != MDReference.VOID && fileRef.get() instanceof MDString) {
                file = ((MDString) fileRef.get()).getString();
            }

            String directory = null;
            final MDReference dirRef = md.getDirectory();
            if (dirRef != MDReference.VOID && dirRef.get() instanceof MDString) {
                directory = ((MDString) dirRef.get()).getString();
            }

            scope.setFile(new LLVMSourceFile(file, directory));
        }

        @Override
        public void visit(MDLexicalBlock md) {
            visit(md, md.getScope(), md.getFile(), md.getLine(), md.getColumn());
        }

        @Override
        public void visit(MDLexicalBlockFile md) {
            visit(md, md.getFile(), md.getFile());
        }

        @Override
        public void visit(MDSubprogram md) {
            visit(md, md.getScope(), MDReference.VOID, md.getLine());
        }

        @Override
        public void visit(MDSubroutine md) {
            // subroutines, unlike subprograms, do not have an attached location descriptor
        }

        @Override
        public void visit(MDCompositeType md) {
            visit(md, md.getScope(), md.getFile(), md.getLine());
        }

        @Override
        public void visit(MDDerivedType md) {
            visit(md, md.getScope(), md.getFile(), md.getLine());
        }

        @Override
        public void visit(MDBasicType md) {
            // A basic type can, according to the llvm implementation, also act as scope. It does
            // however not have a parent scope there. At most a file is given.
            visit(md, MDReference.VOID, md.getFile(), md.getLine());
        }

        @Override
        public void visit(MDCompileUnit md) {
            if (scopes.containsKey(md)) {
                return;
            }

            final LLVMSourceLocation scope = new LLVMSourceLocation(-1L, -1L);
            scopes.put(md, scope);

            final MDReference fileRef = md.getFile();
            if (fileRef != MDReference.VOID) {
                final MDBaseNode fileNode = fileRef.get();
                if (fileNode instanceof MDFile) {
                    copyFile(scope, fileRef);
                    return;

                } else if (fileNode instanceof MDString) {
                    // old-format metadata
                    String file = ((MDString) fileNode).getString();
                    String directory = null;
                    final MDReference dirRef = md.getDirectory();
                    if (dirRef != MDReference.VOID) {
                        final MDBaseNode dirNode = dirRef.get();
                        if (dirNode instanceof MDString) {
                            directory = ((MDString) dirNode).getString();
                        }
                    }
                    scope.setFile(new LLVMSourceFile(file, directory));
                    return;

                }
            }

            final MDReference splitDbgFilenameRef = md.getSplitdebugFilename();
            if (splitDbgFilenameRef != MDReference.VOID) {
                final MDBaseNode splitDbgFileNameNode = splitDbgFilenameRef.get();
                if (splitDbgFileNameNode instanceof MDString) {
                    scope.setFile(new LLVMSourceFile(((MDString) splitDbgFileNameNode).getString(), null));
                }
            }
        }

        @Override
        public void visit(MDNamespace md) {
            visit(md, md.getScope(), md.getFile(), md.getLine());
        }

        @Override
        public void visit(MDModule md) {
            visit(md, md.getScope(), MDReference.VOID);
        }

        @Override
        public void visit(MDGlobalVariable md) {
            visit(md, md.getScope(), md.getFile(), md.getLine());
        }

        @Override
        public void visit(MDLocalVariable md) {
            visit(md, md.getScope(), md.getFile(), md.getLine());
        }

        @Override
        public void visit(MDGlobalVariableExpression md) {
            if (scopes.containsKey(md)) {
                return;
            }

            scopes.put(md, null);
            final LLVMSourceLocation scope = resolve(md.getGlobalVariable());
            scopes.put(md, scope);
        }

        @Override
        public void visit(MDString md) {
            if (scopes.containsKey(md)) {
                return;
            }

            scopes.put(md, null);
            final MDCompositeType ref = typeIdentifier.identify(md.getString());
            final LLVMSourceLocation scope = resolve(ref);
            scopes.put(ref, scope);
            scopes.put(md, scope);
        }

        @Override
        public void visit(MDReference mdRef) {
            if (scopes.containsKey(mdRef)) {
                return;
            }

            if (mdRef != MDReference.VOID) {
                final LLVMSourceLocation target = resolve(mdRef.get());
                if (target != null) {
                    scopes.put(mdRef, target);
                }
            }
        }

        @Override
        public void ifVisitNotOverwritten(MDBaseNode md) {
        }
    }
}
