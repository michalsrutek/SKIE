# SKIE

Skie is a compiler plugin that generates Swift wrappers of the Objective-C headers generated by the Kotlin compiler. Its goal is to improve interoperability between Kotlin and Swift by recreating features supported by both languages, but lost in the translation from Kotlin to Objective-C to Swift.

Please note that Skie is still under active development, has not been publicly releaseed, and should not be used in any production project.

SKIE currently supports the following features:

- Sealed class/interfaces
- Exhaustive enums
- Default arguments

Discussion of SKIE is in the `skie` and `skie-pm` Touchlab Slack channels.

## Installation

Make sure that your project uses Gradle 7.3 or higher and exactly the same Kotlin compiler version as SKIE (1.7.20).

Kotlin compiler plugins are generally not stable and break with almost every release. So before you do anything else, check that your project compiles (especially if you had to change the versions).

SKIE is deployed in a private Touchlab Maven repository. To access artifacts from that repository, you need to add the following code in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        // Previously present repositories like mavenCentral()
        // ...
        maven("https://api.touchlab.dev/public")
    }
}

dependencyResolutionManagement {
    repositories {
        // Previously present repositories like mavenCentral()
        // ...
        maven("https://api.touchlab.dev/public")
    }
}
```

To enable SKIE, add the plugin in `build.gradle.kts` of the module that builds the native framework:

```kotlin
plugins {
    id("co.touchlab.skie") version "XXX"
}
```

You can find the most recent version of the plugin by looking at the [tags](https://github.com/touchlab/SKIE/tags) in this repository. The Maven repository does not have a browser at the time of writing.

SKIE should not be applied to other modules - it automatically works with all of the code in every module (including 3rd party dependencies). The Swift/Xcode side does not require any configuration changes.

That is the totality of what's required to integrate SKIE! Below is a list of features that SKIE supports. If you find that you want or need to customize SKIE's behavior, then check out the [Configuration section](#configuration).

## Supported features

### Sealed classes/interfaces

SKIE allows you to exhaustively switch on sealed Kotlin hierarchies from Swift. For example, consider the following Kotlin code:

```kotlin
sealed interface KotlinSealedInterface {
    class Success(val data: List<Any>) : KotlinSealedInterface
    class Error(val message: String) : KotlinSealedInterface
    object Loading : KotlinSealedInterface
}
```

In Kotlin you can write this:

```kotlin
when (sealedInterface) {
    is KotlinSealedInterface.Success -> configureWithData(sealedInterface.data)
    is KotlinSealedInterface.Error -> configureForErrorMessage(sealedInterface.message)
    is KotlinSealedInterface.Loading -> configureForLoading()
}
```

In the above example, the compiler ensures that the `when` expression lists all possible cases (i.e. that it is exhaustive). The compiler also smart-casts the `sealedInterface` expression to the correct type. The smart-cast allows the developer to access the inner properties of each type without an additional cast.

To support this feature in Swift, SKIE generates code that can be reduced to this:

```swift
enum SwiftWrapperEnum {
    case Success(data: [Any])
    case Error(message: String)
    case Loading
}

