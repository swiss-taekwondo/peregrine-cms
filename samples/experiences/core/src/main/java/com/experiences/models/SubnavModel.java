package com.experiences.models;

import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.Container;
import com.peregrine.nodetypes.models.IComponent;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;

@Model(
    adaptables = Resource.class,
    resourceType = "experiences/components/subnav",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class SubnavModel extends AbstractComponent {

    public SubnavModel(Resource r) {
        super(r);
    }

    @Inject
    private String text;

    public String getText() {
        return text;
    }
}
