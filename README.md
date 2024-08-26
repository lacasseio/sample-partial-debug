# Partial Debug Compilation

This sample explores three different scenarios for partial debug compilation.
Typically, all binaries are compiled with the same build type, such as release or debug.
However, there are cases where you might want to compile most binaries in release mode while compiling only a few selected ones in debug mode.
In these scenarios, we build only the selected binaries in debug mode.

One might attempt to configure dependencies as follows:

```groovy
library {
    dependencies {
        implementation(project(':foo')) {
            attributes {
                attribute(... debug attribute ...)
            }
        }
    }
}
```

However, this approach will not produce the desired outcome.
Although the configured dependency will select the correct library, the modified dependency graph won't influence the graph of the built artifacts.
Gradle creates a new, independent dependency graph when compiling and linking the artifact, ignoring the modification to the dependency.

Therefore, we need a more comprehensive solution that applies to all projects and correctly rewires the exports according to the partial debug compilation scenario.
The implementations presented here address this issue.
We recommend adding an additional layer to configure partial debug compilation across all projects.

**Warning:** Partial debug compilation is recommended only during development.

## Exporting Unstripped Release Binaries

By default, Gradle strips release binaries of their debug symbols.
Although developers can still load these binaries in a debugger, it is sometimes advantageous to use the unstripped version.
This export type rewires the link and runtime outgoing buckets to export the linked binaries.
See project `:lib2` for an example.

## Exporting Full Debug Binaries

When consuming a debug variant, Gradle builds all transitive dependencies in debug.
You cannot simply modify the dependency declaration to consume the debug variant selectively.
In this scenario, the developer wants to use primarily release variants but consume the actual debug variant for some projects.
To achieve this, we export the debug variant as the release variant (affecting all consumers) and configure the debug variant to consume only release variants.
See project `:lib` for an example.

## Exporting Partial Debug Binaries

Sometimes, we may need to debug only specific compilation units.
In this scenario, we use a per-source compile flags capability to configure the release variant so that only certain source files are debuggable.
See project `:lib3` for an example.