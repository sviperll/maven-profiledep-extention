/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.sviperll.maven.profiledep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.ProfileSelector;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 *
 * @author vir
 */
@Component(role = ProfileSelector.class)
public class DependenciesProfileSelector implements ProfileSelector {
    @Requirement(role = ProfileSelector.class)
    List<ProfileSelector> profileSelectors;

    private ProfileSelector defaultProfileSelector;

    private void init() {
        if (defaultProfileSelector == null) {
            for (ProfileSelector profileSelector: profileSelectors) {
                if (profileSelector.getClass() != DependenciesProfileSelector.class) {
                    defaultProfileSelector = profileSelector;
                }
            }
        }
    }

    @Override
    public List<Profile> getActiveProfiles(Collection<Profile> availableProfiles, ProfileActivationContext context, ModelProblemCollector problems) {
        init();
        List<Profile> activeProfiles = new ArrayList<Profile>();
        DependencyResolver resolver = new DependencyResolver(availableProfiles, activeProfiles, new HashSet<String>());
        try {
            resolver.resolve(defaultProfileSelector.getActiveProfiles(availableProfiles, context, problems));
        } catch (ResolutionException ex) {
            ModelProblemCollectorRequest request = new ModelProblemCollectorRequest(ModelProblem.Severity.FATAL, ModelProblem.Version.BASE);
            request.setMessage(ex.getMessage());
            problems.add(request);
        }
        return activeProfiles;
    }

}
