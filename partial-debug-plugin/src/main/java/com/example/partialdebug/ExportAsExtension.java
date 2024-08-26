package com.example.partialdebug;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.ProductionCppComponent;

import javax.inject.Inject;

public abstract /*final*/ class ExportAsExtension {
    @Inject
    public ExportAsExtension() {
        getType().finalizeValueOnRead();
    }

    public static abstract class ExportType {
        protected abstract void execute();
    }

    protected abstract Property<ExportType> getType();

    public ExportAsExtension as(ExportType exportType) {
        getType().set(exportType);
        return this;
    }

    /*private*/ static abstract /*final*/ class Plugin implements org.gradle.api.Plugin<Project> {
        @Inject
        public Plugin() {}

        @Override
        public void apply(Project project) {
            project.getComponents().withType(ProductionCppComponent.class).configureEach(component -> {
                component.getBinaries().whenElementKnown(ofReleaseVariant(binary -> {
                    ((ExtensionAware) binary).getExtensions().create("export", ExportAsExtension.class);
                }));
                component.getBinaries().whenElementFinalized(ofReleaseVariant(binary -> {
                    ifPresent(((ExtensionAware) binary).getExtensions().getByType(ExportAsExtension.class).getType(), ExportType::execute);
                }));
            });
        }

        private static <T> void ifPresent(Provider<T> self, Action<? super T> action) {
            final T value = self.getOrNull();
            if (value != null) {
                action.execute(value);
            }
        }

        private static <T extends CppBinary> Action<T> ofReleaseVariant(Action<? super T> action) {
            return binary -> {
                if (binary.isDebuggable() && binary.isOptimized()) {
                    action.execute(binary);
                }
            };
        }
    }
}
