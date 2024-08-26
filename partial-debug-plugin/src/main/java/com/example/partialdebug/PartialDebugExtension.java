package com.example.partialdebug;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.ProductionCppComponent;
import org.gradle.language.nativeplatform.ComponentWithLinkFile;
import org.gradle.language.nativeplatform.ComponentWithLinkUsage;
import org.gradle.language.nativeplatform.ComponentWithRuntimeUsage;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;

import javax.inject.Inject;

public abstract /*final*/ class PartialDebugExtension {
    private enum DebugType {
        DEFAULT, FULL
    }

    public PartialDebugExtension() {
        getDebugType().finalizeValueOnRead();
    }

    public PartialDebugExtension fullDebug() {
        getDebugType().set(DebugType.FULL);
        return this;
    }

    protected abstract Property<DebugType> getDebugType();

    private <T extends CppBinary> Action<T> selected(Action<? super T> action) {
        return binary -> {
            if (getDebugType().getOrElse(DebugType.DEFAULT).equals(DebugType.FULL)) {
                action.execute(binary);
            }
        };
    }

    /*private*/ static abstract /*final*/ class Plugin implements org.gradle.api.Plugin<Project> {
        @Inject
        public Plugin() {}

        @Override
        public void apply(Project project) {
            PartialDebugExtension extension = project.getExtensions().create("partialDebug", PartialDebugExtension.class);

            project.getComponents().withType(ProductionCppComponent.class).configureEach(component -> {
                component.getBinaries().whenElementFinalized(ofDebugVariant(extension.selected(binary -> {
                    project.getConfigurations().named("cppCompile" + capitalize(qualifyingName(binary))).configure(toResolveReleaseVariant());
                    project.getConfigurations().named("nativeLink" + capitalize(qualifyingName(binary))).configure(toResolveReleaseVariant());
                    project.getConfigurations().named("nativeRuntime" + capitalize(qualifyingName(binary))).configure(toResolveReleaseVariant());

                    // Rewire release to export debug binary
                    if (binary instanceof ComponentWithLinkUsage) {
                        project.getConfigurations().named(forRelease(qualifyingName(binary) + "LinkElements")).configure(configuration -> {
                            configuration.outgoing(outgoing -> {
                                if (binary instanceof ComponentWithLinkFile) {
                                    outgoing.getArtifacts().clear();
                                    outgoing.artifact(((ComponentWithLinkFile) binary).getLinkFile());
                                }
                            });
                        });
                    }
                    if (binary instanceof ComponentWithRuntimeUsage) {
                        project.getConfigurations().named(forRelease(qualifyingName(binary) + "RuntimeElements")).configure(configuration -> {
                            configuration.outgoing(outgoing -> {
                                outgoing.getArtifacts().clear();
                                if (binary instanceof CppExecutable) {
                                    outgoing.artifact(((CppExecutable) binary).getDebuggerExecutableFile());
                                } else if (binary instanceof CppSharedLibrary) {
                                    outgoing.artifact(((CppSharedLibrary) binary).getRuntimeFile());
                                }
                            });
                        });
                    }
                })));
            });
        }

        private static String forRelease(String debugName) {
            return debugName.replace("debug", "release");
        }

        private static Action<Configuration> toResolveReleaseVariant() {
            return configuration -> {
                configuration.attributes(attributes -> {
                    attributes.attribute(CppBinary.OPTIMIZED_ATTRIBUTE, true);
                });
            };
        }

        private static <T extends CppBinary> Action<T> ofDebugVariant(Action<? super T> action) {
            return binary -> {
                if (binary.isDebuggable() && !binary.isOptimized()) {
                    action.execute(binary);
                }
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
}
