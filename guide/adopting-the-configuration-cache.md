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
3. buildSrc and included build logic build contents (build scripts, sources, and intermediate build outputs)
4. Build and Settings scripts, including included scripts (apply from: foo.gradle)
5. Gradle configuration files (Version Catalogs, dependency verification files, dependency lock files, gradle.properties files)
6. Contents of files read at configuration time
7. File system state checked at configuration time (file presence, directory contents, etc.)
8. Custom ValueSource values obtained at configuration time (this also includes built-in providers, like providers.exec and providers.fileContents).
9. System properties used during the configuration phase
10. Environment variables used during the configuration phase


### Configuration outputs
The configuration phase output is a task [DAG](https://en.wikipedia.org/wiki/Directed_acyclic_graph), describing the target task and it's dependencies. Different tasks have different dependencies, and thus produce different task graphs. However, the target is always at the root of the DAG. Here are some of the task graphs of this repository:  

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

When you first run a gradle task, the task graph is serialized and stored on disk. Upon a subsequent run of the same task, If none of these inputs have changed, Gradle skips the configuration phase entirely, loads the task graph from disk, and goes straight to the execution phase.

The configuration phase typically runs faster than execution since heavy work belongs in the latter. However, without the cache, configuration phase is rerun with every single Gradle run. If you run a unit test multiple times, configuration is repeated, reproducing the same task graph every time. When the task itself is light, configuration can take up a large fraction of the latency. Configuration caching solves this by storing and reusing configuration results between runs, eliminating redundant work and speeding up iteration cycles.



## Finding Configuration Cache problems

To find Configuration Cache problems:

1. Run `./gradlew build --configuration-cache`
2. Check the output for any problems. If none appear, great! Your build is compatible.
3. If not, fix the problems.
4. Run `./gradlew build --configuration-cache` again. In our experience, this reveals additional issues not seen in the first run.

The problems typically fall into three categories:

1. **external process started `/usr/bin/git --version`**
2. **cannot serialize object of type `org.gradle.api.internal.project.DefaultProject`, a subtype of `org.gradle.api.Project`, as these are not supported with the configuration cache**
3. **invocation of `Task.project` at execution time is unsupported**

The first error occurs when you start external processes during the configuration phase. Generally, , it is preferred to run external processes in tasks with properly declared inputs and outputs to avoid unnecessary work when the task is UP-TO-DATE. 

without using `ExecOperations`. Gradle needs to be aware of all external processes so it can determine if any changes require the configuration cache to be re-run.

The second and third errors occur because Gradle tasks shouldn't take mutable types as input — Gradle, Settings, Project, SourceSet, or Configuration. Having these as inputs limits task concurrency (what if two tasks mutate a `Project` concurrently?) and prevents Gradle from serializing task inputs into the cache (they are too large to be serialized). 

To solve the second and third errors, you can
- Declare the smallest "surface area" your task needs as task input, e.g. take in `Property<String>` for project version, instead of taking in `Project` and accessing `project.version`
- [Inject a service](https://docs.gradle.org/current/userguide/service_injection.html) which provides the information/operation you need


## Solving Configuration Cache problems

Let’s walk through making a task compatible with the Configuration Cache.

> [!TIP]
> If you have a huge Gradle projects with many tasks, you can adopt the Configuration Cache incrementally. First, resolve all configuration phase issues, as incremental adoption only works for the execution phase. Then, apply the [gradle-incremental-configuration-cache](https://github.com/palantir/gradle-incremental-configuration-cache) plugin. Gradually add tasks to the allow list as they become compatible.


### Before:

- `CompanyReportPlugin` and `GenerateReportTask` are already [Gradle managed types](managed-types-and-properties.md)
- `ReportGenerator` is a plain old Java object.
- `CompanyReportPlugin::apply` starts an external process in the configuration phase, causing the configuration cache to fail
- Furthermore, `GenerateReportTask` is calling `getProject()` at build time, causing another configuration cache failure: **invocation of `Task.project` at execution time is unsupported**

```java
abstract class CompanyReportPlugin implements Plugin<Project> {
   @Override
   public final void apply(Project project) {
      String gitTag = getLatestGitTag(project);
      if (gitTag.equals("develop")) {
         project.getTasks().register("generateReport", GenerateReportTask.class);
      }
   }

   private static String getLatestGitTag(Project project) {
      try {
         Process process = new ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                 .directory(project.getProjectDir())
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

abstract class GenerateReportTask extends DefaultTask {
   @TaskAction
   public void upload() {
      SassyReporter generator = new ReportGenerator(1000);
      generator.generate(getProject(), Path.of("profits-file"), Path.of("report"));
   }
}

class ReportGenerator {
   private final int minProfit;

   public ReportGenerator(int minProfit) {
      this.minProfit = minProfit;
   }

   public void generate(Project project, Path dataFileRelative, Path reportFileRelative) {
      Path projectDir = project.getProjectDir().toPath();
      try {
         Integer profits = Integer.valueOf(Files.readString(projectDir.resolve(dataFileRelative)));
         String report = profits >= minProfit
                 ? String.format("The company generated $ %d in profits", profits)
                 : "The company is not doing well";
         Files.writeString(reportFileRelative, report);
      } catch (IOException | NumberFormatException e) {
         throw new RuntimeException(e);
      }
   }
}
```

### Step 1: Fixing "external process started"

- To safely do an external call during the configuration phase, we can use the `ExecOperations` service.
- To use `ExecOperations`, we can inject it into `CompanyReportPlugin`. 

```java
abstract class CompanyReportPlugin implements Plugin<Project> {
   @Inject
   protected abstract ExecOperations getExecOperations();

   @Override
   public final void apply(Project project) {
      String gitTag = getLatestGitTag(project);
      if (gitTag.equals("develop")) {
         project.getTasks().register("generateCompanyReport", GenerateReportTask.class);
      }
   }

   private String getLatestGitTag(Project project) {
      try {
         OutputStream output = new ByteArrayOutputStream();
         getExecOperations().exec(execSpec -> {
            execSpec.workingDir(project.getProjectDir());
            execSpec.setCommandLine("git", "describe", "--tags", "--abbrev=0");
            execSpec.setStandardOutput(output);
         });
         return output.toString().strip();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }
}
```

### Step 2: Fixing **invocation of `Task.project` at execution time is unsupported**

- To get the project dir, we can use the `ProjectLayout` service.
- To use `ProjectLayout` in , we can inject it into a gradle-managed type — either `CompanyReportPlugin` or `GenerateReportTask`.
- Let's inject `ProjectLayout` in `GenerateReportTask`, then pass it into the `ReportGenerator`. 

```java
abstract class GenerateReportTask extends DefaultTask {
   @Inject
   protected abstract ProjectLayout getProjectLayout();

   @TaskAction
   public void upload() {
      ReportGenerator generator = new ReportGenerator(1000);
      generator.generate(getProjectLayout(), Path.of("profits-file"), Path.of("report"));
   }
}


class ReportGenerator {
   private final int minProfit;

   public ReportGenerator(int minProfit) {
      this.minProfit = minProfit;
   }

   public void generate(ProjectLayout layout, Path dataFileRelative, Path reportFileRelative) {
      Path projectDir = layout.getProjectDirectory().getAsFile().toPath();
      try {
         Integer profits = Integer.valueOf(Files.readString(projectDir.resolve(dataFileRelative)));
         String report = profits >= minProfit
                 ? String.format("The company generated $ %d in profits", profits)
                 : "The company is not doing well";
         Files.writeString(reportFileRelative, report);
      } catch (IOException | NumberFormatException e) {
         throw new RuntimeException(e);
      }
   }
}
```

### Step 3: Making it clean by using Gradle-managed types

- The plugin now works with the Configuration Cache. However, we can make it cleaner.
- Instead of passing services down the class hierarchy, we can make `ReportGenerator` a Gradle-managed type, and inject directly.
- To make `ReportGenerator` a Gradle-managed type, let's make it abstract, and `@Inject` the constructor. Now, we need to instantiate it with `ObjectFactory::newInstance`
- Now, `ReportGenerator` can inject the services it needs, rather than passing them as parameters.

```java
abstract class GenerateReportTask extends DefaultTask {
   @Inject
   protected abstract ObjectFactory getObjectFactory();

   @TaskAction
   public void upload() {
      ReportGenerator generator = getObjectFactory().newInstance(ReportGenerator.class, 1000);
      generator.generate(Path.of("profits-file"), Path.of("report"));
   }
}

abstract class ReportGenerator {
   private final int minProfit;

   @Inject
   protected abstract ProjectLayout getProjectLayout();

   @Inject
   public ReportGenerator(int minProfit) {
      this.minProfit = minProfit;
   }

   public void generate(Path dataFileRelative, Path reportFileRelative) {
      Path projectDir = getProjectLayout().getProjectDirectory().getAsFile().toPath();
      try {
         Integer profits = Integer.valueOf(Files.readString(projectDir.resolve(dataFileRelative)));
         String report = profits >= minProfit
                 ? String.format("The company generated $ %d in profits", profits)
                 : "The company is not doing well";
         Files.writeString(reportFileRelative, report);
      } catch (IOException | NumberFormatException e) {
         throw new RuntimeException(e);
      }
   }
}
```

> [!NOTE]
> The choice between passing down services into utility classes, versus turning utility classes into Gradle-managed types, is a stylistic one. As a utility class adopts more and more services, it makes sense to turn it into a Gradle-managed to simplify its method signatures. 

### Bonus: Thinking about the design of your tasks

- When refactoring, it's easy to do a 1:1 conversion from an old to a new API. However, it's worthwhile to think about what the task is doing, and whether it is using Gradle's task model correctly. 
- Heavy work (e.g. external processes) should be done in tasks, within the execution phase (i.e. within `@TaskAction`s)

```java
abstract class CompanyReportPlugin implements Plugin<Project> {
    @Override
    public final void apply(Project project) {
        project.getTasks().register("generateCompanyReport", GenerateReportTask.class, task -> {
        });
    }
}

abstract class GenerateReportTask extends DefaultTask {
   @TaskAction
   public void upload() {
      boolean shouldGenerateReport = getLatestGitTag().equals("develop");
      if (shouldGenerateReport) {
         ReportGenerator generator = new ReportGenerator(1000);
         generator.generate(Path.of("profits-file"), Path.of("report"));
      }
   }

   private String getLatestGitTag() {
      try {
         Process process = new ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                 .directory(getProject().getProjectDir())
                 .start();
         process.waitFor();
         return process.getInputStream().toString().strip();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }
}

class ReportGenerator {
   private final int minProfit;

   public ReportGenerator(int minProfit) {
      this.minProfit = minProfit;
   }

   public void generate(Path dataFileAbsolute, Path reportFileAbsolute) {
      try {
         int profits = Integer.parseInt(Files.readString(dataFileAbsolute));
         String report = profits >= minProfit
                 ? String.format("The company generated $ %d in profits", profits)
                 : "The company is not doing well";
         Files.writeString(reportFileAbsolute, report);
      } catch (IOException | NumberFormatException e) {
         throw new RuntimeException(e);
      }
   }
}
```

## Two strategies to write Configuration Cache friendly Gradle

To summarize the two strategies we used to solve Configuration Cache issues:

### You can inject services into Gradle-managed types

Do you need the project directory? Inject [`ProjectLayout`](https://docs.gradle.org/current/javadoc/org/gradle/api/file/ProjectLayout.html). Do you need to run a bash command? Inject [`ExecOperations`](https://docs.gradle.org/current/javadoc/org/gradle/process/ExecOperations.html). Gradle offers a [list](https://docs.gradle.org/current/userguide/service_injection.html) of injectable services which will cover 99% of your use cases. However, that list is incomplete — in reality,  almost [anything in Gradle source](https://github.com/search?q=repo%3Agradle%2Fgradle%20%40ServiceScope&type=code) annotated with [`@ServiceScope`](https://github.com/gradle/gradle/blob/196bb409d47f5b6e39d62edd39be939f7606a5cc/platforms/core-runtime/stdlib-java-extensions/src/main/java/org/gradle/internal/service/scopes/ServiceScope.java#L43) can be injected into a Gradle managed type.



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

> [!TIP]
> `@Nested` is an alternative to `ObjectFactory::newInstance` for classes with nullary constructors.
> It does the same thing as the following
> ```java
> @Inject
> protected abstract ObjectFactory getObjectFactory();
> public Team team = getObjectFactory().newInstance(Team.class);
> ```



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
        UploadUtils.upload(startup, getUploadUri().get());
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
