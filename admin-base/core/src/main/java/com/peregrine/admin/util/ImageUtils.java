package com.peregrine.admin.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

public final class ImageUtils {

    public static final long MAX_IMAGE_BYTES = 7L * 1024 * 1024;
    public static final int MAX_IMAGE_DIMENSION = 640;

    private ImageUtils() {
        // Utility class
    }

    public static final class AnalyzedImage {
        public final byte[] bytes;
        public final String mimeType;
        public final int width;
        public final int height;

        public AnalyzedImage(byte[] bytes, String mimeType, int width, int height) {
            this.bytes = bytes;
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
        }
    }

    public static AnalyzedImage prepareUploadedImage(byte[] bytes, String mimeType) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return new AnalyzedImage(bytes, mimeType, 0, 0);

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return new AnalyzedImage(bytes, mimeType, 0, 0);

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                int subsampling = Math.max(1, (int) Math.ceil((double) Math.max(sourceWidth, sourceHeight) / MAX_IMAGE_DIMENSION));

                if (subsampling == 1) {
                    return new AnalyzedImage(bytes, mimeType, sourceWidth, sourceHeight);
                }

                ImageReadParam readParam = reader.getDefaultReadParam();
                readParam.setSourceSubsampling(subsampling, subsampling, 0, 0);
                BufferedImage image = reader.read(0, readParam);

                if (image == null) {
                    return new AnalyzedImage(bytes, mimeType, sourceWidth, sourceHeight);
                }

                int imageType = "image/png".equals(mimeType) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
                BufferedImage resized = new BufferedImage(image.getWidth(), image.getHeight(), imageType);
                Graphics2D graphics = resized.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.drawImage(image, 0, 0, null);
                } finally {
                    graphics.dispose();
                }

                String format = "image/png".equals(mimeType) ? "png" : "jpeg";
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(resized, format, output)) {
                        return new AnalyzedImage(bytes, mimeType, sourceWidth, sourceHeight);
                    }
                    return new AnalyzedImage(output.toByteArray(), "png".equals(format) ? "image/png" : "image/jpeg", resized.getWidth(), resized.getHeight());
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