func onEnum(of sealed: KotlinSealedInterface) -> SwiftWrapperEnum {
    if let sealed = sealed as? KotlinSealedSuccess {
        return SwiftWrapperEnum.Success(data: sealed.data)
    } else if let sealed = sealed as? KotlinSealedError {
        return SwiftWrapperEnum.Error(message: sealed.message)
    } else if sealed is KotlinSealedLoading {
        return SwiftWrapperEnum.Loading
    } else {
        fatalError("Unknown subtype. This error should not happen under normal circumstances since KotlinSealedInterace is sealed.")
    }
}
```

The `onEnum(of:)` function wraps the Kotlin object in a Swift enum.

SKIE leverages the fact that Swift `switch` statements **do not** always require a `default` case. A `default` is **not** required if every possible value of the type being considered is matched by one of the `switch` statement's cases (e.g. a `switch` that takes an enum and has a `case` for each and every `case` in that enum.)

To simulate Kotlin's smart-casting we use an enum with associated values.

Thanks to the above code you can now write this:

```swift
switch onEnum(of: wrapperEnum) {
case .Success(let data):
    configureForSuccess(withData: data)
case .Error(let message):
    configureForError(withMessage: String)
case .Loading:
    configureForLoading()
}
```

If you do not need the smart-casting, you can write just this:

```swift
switch onEnum(of: wrapperEnum) {
case .Success:
    print("success!")
case .Error:
    fatalError()
case .Loading:
    configureForLoading()
}
```

### Exhaustive enums

The Kotlin compiler (without SKIE) generates Kotlin enums as Objective-C classes (albeit with restricted subclassing). As a result, Swift code cannot leverage some of its language features in regards to enums (mainly exhaustive switching).

SKIE adds back this functionality by generating a Swift version of the given Kotlin enum. The Swift enum is accessible without any extra code (like the `onEnum` in the case of sealed classes).
This is possible because SKIE generates a so-called bridging header that tells the Swift compiler how to do the conversion automatically.

For example, consider following Kotlin code:

```kotlin
enum class Direction {
    NORTH, SOUTH, EAST, WEST
}
```

Without SKIE you can still use `switch` from Swift code, like this:

```swift
switch (direction) {
case .north: print("NORTH")
case .south: print("SOUTH")
case .east: print("EAST")
case .west: print("WEST")
default: print("Unknown")
}
```

Note that the `default` case is required. However, with SKIE, the `default` case is no longer necessary. Instead, you will see a compiler warning similar to this one:

```
warning: default will never be executed
default: print("Unknown")
```

### Default arguments/parameters

Default arguments (or parameters) are a feature in both [Kotlin](https://kotlinlang.org/docs/functions.html#default-arguments) and [Swift](https://docs.swift.org/swift-book/LanguageGuide/Functions.html#ID169) that allows caller functions to omit arguments when the missing arguments are provided by the called function. Unfortunately, Objective-C does not support default arguments in any way. Therefore, Swift must always specify all arguments when calling Kotlin functions.

Default arguments are implemented differently in Kotlin and Swift, so the two languages have different semantics for the feature. For example, Kotlin default arguments can access the values of previous function parameters as well as the `this` expression (which is `self` in Swift).

However, Swift default parameters must be globally available. As a result, SKIE cannot generate Swift functions with default arguments (at least not in all cases). To solve this issue, SKIE generates Kotlin overloads of the given functions to match all possible ways to call that functions.

For example, let's take a `data class`:

```kotlin
data class User(
    val name: String, 
    val age: Int
)
```

Kotlin data classes have an automatically generated method named `copy` that creates a new instance of the data class with some values modified. For our data class `User` up above, the method can be written as:

```kotlin
fun User.copy(name: String = this.name, age: Int = this.age) = User(name, age)
```

Without SKIE the `copy` method is exposed to Swift under the following signature: `User.doCopy(name:age:)` due to a naming collision with an Objective-C method named `copy`. Since Swift cannot use Kotlin default arguments, all parameters must be provided, which defeats the purpose of the Kotlin `copy` method.

SKIE generates additional Kotlin overloads, that are visible from Swift under the following signatures:

- `User.doCopy()`
- `User.doCopy(name:)`
- `User.doCopy(age:)`

These overloads allow Swift to call `copy` method as if the default arguments were directly supported.

#### Limitations

While this approach to default arguments is completely transparent from Swift, it has some drawbacks:

- It does not support interface methods (all other types of functions are supported, including interface extensions).
- Generated overloads may cause resolution conflicts.
- The number of generated overloads is `O(2^n)` where `n` is the number of default arguments (not all parameters).

SKIE tries to avoid generating functions that would cause conflicts, however the implementation is not complete yet. Specifically, it does not yet properly handle inheritance, generics, and generated overloads of multiple functions with default arguments. If you run into this issue, you might have to disable the code generation for one of the functions (see [Local Configuration section](#local-via-kotlin-annontation)).

Alternatively, you can rename one conflicting functions (or their parameters).

Since it is not possible to generate exponential numbers of functions, the number of default arguments supported is limited to 5, so that at most 31 additional functions will be generated per function with default arguments. If a function has more than 5 default arguments, SKIE will not generate any extra functions.

The limit of 5 was chosen as the result of internal experiments, but that number might change. For now, we think it's a suitable balance between the number of supported cases (since not many functions exceed that number) and the overhead introduced in the form of compilation time and binary size.

The maximum number of default arguments can be explicitly configured using the `DefaultArgumentInterop.MaximumDefaultArgumentCount` key/annotation (see Configuration, and specifically Local in the next section).

*NOTE: All of the above-mentioned problems might be mitigated in the future versions of SKIE. For instancce, the limit on the number of default arguments may increase in the future as we test the plugin on larger projects.*

## Configuration

SKIE is an opinionated plugin and makes choices that might not work for every use case, so it provides a way to change some of its default behavior. There are two different ways to change the configuration:

- locally - using Kotlin annotations
- globally - using Gradle extension provided by the SKIE Gradle plugin

### Local (via Kotlin annontation)

A local configuration change affects the behavior of a single declaration, which makes it suitable for suppressing the plugin if for example it does not work properly because of a bug.

The available annotations can be found in [the `:plugin:generator:configuration-annotations` module](https://github.com/touchlab/SKIE/tree/main/plugin/generator/configuration-annotations).

The following example changes the name of the `onEnum(of:)` function generated for `SealedKotlinClass` to `something(of:)`:

```kotlin
// SealedKotlinClass.kt

