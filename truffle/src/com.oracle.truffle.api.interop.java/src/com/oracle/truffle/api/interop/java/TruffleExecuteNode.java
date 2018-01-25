package com.oracle.truffle.api.interop.java;

import static com.oracle.truffle.api.interop.ForeignAccess.sendExecute;
import static com.oracle.truffle.api.interop.ForeignAccess.sendIsExecutable;
import static com.oracle.truffle.api.interop.ForeignAccess.sendIsInstantiable;
import static com.oracle.truffle.api.interop.ForeignAccess.sendNew;

import java.lang.reflect.Type;
import java.util.function.BiFunction;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

class TruffleExecuteNode extends Node {

    @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();
    @Child private Node isInstantiable = Message.IS_INSTANTIABLE.createNode();
    @Child private Node execute = Message.createExecute(0).createNode();
    @Child private Node instantiate = Message.createNew(0).createNode();
    private final BiFunction<Object, Object[], Object[]> toGuests = JavaInterop.ACCESSOR.engine().createToGuestValuesNode();
    private final ConditionProfile condition = ConditionProfile.createBinaryProfile();
    @Child private ToJavaNode toHost = ToJavaNode.create();

    protected Class<?> getResultClass() {
        return Object.class;
    }

    protected Type getResultType() {
        return Object.class;
    }

    protected Class<?> getArgumentClass() {
        return Object[].class;
    }

    protected Type getArgumentType() {
        return Object[].class;
    }

    public final Object execute(Object languageContext, TruffleObject function, Object functionArgsObject) {
        Class<?> argumentClass = getArgumentClass();
        if (!argumentClass.isInstance(functionArgsObject)) {
            CompilerDirectives.transferToInterpreter();
            throw HostEntryRootNode.newIllegalArgumentException(
                            String.format("Function arguments must be of type %s but is %s.", argumentClass.getName(), functionArgsObject != null ? functionArgsObject.getClass().getName() : "null"));
        }
        Object[] argsArray;
        if (argumentClass == Object[].class) {
            argsArray = (Object[]) functionArgsObject;
        } else {
            argsArray = new Object[]{functionArgsObject};
        }
        Object[] functionArgs = toGuests.apply(languageContext, argsArray);
        Object result;
        try {
            if (condition.profile(sendIsExecutable(isExecutable, function))) {
                result = sendExecute(execute, function, functionArgs);
            } else if (sendIsInstantiable(isInstantiable, function)) {
                result = sendNew(instantiate, function, functionArgs);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw HostEntryRootNode.newUnsupportedOperationException("Unsupported operation.");
            }
        } catch (UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw HostEntryRootNode.newIllegalArgumentException("Illegal argument provided.");
        } catch (ArityException e) {
            CompilerDirectives.transferToInterpreter();
            throw HostEntryRootNode.newIllegalArgumentException("Illegal number of arguments.");
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw HostEntryRootNode.newUnsupportedOperationException("Unsupported operation.");
        }
        return toHost.execute(result, getResultClass(), getResultType(), languageContext);
    }
}