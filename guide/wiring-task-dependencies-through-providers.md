<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="avoiding-unnecessary-configuration.md">Avoiding Unnecessary Configuration</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
</tr></table>
<!-- PreviousNext:END -->

# Wiring Task Dependencies Through Providers

> Further reading: [Lazy Configuration: Working with task inputs and outputs](https://docs.gradle.org/current/userguide/lazy_configuration.html#working_with_task_inputs_and_outputs)

When you hand a `Provider` to something that consumes files (`CopySpec.from`, `ConfigurableFileCollection.from`, an `@InputFiles` property), Gradle works out which tasks must run first. For a `Provider`, the answer does not come from the value. It comes from the provider's **producer**: the tasks the provider itself says produce its value.

The producer is tracked as you transform providers:

| Provider | Producer |
| --- | --- |
| A `TaskProvider`, or a task's output property (`myTask.flatMap(MyTask::getOutputFile)`) | That task |
| `a.map(...)` | `a`'s producer. What the lambda returns is not inspected |
| `a.zip(b, ...)` | `a`'s and `b`'s producers combined. What the lambda returns is not inspected |
| `a.orElse(b)` | `a`'s producer if `a` might have a value, otherwise `b`'s (see [Falling back with `orElse`](#falling-back-with-orelse)) |
| `a.flatMap(...)` | Only the producer of the provider the lambda returns. `a`'s producer is not included |
| Extension properties with plain values, `project.provider(...)` | Unknown |

The producers of the providers you combine are kept: `envVar.zip(taskOutput, ...)` still depends on the task. What can be lost are task dependencies that only exist in the _value_ a lambda returns, as in `a.map(x -> project.files(someTask))`.

Gradle only looks for those as a fallback, when the producer is _unknown_. But some providers report a known producer with no tasks, for example:

- a `ListProperty`, `SetProperty` or `MapProperty` that nothing has been added to (left unset, or only `.empty()` called on it)
- `providers.environmentVariable(...)`, whether or not the variable is set

Once one of these is anywhere in the chain, the fallback never runs, and the task dependencies in the lambda's result are silently dropped. The build does not fail; the consuming task just runs without the producing task, and its output is missing or stale.

The rule of thumb: **if the lambda returns something with its own task dependencies (a `Provider`, `TaskProvider`, `FileCollection` or `Configuration`), use `flatMap`, not `map`**.

## Passing a task's output to another task

#### Bad:

Wrapping the task in a file collection hides it from the provider:

```java
Provider<FileCollection> files = someSetting.map(value -> project.files(generateTask));

copy.from(files);
```

#### Good:

Derive the provider from the `TaskProvider`, so its producer is the task. Which method to use depends on what the getter returns:

- If it returns a `Provider` or `Property` (such as a `DirectoryProperty` annotated with `@OutputDirectory`), use `flatMap`:

  ```java
  copy.from(generateTask.flatMap(GenerateTask::getOutputDir));
  ```

- If it returns a plain value (such as the `File` from `Sync.getDestinationDir()`), use `map`:

  ```java
  copy.from(syncTask.map(Sync::getDestinationDir));
  ```

Both carry the task as the producer. `flatMap` takes it from the output property, and `map` passes on the producer of the `TaskProvider`.

Plain `map` is fine for transforming the _value_ of a task-derived provider, since the producer passes through unchanged:

```java
Provider<String> fileName = jarTask.flatMap(Jar::getArchiveFile).map(file -> file.getAsFile().getName());
```

## Combining providers with `zip`

`zip` keeps the task dependencies of both inputs, so combining an external value with a task output is fine:

```java
Provider<String> summary = providers.environmentVariable("LABEL")
        .zip(generateTask.flatMap(GenerateTask::getOutputFile), (label, file) -> label + ": " + file.getAsFile().getName());
```

As with `map`, what the combiner returns is not inspected, so it should not return files with their own task dependencies.

## Falling back with `orElse`

`orElse` only keeps the dependencies of the side it expects to use. For environment variables and Gradle properties, Gradle treats the left side as possibly present even when it is unset, so it never picks up the right side's task.

#### Bad:

`generateTask` does not run, even when `OUTPUT_DIR` is unset:

```java
Provider<Directory> dir = providers.environmentVariable("OUTPUT_DIR")
        .map(path -> layout.getProjectDirectory().dir(path))
        .orElse(generateTask.flatMap(GenerateTask::getOutputDir));
```

#### Good:

Decide first, then `flatMap` to the chosen provider:

```java
Provider<String> override = providers.environmentVariable("OUTPUT_DIR");

Provider<Directory> dir = override.map(path -> true)
        .orElse(false)
        .flatMap(present -> present
                ? layout.getProjectDirectory().dir(override)
                : generateTask.flatMap(GenerateTask::getOutputDir));
```

With an extension property on the left instead, `orElse` does use the right side's task when the property is unset.

## Choosing between alternatives

A common pattern is to pick the files to package based on how the build is configured.

#### Bad:

The `map` reports the producer of `useFoo`, not of the chosen file collection:

```java
Provider<Boolean> useFoo = extension.getUseFoo().zip(extension.getFooOptions(), (use, opts) -> use);

copy.from(useFoo.map(use -> use ? project.files(fooTask) : project.files(barTask)));
```

If nothing has been added to the `getFooOptions()` `MapProperty` (it is left unset, or only `.empty()` was called on it), neither `fooTask` nor `barTask` runs.

#### Good:

`flatMap` to the chosen task-derived provider:

```java
copy.from(useFoo.flatMap(use -> use ? fooTask.flatMap(FooTask::getOutputDir) : barTask.flatMap(BarTask::getOutputDir)));
```

A `NamedDomainObjectProvider<Configuration>` is a provider too, so it can be returned from a `flatMap` the same way, and its dependencies are resolved from the `Configuration`.

## Selecting a group of related providers

If several providers are chosen together, it is fine to select a value class holding them with `map`. Store providers in it, not file collections, and have every consumer get them out with `flatMap`:

```java
record Packaging(Provider<?> scripts, Provider<?> libs) {}

Packaging foo = new Packaging(fooScriptsTask.flatMap(FooScripts::getOutputDir), fooLibsConfiguration);
Packaging bar = new Packaging(barScriptsTask.map(Sync::getDestinationDir), project.provider(project::files));

Provider<Packaging> packaging = useFoo.map(use -> use ? foo : bar);
```

#### Bad:

```java
copy.from(packaging.map(Packaging::scripts));
```

#### Good:

```java
copy.from(packaging.flatMap(Packaging::scripts));
```

The `map` that selects the value class has no files of its own to lose. The `flatMap` in each consumer discards its producer and asks the selected provider instead. Anything that only reads plain values from it (`packaging.map(Packaging::name)`) can keep using `map`.

## When the value has to be a `FileCollection`

`flatMap` needs the lambda to return a `Provider`, so it cannot return a `FileCollection` directly. If the value really is a file collection, for example one assembled from several sources, select it with a `Callable` in a file collection instead. Gradle calls it and uses the build dependencies of whatever it returns:

```java
copy.from(project.files((Callable<FileCollection>) () -> useFoo.get() ? fooFiles : barFiles));
```

## Don't paper over this with `dependsOn`

Adding the producing task to `dependsOn` makes one consumer work, but every other consumer of the same provider still has the bug, and the dependency drifts out of sync with what is actually consumed. Fix the provider so the dependency is inferred.

See [Needlessly included tasks](diagnosing-build-performance.md#needlessly-included-tasks) for the wider problems with `dependsOn`, and the [`TaskDependsOn`](../errorprones.md#TaskDependsOn) check, which flags it.

## Things to be aware of

### `flatMap` only keeps the dependencies of what it returns

The outer provider of a `flatMap` is only used to choose a value, so its producer is dropped:

```java
// Only secondTask runs before the copy; firstTask does not
copy.from(firstTask.flatMap(FirstTask::getOutputDir).flatMap(dir -> secondTask.flatMap(SecondTask::getOutputDir)));
```

This is what makes selection work, but it means the selection should only depend on configuration values (extension properties, Gradle properties), not on what a task produces. If the selection reads the _contents_ of a task's output, Gradle fails while building the task graph with `Querying the mapped value of ... before task ... has completed is not supported`. If the choice really depends on a task's output, move the choice into a task.

### Selections are evaluated when the task graph is built

`flatMap` (and the `Callable` approach) evaluate the selection when Gradle works out task dependencies. An error thrown while computing it, such as validation of the build's configuration, fails the build even when the consuming task is disabled or skipped with `onlyIf`, since Gradle still works out the dependencies of tasks it will not run.

## Testing

A dropped task dependency fails silently, so test the end result: assert that the produced files end up where they are consumed, for every alternative including the default. A test that only checks the consuming task succeeded will keep passing.

<!-- PreviousNext:START -->
<hr>
<table><tr>
  <td>Previous: <a href="avoiding-unnecessary-configuration.md">Avoiding Unnecessary Configuration</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="testing-and-running-your-plugins-locally.md">Testing and running your plugins</a></td>
</tr></table>
<!-- PreviousNext:END -->
