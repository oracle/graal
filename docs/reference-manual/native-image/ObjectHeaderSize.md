---
layout: docs
toc_group: optimizations-and-performance
link_title: Object Header Size in Native Image
permalink: /reference-manual/native-image/optimizations-and-performance/ObjectHeaderSize/
---

# Object Header Size in Native Image

The object header is part of every object in memory, storing metadata about the object, and its size varies depending on the JVM implementation, and specific JVM options such as compressed references.
The size of the object header directly affects the memory footprint of a Java application, particularly if a lot of small objects are allocated.

In Oracle GraalVM Native Image, the object header is 4 bytes by default, which is smaller than when running on HotSpot.

For example, in a 64-bit HotSpot VM with compressed references, an instance of `java.lang.Object` consumes 16 bytes (12-byte header plus 4-byte padding).
Using Oracle GraalVM Native Image, the same object consumes only 8 bytes, offering significant memory savings.
However, in case of Native Image, the object size heavily depends on the used garbage collector (GC), the allocated instance type, and the state of compressed references.
Compressed references use 32-bit instead of 64-bit, and are enabled by default in Oracle GraalVM.

To observe the memory usage differences, consider this example application that measures thread-allocated bytes using the [ThreadMXBean API](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/ThreadMXBean.html):
```java
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;

public class ObjectSize {
    public static void main(String[] args) {
        long threadId = Thread.currentThread().threadId();
        ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long initialValue = threadMXBean.getThreadAllocatedBytes(threadId);

        int count = 12 * 1024 * 1024;
        ArrayList<Object> objects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            objects.add(new Object());
        }

        long allocatedBytes = threadMXBean.getThreadAllocatedBytes(threadId) - initialValue;
        System.out.println("Object allocation test completed: " + objects.hashCode());
        System.out.println("Thread allocated " + allocatedBytes + " bytes");
    }
}
```

The application creates millions of object instances and calculates the total memory allocated during their creation.
The application reports the total allocated bytes, which include both the memory for `ArrayList` and the individual objects.

Running this application on a machine with 16 GB of RAM and **Oracle GraalVM for JDK 23**, produces the following results.

#### Native Image with compressed references and default Serial GC:
```
Object allocation test completed: -718496536
Thread allocated 150995032 bytes
```
Breaking this down translates to:
```
48 MB for the ArrayList
96 MB for the Objects (12 * 1024 * 1024 objects × 8 bytes)
----------------------------------------------------------
Total: 144 MB
```

#### HotSpot with compressed references and default G1 GC:
```
Object allocation test completed: -1131298887
Thread allocated 251658592 bytes
```

Breaking this down translates to:
```
48 MB for the ArrayList
192 MB for the Objects (12 * 1024 * 1024 objects × 16 bytes)
------------------------------------------------------------
Total: 240 MB
```

The primary difference lies in the object header size (4-byte header vs 12-byte header).
Note that the memory footprint for the `ArrayList` is roughly identical in both VMs.
However, the memory for the millions of individual objects diverges due to the larger object headers on HotSpot.

To summarize, when it comes to applications dealing with large numbers of small objects, Native Image may offer a smaller memory footprint.
For Native Image, the object header size primarily depends on the used GC, the allocated instance type, and the state of compressed references.

### Further Reading

- [Memory Management](MemoryManagement.md)