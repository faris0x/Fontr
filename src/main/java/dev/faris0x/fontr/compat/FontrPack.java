// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.faris0x.fontr.FontrEngine;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.core.api.util.function.IOSupplier;
import net.ornithemc.osl.resource.loader.api.resource.ResourceMetadata;
import net.ornithemc.osl.resource.loader.api.resource.ResourceType;
import net.ornithemc.osl.resource.loader.api.resource.pack.ResourceConsumer;
import net.ornithemc.osl.resource.loader.api.resource.pack.ResourcePack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

/**
 * In-memory OSL resource pack that exposes the generated font sheets.
 *
 * <p>Registering a real pack (rather than intercepting Minecraft's resource
 * manager) is what makes the override visible to PolyCompose's
 * {@code McResourceFont} and Cera, which read the OSL resource manager directly.
 * The pack is required and fixed at the top of the stack, so a resource pack's
 * HD font cannot shadow it.</p>
 */
public final class FontrPack implements ResourcePack {
    public static final String ID = "fontr";
    private static final String METADATA =
        "{\"pack\":{\"pack_format\":1,\"description\":\"Fontr generated font\"}}";
    private static final Set<String> NAMESPACES = Collections.singleton("minecraft");

    @Override
    public String getName() {
        return "Fontr";
    }

    @Override
    public boolean hasResource(String path) {
        return ResourcePack.METADATA_FILE.equals(path);
    }

    @Override
    public InputStream getResource(String path) throws IOException {
        if (ResourcePack.METADATA_FILE.equals(path)) {
            return new ByteArrayInputStream(METADATA.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        return type == ResourceType.CLIENT_ASSETS ? NAMESPACES : Collections.emptySet();
    }

    @Override
    public boolean hasResource(ResourceType type, NamespacedIdentifier id) {
        return type == ResourceType.CLIENT_ASSETS && FontrEngine.INSTANCE.resourceBytes(id) != null;
    }

    @Override
    public IOSupplier<InputStream> getResource(ResourceType type, NamespacedIdentifier id) {
        if (type != ResourceType.CLIENT_ASSETS) {
            return null;
        }
        byte[] bytes = FontrEngine.INSTANCE.resourceBytes(id);
        if (bytes == null) {
            return null;
        }
        return () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void findResources(ResourceType type, String namespace, String path,
                              ResourceConsumer consumer) {
        if (type != ResourceType.CLIENT_ASSETS) {
            return;
        }
        FontrEngine.INSTANCE.findResources(namespace, path,
            (id, bytes) -> consumer.accept(id, () -> new ByteArrayInputStream(bytes)));
    }

    @Override
    public <T> T getMetadata(String name, ResourceMetadata.Section.Serializer<T> serializer)
        throws IOException {
        if (!"pack".equals(name)) {
            return null;
        }
        JsonObject root = JsonParser.parseString(METADATA).getAsJsonObject();
        JsonObject section = root.getAsJsonObject(name);
        return section == null ? null : serializer.deserialize(section);
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }
}
