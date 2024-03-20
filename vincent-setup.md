## vincent development setup

The current build is based on 

1. **fetch jdk testd by graal**

I used ce-17.0.7+4-jvmci-23.1-b02
```
$ mx fetch-jdk      
[1]   labsjdk-ce-17             | ce-17.0.7+4-jvmci-23.1-b02
[2]   labsjdk-ce-17-debug       | ce-17.0.7+4-jvmci-23.1-b02
.
.
.
Select JDK> 1
```

2. **set JAVA_HOME**
```
export JAVA_HOME=/root/.mx/jdks/labsjdk-ce-17-jvmci-23.1-b02
```

3. **Checkout desired tag and make a new branch**

I used <tagname>=vm-ce-23.0.2 <branchmame>=vincent_dev
```
git fetch --all --tags --prune
git checkout tags/<tagname> -b <branchname>   
```
or just copy:
```
git checkout tags/vm-ce-23.0.2 -b vincent_dev
```

4. **build the graal compiler and attatch to jvm**
```
cd compiler
mx build
mx vm
```