package org.renjin.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

class RenjinExtension {
    final Property<String> renjinVersion

    final Property<String> renjin

    String resolveRenjinVersion() {
        if(!renjinVersion.isPresent()) {
            throw new RuntimeException("renjinVersion is not specified.")
        }
        return renjinVersion.get();
    }

    @Inject
    RenjinExtension(ObjectFactory objects) {
        renjinVersion = objects.property(String)
    }
}
