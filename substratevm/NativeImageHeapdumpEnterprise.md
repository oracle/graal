# Generating Heap Dumps from Native Images

With GraalVM Enterprise Edition you can generate heap dumps of the Native Image processes to monitor the execution.

Native Image does not implement JVMTI agent and it is not possible to trigger
heap dump creation using tools like _VisualVM_ or _jmap_. You can build a native image for your application in a way so that it can handle certain signals and then get a heap
dump when the application receives the `USR1` signal (other supported signals are `QUIT/BREAK` for stackdumps and `USR2` to dump runtime compilation info). You only need to build your image with GraalVM Enterprise Native Image and use the `-H:+AllowVMInspection` option.

Another possibility is to
write a special method which will generate a heap dump at certain points in the
lifetime of your application. For example, when certain conditions are met while
executing a native image, your application code can trigger heap dump creation.
A dedicated [`org.graalvm.nativeimage.VMRuntime#dumpHeap`](https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/VMInspection.java) API exists for this
purpose. Both possibilities are covered in this guide.

Note: This feature is available with **GraalVM Enterprise** only.

## Handle SIGUSR1 Signal
The following Java example is a simple multi-threaded application which runs for
60 seconds. There is enough time to get its PID and send the SIGUSR1 signal
which will generate a heap dump into the application's working directory. Save
the following code as _SVMHeapDump.java_ file on your disk:
```java
import java.text.DateFormat;
import java.util.Date;

public class SVMHeapDump extends Thread {
    static int i = 0;
    static int runs = 60;
    static int sleepTime = 1000;
    @Override
    public void run() {
        System.out.println(DateFormat.getDateTimeInstance().format(new Date()) + ": Thread started, it will run for " + runs + " seconds");
        while (i < runs){
            System.out.println("Sleeping for " + (runs-i) + " seconds." );
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ie){
                System.out.println("Sleep interrupted.");
            }
            i++;
        }
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        StringBuffer sb1 = new StringBuffer(100);
        sb1.append(DateFormat.getDateTimeInstance().format(new Date()));
        sb1.append(": Hello GraalVM native image developer! \nGet PID of this process: ");
        sb1.append("'ps -C svmheapdump -o pid= '\n");
        sb1.append("then send it signal: ");
        sb1.append("'kill -SIGUSR1 <pid_printed_above>' \n");
        sb1.append("to get heap dump generated into working directory.\n");
        sb1.append("Starting thread!");
        System.out.println(sb1);
        SVMHeapDump t = new SVMHeapDump();
        t.start();
        while (t.isAlive()) {
            t.join(0);
        }
        sb1 = new StringBuffer(100);
        sb1.append(DateFormat.getDateTimeInstance().format(new Date()));
        sb1.append(": Thread finished after: ");
        sb1.append(i);
        sb1.append(" iterations.");
        System.out.println(sb1);
    }
}
```
#### Build a Native Image
Compile SVMHeapDump.java as following:
```shell
$JAVA_HOME/bin/javac SVMHeapDump.java
```
If you run it on `java`, you will see that it runs for 60 seconds then finishes.

Build a native executable and provide the `-H:+AllowVMInspection` option for the
builder. This way the native executable will accept SIGUSR1 signal to produce a
heap dump.

```shell
$JAVA_HOME/bin/native-image SVMHeapDump -H:+AllowVMInspection
[svmheapdump:41691]    classlist:     412.03 ms,  2.52 GB
[svmheapdump:41691]        (cap):   1,655.34 ms,  2.52 GB
[svmheapdump:41691]        setup:   2,741.18 ms,  2.52 GB
[svmheapdump:41691]     (clinit):     190.08 ms,  2.59 GB
[svmheapdump:41691]   (typeflow):   5,231.29 ms,  2.59 GB
[svmheapdump:41691]    (objects):   6,489.13 ms,  2.59 GB
[svmheapdump:41691]   (features):     203.11 ms,  2.59 GB
[svmheapdump:41691]     analysis:  12,394.98 ms,  2.59 GB
[svmheapdump:41691]     universe:     425.55 ms,  2.59 GB
[svmheapdump:41691]      (parse):   1,418.69 ms,  2.59 GB
[svmheapdump:41691]     (inline):   1,289.94 ms,  2.59 GB
[svmheapdump:41691]    (compile):  21,338.61 ms,  2.62 GB
[svmheapdump:41691]      compile:  24,795.01 ms,  2.62 GB
[svmheapdump:41691]        image:   1,446.14 ms,  2.62 GB
[svmheapdump:41691]        write:   5,482.12 ms,  2.62 GB
[svmheapdump:41691]      [total]:  47,805.47 ms,  2.62 GB
```

The `native-image` builder analyzes existing `SVMHeapDump.class` and creates from
it an executable file. When the command completes, `svmheapdump` is created in
the current directory.

