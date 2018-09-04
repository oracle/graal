package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

public class MainLauncherRootNode extends RootNode {

    private final MethodInfo main;

    public MainLauncherRootNode(EspressoLanguage language, MethodInfo main) {
        super(language);
        this.main = main;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            assert frame.getArguments().length == 0;
            EspressoContext context = main.getContext();
            // No var-args here, pull parameters from the context.
            return main.getCallTarget().call((Object) toGuestArguments(context, context.getMainArguments()));
        } catch (EspressoException wrapped) {
            throw wrapped;
        } catch (Throwable throwable) {
            // Non-espresso exceptions cannot escape to the guest.
            // throw EspressoError.shouldNotReachHere();
            throw new RuntimeException(throwable);
        }
    }

    private static StaticObject toGuestArguments(EspressoContext context, String... args) {
        Meta meta = context.getMeta();
        return (StaticObject) meta.STRING.allocateArray(args.length, i -> meta.toGuest(args[i]));
    }
}
