<div style="display: flex; justify-content: space-between;">
    <span>Previous: [Background: What is a Gradle file?](background-what-is-a-gradle-file.md)</span>
    <span>Next: [Testing and running your plugins](testing-and-running-your-plugins-locally.md)</span>
</div>

# Anatomy of a Gradle Plugin

> Further reading: [Developing Custom Gradle Plugins](https://docs.gradle.org/current/userguide/custom_plugins.html)

Gradle plugins are made up 3 mains parts: Plugins, Extensions and Tasks. This will provide an overview of them before we dive deeper into the best practice for each.

## A simple motivating example

Let's try to make a basic plugin that just writes a name to file, where both the name and output file are configurable. Something we can use in a Gradle build likelike:

```gradle
apply plugin: 'hello-plugin'

hello {
    name = 'callum'
    outputFile = file('build/name')
}
```

Such that when the user runs:

```commandline
./gradlew sayHello
```

it writes `callum` to the file `build/name`.

## Plugins

A plugin defines the entrypoint to your Gradle code. When a Gradle script calls:

```groovy
apply plugin: 'hello-plugin'
```

Gradle looks for a `Plugin` class registered under the name `hello-plugin`[^1], instantiates it with the default constructor, then calls the `apply` method on it:

[^1]: [Gradle looks for a file on the classpath](https://docs.gradle.org/current/userguide/custom_plugins.html#behind_the_scenes) called `META-INF/gradle-plugins/<plugin-id>.properties` that lists the `implementation-class` of the plugin

```java
public class HelloPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // do stuff
    }
}
```

That's it! You have access to `Project`[^2] variable that allows you to create extensions/tasks/whatever else you need.

[^2]: `Settings` instead if you make a setting plugin aka `Plugin<Settings>`.

## Extensions

Extensions are the way users can enter configuration for your Gradle plugin. We want to allow our users to set a `name`, looking something like:

```gradle
hello {
    name = 'callum'
    outputFile = file('build/name')
}
```

The way we do this is to make an extension class that holds the configuration state (we'll dive deeper into how the properties/abstract classes work later):

```java
public abstract class HelloExtension {
    public abstract Property<String> getName();
    public abstract RegularFileProperty getOutputFile();
}
```

In the plugin, we need to create an instance of the extension class and give it a name (`hello`) so it can be referenced from the Gradle build.

```java
public class HelloPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getExtensions().create("hello", HelloExtension.class);
    }
}
```

Now people can set configuration inside their Gradle build script!

## Tasks

Tasks are what actually make changes to files in the build. We first need to make a new class to define the Task:

> Note: There's a better way of writing tasks than this! But we'll go into later and keep this simple for now.

```java
public abstract class SayHelloTask extends DefaultTask {
    // All inputs/outputs to the task must be listed as properties, and annotated
    // whether they are inputs or outputs so Gradle can work out when a task is up-to-date 
    @Input
    public abstract Property<String> getName();
    
    @OutputFile
    public abstract RegularFileProperty getOutputFile();
    
    @TaskAction
    public static void action() throws IOException {
        Path outputPath = getOutputFile().getAsFile().get().toPath();
        String name = getName().get();
        
        Files.writeString(outputPath, name);
    }
}
```

We need to do two further things:

1. Create the task with the name `sayHello`.
1. Wire up the configuration from the extension to the task.

```java
public class HelloPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        HelloExtension extension = project.getExtensions().create("hello", HelloExtension.class);
        
        project.getTasks().register("sayHello", SayHelloTask.class, sayHello -> {
            // wire up the inputs to of the tasks 
            sayHello.getName().set(extension.getName());
            sayHello.getOutputFile().set(extension.getOutputFile());
        });
    }
}
```

And we're done! We've:

1. Made a plugin entry point.
2. Used that to set up an extension for users to add configuration.
3. Created a task that is configured via the extension to actually do some work.

## Next steps

This is pretty much the simplest possible Gradle plugin. In the next sections, we'll go over the best practice for each of the different parts, looking at how any magic works, common mistakes to avoid and how to structure your code to avoid bugs.