##### Run the application and check the heap dump
Run the application:
```shell
./svmheapdump
May 15, 2020, 4:28:14 PM: Hello GraalVM native image developer!
Get PID of this process: 'ps -C svmheapdump -o pid= '
then send it signal: 'kill -SIGUSR1 <pid_printed_above>'
to get heap dump generated into working directory.
Starting thread!
May 15, 2020, 4:28:14 PM: Thread started, it will run for 60 seconds
```

Open the 2nd terminal to get the process ID of the running `svmheapdump`
application using a command like `ps -C svmheapdump -o pid=` for Linux OS and
`pgrep svmheapdump` for macOS. Copy the printed process ID, e.g. 100, and use it
to send the signal to the running application:
```shell
kill -SIGUSR1 100
```
The heap dump will be available at the working directory while the application continues to run.

## Generate a Heap Dump from within a Java Application

The following Java example shows how a heap dump can be generated from within
a running Java application using `VMRuntime.dumpHeap()` after some condition is met.
The condition to generate a heap dump is provided as an option on the command line.
Save the code snippet below as _SVMHeapDumpAPI.java_.

```java
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import org.graalvm.nativeimage.VMRuntime;

public class SVMHeapDumpAPI {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        StringBuffer sb1 = new StringBuffer(100);
        sb1.append(DateFormat.getDateTimeInstance().format(new Date()));
        sb1.append(": Hello GraalVM native image developer. \nYour command line options are: ");
        String liveArg = "true";
        if (args.length > 0) {
            sb1.append(args[0]);
            System.out.println(sb1);
            if (args[0].equalsIgnoreCase("--heapdump")){
                if(args.length > 1 ) {
                  liveArg = args[1];
                }
                createHeapDump(Boolean.valueOf(liveArg));
            }
        } else {
            sb1.append("None");
            System.out.println(sb1);
        }
     }

    /**
     * Generate heap dump and save it into temp file
     */
     private static void createHeapDump(boolean live) {
     try {
         File file = File.createTempFile("SVMHeapDump-", ".hprof");
         VMRuntime.dumpHeap(file.getAbsolutePath(), live);
         System.out.println("  Heap dump created " + file.getAbsolutePath() + ", size: " + file.length());
     } catch (UnsupportedOperationException unsupported) {
         System.out.println("  Heap dump creation failed." + unsupported.getMessage());
     } catch (IOException ioe) {
         System.out.println("IO went wrong: " + ioe.getMessage());
     }
 }
}
```
The application creates some data to have something to dump, checks the command line
to see if heap dump has to be created, and then in method `createHeapDump()` creates
the actual heap dump, performing checks for file's existence.

#### Building a Native Image
In the next step, compile _SVMHeapDumpAPI.java_:
```shell
$JAVA_HOME/bin/javac SVMHeapDumpAPI.java
```
Then build a native executable:
```shell
$JAVA_HOME/bin/native-image SVMHeapDumpAPI
[svmheapdumpapi:41691]    classlist:     447.96 ms,  2.53 GB
[svmheapdumpapi:41691]        (cap):   2,105.64 ms,  2.53 GB
[svmheapdumpapi:41691]        setup:   3,010.19 ms,  2.53 GB
[svmheapdumpapi:41691]     (clinit):     178.51 ms,  2.61 GB
[svmheapdumpapi:41691]   (typeflow):   9,153.49 ms,  2.61 GB
[svmheapdumpapi:41691]    (objects):   9,170.40 ms,  2.61 GB
[svmheapdumpapi:41691]   (features):     347.67 ms,  2.61 GB
[svmheapdumpapi:41691]     analysis:  19,208.00 ms,  2.61 GB
[svmheapdumpapi:41691]     universe:     390.40 ms,  2.61 GB
[svmheapdumpapi:41691]      (parse):   1,519.70 ms,  2.63 GB
[svmheapdumpapi:41691]     (inline):   1,072.87 ms,  2.63 GB
[svmheapdumpapi:41691]    (compile):  36,028.90 ms,  2.61 GB
[svmheapdumpapi:41691]      compile:  40,595.67 ms,  2.61 GB
[svmheapdumpapi:41691]        image:   2,384.57 ms,  2.61 GB
[svmheapdumpapi:41691]        write:   3,161.35 ms,  2.63 GB
[svmheapdumpapi:41691]      [total]:  69,300.73 ms,  2.63 GB
```

When the command completes, the `svmheapdumpapi` executable is created in the current directory.

##### Run the application and check the heap dump
Now you can run your native image application and generate a heap dump from it
with the output similar to one below:
```shell
./svmheapdumpapi --heapdump
Sep 15, 2020, 4:06:36 PM: Hello GraalVM native image developer.
Your command line options are: --heapdump
  Heap dump created /var/folders/hw/s9d78jts67gdc8cfyq5fjcdm0000gp/T/SVMHeapDump-6437252222863577987.hprof, size: 8051959
```

The resulting heap dump can be then opened with the [VisualVM](https://www.graalvm.org/docs/tools/visualvm) tool like any other Java heap dump.
