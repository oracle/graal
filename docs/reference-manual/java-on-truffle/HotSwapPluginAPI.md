---
layout: docs-experimental
toc_group: espresso
link_title: HotSwap Plugin API
permalink: /reference-manual/java-on-truffle/hotswap-plugin/
---

# Truffle on Java HotSwap Plugin API

With Java on Truffle you can benefit from enhanced HotSwap [capabilites](Demos.md#enhanced-hotswap-capabilities-with-java-on-truffle) that allow the code to evolve naturally during development without the need for restarting a running application.
​
While code reloading (HotSwap) is a powerful tool, it is not sufficient to reflect all kinds of changes, e.g., changes to annotations, framework-specific changes such as implemented services or beans.
For these things the code often needs to be executed to reload configurations or contexts before the changes are fully reflected in the running instance.
This is where the Truffle on Java HotSwap Plugin API comes in handy.
​
The Truffle on Java HotSwap Plugin API is meant for framework developers by setting up appropriate hooks to reflect changes in response to source code edits in your IDE.
The main design principle is that you can register various HotSwap listeners that will be fired on specified HotSwap events.
Examples include the ability to re-run a static initializer, a generic post HotSwap callback and hooks when implementations for a certain service provider changes.
​
**Note**: The HotSwap Plugin API is under development and more fine-grained registration of HotSwap listeners are likely to be added upon requests from the community.
You are welcomed to send enhancement requests to help shape the API through our community support [channels](https://www.graalvm.org/community/).

Review the HotSwap Plugin API by going through a running example that will enable more powerful reloading support on [Micronaut](https://micronaut.io/).
​
## Micronaut HotSwap Plugin
​
The Micronaut HotSwap plugin example implementation is hosted as a [fork](https://github.com/javeleon/micronaut-core) of Micronaut-core.
The following instructions are based on a macOS X setup and only minor variations are needed for Windows.
To get started:
​
1. Clone the repository:
  ```shell
  git clone git@github.com:javeleon/micronaut-core.git
  ```
  ​
2. Build and publish to local Maven repository:
  ```shell
  cd micronaut-core
  ./gradlew publishMavenPublicationToMavenLocal
  ```
​
Now you will have a HotSwap-ready version of Micronaut.
Before setting up a sample application that uses the enhanced version of Micronaut, look at what the plugin does under the hood.
​
The interesting class is `MicronautHotSwapPlugin` which holds on to an application context that can be reloaded when certain changes are made to the application source code.
The class looks like this:
​
```java
final class MicronautHotSwapPlugin implements HotSwapPlugin {
​
    private final ApplicationContext context;
    private boolean needsBeenRefresh = false;
​
    MicronautHotSwapPlugin(ApplicationContext context) {
        this.context = context;
        // register class re-init for classes that provide annotation metadata
        EspressoHotSwap.registerClassInitHotSwap(
                AnnotationMetadataProvider.class,
                true,
                () -> needsBeenRefresh = true);
        // register ServiceLoader listener for declared bean definitions
        EspressoHotSwap.registerMetaInfServicesListener(
                BeanDefinitionReference.class,
                context.getClassLoader(),
                () -> reloadContext());
        EspressoHotSwap.registerMetaInfServicesListener(
                BeanIntrospectionReference.class,
                context.getClassLoader(),
                () -> reloadContext());
    }
​
    @Override
    public String getName() {
        return "Micronaut HotSwap Plugin";
    }
​
    @Override
    public void postHotSwap(Class<?>[] changedClasses) {
        if (needsBeenRefresh) {
            reloadContext();
        }
        needsBeenRefresh = false;
    }
​
    private void reloadContext() {
        if (Micronaut.LOG.isInfoEnabled()) {
            Micronaut.LOG.info("Reloading app context");
        }
        context.stop();
        context.flushBeanCaches();
        context.start();
​
        // fetch new embedded application bean which will re-wire beans
        Optional<EmbeddedApplication> bean = context.findBean(EmbeddedApplication.class);
        // now restart the embedded app/server
        bean.ifPresent(ApplicationContextLifeCycle::start);
    }
}
```
​
The logic regarding the HotSwap API sits in the constructor of this class.
Micronaut is architected around compile-time annotation processing where annotation metadata is gathered and stored into static fields in generated classes.
Whenever a developer makes a change to a Micronaut-annotated class, the corresponding metadata classes are re-generated.
Since standard HotSwap does not (and it should not) re-run static initializers, with HotSwap Plugin static initializer are re-run for all classes that provide metadata (the Micronaut-generated classes). Thus, this API method `EspressoHotSwap.registerClassInitHotSwap` is used:
​
```java
public static boolean registerClassInitHotSwap(Class<?> klass, boolean onChange, HotSwapAction action)
```
​
This will register a listener on Class changes for the specific class and importantly any subclass thereof.
The `onChange` variable instructs if static initializers should only be re-run if the code within changed.
The `action` parameter is a hook for firing a specific action whenever a static initializer has been re-run.
Here we pass a function for setting the `needsBeenRefresh` field to true whenever an static initializer is re-run.
Upon completion of a HotSwap action the plugin receives a `postHotSwap` call that, in response to a true `needsBeenRefresh`, executes the Micronaut-specific code to reload the application context in the `reloadContext` method.
​
## Detecting and Injecting New Classes
​
HotSwap is designed to enable classes to be hotswapped in a running application.
However, if a developer introduces an entirely new class, e.g., a new `@Controller `class in Micronaut, HotSwap does not magically inject a new class, as doing so would require knowledge about internal classloading logic at the very least.
​
A standard way in which classes are discovered by a framework is through the `ServiceLoader` mechanism.
The Truffle on Java HotSwap API has built-in support for registering service implementation change listeners by means of the method `EspressoHotSwap.registerMetaInfServicesListener`:
​
```java
public static boolean registerMetaInfServicesListener(Class<?> serviceType, ClassLoader loader, HotSwapAction action)
```
​
The current support is limited to listening for implementation changes for class path based service deployment in `META-INF/services`.
Whenever there is a change to the set of service implementations for the registered class type, the `action` is fired.
In the Micronaut HotSwap plugin, `reloadContext` is executed which will then pickup the changes automatically.
​
**Note**: HotSwap actions caused by changes to service implementation changes are fired indepent of HotSwap. As a developer, you do not need to perform a HotSwap from your IDE to see the new functionality in the running application.
​
## Next-Level HotSwap for Micronaut
​
Now that you know how the Micronaut HotSwap plugin works, use this feature in a real application.
Here is a sample application created from the tutorial ["Creating your first Micronaut Graal Application"](https://guides.micronaut.io/latest/micronaut-creating-first-graal-app-gradle-java.html).
Example's sources can be downloaded as a ready-made Gradle project from [here](https://guides.micronaut.io/latest/micronaut-creating-first-graal-app-gradle-java.zip).
Download, unzip and open the project in your IDE.

Before you proceed, make sure that you have Java on Truffle [installed](README.md#install-java-on-truffle) and set the GraalVM as the project SDK.
​
1. In your IDE navigate to the root `build.gradle` within the sample project. Add:

  ```groovy
  run.jvmArgs+="-truffle"
  ```

2. Also add maven local repository where we previously published the enhanced Micronaut framework. For example:
​
  ```shell
  repositories {
      mavenLocal()
      ...
  }
  ```
​
3. In `gradle.properties` update the Micronaut version that you published. For example:
​
  ```groovy
  micronautVersion=2.5.8-SNAPSHOT
  ```
  Now you are all setup.

4. Execute`assemble` task and create a run configuration using the defined `run` gradle task.
​
5. Press the Debug button to start the application in debugging mode, which enables enhanced HotSwap support.

6. Once the application is started, verify that you get a response from the `ConferenceController` by going to `http://localhost:8080/conferences/random`.
​
7. Try to make various changes to the classes within the sample app, e.g., change the `@Controller` mapping to a different value, or add a new `@Get`annotated method and apply HotSwap to see the magic. In case you define a new `@Controller` class, all you need is compiling the class and once the change is picked up by the file system watch, you will see the reload without the need for explicitly HotSwap.