@SealedInterop.Function.Name("something")
sealed class SealedKotlinClass {
    ...
}
```

To use these annotations you need to add a dependency in `build.gradle.kts`:

```kotlin
dependencies {
    implementation("co.touchlab.skie:skie-generator-configuration-annotations:XXX")
}
```

These annotations can be used in any module that has access to that dependency - not just the one that applies the SKIE plugin.

### Global (via Gradle extension)

The global configuration can be applied to any class, including those from third party dependencies, which makes it a good place for changing SKIE's default behavior and providing configuration for classes that you cannot modify.

Global configurations are performed through a `skie` Gradle extension:

```kotlin
// build.gradle.kts
import co.touchlab.skie.configuration.gradle.SealedInterop

skie {
    configuration {
        group {
            SealedInterop.Function.Name("something")
        }
    }
}
```

The above example changes the name of the `onEnum(of:)` function to `something(of:)` for **all** sealed classes and interfaces. Note that you can add multiple options to a single `group { ... }`.

All of the available configuration options are listed in [the `:plugin:generator:configuration-gradle` module](https://github.com/touchlab/SKIE/tree/main/plugin/generator/configuration-gradle). Make sure that you import classes from package `co.touchlab.skie.configuration.gradle` (not `.annotations`).

The configuration can also be applied such that it affects only some declarations:

```kotlin
// build.gradle.kts

skie {
    configuration {
        group("co.touchlab.") {
            SealedInterop.Function.Name("something")
        }
    }
}
```

The configuration in the above example only applies to declarations from the `co.touchlab` package. The argument represents a prefix that is matched against the declaration's fully qualified name. You can target all declarations (by passing an empty string), everything in a package, just a single class, etc.

The `group` block can be called multiple times so that declarations can have different configurations. For example:

```kotlin
// build.gradle.kts

skie {
    configuration {
        group {
            SealedInterop.Function.Name("something")
        }
        group("co.touchlab.") {
            SealedInterop.Function.Name("somethingElse")
        }
    }
}
```

If multiple matching groups provide the same configuration key, then only the last one added will be implemented.


### Both Local and Global

Local and global configuration can be used at the same time, in which case the local configuration takes precedence. This behavior can be overridden like so:

```kotlin
// build.gradle.kts

skie {
    configuration {
        group(overridesAnnotations = true) {
            SealedInterop.Function.Name("something")
        }
    }
}
```

If the `overridesAnnotations` argument is set, then all keys in the group take precedence over the annotations. Keep in mind that the configuration can still be changed by another `group` block. Annotations can still be used to configure behavior not specified in the overriding group.

Configuration can be loaded from a file:

```kotlin
// build.gradle.kts

skie {
    configuration {
        from(File("config.json"))
    }
}
```

`group` and `from` can be freely mixed together and repeated multiple times. The file format is identical to [`acceptance-tests/src/test/resources/tests/config.json`](/acceptance-tests/src/test/resources/tests/config.json).
