package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser;

import java.util.function.BiFunction;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.runtime.LLVMContext;

public class DebugExprSymbolTable {
    public class TabObj {
        public final Object type;
        public final String name;
        public final Object value;

        public TabObj() {
            type = "noType";
            name = "$none";
            value = null;
        }

        public TabObj(String memberKey, Object member, Object metaObject) {
            this.name = memberKey;
            this.value = member;
            this.type = metaObject;
        }

        @Override
        public String toString() {
            return name + " " + type + " " + value.toString();
        }
    }

    private Iterable<Scope> scopes;
    private LLVMContext context;
    private final TabObj noObj = new TabObj();
    private BiFunction<LLVMContext, Object, Object> metaObjFunc;

    public DebugExprSymbolTable(Iterable<Scope> scopes, LLVMContext context, BiFunction<LLVMContext, Object, Object> metaObjFunc) {
        this.scopes = scopes;
        this.context = context;
        this.metaObjFunc = metaObjFunc;
    }

    public TabObj find(String name) {
        for (Scope scope : scopes) {
            scope.getReceiver();
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
                    final Object member = library.readMember(vars, memberKey);
                    final Object metaObject = metaObjFunc.apply(context, member);
                    return new TabObj(memberKey, member, metaObject);
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
