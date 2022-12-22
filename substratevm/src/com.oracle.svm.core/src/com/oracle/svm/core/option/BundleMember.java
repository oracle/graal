package com.oracle.svm.core.option;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface BundleMember {
    Role role();

    enum Role {
        Input,
        Output,
        Ignore
    }
}
