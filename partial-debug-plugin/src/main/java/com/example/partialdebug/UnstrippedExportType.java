package com.example.partialdebug;

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.nativeplatform.ComponentWithLinkUsage;
import org.gradle.language.nativeplatform.ComponentWithRuntimeUsage;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;

import javax.inject.Inject;

public abstract /*final*/ class UnstrippedExportType extends ExportAsExtension.ExportType {
    private final ConfigurationContainer configurations;
    private final CppBinary binary;

    @Inject
    public UnstrippedExportType(ConfigurationContainer configurations, CppBinary binary) {
        assert binary.isOptimized() : "requires a release binary";
        this.configurations = configurations;
        this.binary = binary;
    }

    protected void execute() {
        if (binary instanceof ComponentWithLinkUsage) {
            configurations.named(qualifyingName(binary) + "LinkElements").configure(configuration -> {
                configuration.outgoing(outgoing -> {
                    if (binary instanceof CppSharedLibrary) {
                        outgoing.getArtifacts().clear();
                        outgoing.artifact(((CppSharedLibrary) binary).getLinkTask().flatMap(it -> it.getImportLibrary().orElse(it.getLinkedFile())));
                    }
                });
            });
        }
        if (binary instanceof ComponentWithRuntimeUsage) {
            configurations.named(qualifyingName(binary) + "RuntimeElements").configure(configuration -> {
                configuration.outgoing(outgoing -> {
                    outgoing.getArtifacts().clear();
                    if (binary instanceof CppExecutable) {
                        outgoing.artifact(((CppExecutable) binary).getLinkTask().flatMap(AbstractLinkTask::getLinkedFile));
                    } else if (binary instanceof CppSharedLibrary) {
                        outgoing.artifact(((CppSharedLibrary) binary).getLinkTask().flatMap(AbstractLinkTask::getLinkedFile));
                    }
                });
            });
        }
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
