package com.themeclean.models;

import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.IComponent;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Model(
    adaptables = Resource.class,
    resourceType = "themeclean/components/breadcrumb",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class BreadcrumbModel extends AbstractComponent {

    public BreadcrumbModel(Resource r) {
        super(r);
    }

    @Inject
    private String level;

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
    private String toppadding;

    @Inject
    private String bottompadding;

    public String getLevel() {
        return level;
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

    public String getToppadding() {
        return toppadding;
    }

    public String getBottompadding() {
        return bottompadding;
    }

    private static final Logger LOG = LoggerFactory.getLogger(
        BreadcrumbModel.class
    );

    public List<TextLink> links;

    /* Method to recursively get child page links, given a root page path */
    public List<TextLink> getLinks() {
        links = new ArrayList<TextLink>();
        try {
            if (Integer.parseInt(getLevel()) > 0) {
                Resource page = getRootResource();
                LOG.debug("page: {}", page);
                return getDeepLinks(page != null ? page : getResource());
            } else {
                return null;
            }
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private List<TextLink> getDeepLinks(Resource resource) {
        try {
            ValueMap props = resource.adaptTo(ValueMap.class);
            String resourceType = props.get(
                "jcr:primaryType",
                "type not found"
            );
            // we only care about per:page child
            if (resourceType.equals("per:Page")) {
                if (resource.getChild("index") != null) {
                    // if the page has a sub page called index use that one instead (takes care of the root)
                    Resource index = resource.getChild("index");
                    TextLink link = new TextLink(
                        index.getPath(),
                        getPageTitle(index.getPath())
                    );
                    links.add(0, link);
                } else {
                    TextLink link = new TextLink(
                        resource.getPath(),
                        getPageTitle(resource.getPath())
                    );
                    links.add(0, link);
                }
            }
            // move on to its parent resource
            if (
                resource.getParent() != null &&
                links.size() < Integer.parseInt(getLevel())
            ) {
                getDeepLinks(resource.getParent());
            }
        } catch (Exception e) {
            LOG.error("getDeepLinks error: {}", e);
        }

        return links;
    }

    private String getPageTitle(String pageUrl) {
        try {
            String resourcePath = pageUrl + "/jcr:content";
            ResourceResolver resourceResolver =
                getResource().getResourceResolver();
            ValueMap props = resourceResolver
                .getResource(resourcePath)
                .adaptTo(ValueMap.class);
            return props.get("jcr:title", "title not found");
        } catch (Exception e) {
            LOG.error("getPageTitle error: {}", e);
            return "title not found....";
        }
    }

    private class TextLink {

        public TextLink(String link, String text) {
            this.link = link;
            this.text = text;
        }

        private String link;
        private String text;

        public String getLink() {
            return link;
        }

        public String getText() {
            return text;
        }
    }
}
