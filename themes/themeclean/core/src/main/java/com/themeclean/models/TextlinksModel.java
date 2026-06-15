package com.themeclean.models;

import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.IComponent;
import java.util.List;
import javax.inject.Inject;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;

@Model(
    adaptables = Resource.class,
    resourceType = "themeclean/components/textlinks",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class TextlinksModel extends AbstractComponent {

    public TextlinksModel(Resource r) {
        super(r);
    }

    @Inject
    private List<IComponent> links;

    public List<IComponent> getLinks() {
        return links;
    }
}
