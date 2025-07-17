<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="diagnosing-build-performance.md">Diagnosing Build Performance</a></td>
</tr></table>
<!-- PreviousNext:END -->

# Adopting the Configuration Cache

The Gradle Configuration Cache is a powerful new feature. It reduces build latency by caching the task graph from the configuration phase.

Builds run with the Configuration Cache optionally in Gradle 8, by default in Gradle 9, and necessarily by Gradle 10. Furthermore, your build has to meet [strict requirements](https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:requirements) to be compatible. This guide provides practical steps to adopt the Configuration Cache.


## How does Configuration Caching work?

Gradle builds are split into two phases — the configuration phase creates the task graph, while the execution phase runs it. The configuration phase takes various inputs and produces a complete task graph that defines what work needs to be done and in what order.

### Configuration inputs

1. Gradle environment
   - `GRADLE_USER_HOME`
   - Gradle Daemon JVM
2. Init scripts
3. `buildSrc` and included build logic build contents (build scripts, sources, and intermediate build outputs)
4. Build and Settings scripts, including included scripts (`apply from: 'foo.gradle'`)
5. Gradle configuration files (Version Catalogs, dependency verification files, dependency lock files, `gradle.properties` files)
6. Contents of files read at configuration time
7. File system state checked at configuration time (file presence, directory contents, etc.)
8. Custom `ValueSource` values obtained at configuration time (this also includes built-in providers, like `ProviderFactory#exec` and `ProviderFactory#fileContents`).
9. System properties used during the configuration phase
10. Environment variables used during the configuration phase


