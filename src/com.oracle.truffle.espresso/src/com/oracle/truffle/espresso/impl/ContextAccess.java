package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StringTable;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

public interface ContextAccess {
    EspressoContext getContext();

    default EspressoLanguage getEspressoLanguage() {
        return getContext().getLanguage();
    }

    default Types getTypes() {
        return getContext().getTypes();
    }

    default Signatures getSignatures() {
        return getContext().getSignatures();
    }

    default Meta getMeta() {
        return getContext().getMeta();
    }

    default VM getVM() {
        return getContext().getVM();
    }

    default InterpreterToVM getInterpreterToVM() {
        return getContext().getInterpreterToVM();
    }

    default StringTable getStrings() {
        return getContext().getStrings();
    }
}
