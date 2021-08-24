package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

@GenerateUncached
@NodeInfo(shortName = "nullcheck")
public abstract class NullCheck extends Node {

    abstract StaticObject execute(StaticObject receiver);

    @Specialization
    StaticObject execute(StaticObject receiver, @Cached BranchProfile exceptionProfile) {
        if (StaticObject.isNull(receiver)) {
            exceptionProfile.enter();
            throw EspressoContext.get(this).getMeta().throwNullPointerException();
        }
        return receiver;
    }
}
