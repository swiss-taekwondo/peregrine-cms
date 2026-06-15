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
    resourceType = "themeclean/components/socialicons",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class SocialiconsModel extends AbstractComponent {

    public SocialiconsModel(Resource r) {
        super(r);
    }

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
}
