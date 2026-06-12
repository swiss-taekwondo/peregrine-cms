package com.themeclean.models;

import com.peregrine.adaption.PerPage;
import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.IComponent;
import javax.inject.Inject;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Model(
    adaptables = Resource.class,
    resourceType = "themeclean/components/articlepager",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class ArticlepagerModel extends AbstractComponent {

    private static final Logger LOG = LoggerFactory.getLogger(
        ArticlepagerModel.class
    );

    public ArticlepagerModel(Resource r) {
        super(r);
    }

    @Inject
    private String prevlabel;

    @Inject
    private String nextlabel;

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

    public String getPrevlabel() {
        return prevlabel;
    }

    public String getNextlabel() {
        return nextlabel;
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

    public String getPrevious() {
        Resource res = getCurrentPage(getRootResource());
        LOG.debug("resource: {}", res);
        if (res == null) res = getCurrentPage(getResource());
        PerPage page = res.adaptTo(PerPage.class);
        if (page == null) return "not adaptable";
        PerPage prev = page.getPrevious();
        return prev != null ? prev.getPath() : "unknown";
    }

    public String getNext() {
        Resource res = getCurrentPage(getRootResource());
        if (res == null) res = getCurrentPage(getResource());
        PerPage page = res.adaptTo(PerPage.class);
        if (page == null) return "not adaptable";
        PerPage next = page.getNext();
        return next != null ? next.getPath() : "unknown";
    }

    private Resource getCurrentPage(Resource resource) {
        if (resource == null) {
            return null;
        }
        String resourceType = null;
        try {
            ValueMap props = resource.adaptTo(ValueMap.class);
            resourceType = props.get("jcr:primaryType", "type not found");
            LOG.debug(
                "resource type is: " +
                    resourceType +
                    "  path is:" +
                    resource.getPath()
            );
            // we only care about per:page node
            if ("per:Page".equals(resourceType)) {
                LOG.debug(
                    "returned resource type is: " +
                        resourceType +
                        "  path is:" +
                        resource.getPath()
                );
                return resource;
            } else {
                if (resource.getParent() != null) {
                    return getCurrentPage(resource.getParent());
                }
            }
        } catch (Exception e) {
            LOG.error("Exception: " + e);
        }
        return null;
    }
}
