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
    resourceType = "themeclean/components/teaservertical",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class TeaserverticalModel extends AbstractComponent {

    public TeaserverticalModel(Resource r) {
        super(r);
    }

    @Inject
    @Default(values = "center")
    private String aligncontent;

    @Inject
    private String isprimary;

    @Inject
    private String showtitle;

    @Inject
    private String title;

    @Inject
    private String showsubtitle;

    @Inject
    private String subtitle;

    @Inject
    private String showtext;

    @Inject
    private String text;

    @Inject
    @Default(values = "100")
    private String textwidth;

    @Inject
    private String showbutton;

    @Inject
    @Default(values = "center")
    private String alignbuttons;

    @Inject
    @Default(values = "default")
    private String buttonsize;

    @Inject
    private List<IComponent> buttons;

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

    public String getAligncontent() {
        return aligncontent;
    }

    public String getIsprimary() {
        return isprimary;
    }

    public String getShowtitle() {
        return showtitle;
    }

    public String getTitle() {
        return title;
    }

    public String getShowsubtitle() {
        return showsubtitle;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getShowtext() {
        return showtext;
    }

    public String getText() {
        return text;
    }

    public String getTextwidth() {
        return textwidth;
    }

    public String getShowbutton() {
        return showbutton;
    }

    public String getAlignbuttons() {
        return alignbuttons;
    }

    public String getButtonsize() {
        return buttonsize;
    }

    public List<IComponent> getButtons() {
        return buttons;
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
