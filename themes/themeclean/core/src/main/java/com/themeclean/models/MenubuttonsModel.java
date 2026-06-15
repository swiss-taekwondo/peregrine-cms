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
    resourceType = "themeclean/components/menubuttons",
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = "jackson", extensions = "json")
public class MenubuttonsModel extends AbstractComponent {

    public MenubuttonsModel(Resource r) {
        super(r);
    }

    @Inject
    @Default(values = "default")
    private String buttonsize;

    @Inject
    private List<IComponent> buttons;

    public String getButtonsize() {
        return buttonsize;
    }

    public List<IComponent> getButtons() {
        return buttons;
    }
}