### Configuration outputs
The configuration phase output is a task [DAG](https://en.wikipedia.org/wiki/Directed_acyclic_graph), describing the target task and its dependencies. Different tasks have different dependencies, and thus produce different task graphs. However, the target is always at the root of the DAG. Here are some of the task graphs of this repository:  

#### Task graph of `./gradlew check`
```
:check                                                    (org.gradle.api.DefaultTask)
+--- :checkstyleMain                                      (org.gradle.api.plugins.quality.Checkstyle)
|    +--- ...
+--- :checkstyleTest                                      (org.gradle.api.plugins.quality.Checkstyle)
|    +--- ...
+--- :spotlessCheck                                       (org.gradle.api.DefaultTask)
|    `--- ...
+--- :test                                                (org.gradle.api.tasks.testing.Test)
|    +--- :classes                                        (org.gradle.api.DefaultTask)
|    |    +--- :compileJava                               (org.gradle.api.tasks.compile.JavaCompile)
|    |    `--- :processResources                          (org.gradle.language.jvm.tasks.ProcessResources)
|    +--- :compileJava                                    (org.gradle.api.tasks.compile.JavaCompile)
|    +--- :compileTestJava                                (org.gradle.api.tasks.compile.JavaCompile)
|    |    +--- :classes                                   (org.gradle.api.DefaultTask)
|    |    |    +--- :compileJava                          (org.gradle.api.tasks.compile.JavaCompile)
|    |    |    `--- :processResources                     (org.gradle.language.jvm.tasks.ProcessResources)
|    |    `--- :compileJava                               (org.gradle.api.tasks.compile.JavaCompile)
|    `--- :testClasses                                    (org.gradle.api.DefaultTask)
|         +--- :compileTestJava                           (org.gradle.api.tasks.compile.JavaCompile)
|         |    +--- :classes                              (org.gradle.api.DefaultTask)
|         |    |    +--- :compileJava                     (org.gradle.api.tasks.compile.JavaCompile)
|         |    |    `--- :processResources                (org.gradle.language.jvm.tasks.ProcessResources)
|         |    `--- :compileJava                          (org.gradle.api.tasks.compile.JavaCompile)
|         `--- :processTestResources                      (org.gradle.language.jvm.tasks.ProcessResources)
...
```

#### Task graph of `./gradlew assemble`
```
:assemble                        (org.gradle.api.DefaultTask)
`--- :jar                        (org.gradle.api.tasks.bundling.Jar)
     +--- :classes               (org.gradle.api.DefaultTask)
     |    +--- :compileJava      (org.gradle.api.tasks.compile.JavaCompile)
     |    `--- :processResources (org.gradle.language.jvm.tasks.ProcessResources)
     `--- :compileJava           (org.gradle.api.tasks.compile.JavaCompile)
```

#### Task graph of `./gradlew build`
```
:build                                                         (org.gradle.api.DefaultTask)
+--- :assemble                                                 (org.gradle.api.DefaultTask)
|    `--- :jar                                                 (org.gradle.api.tasks.bundling.Jar)
|         +--- :classes                                        (org.gradle.api.DefaultTask)
|         |    +--- :compileJava                               (org.gradle.api.tasks.compile.JavaCompile)
|         |    `--- :processResources                          (org.gradle.language.jvm.tasks.ProcessResources)
|         `--- :compileJava                                    (org.gradle.api.tasks.compile.JavaCompile)
`--- :check                                                    (org.gradle.api.DefaultTask)
   +--- :checkstyleMain                                      (org.gradle.api.plugins.quality.Checkstyle)
   |    +--- ...
   +--- :checkstyleTest                                      (org.gradle.api.plugins.quality.Checkstyle)
   |    +--- ...
   +--- :spotlessCheck                                       (org.gradle.api.DefaultTask)
   |    `--- ...
   +--- :test                                                (org.gradle.api.tasks.testing.Test)
   |    `--- ...
   ...
```

### Why cache the configuration phase — speeding up feedback loops

When you first request a task(s) (`./gradlew <tasks-requested...>`), the task graph is serialized and stored on disk. Upon a subsequent request of the same task(s), if none of these inputs have changed, Gradle skips the configuration phase entirely, loads the task graph from disk, and goes straight to the execution phase.

The configuration phase typically runs faster than execution since heavy work belongs in the latter. However, without the cache, configuration phase is rerun with every Gradle run. If you run a unit test multiple times, configuration is repeated, reproducing the same task graph every time. When the task itself is light, configuration can take up a large fraction of the latency. Configuration caching solves this by storing and reusing configuration results between runs, eliminating redundant work and speeding up iteration cycles.

The Configuration Cache dramatically speeds up development feedback loops in two key ways:

1. Eliminates wasted work: Without caching, every Gradle run repeats the configuration phase, reproducing identical task graphs for the same tasks. For light operations like unit tests, this configuration overhead can dominate the total build time.
2. Removes startup delay: When iterating on features, the configuration phase creates a noticeable delay before any actual compilation or testing begins.

By storing and reusing configuration results between runs, the cache delivers immediate productivity gains during development.

## Finding Configuration Cache problems

Start with a workflow you want to speed up (e.g. `./gradlew classes`). Then, to find Configuration Cache problems:

1. Run `./gradlew classes --configuration-cache` 
2. Check the output for any problems. If none appear, great! Your build is compatible.
3. If not, fix the problems.
4. Run `./gradlew classes --configuration-cache` again. In our experience, this reveals additional issues not seen in the first run.

The problems typically fall into three categories:

### Most common categories of errors 

#### external process started `/usr/bin/git --version`

Occurs when you start external processes during the configuration phase. Generally, it is preferred to run external processes in tasks with properly declared inputs and outputs to avoid unnecessary work when the task is UP-TO-DATE.


#### cannot serialize object of type `org.gradle.api.internal.project.DefaultProject`, a subtype of `org.gradle.api.Project`, as these are not supported with the Configuration Cache

Occurs because Gradle tasks shouldn't take Gradle model types as input — `Gradle`, `Settings`, `Project`, `SourceSet`, or `Configuration`. Having these as inputs limits task concurrency (what if two tasks mutate a `Project` concurrently?) and prevents Gradle from serializing task inputs into the cache (Gradle model types aren't serializable).

To solve this, you can
- Declare the smallest "surface area" your task needs as task input, e.g. take in `Property<String>` for project version, instead of taking in `Project` and accessing `Project#version`
- [Inject a service](https://docs.gradle.org/current/userguide/service_injection.html) which provides the information/operation you need

#### invocation of `Task.project` at execution time is unsupported

Similar to the previous error. 



## Solving Configuration Cache problems

Let’s walk through some examples of fixing Configuration Cache problems

> [!TIP]
> If you have a huge Gradle projects with many tasks, you can adopt the Configuration Cache incrementally. First, resolve all configuration phase issues (i.e. `external process started`), as incremental adoption only works for the execution phase. Then, apply the [gradle-incremental-configuration-cache](https://github.com/palantir/gradle-incremental-configuration-cache) plugin. Gradually add tasks to the allow list as they become compatible.


### Example 1: Fixing "external process started"

#### Before: 
- `MyPlugin` is already a [Gradle managed type](managed-types-and-properties.md)
- `MyPlugin#apply` starts an external process `git`. This external process is started in the configuration phase, causing the Configuration Cache to fail

```java
abstract class MyPlugin implements Plugin<Project> {
   @Override
   public final void apply(Project project) {
      String gitTag = getLatestGitTag(project);
      if (gitTag.equals("develop")) {
         // Do something...
      }
   }

   private static String getLatestGitTag(Project project) {
      try {
         Process process = new ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                 .start();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String tag = reader.readLine();
            process.waitFor();
            return tag.strip();
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }
}
```

#### After

- To safely do an external call during the configuration phase, we can use the `ProviderFactory` service.
- To use `ProviderFactory`, we can inject it into `MyPlugin`. 

> [!NOTE]
> To inject a service, make a protected/public abstract getter method returning that service. It has to be prefixed with `get-` for Gradle's injection to work properly!.
> 
> An alternative to `@Inject`-ing an abstract getter is to use a field with constructor injection (see [this code snippet](https://docs.gradle.org/current/userguide/service_injection.html#filesystemoperations)) 



```java
abstract class MyPlugin implements Plugin<Project> {
   @Inject
   protected abstract ProviderFactory getProviderFactory();

   private Provider<String> latestTag = getProviderFactory()
            .exec(execSpec -> execSpec.setCommandLine("git", "describe", "--tags", "--abbrev=0"))
            .getStandardOutput()
            .getAsText()
            .map(String::trim); 

   @Override
   public final void apply(Project project) {
      String gitTag = latestTag.get();
      if (gitTag.equals("develop")) {
         // Do something...
      }
   }
}
```

> [!TIP]
> If ProviderFactory#exec is called multiple times, the underlying external process e.g. git describe is run multiple times.
>
> However, if we have a single reference to the provider returned by ProviderFactory#exec, then we utilize ExecResult's caching, and `git describe` is only called once



### Example 2: Fixing "invocation of `Task.project` at execution time is unsupported"

#### Before
- `MyTask` is already a [Gradle managed type](managed-types-and-properties.md)
- `MyTask` is calling `getProject()` at build time, causing the Configuration Cache to fail.

```java
abstract class MyTask extends DefaultTask {
   @InputFile
   protected abstract FileProperty getMyFiles();

   @TaskAction
   public void delete() {
      boolean deleted = getProject().delete(getMyFiles().getFiles());
      // Do something with `deleted`...
      Path projectDir = getProject().getProjectDir().toPath();
      // Do something with `projectDir`...
   }
}

```

#### After
- To get the project directory, we can use the `ProjectLayout` service.
- To do file system operations like delete, we can use the `FileSystemOperations` service
- We can inject these services into `MyTask`

```java
abstract class MyTask extends DefaultTask {
   @InputFile
   protected abstract FileProperty getMyFiles();

   @Inject
   protected abstract ProjectLayout getProjectLayout();

   @Inject
   protected abstract FileSystemOperations getFileSystemOperations();

   @TaskAction
   public void delete() {
      WorkResult result = getFileSystemOperations().delete(spec -> spec.delete(getMyFiles().getFiles()));
      // Do something with `result`...
      Path projectDir = getProjectLayout().getProjectDirectory().getAsFile().toPath();
      // Do something with `projectDir`...
   }
}
```

### Example 3: Fixing multiple problems

Let's look at a more complicated example involving plain java classes being used by a Task.

#### Before
- `MyTask` is already a [Gradle managed type](managed-types-and-properties.md)
- `MyTask` is calling `getProject()` at build time, causing the Configuration Cache to fail: 
   
   ```
   invocation of `Task.project` at execution time is unsupported
   ```

- `Project` is being passed down to `Intermediate`, which uses two helpers `HelperA` and `HelperB`

```java
abstract class MyTask extends DefaultTask {
   @InputFile
   protected abstract FileProperty getMyFiles();

   @TaskAction
   public void action() {
      Intermediate intermediate = new Intermediate();
      intermediate.doSomething(getProject());
   }
}

class Intermediate {
   public void doSomething(Project project) {
      HelperA helperA = new HelperA();
      helperA.doSomething(project);

      HelperB helperB = new HelperB();
      helperB.doSomething(project);
   }
}

class HelperA {
    public void doSomething(Project project) {
       WorkResult result = project.copy(...);
       // Do stuff with `result`... 
    }
}

class HelperB {
   public void doSomething(Project project) {
       Path projectDir = project.getProjectDir().toPath();
       // Do stuff with projectDir...
   }
}
```

#### After
- Instead of `project.copy(...)`, we can use `FileSystemOperations`
- Instead of `project.getProjectDir()`, we can use `ProjectLayout`
- We can `@Inject` these services into `MyTask`, because `MyTask` is a Gradle managed type
- Then, we can pass these services down the call chain

```java
abstract class MyTask extends DefaultTask {
   @InputFile
   protected abstract FileProperty getMyFiles();
   
   @Inject
   protected abstract FileSystemOperations getFileSystemOperations();
   
   @Inject
   protected abstract ProjectLayout getProjectLayout();

   @TaskAction
   public void action() {
      Intermediate intermediate = new Intermediate(getFileSystemOperations(), getProjectLayout());
      intermediate.doSomething();
   }
}

class Intermediate {
   private FileSystemOperations fileSystemOperations;
   private ProjectLayout projectLayout;
   
   Intermediate(FileSystemOperations fileSystemOperations, ProjectLayout projectLayout) {
      this.fileSystemOperations = fileSystemOperations;
      this.projectLayout = projectLayout;
   }
   
   public void doSomething() {
      HelperA helperA = new HelperA(fileSystemOperations);
      helperA.doSomething();

      HelperB helperB = new HelperB(projectLayout);
      helperB.doSomething();
   }
}

class HelperA {
   private FileSystemOperations fileSystemOperations;
   
   HelperA(FileSystemOperations fileSystemOperations) {
       this.fileSystemOperations = fileSystemOperations;
   }

   public void doSomething() {
      WorkResult result = fileSystemOperations.copy(...);
      // Do stuff with `result`... 
   }
}

class HelperB {
   private ProjectLayout projectLayout;
   
   HelperB(ProjectLayout projectLayout) {
      this.projectLayout = projectLayout;
   }
   
   public void doSomething() {
      Path projectDir = projectLayout.getProjectDirectory().getAsFile().toPath();
      // Do stuff with projectDir...
   }
}
```

#### We can do better!
- Instead of passing services down the call chain (which can bloat constructors), we can inject services into classes directly!
- To inject a service into `HelperA`, we have to make it a Gradle-managed type by making it `abstract`. Ditto for `HelperB`
- Now that `HelperA` is an abstract Gradle-managed type, we need to use `ObjectFactory#newInstance` to instantiate it.
- To get `ObjectFactory` in `Intermediate`, we have to inject it.
- For injection to work in `Intermediate`, we also have to make it a Gradle-managed type.  

```java
abstract class MyTask extends DefaultTask {
   @InputFile
   protected abstract FileProperty getMyFiles();
   
   @Inject
   protected abstract ObjectFactory getObjectFactory();

   @TaskAction
   public void action() {
      Intermediate intermediate = getObjectFactory().newInstance(Intermediate.class);
      intermediate.doSomething();
   }
}

abstract class Intermediate {
   @Inject
   protected abstract ObjectFactory getObjectFactory();
   
   public void doSomething() {
      HelperA helperA = getObjectFactory().newInstance(HelperA.class);
      helperA.doSomething();

      HelperB helperB = getObjectFactory().newInstance(HelperB.class);
      helperB.doSomething();
   }
}

abstract class HelperA {
   @Inject
   protected abstract FileSystemOperations getFileSystemOperations();

   public void doSomething() {
      WorkResult result = getFileSystemOperations().copy(...);
      // Do stuff with `result`... 
   }
}

abstract class HelperB { 
   @Inject 
   protected abstract ProjectLayout getProjectLayout();

   public void doSomething() {
      Path projectDir = getProjectLayout().getProjectDirectory().getAsFile().toPath();
      // Do stuff with projectDir...
   }
}
```

#### And better!
- Since `HelperA` doesn't have a constructor with arguments, we can use Gradle's `@Nested` magic in place of `ObjectFactory#newInstance`. Ditto for `HelperB`

```java
abstract class MyTask extends DefaultTask {
   @InputFile
   protected abstract FileProperty getMyFiles();

   @Nested
   protected abstract Intermediate getIntermediate();

   @TaskAction
   public void action() {
      getIntermediate().doSomething();
   }
}

abstract class Intermediate {
   @Nested
   protected abstract HelperA getHelperA();
   
   @Nested
   protected abstract HelperB getHelperB();

   public void doSomething() {
      getHelperA().doSomething();
      getHelperB().doSomething();
   }
}

abstract class HelperA {
   @Inject
   protected abstract FileSystemOperations getFileSystemOperations();

   public void doSomething() {
      WorkResult result = getFileSystemOperations().copy(...);
      // Do stuff with `result`... 
   }
}

abstract class HelperB {
   @Inject
   protected abstract ProjectLayout getProjectLayout();

   public void doSomething() {
      Path projectDir = getProjectLayout().getProjectDirectory().getAsFile().toPath();
      // Do stuff with projectDir...
   }
}
```


## Two strategies to write Configuration Cache friendly Gradle

To summarize the two strategies we used to solve Configuration Cache issues:

### You can inject services into Gradle-managed types

Do you need the project directory? Inject [`ProjectLayout`](https://docs.gradle.org/current/javadoc/org/gradle/api/file/ProjectLayout.html). Do you need to run a bash command at configuration time? Inject [`ExecOperations`](https://docs.gradle.org/current/javadoc/org/gradle/process/ExecOperations.html). Gradle offers a [list](https://docs.gradle.org/current/userguide/service_injection.html) of injectable services which will cover 99% of your use cases. However, that list is incomplete — in reality,  almost [anything in Gradle source](https://github.com/search?q=repo%3Agradle%2Fgradle%20%40ServiceScope&type=code) annotated with [`@ServiceScope`](https://github.com/gradle/gradle/blob/196bb409d47f5b6e39d62edd39be939f7606a5cc/platforms/core-runtime/stdlib-java-extensions/src/main/java/org/gradle/internal/service/scopes/ServiceScope.java#L43) can be injected into a Gradle managed type.


### You can make any class into a Gradle-managed type

A standard class...

```java
class Employee {
    private String name;

    public Employee(String name) {
        this.name = name;
    }
}
```

...instantiated like so...

```java
Employee employee = new Employee("Kelvin");
```

...can be turned into a Gradle-managed type by making it abstract, and adding `@Inject` to the constructor.

```java
abstract class Employee {
    private String name;

    @Inject
    public Employee(String name) {
        this.name = name;
    }
}
```

Now, we can instantiate it with `ObjectFactory`.

```java
Employee employee = objectFactory.newInstance(Employee.class, "Kelvin");
```

Hold on, what if the class using `Employee` doesn't have access to an `ObjectFactory`? 

```java
class Team {
    private ArrayList<Employee> employees = new ArrayList<>();

    public addEmployee() {
        Employee employee = new Employee("Kelvin");
        employees.add(employee);
    }
}
```

In that case, you can turn *it* a Gradle managed type as well...

```java
abstract class Team {
    private ArrayList<Employee> employees = new ArrayList<>();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    public addEmployee() {
        Employee employee = getObjectFactory().newInstance(Employee.class, "Kelvin");
        employees.add(employee);
    }
}
```

...so on and so forth...

```java
abstract class Deployment {
    private String customerName;

    @Nested
    public abstract Team getTeam();
 
    @Inject
    public Deployment(String customerName) {
        this.customerName = customerName; 
    }
}

```


...until you reach the `Task`/`Project`/`Extension` that uses our class.

```java
abstract class UploadDeploymentInformation extends DefaultTask {
    @Input
    protected abstract Property<URI> getUploadUri();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @TaskAction
    public void compress() {
        Deployment deployment = getObjectFactory().newInstance(Deployment.class, "The Shire");
        UploadUtils.upload(deployment, getUploadUri().get());
    }
}
```

This way, you "propagate" the reach of the Gradle managed type system, removing boilerplate and making Configuration Cache adoption easy.

<!-- PreviousNext:START -->
<hr>
<table><tr>
  <td>Previous: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="diagnosing-build-performance.md">Diagnosing Build Performance</a></td>
</tr></table>
<!-- PreviousNext:END -->
