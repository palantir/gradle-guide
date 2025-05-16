package com.palantir.gradle.guide.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary = "When defining a custom Task or Extension, you should make it an abstract class "
                + "with abstract getter methods of each of the properties and other "
                + "gradle containers (eg NamedDomainObjectSet). Gradle will then automatically "
                + "create the properties and containers, removing a lot of boilerplate. Additionally, "
                + "as you declare eg `public abstract Property<Integer> getFoo()`, this will automatically "
                + "make the `foo = 3` groovy syntax work of the box.")
public final class NonAbstractGradleType extends GradleGuideBugChecker
        implements BugChecker.ClassTreeMatcher, BugChecker.MethodInvocationTreeMatcher {
    private static final Matcher<ClassTree> IS_TASK = Matchers.isSubtypeOf("org.gradle.api.Task");
    private static final Matcher<ExpressionTree> IS_EXTENSION_CREATE = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.plugins.ExtensionContainer")
            .named("create");
    private static final Supplier<Type> CLASS_TYPE_SUPPLIER = Suppliers.typeFromString("java.lang.Class");

    private static final Set<String> SUPPORTED_PROPERTY_TYPES = Set.of(
            "org.gradle.api.provider.Property",
            "org.gradle.api.provider.ListProperty",
            "org.gradle.api.provider.SetProperty",
            "org.gradle.api.provider.MapProperty",
            "org.gradle.api.file.RegularFileProperty",
            "org.gradle.api.file.DirectoryProperty",
            "org.gradle.api.provider.Provider",
            "org.gradle.api.model.ObjectFactory");

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (tree.getKind().equals(Kind.INTERFACE)) {
            return Description.NO_MATCH;
        }

        if (IS_TASK.matches(tree, state)) {
            if (!tree.getModifiers().getFlags().contains(Modifier.ABSTRACT)) {
                return buildDescription(tree)
                        .setMessage("Gradle managed Task types must be abstract classes.")
                        .build();
            }
            return tree.getMembers().stream()
                    .filter(member -> member instanceof MethodTree)
                    .map(member -> (MethodTree) member)
                    .filter(method -> isManagedPropertyGetter(method, state))
                    .map(method -> {
                        if (!method.getModifiers().getFlags().contains(Modifier.ABSTRACT)) {
                            return buildDescription(method)
                                    .setMessage("Gradle managed property getter must be abstract.")
                                    .build();
                        }
                        if (!method.getName().toString().startsWith("get")) {
                            return buildDescription(method)
                                    .setMessage("Gradle managed property getter must be named starting with 'get'.")
                                    .build();
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(Description.NO_MATCH);
        }

        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // We have to do our checks on Extensions where they are created rather than on the Extension types themselves
        // This is because Extension types do not extend a class or implement an interface. There's no way to tell if
        // a certain class is an extension or not just looking at its type declaration alone.. Especially given
        // there are many other types with names ending in Extension, eg Junit 5 extensions.
        if (!IS_EXTENSION_CREATE.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        // We need at least 2 arguments (name and class)
        if (tree.getArguments().size() < 2) {
            return Description.NO_MATCH;
        }

        // Get the second argument which should be the class type
        ExpressionTree classArg = tree.getArguments().get(1);

        Optional<Type> extensionTypeOpt = typeArgumentFromPossibleClassType(state, classArg);
        if (extensionTypeOpt.isEmpty()) {
            return Description.NO_MATCH;
        }
        Type extensionType = extensionTypeOpt.get();

        // Check: must be abstract, not interface
        if (!(isTypeAbstract(extensionType) || extensionType.isInterface())) {
            return buildDescription(tree)
                    .setMessage("Gradle managed Extension types must be abstract classes.")
                    .build();
        }

        ClassSymbol extSym = (ClassSymbol) extensionType.tsym;
        // Stream over the symbol table for methods
        return StreamSupport.stream(extSym.members().getSymbols().spliterator(), false)
                .filter(memberSym -> memberSym instanceof MethodSymbol)
                .map(memberSym -> (MethodSymbol) memberSym)
                .filter(memberSym -> isManagedPropertyGetter(memberSym, state))
                .map(memberSym -> {
                    if ((memberSym.flags() & Flags.ABSTRACT) == 0) {
                        return buildDescription(tree)
                                .setMessage("Gradle managed property getter in Extension must be abstract: "
                                        + memberSym.getSimpleName())
                                .build();
                    }
                    if (!memberSym.getSimpleName().toString().startsWith("get")) {
                        return buildDescription(tree)
                                .setMessage(
                                        "Gradle managed property getter in Extension must be named starting with 'get': "
                                                + memberSym.getSimpleName())
                                .build();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Description.NO_MATCH);
    }

    private static Optional<Type> typeArgumentFromPossibleClassType(VisitorState state, ExpressionTree classArg) {
        // From a possible Class<T> extract T
        return Optional.ofNullable(ASTHelpers.getType(classArg))
                .filter(argType -> ASTHelpers.isSubtype(argType, CLASS_TYPE_SUPPLIER.get(state), state))
                .filter(argType -> !argType.getTypeArguments().isEmpty())
                .map(argType -> argType.getTypeArguments().get(0))
                .filter(extensionType -> extensionType.tsym != null);
    }

    private static boolean isTypeAbstract(Type type) {
        return (type.tsym.flags() & Flags.ABSTRACT) != 0;
    }

    private static boolean isManagedPropertyGetter(MethodTree method, VisitorState state) {
        if (!method.getParameters().isEmpty()) {
            return false;
        }
        Type returnType = ASTHelpers.getType(method.getReturnType());
        if (returnType == null || returnType.tsym == null) {
            return false;
        }
        String rawType = returnType.tsym.getQualifiedName().toString();
        return SUPPORTED_PROPERTY_TYPES.contains(rawType);
    }

    private static boolean isManagedPropertyGetter(MethodSymbol method, VisitorState state) {
        if (!method.getParameters().isEmpty()) {
            return false;
        }
        Type returnType = method.getReturnType();
        if (returnType == null || returnType.tsym == null) {
            return false;
        }
        String rawType = returnType.tsym.getQualifiedName().toString();
        return SUPPORTED_PROPERTY_TYPES.contains(rawType);
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoPageLink("managed-types-and-properties.md");
    }
}
