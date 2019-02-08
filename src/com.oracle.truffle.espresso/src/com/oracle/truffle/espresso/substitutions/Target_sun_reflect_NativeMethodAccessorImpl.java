package com.oracle.truffle.espresso.substitutions;

import java.lang.reflect.InvocationTargetException;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

@EspressoSubstitutions
public final class Target_sun_reflect_NativeMethodAccessorImpl {
    /**
     * <h3>Verbatim {@link java.lang.reflect.Method#invoke}</h3>
     * 
     * Invokes the underlying method represented by this {@code Method} object, on the specified
     * object with the specified parameters. Individual parameters are automatically unwrapped to
     * match primitive formal parameters, and both primitive and reference parameters are subject to
     * method invocation conversions as necessary.
     *
     * <p>
     * If the underlying method is static, then the specified {@code receiver} argument is ignored.
     * It may be null.
     *
     * <p>
     * If the number of formal parameters required by the underlying method is 0, the supplied
     * {@code args} array may be of length 0 or null.
     *
     * <p>
     * If the underlying method is an instance method, it is invoked using dynamic method lookup as
     * documented in The Java Language Specification, Second Edition, section 15.12.4.4; in
     * particular, overriding based on the runtime type of the target object will occur.
     *
     * <p>
     * If the underlying method is static, the class that declared the method is initialized if it
     * has not already been initialized.
     *
     * <p>
     * If the method completes normally, the value it returns is returned to the caller of invoke;
     * if the value has a primitive type, it is first appropriately wrapped in an object. However,
     * if the value has the type of an array of a primitive type, the elements of the array are
     * <i>not</i> wrapped in objects; in other words, an array of primitive type is returned. If the
     * underlying method return type is void, the invocation returns null.
     *
     * @param receiver the object the underlying method is invoked from
     * @param args the arguments used for the method call
     * @return the result of dispatching the method represented by this object on {@code receiver}
     *         with parameters {@code args}
     *
     * @exception IllegalAccessException if this {@code Method} object is enforcing Java language
     *                access control and the underlying method is inaccessible.
     * @exception IllegalArgumentException if the method is an instance method and the specified
     *                object argument is not an instance of the class or interface declaring the
     *                underlying method (or of a subclass or implementor thereof); if the number of
     *                actual and formal parameters differ; if an unwrapping conversion for primitive
     *                arguments fails; or if, after possible unwrapping, a parameter value cannot be
     *                converted to the corresponding formal parameter type by a method invocation
     *                conversion.
     * @exception InvocationTargetException if the underlying method throws an exception.
     * @exception NullPointerException if the specified object is null and the method is an instance
     *                method.
     * @exception ExceptionInInitializerError if the initialization provoked by this method fails.
     */
    @Substitution
    public static Object invoke0(@Host(java.lang.reflect.Method.class) StaticObject method, @Host(Object.class) StaticObject receiver, @Host(Object[].class) StaticObject args) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        StaticObject curMethod = method;
        Method target = null;
        while (target == null) {
            target = (Method) ((StaticObjectImpl) curMethod).getHiddenField("$$method_info");
            if (target == null) {
                curMethod = (StaticObject) meta.Method_root.get(curMethod);
            }
        }
        Method m = target;
        try {
            return m.invokeDirect(receiver,
                            StaticObject.isNull(args)
                                            ? new Object[0]
                                            : ((StaticObjectArray) args).unwrap());

        } catch (EspressoException e) {
            throw meta.throwExWithCause(meta.InvocationTargetException, e.getException());
        } catch (VirtualMachineError vme) {
            // TODO(peterssen): Include stack trace.
            throw meta.throwEx(vme.getClass());
        }
    }
}
