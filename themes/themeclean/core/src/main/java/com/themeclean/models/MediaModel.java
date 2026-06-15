package com.themeclean.models;

import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.IComponent;
import javax.inject.Inject;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;

@Model(
    adaptables = Resource.class,
    resourceType = "themeclean/components/media",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class MediaModel extends AbstractComponent {

    public MediaModel(Resource r) {
        super(r);
    }

    @Inject
    private String text;

    public String getText() {
        return text;
    }
}
