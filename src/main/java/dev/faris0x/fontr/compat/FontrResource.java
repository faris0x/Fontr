package dev.faris0x.fontr.compat;

import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.resource.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** An in-memory resource served in place of a vanilla font file. */
public final class FontrResource implements Resource {
    private final Identifier location;
    private final byte[] data;

    private FontrResource(Identifier location, byte[] data) {
        this.location = location;
        this.data = data;
    }

    public static FontrResource of(Identifier location, byte[] data) {
        return new FontrResource(location, data);
    }

    @Override
    public Identifier getLocation() {
        return location;
    }

    @Override
    public InputStream asStream() {
        return new ByteArrayInputStream(data);
    }

    @Override
    public boolean hasMetadata() {
        return false;
    }

    @Override
    public <T extends ResourceMetadataSection> T getMetadata(String name) {
        return null;
    }

    @Override
    public String getSourceName() {
        return "Fontr";
    }
}
