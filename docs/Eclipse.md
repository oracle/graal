This page describes how to set up Eclipse for Graal development. For convenience, `$GRAAL` denotes your local Graal repository (i.e. the directory containing `README.md`).

Eclipse can be downloaded [here](http://download.eclipse.org/eclipse/downloads/). The currently supported version for Graal development is 4.5.2.

Once you have installed Eclipse, if you have multiple Java versions on your computer, you should edit [eclipse.ini](http://wiki.eclipse.org/Eclipse.ini) to [specify the JVM](http://wiki.eclipse.org/Eclipse.ini#Specifying_the_JVM) that Eclipse will be run with. It must be run with a JDK 8 VM. For example:
```
-vm
/usr/lib/jvm/jdk1.8.0/bin/java
```

When first launching Eclipse, you should create a new workspace for Graal development. Select the parent of  `$GRAAL` as the workspace as you will also be importing projects from the suites that Graal depends on.

The configurations created by the `mx eclipseinit` command binds projects to Execution Environments corresponding to the Java compliance level of the projects. You need to configure these Execution Environments as follows:

1. From the main menu bar, select **Window > Preferences**.
2. On the left, select **Java > Installed JREs > Execution Environments**
3. Configure the **JavaSE-1.8** environments to refer to a 1.8 JRE. You may need to first add any missing JRE. 
4. Click **OK**

Run `mx eclipseinit` to create the Eclipse project configurations for all the Java projects then use the **Import Wizard** to import the created/updated projects:

1. From the main menu bar, select **File > Import...** to open the Import Wizard.
2. Select **General > Existing Projects into Workspace** and click **Next**.
3. Enter the parent of the `$GRAAL` directory in the **Select root directory** field.
4. Under **Projects** select all the projects.
5. Click **Finish** to complete the import.

Any time Eclipse updates a class file needed by the Graal runtime, the updated classes are automatically deployed to the right place so that the next execution of the VM will see the changes.

> Occasionally, a new Eclipse project is added to Graal.  This usually results in an Eclipse error message indicating that a project is missing another required Java project. To handle this, you simply need repeat the steps above for importing projects.

In order to debug Graal with Eclipse, you should launch Graal using the `-d` global option as described [Debugging](Debugging.md).
