<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="anatomy-of-a-gradle-plugin.md">Anatomy of a Gradle Plugin</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="avoiding-unnecessary-configuration.md">Avoiding Unnecessary Configuration</a></td>
</tr></table>
<!-- PreviousNext:END -->

# Managed Types and Properties

> Further Reading: 

Gradle provides us with some tools to make building plugins easier. Particularly around creating types with less boilerplate. We'll cover how to use these and what it does behind the scenes.

## Managed types

Gradle has a concept of a "managed type". This is an abstract class that contains "managed properties" and should be the preferred way to make types in Gradle.

An example managed type is the extension we saw in the previous section:

```java
public abstract class HelloExtension {
    public abstract Property<String> getName();
    public abstract RegularFileProperty getOutputFile();
}
```

Here, we have an abstract class with no implementation that defines two properties, `name` and `outputFile`. When we instantiate it as an extension:

```java
project.getExtension().create("hello", HelloExtension.class);
```

Gradle makes an implementation class with all the boilerplate like this:

```java
public final class HelloExtension_Generated extends HelloExtension {
    private final Property<String> name;
    private final RegularFileProperty outputFile;
    
    public HelloExtension_Generated(ObjectFactory objects) {
        this.name = objects.property(String.class);
        this.outputFile = objects.fileProperty();
    }
    
    @Override
    public Property<String> getName() {
        return name;
    }
    
    @Override
    public RegularFileProperty getOutputFile() {
        return outputFile;
    }
}
```

This is very similar to [the commonly used `immutables` library](https://immutables.github.io/). However, it is aware of Gradle types so will automatically create instances of properties (and other Gradle services) for us.

It is possible to write extensions and tasks directly as in the `HelloExtension_Generated` class above. If you look into the depths of older Palantir Gradle code you will find many instances of this, but this is no longer recommended. All new code should use Manaded Types.

<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="anatomy-of-a-gradle-plugin.md">Anatomy of a Gradle Plugin</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="avoiding-unnecessary-configuration.md">Avoiding Unnecessary Configuration</a></td>
</tr></table>
<!-- PreviousNext:END -->
