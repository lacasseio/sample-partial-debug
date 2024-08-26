package com.example.partialdebug;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.nativeplatform.ComponentWithLinkUsage;
import org.gradle.language.nativeplatform.ComponentWithRuntimeUsage;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;

public abstract /*final*/ class PartialDebugExportType extends ExportAsExtension.ExportType {
    private final ConfigurationContainer configurations;
    private final CppBinary binary;
    private final Set<File> debuggableFiles;

    @Inject
    public PartialDebugExportType(ConfigurationContainer configurations, CppBinary binary, Set<File> debuggableFiles) {
        assert binary.isOptimized() : "requires a release binary";
        for (File debuggableFile : debuggableFiles) {
            if (!debuggableFile.exists()) {
                throw new RuntimeException("debuggable file must exists");
            }
        }
        this.configurations = configurations;
        this.binary = binary;
        this.debuggableFiles = debuggableFiles;
    }

    @Override
    protected void execute() {
        // TODO: configure debuggable files (per source compile flags)
        for (File debuggableFile : debuggableFiles) {
            System.out.printf("Configure file '%s' as debuggable.%n", debuggableFile);
        }

        // Mark outgoing bucket as partial debug
        if (binary instanceof ComponentWithLinkUsage) {
            configurations.named(qualifyingName(binary) + "LinkElements").configure(asPartialDebug());
        }
        if (binary instanceof ComponentWithRuntimeUsage) {
            configurations.named(qualifyingName(binary) + "RuntimeElements").configure(asPartialDebug());
        }
    }

    private static Action<Configuration> asPartialDebug() {
        return configuration -> {
            configuration.attributes(attributes -> {
                attributes.attribute(Attribute.of("com.example.partial-debug", Boolean.class), true);
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
    //endregion
}
