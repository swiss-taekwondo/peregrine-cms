package com.peregrine.replication;

import com.peregrine.reference.Reference;
import org.apache.sling.api.resource.Resource;

/** Keeps legacy authored image alt text out of asset-alt republish results. **/
public final class AssetAltTextOverrideDetector {
    private AssetAltTextOverrideDetector() { }

    public static boolean hasOverride(final Reference reference, final Resource pageContent) {
        if (reference == null || pageContent == null) return false;
        final String property = reference.getPropertyName();
        final Resource propertyResource = reference.getPropertyResource();
        if (propertyResource == null) return false;

        if ("logo".equals(property) && hasText(propertyResource, "logoalttext")) return true;
        if ("icon".equals(property) && hasText(propertyResource, "iconalttext")) return true;
        final org.apache.sling.api.resource.ValueMap values = propertyResource.getValueMap();
        final String resourceType = propertyResource.getResourceType();
        if ("bgimage".equals(property) || (isResponsiveImageProperty(property)
                && ((values.containsKey("custombackground") && values.containsKey("backgroundtype"))
                    || "stkdtheme/components/teaserhorizontal".equals(resourceType)
                    || "stkdtheme/components/teaservertical".equals(resourceType)))) {
            if ("stkdtheme/components/breadcrumb".equals(resourceType)
                    || "stkdtheme/components/separator".equals(resourceType)) return true;
            return !"true".equals(values.get("custombackground", String.class))
                    || !"image".equals(values.get("backgroundtype", String.class))
                    || !"true".equals(values.get("useBackgroundImageAltText", String.class))
                    || "true".equals(values.get("customBackgroundImageAltText", String.class));
        }
        if (!isPrimaryImageProperty(property)) return false;

        Resource current = propertyResource;
        while (current != null) {
            // Page metadata must not act as a custom override for every image below it.
            if (current != propertyResource && pageContent.getPath().equals(current.getPath())) break;
            final Object switchValue = current.getValueMap().get("customImageAltText");
            if (switchValue != null) return Boolean.parseBoolean(switchValue.toString());
            if (hasText(current, "alt", "alttext", "imagealttext", "altText")) return true;
            if ("imagesrc".equals(property) && hasText(current, "mediatitle", "title")) return true;
            if (pageContent.getPath().equals(current.getPath())) break;
            current = current.getParent();
        }
        return false;
    }

    private static boolean isPrimaryImageProperty(final String property) {
        return "image".equals(property) || "imagesrc".equals(property) || "imagepath".equals(property)
                || isResponsiveImageProperty(property);
    }

    private static boolean isResponsiveImageProperty(final String property) {
        return "mobile_imagepath".equals(property) || "mobile_landscape_imagepath".equals(property)
                || "tablet_imagepath".equals(property) || "tablet_landscape_imagepath".equals(property)
                || "laptop_imagepath".equals(property);
    }

    private static boolean hasText(final Resource resource, final String... keys) {
        for (final String key : keys) {
            final String value = resource.getValueMap().get(key, String.class);
            if (value != null && !value.trim().isEmpty()) return true;
        }
        return false;
    }
}
