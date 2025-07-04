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

Configuration inputs include:
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

When you first run a gradle task, the task graph is serialized and stored on disk. Upon a subsequent run of the same task, If none of these inputs have changed, Gradle skips the configuration phase entirely, loads the task graph from disk, and goes straight to the execution phase.

The configuration phase typically runs faster than execution since heavy work belongs in the latter. However, without the cache, configuration phase is rerun with every single Gradle run. If you run a unit test multiple times, configuration is repeated, reproducing the same task graph every time. Configuration caching solves this by storing and reusing configuration results between runs, eliminating redundant work and speeding up iteration cycles.



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

The first error occurs when you start external processes without using `ExecOperations`. Gradle needs to be aware of all external processes so it can determine if any changes require the configuration cache to be re-run.

The second and third errors occur because Gradle tasks shouldn't take mutable types as input — Gradle, Settings, Project, SourceSet, or Configuration. Having these as inputs limits task concurrency (what if two tasks mutate a `Project` concurrently?) and prevents Gradle from serializing task inputs into the cache (they are too large to be serialized). 

To solve the second and third errors, you can
- Declare the smallest "surface area" your task needs as task input, e.g. take in `Property<String>` for project version, instead of accessing `project.version`
- [Inject a service](https://docs.gradle.org/current/userguide/service_injection.html) which provides the information/operation you need


## Solving Configuration Cache problems

Let’s walk through fixing a task incompatible with the Configuration Cache.


### Before:

- `ZstdCompressTask` is already a [Gradle managed type](managed-types-and-properties.md)
- `ZstdCompressor` is a POJO.
- `ZstdCompressor`  starts an external process, causing the configuration cache to fail: **external process started**
- Furthermore, `ZstdCompressTask` is calling `getProject()` at build time, causing another configuration cache failure: **cannot serialze object of type `org.gradle.api.internal.project.DefaultProject`**  

```java
abstract class ZstdCompressTask extends org.gradle.api.DefaultTask {
    @Input
    protected abstract Property<Path> getInputFile();

    @Input
    protected abstract Property<Path> getOutputFile();

    @TaskAction
    public void compress() {
        ZstdCompressor compressor = new ZstdCompressor(5);
        compressor.compress(getProject(), getInputFile().get(), getOutputFile().get());
    }
}

class ZstdCompressor {
    private int compressionLevel;

    public ZstdCompressor(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public void compress(Project project, Path inputFileRelative, Path outputFileRelative) {
        Path projectDir = project.getProjectDir().toPath();
        Path inputPath = projectDir.resolve(inputFileRelative);
        Path outputPath = projectDir.resolve(outputFileRelative);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "zstd", "-" + compressionLevel, inputPath.toString(), "-o", outputPath.toString());
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("zstd compression failed with exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to execute zstd compression", e);
        }
    }
}
```

### Step 1: Fixing "external process started"

- To safely do the external call, we need to use `ExecOperations` in `ZstdCompressor`
- To use `ExecOperations`, we can first make `ZstdCompressor` a Gradle-managed type, then inject `ExecOperations`
- To make `ZstdCompressor` a Gradle-managed type, we make it abstract. It now needs to be instantiated with `ObjectFactory::newInstance`
- To get an `ObjectFactory`, we inject it into `ZstdCompressTask`, which is conveniently already a Gradle-managed type

```java
abstract class ZstdCompressTask extends org.gradle.api.DefaultTask {
    @Input
    protected abstract Property<Path> getInputFile();

    @Input
    protected abstract Property<Path> getOutputFile();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @TaskAction
    public void compress() {
        ZstdCompressor compressor = getObjectFactory().newInstance(ZstdCompressor.class, 0);
        compressor.compress(getProject(), getInputFile().get(), getOutputFile().get());
    }
}

abstract class ZstdCompressor {
    @Inject
    protected abstract ExecOperations getExecOperations();

    private int compressionLevel;

    @Inject
    public ZstdCompressor(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public void compress(Project project, Path inputFileRelative, Path outputFileRelative) {
        Path projectDir = project.getProjectDir().toPath();
        Path inputPath = projectDir.resolve(inputFileRelative);
        Path outputPath = projectDir.resolve(outputFileRelative);

        getExecOperations().exec(execSpec -> {
            execSpec.commandLine("zstd", "-" + compressionLevel, inputPath.toString(), "-o", outputPath.toString());
        });
    }
}
```


### Step 2: Fixing "invocation of `Task.project` at execution time is unsupported"

- To access the project dir, we can use the `ProjectLayout` service
- We've already made `ZstdCompressor` a Gradle-managed type! We can simply `@Inject` the service we need.

```java
abstract class ZstdCompressTask extends org.gradle.api.DefaultTask {
    @Input
    protected abstract Property<Path> getInputFile();

    @Input
    protected abstract Property<Path> getOutputFile();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @TaskAction
    public void compress() {
        ZstdCompressor compressor = getObjectFactory().newInstance(ZstdCompressor.class, 0);
        compressor.compress(getInputFile().get(), getOutputFile().get());
    }
}

abstract class ZstdCompressor {
    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    private int compressionLevel;

    @Inject
    public ZstdCompressor(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public void compress(Path inputFileRelative, Path outputFileRelative) {
        Path projectDir = getProjectLayout().getProjectDirectory().getAsFile().toPath();
        Path inputPath = projectDir.resolve(inputFileRelative);
        Path outputPath = projectDir.resolve(outputFileRelative);

        getExecOperations().exec(execSpec -> {
            execSpec.commandLine("zstd", "-" + compressionLevel, inputPath.toString(), "-o", outputPath.toString());
        });
    }
}
```


> [!TIP]
> If you have a huge Gradle projects with many tasks, you can adopt the Configuration Cache incrementally. First, resolve all configuration phase issues, as incremental adoption only works for the execution phase. Then, apply the [gradle-incremental-configuration-cache](https://github.com/palantir/gradle-incremental-configuration-cache) plugin. Gradually add tasks to the allow list as they become compatible.

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
        Deployment startup = getObjectFactory().newInstance(Startup.class, 1000000000000000);
        UploadUtils.upload(startup, getUploadUri());
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
