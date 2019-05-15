package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class DebugExprVarNode extends LLVMExpressionNode {

    private final String name;
    private Iterable<Scope> scopes;
    public final static Object noObj = "<unknown value>";

    public DebugExprVarNode(String name, Iterable<Scope> scopes) {
        this.name = name;
        this.scopes = scopes;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        LLVMLanguage.getLLVMContextReference().get();
        for (Scope scope : scopes) {
            Object vars = scope.getVariables();
            InteropLibrary library = InteropLibrary.getFactory().getUncached();
            library.hasMembers(vars);
            try {
                final Object memberKeys = library.getMembers(vars);
                library.hasArrayElements(memberKeys);
                for (long i = 0; i < library.getArraySize(memberKeys); i++) {
                    final String memberKey = (String) library.readArrayElement(memberKeys, i);
                    if (!memberKey.equals(name))
                        continue;

                    Object member = library.readMember(vars, memberKey);
                    return member;

                }
            } catch (UnsupportedMessageException e) {
                // should only happen if hasMembers == false
            } catch (InvalidArrayIndexException e) {
                // should only happen if memberKeysHasKeys == false
            } catch (UnknownIdentifierException e) {
                // should not happen
            }
        }
        return noObj;
    }

}
