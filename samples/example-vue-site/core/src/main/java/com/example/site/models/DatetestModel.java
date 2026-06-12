package com.example.site.models;

import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.IComponent;
import javax.inject.Inject;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;

@Model(
    adaptables = Resource.class,
    resourceType = "example/objects/datetest",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class DatetestModel extends AbstractComponent {

    public DatetestModel(Resource r) {
        super(r);
    }

    @Inject
    private String description;

    @Inject
    private String start;

    @Inject
    private String end;

    public String getDescription() {
        return description;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }
}
