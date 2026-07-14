package com.themeclean.models;

import com.peregrine.model.api.ImageInfo;
import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.IComponent;
import java.awt.Dimension;
import javax.inject.Inject;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;

@Model(
    adaptables = Resource.class,
    resourceType = "themeclean/components/mediablock",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class MediablockModel extends AbstractComponent {

    public MediablockModel(Resource r) {
        super(r);
    }

    @Inject
    private String mediatype;

    @Inject
    private String mediaicon;

    @Inject
    @Default(values = "50")
    private String mediaiconsize;

    @Inject
    @Default(values = "#000000")
    private String mediaiconcolor;

    @Inject
    private String imagesrc;

    @Inject
    private String mediaalttext;

    @Inject
    private String videosrc;

    @Inject
    @Default(values = "100")
    private String mediawidth;

    @Inject
    private String anchorname;

    @Inject
    @Default(values = "")
    private String colorscheme;

    @Inject
    @Default(values = "false")
    private String custombackground;

    @Inject
    private String backgroundtype;

    @Inject
    @Default(values = "https://www.youtube.com/embed/Ju86mknumYM")
    private String bgvideo;

    @Inject
    private String bgimage;

    @Inject
    private String overlay;

    @Inject
    @Default(values = "#ffffff")
    private String overlaycolor;

    @Inject
    @Default(values = "50")
    private String overlayopacity;

    @Inject
    @Default(values = "#ffffff")
    private String bgcolor;

    @Inject
    @Default(values = "#c0c0c0")
    private String color2;

    @Inject
    private String fullwidth;

    @Inject
    private String fullheight;

    @Inject
    private String toppadding;

    @Inject
    private String bottompadding;

    @Inject
    @ImageInfo(name = "imagesrc")
    private Dimension imageinfo;

    public String getMediatype() {
        return mediatype;
    }

    public String getMediaicon() {
        return mediaicon;
    }

    public String getMediaiconsize() {
        return mediaiconsize;
    }

    public String getMediaiconcolor() {
        return mediaiconcolor;
    }

    public String getImagesrc() {
        return imagesrc;
    }

    public String getMediaalttext() {
        return mediaalttext;
    }

    public String getVideosrc() {
        return videosrc;
    }

    public String getMediawidth() {
        return mediawidth;
    }

    public String getAnchorname() {
        return anchorname;
    }

    public String getColorscheme() {
        return colorscheme;
    }

    public String getCustombackground() {
        return custombackground;
    }

    public String getBackgroundtype() {
        return backgroundtype;
    }

    public String getBgvideo() {
        return bgvideo;
    }

    public String getBgimage() {
        return bgimage;
    }

    public String getOverlay() {
        return overlay;
    }

    public String getOverlaycolor() {
        return overlaycolor;
    }

    public String getOverlayopacity() {
        return overlayopacity;
    }

    public String getBgcolor() {
        return bgcolor;
    }

    public String getColor2() {
        return color2;
    }

    public String getFullwidth() {
        return fullwidth;
    }

    public String getFullheight() {
        return fullheight;
    }

    public String getToppadding() {
        return toppadding;
    }

    public String getBottompadding() {
        return bottompadding;
    }

    public Dimension getImageinfo() {
        return imageinfo;
    }
}
