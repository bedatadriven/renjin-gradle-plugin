package org.renjin.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

class RenjinExtension {
    final Property<String> renjinVersion

    final Property<List<String>> linkingTo

    @Inject
    RenjinExtension(ObjectFactory objects) {
        renjinVersion = objects.property(String)
        linkingTo = objects.property(List.class).convention(Collections.emptyList())
    }
}
