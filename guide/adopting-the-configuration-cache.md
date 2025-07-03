<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="diagnosing-build-performance.md">Diagnosing Build Performance</a></td>
</tr></table>
<!-- PreviousNext:END -->

# Adopting the Configuration Cache

The Gradle Configuration Cache is a new feature which aims to decrease build latency by caching the outputs of the configuration phase, i.e. the task graph. 

Builds run with the Configuration Cache optionally in Gradle 8, by default in Gradle 9, and necessarily by Gradle 10. Furthermore, your Gradle build has to fulfill [strict requirements]([this section of the Gradle User Guide](https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:requirements)) to be Configuration Cachable. This section aims to be a practical guide for adopting the Configuration Cache in your Gradle projects.


## Finding Configuration Cache problems

To find Configuration Cache problems:

1. Run `./gradlew build --configuration-cache`
2. Find the configuration cache problems in the output. If there are no problems, congratulations! Your build is Configuration Cache compatible.
3. If not, fix the problems. 
4. Run `./gradlew build --configuration-cache` again. In our experience, this uncovers more problems that weren't there in (1). 

The problems you find in step (2) are usually one of two kinds:

1. **external process started `/usr/bin/git --version`**
2. **cannot serialize object of type `org.gradle.api.internal.project.DefaultProject`, a subtype of `org.gradle.api.Project`, as these are not supported with the configuration cache**

The first error occurs when you start external processes without using `ExecOperations`. Gradle wants to know about external processes

The second error occurs when you pass large, mutable Gradle types into tasks — Gradle, Settings, Project, SourceSet, Configuration. To solve this, you can either explicitly declare your inputs in tasks (take in Property\<String\> for project version, instead of accessing project.version within a task), or you can inject a service which contains the information/operation you need.


## Solving Configuration Cache problems

Let's look at an example of 

### Before:

- `ZstdCompressTask` is already a gradle-managed type
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


### Step 2: Fixing the use of `Project` 

- To access the project dir, we can use the `ProjectLayout` service
- We've already made `ZstdCompressor` a Gradle-managed type! We can simply @Inject the service we need.

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
> If you have huge Gradle projects with many tasks, you can adopt the Configuration Cache incrementally. Firstly, solve all configuration phase issues, as incremental adoption only works for the execution phase.. Then, apply the [gradle-incremental-configuration-cache](https://github.com/palantir/gradle-incremental-configuration-cache) plugin. Then, as you make more and more tasks Configuration-Cache friendly, you can add them to the [allow list](https://github.com/palantir/gradle-incremental-configuration-cache?tab=readme-ov-file#motivation).  

## Two key principles behind writing Configuration Cache friendly Gradle

To summarize the two principles we used to solve Configuration Cache issues:

### You can make almost anything into a Gradle-managed type

A standard class

```java
class Counter {
    private int count;

    public Counter(int init) {
        this.count = init;
    }
}
```

instantiated like so

```java
Counter counter = new Counter(5);
```

can be turned into a Gradle-managed type by making it abstract, and adding `@Inject` to the constructor

```java
abstract class Counter {
    private int count;

    @Inject
    public Counter(int init) {
        this.count = init;
    }
}
```

Now, we can instantiate it with `ObjectFactory`

```java
Counter counter = objectFactory.newInstance(Counter.class, 5);
```


### You can inject things into Gradle-managed types

Do you need the project dir? Inject ProjectLayout. Do you need to run a bash command? Inject ExecOperations. Gradle provides a [list](https://docs.gradle.org/current/userguide/service_injection.html) of things that can be injected. However, that list is incomplete — in reality,  almost anything in Gradle source annotated with [@ServiceScope](https://github.com/gradle/gradle/blob/196bb409d47f5b6e39d62edd39be939f7606a5cc/platforms/core-runtime/stdlib-java-extensions/src/main/java/org/gradle/internal/service/scopes/ServiceScope.java#L43) can be injected into a Gradle managed type.




<!-- PreviousNext:START -->
<hr>
<table><tr>
  <td>Previous: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="diagnosing-build-performance.md">Diagnosing Build Performance</a></td>
</tr></table>
<!-- PreviousNext:END -->
