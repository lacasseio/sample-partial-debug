package com.example.partialdebug;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.language.BinaryProvider;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.ProductionCppComponent;
import org.gradle.language.nativeplatform.ComponentWithLinkFile;
import org.gradle.language.nativeplatform.ComponentWithLinkUsage;
import org.gradle.language.nativeplatform.ComponentWithRuntimeUsage;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;

import javax.inject.Inject;

public abstract /*final*/ class DebugVariantExportType extends ExportAsExtension.ExportType {
    private final ConfigurationContainer configurations;
    private final ProductionCppComponent component;
    private final CppBinary binary;

    @Inject
    public DebugVariantExportType(ConfigurationContainer configurations, ProductionCppComponent component, CppBinary binary) {
        assert binary.isOptimized() : "requires a release binary";
        this.configurations = configurations;
        this.component = component;
        this.binary = binary;
    }

    @Override
    protected void execute() {
        // Reconfigure debug binary
        asDebug(binary).whenFinalized(binary -> {
            configurations.named("cppCompile" + capitalize(qualifyingName(binary))).configure(toResolveReleaseVariant());
            configurations.named("nativeLink" + capitalize(qualifyingName(binary))).configure(toResolveReleaseVariant());
            configurations.named("nativeRuntime" + capitalize(qualifyingName(binary))).configure(toResolveReleaseVariant());
        });

        // Rewire release binary
        if (binary instanceof ComponentWithLinkUsage) {
            configurations.named(qualifyingName(binary) + "LinkElements").configure(configuration -> {
                configuration.outgoing(outgoing -> {
                    if (binary instanceof ComponentWithLinkFile) {
                        outgoing.getArtifacts().clear();
                        outgoing.artifact(asDebug(binary).flatMap(it -> ((ComponentWithLinkFile) it).getLinkFile()));
                    }
                });
            });
        }
        if (binary instanceof ComponentWithRuntimeUsage) {
            configurations.named(qualifyingName(binary) + "RuntimeElements").configure(configuration -> {
                configuration.outgoing(outgoing -> {
                    outgoing.getArtifacts().clear();
                    if (binary instanceof CppExecutable) {
                        outgoing.artifact(asDebug(binary).flatMap(it -> ((CppExecutable) it).getDebuggerExecutableFile()));
                    } else if (binary instanceof CppSharedLibrary) {
                        outgoing.artifact(asDebug(binary).flatMap(it -> ((CppSharedLibrary) it).getRuntimeFile()));
                    }
                });
            });
        }
    }

    private BinaryProvider<? extends CppBinary> asDebug(CppBinary binary) {
        return component.getBinaries().getByName(binary.getName().replace("Release", "Debug"));
    }

    private static Action<Configuration> toResolveReleaseVariant() {
        return configuration -> {
            configuration.attributes(attributes -> {
                attributes.attribute(CppBinary.OPTIMIZED_ATTRIBUTE, true);
            });
        };
    }

    //region Names
    private static String qualifyingName(CppBinary binary) {
        // The binary name follow the pattern <componentName><variantName>[Executable]
        String result = binary.getName();
        if (result.startsWith("main")) {
            result = result.substring("main".length());
        }

        // CppTestExecutable
        if (binary instanceof CppTestExecutable) {
            result = result.substring(0, binary.getName().length() - "Executable".length());
        }

        return uncapitalize(result);
    }

    private static String uncapitalize(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    //endregion
}
