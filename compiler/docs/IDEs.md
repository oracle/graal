## Loading the Project into IDEs

### IntelliJ

Download and install the latest IntelliJ IDEA Community Edition: [https://www.jetbrains.com/idea/download/](https://www.jetbrains.com/idea/download/)

Change the IntelliJ maximum memory to 2 GB or more. As per the [instructions](https://www.jetbrains.com/idea/help/increasing-memory-heap.html#d1366197e127), from the main menu choose **Help | Edit Custom VM Options** and modify the **-Xmx** and **-Xms** options.

Enable parallel builds in **Preferences > Build, Execution, Deployment > Compiler > Compile independent modules in parallel**.

Open IntelliJ and go to **Preferences > Plugins > Browse Repositories**. Install the following plugins:

* [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546): formats code according to Eclipse
* [Checkstyle-IDEA](https://plugins.jetbrains.com/plugin/1065): runs style checks as you develop
* [Save Actions](https://plugins.jetbrains.com/plugin/7642): allows code reformatting on save similar to Eclipse
* [FindBugs-IDEA](https://plugins.jetbrains.com/plugin/3847): looks for suspicious code
* [Python Plugin](https://plugins.jetbrains.com/idea/plugin/631-python): python plugin
* [Markdown Navigator](https://plugins.jetbrains.com/plugin/7896-markdown-navigator): markdown plugin

Check that the bundled Ant plugin is enabled in **Preferences > Plugins > Installed** (you may get `Unknown artifact properties: ant-postprocessing.` errors in your project artifacts otherwise).

Make sure you have [`mx`](https://github.com/graalvm/mx) installed and updated (`mx update`). Then, to initialize IntelliJ project files, go to the root of your project and invoke: `mx intellijinit`

Open the folder of your freshly initialized project from IntelliJ (**IntelliJ IDEA > File > Open…**). All depending projects will be included automatically.

Configure the `Eclipse Code Formatter` (**IntelliJ IDEA > Preferences > Other Settings > Eclipse Code Formatter**):

1. Set "Use the Eclipse code formatter"
2. Choose the right version of the formatter for your project (e.g., 4.5 vs 4.6)

##### Making IntelliJ Feel Similar to Eclipse (Optional)

Set IntelliJ to use the Eclipse compiler by going to *IntelliJ IDEA > Preferences > Build, Execution, Deployment > Java Compiler*
To make IntelliJ work the same way as Eclipse with respect to Problems View and recompilation you need to:

1. In preferences set the "Make project automatically" flag.
2. Open the problems view:  View > Tool Windows > Problems
3. Navigate the problems with Cmd ⌥ ↑ and Cmd ⌥ ↓


### Eclipse
This section describes how to set up Eclipse for development. For convenience, `$GRAAL` denotes your local repository.

Eclipse can be downloaded [here](http://download.eclipse.org/eclipse/downloads/). The currently recommended version for development is 4.7.3a ("Oxygen").

Once you have installed Eclipse, if you have multiple Java versions on your computer, you should edit [eclipse.ini](http://wiki.eclipse.org/Eclipse.ini) to [specify the JVM](http://wiki.eclipse.org/Eclipse.ini#Specifying_the_JVM) that Eclipse will be run with. It must be run with a JDK 9 or later VM. For example:
```
-vm
/usr/lib/jvm/jdk-9.0.4/bin/java
```

When first launching Eclipse, you should create a new workspace for development. Select the parent of  `$GRAAL` as the workspace as you will also be importing projects from the suites that the compiler depends on.

The configurations created by the `mx eclipseinit` command binds projects to Execution Environments or JREs corresponding to the Java compliance level of the projects. You need to configure these Execution Environments and JREs as follows:

1. From the main menu bar, select **Window > Preferences**.
2. On the left, select **Java > Installed JREs**
3. Ensure there is an installed JRE with the name `jdk-11`.
4. Select **Execution Environments** and configure the **JavaSE-1.8** environment.
4. Click **OK**

Run `mx eclipseinit` to create the Eclipse project configurations for all the Java projects then use the **Import Wizard** to import the created/updated projects:

1. From the main menu bar, select **File > Import...** to open the Import Wizard.
2. Select **General > Existing Projects into Workspace** and click **Next**.
3. Enter the parent of the `$GRAAL` directory in the **Select root directory** field.
4. Under **Projects** select all the projects.
5. Click **Finish** to complete the import.

Any time Eclipse updates a class file used by the compiler, the updated classes are automatically deployed to the right place so that the next execution of the VM will see the changes.

> After updating your sources and re-running `mx eclipseint`, new Eclipse projects made be created and old ones removed. This usually results in an Eclipse error message indicating that a project is missing another required Java project. To handle this, you simply need repeat the steps above for importing projects.

In order to debug with Eclipse, you should launch using the `-d` global option as described in [Debugging](Debugging.md).
