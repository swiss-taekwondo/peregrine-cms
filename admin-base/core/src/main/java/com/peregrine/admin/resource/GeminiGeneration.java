package com.peregrine.admin.resource;

import org.apache.sling.api.resource.ResourceResolver;
import javax.jcr.Node;
import java.util.Map;

public interface GeminiGeneration {
    void generateAndApplyAltTextAndTranslations(Node assetNode, ResourceResolver resolver) throws Exception;
    boolean isAltTextConfigured();
    boolean isTranslationConfigured();
    Map<String, String> getLanguageMap();
    String getOptimizedImageOrigin();
    String generateAltText(byte[] imageBytes, String mimeType, String folderName) throws Exception;
    String[] translateValues(String[] valuesToTranslate, String targetLanguage) throws Exception;
}
