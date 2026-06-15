package com.peregrine.admin.models;

/*-
 * #%L
 * admin base - Core
 * %%
 * Copyright (C) 2017 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */

import static com.peregrine.admin.util.AdminConstants.ACTION_COMPONENT_PATH;
import static com.peregrine.commons.util.PerConstants.JACKSON;
import static com.peregrine.commons.util.PerConstants.JSON;

import com.peregrine.nodetypes.models.AbstractComponent;
import com.peregrine.nodetypes.models.IComponent;
import javax.inject.Inject;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;

@Model(
    adaptables = Resource.class,
    resourceType = ACTION_COMPONENT_PATH,
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL,
    adapters = IComponent.class
)
@Exporter(name = JACKSON, extensions = JSON)
public class ActionModel extends AbstractComponent {

    public ActionModel(Resource r) {
        super(r);
    }

    @Inject
    private String title;

    @Inject
    private String target;

    @Inject
    private String command;

    @Inject
    private String type;

    @Inject
    private String icon;

    @Inject
    private String stateFrom;

    @Inject
    private String stateFromDefault;

    public String getTitle() {
        return title;
    }

    public String getTarget() {
        return target;
    }

    public String getCommand() {
        return command;
    }

    public String getType() {
        return type;
    }

    public String getIcon() {
        return icon;
    }

    public String getStateFrom() {
        return stateFrom;
    }

    public String getStateFromDefault() {
        return stateFromDefault;
    }

    @Inject
    private String classes;

    public String getClasses() {
        return classes;
    }

    @Inject
    private String visibility;

    public String getVisibility() {
        return visibility;
    }
}
