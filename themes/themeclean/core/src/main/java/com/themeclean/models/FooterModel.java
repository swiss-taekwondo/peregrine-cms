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
    resourceType = "themeclean/components/footer",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class FooterModel extends AbstractComponent {

    public FooterModel(Resource r) {
        super(r);
    }

    @Inject
    private String showlogo;

    @Inject
    private String logo;

    @Inject
    private String logoalttext;

    @Inject
    private String logourl;

    @Inject
    private String logosize;

    @Inject
    private List<IComponent> columns;

    @Inject
    private String copyright;

    @Inject
    private String iconcustomcolor;

    @Inject
    @Default(values = "#000000")
    private String iconcolor;

    @Inject
    @Default(values = "25")
    private String iconsize;

    @Inject
    private List<IComponent> icons;

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

    public String getShowlogo() {
        return showlogo;
    }

    public String getLogo() {
        return logo;
    }

    public String getLogoalttext() {
        return logoalttext;
    }

    public String getLogourl() {
        return logourl;
    }

    public String getLogosize() {
        return logosize;
    }

    public List<IComponent> getColumns() {
        return columns;
    }

    public String getCopyright() {
        return copyright;
    }

    public String getIconcustomcolor() {
        return iconcustomcolor;
    }

    public String getIconcolor() {
        return iconcolor;
    }

    public String getIconsize() {
        return iconsize;
    }

    public List<IComponent> getIcons() {
        return icons;
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
