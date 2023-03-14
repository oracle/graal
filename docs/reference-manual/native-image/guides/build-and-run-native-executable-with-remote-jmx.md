---
layout: ni-docs
toc_group: how-to-guides
link_title: Build and Run Native Executables with Remote JMX
permalink: /reference-manual/native-image/guides/build-and-run-native-executable-with-remote-jmx/
---

# Build and Run Native Executables with Remote JMX

Remote management over JMX is now possible in executables built with Native Image.
This feature is still experimental.

This guide will cover the steps required to build, run, and interact with such an executable using JMX.
It will also show you how to register a custom `MBean` with the `MBean` server and the additional steps required for it to work with Native Image.

## Step 1: Create the Demo Application
Create and navigate to a directory named `demo`. All the files in this walk-through should be placed here.

Save the following code to the file named `SimpleJmx.java`.
This will be the JMX server.
Its job is to register a custom `MBean`, then loop endlessly, so we have time to inspect it using VisualVM.

```java
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

public class SimpleJmx {
    public static void main(String args[]) throws Exception {
        ObjectName objectName = new ObjectName("com.jmx.test.basic:name=simple");
        Simple simple = new Simple();
        simple.setName("someName");
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.registerMBean(simple, objectName);
        while(true){
            Thread.sleep(1000);
            System.out.println("JMX server running...");
        }
    }
}
```
Save the following `MBean` implementation to `Simple.java`.
```java
public class Simple implements SimpleMBean {
    private String name;
    @Override
    public String getName() {
        return name;
    }
    @Override
    public void setName(String name) {
        this.name = name;
    }
    @Override
    public String print(){
        return "Print output " + name;
    }
}
```
Save the following `MBean` interface to `SimpleMBean.java`.
```java
public interface SimpleMBean {
    String getName();
    void setName(String name);

    String print();
}
```

## Step 2: Compile To Java Bytecode

Compile the Java file using the GraalVM JDK:
```shell 
$JAVA_HOME/bin/javac SimpleMBean.java Simple.java SimpleJmx.java
```
This will create `SimpleJmx.class`, `Simple.class`, and `SimpleMBean.class`.

## Step 3: Make a Dynamic Proxy Configuration
This is required because we would like to register a custom MBean `SimpleMBean` and be able to interact with it.
In a JSON file, we provide the MBean interface of our custom MBean.
Later, we will provide the `native-image` tool with the path to this JSON file.
```json
[
  { "interfaces": [ "SimpleMBean"] }
]
```
Why is this needed?
Remote management of MBeans can use dynamic proxies to simplify the client's interaction with MBeans on the server (in a different application). 
Using proxies makes the sending/receiving of data transparent. 
Specifically, the connection, request, and return type conversion are all taken care of in the process of forwarding to the MBean server and back.

## Step 4: Build the Native Image with JMX Support
Build a native executable with the VM inspection enabled:
```shell
$JAVA_HOME/bin/native-image --enable-monitoring=jmxserver,jvmstat  -H:DynamicProxyConfigurationFiles=/path/to/proxyconfig.json SimpleJmx

```
The `--enable-monitoring=jmxserver` option enables the JMX Server feature which allows accepting incoming connections.
The `--enable-monitoring=jmxclient` option enables the JMX Client feature which allows making outgoing connections.
Both features can be used together by providing `--enable-monitoring=jmxserver,jmxclient`.
The `jvmstat` option should also be included if you want to enable discovery by other JVMs, ie `--enable-monitoring=jmxserver,jmxclient,jvmstat`.


## Step 5: Run the Executable with JMX Properties
```shell
./simplejmx -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.port=9996 -Dcom.sun.management.jmxremote.ssl=false
```
This will start the application as a simple JMX server, without password authentication or SSL using port 9996. 
You can configure JMX using all the usual properties as shown in [this guide](https://docs.oracle.com/javadb/10.10.1.2/adminguide/radminjmxenabledisable.html), but we use a basic configuration here for simplicity.

## Step 6: Inspect Using VisualVM
Start [VisualVM](https://visualvm.github.io/) to view the managed beans in a user-friendly way.
Go to the **Applications** tab and select **SimpleJmx**. From there you can select the **MBeans** tab.

![Remote JMX](img/rjmx_monitor.png)

In the **MBeans** tab, we can inspect the custom MBean we created earlier and perform operations on it.
![Custom MBean Attributes](img/rjmx_attributes.png)
![Custom MBean Operations](img/rjmx_operations.png)
