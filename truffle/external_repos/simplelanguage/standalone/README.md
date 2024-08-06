# The simple language standalone build

By default building with `mvn package` will build a jvm standalone version of simple language that uses the JDK on the JAVA_HOME.
By running `mvn package -Pnative` it will also automatically create a native image of the language.
To use the standalone build either run `target/sl` or `target/slnative` depending on whether the native build was created.
