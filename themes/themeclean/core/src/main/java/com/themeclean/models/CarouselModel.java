package com.themeclean.models;

import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.IComponent;
import java.util.List;
import javax.inject.Inject;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;

@Model(
    adaptables = Resource.class,
    resourceType = "themeclean/components/carousel",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class CarouselModel extends AbstractComponent {

    public CarouselModel(Resource r) {
        super(r);
    }

    @Inject
    @Default(values = "80")
    private String carouselheight;

    @Inject
    private String autoplay;

    @Inject
    @Default(values = "5")
    private String interval;

    @Inject
    @Default(values = "false")
    private String pause;

    @Inject
    @Default(values = "true")
    private String wrap;

    @Inject
    @Default(values = "true")
    private String indicators;

    @Inject
    @Default(values = "true")
    private String controls;

    @Inject
    @Default(values = "true")
    private String keyboard;

    @Inject
    private String captionbg;

    @Inject
    private List<IComponent> slides;

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

    public String getCarouselheight() {
        return carouselheight;
    }

    public String getAutoplay() {
        return autoplay;
    }

    public String getInterval() {
        return interval;
    }

    public String getPause() {
        return pause;
    }

    public String getWrap() {
        return wrap;
    }

    public String getIndicators() {
        return indicators;
    }

    public String getControls() {
        return controls;
    }

    public String getKeyboard() {
        return keyboard;
    }

    public String getCaptionbg() {
        return captionbg;
    }

    public List<IComponent> getSlides() {
        return slides;
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
}
