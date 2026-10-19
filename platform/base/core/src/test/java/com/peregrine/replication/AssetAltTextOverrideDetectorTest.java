package com.peregrine.replication;

import com.peregrine.reference.Reference;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AssetAltTextOverrideDetectorTest {
    @Test
    public void recognizesLegacyTextWithoutASwitch() {
        final Resource pageContent = resource("/page/jcr:content", Map.of(), null);
        final Resource image = resource("/page/jcr:content/image", Map.of("imagealttext", "Custom text"), pageContent);
        assertTrue(AssetAltTextOverrideDetector.hasOverride(new Reference(image, "imagesrc", image), pageContent));
    }

    @Test
    public void explicitAssetModeWinsOverOldText() {
        final Resource pageContent = resource("/page/jcr:content", Map.of(), null);
        final Resource image = resource("/page/jcr:content/image",
                Map.of("customImageAltText", "false", "imagealttext", "Old text"), pageContent);
        assertFalse(AssetAltTextOverrideDetector.hasOverride(new Reference(image, "imagesrc", image), pageContent));
    }

    @Test
    public void mediaTitleOverridesImageWhileBackgroundIsDecorativeByDefault() {
        final Resource pageContent = resource("/page/jcr:content", Map.of(), null);
        final Resource media = resource("/page/jcr:content/media", Map.of("mediatitle", "Old image alt"), pageContent);
        assertTrue(AssetAltTextOverrideDetector.hasOverride(new Reference(media, "imagesrc", media), pageContent));
        assertTrue(AssetAltTextOverrideDetector.hasOverride(new Reference(media, "bgimage", media), pageContent));
        final Resource titledImage = resource("/page/jcr:content/titled", Map.of("title", "Fallback alt"), pageContent);
        assertTrue(AssetAltTextOverrideDetector.hasOverride(new Reference(titledImage, "imagesrc", titledImage), pageContent));
    }

    @Test
    public void pageLevelAltDoesNotOverrideChildImages() {
        final Resource pageContent = resource("/page/jcr:content", Map.of("alt", "Page metadata"), null);
        final Resource image = resource("/page/jcr:content/image", Map.of(), pageContent);
        assertFalse(AssetAltTextOverrideDetector.hasOverride(new Reference(image, "image", image), pageContent));
    }

    @Test
    public void cardLogoOverrideDoesNotHideItsAssetDerivedMainImage() {
        final Resource pageContent = resource("/page/jcr:content", Map.of(), null);
        final Resource card = resource("/page/jcr:content/cards/card1",
                Map.of("logoalttext", "Custom logo", "customImageAltText", "false"), pageContent);
        assertTrue(AssetAltTextOverrideDetector.hasOverride(new Reference(card, "logo", card), pageContent));
        assertFalse(AssetAltTextOverrideDetector.hasOverride(new Reference(card, "image", card), pageContent));
    }

    @Test
    public void backgroundOnlyTracksAssetAltWhenExplicitlyEnabled() {
        final Resource pageContent = resource("/page/jcr:content", Map.of(), null);
        final Resource decorative = resource("/page/jcr:content/decorative",
                Map.of("custombackground", "true", "backgroundtype", "image"), pageContent);
        final Resource assetAlt = resource("/page/jcr:content/asset-alt",
                Map.of("custombackground", "true", "backgroundtype", "image",
                        "useBackgroundImageAltText", "true", "customBackgroundImageAltText", "false"), pageContent);
        final Resource customAlt = resource("/page/jcr:content/custom-alt",
                Map.of("custombackground", "true", "backgroundtype", "image",
                        "useBackgroundImageAltText", "true", "customBackgroundImageAltText", "true"), pageContent);

        assertTrue(AssetAltTextOverrideDetector.hasOverride(new Reference(decorative, "bgimage", decorative), pageContent));
        assertFalse(AssetAltTextOverrideDetector.hasOverride(new Reference(assetAlt, "bgimage", assetAlt), pageContent));
        assertFalse(AssetAltTextOverrideDetector.hasOverride(new Reference(assetAlt, "mobile_imagepath", assetAlt), pageContent));
        assertTrue(AssetAltTextOverrideDetector.hasOverride(new Reference(customAlt, "bgimage", customAlt), pageContent));
        assertTrue(AssetAltTextOverrideDetector.hasOverride(new Reference(customAlt, "mobile_imagepath", customAlt), pageContent));
    }

    private Resource resource(final String path, final Map<String, String> properties, final Resource parent) {
        final Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(path);
        when(resource.getParent()).thenReturn(parent);
        when(resource.getValueMap()).thenReturn(new ValueMapDecorator(new HashMap<>(properties)));
        return resource;
    }
}
