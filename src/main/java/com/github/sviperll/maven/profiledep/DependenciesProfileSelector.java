/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.sviperll.maven.profiledep;

import com.github.sviperll.maven.profiledep.DependencyResolution;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
        List<Profile> activatedProfiles = defaultProfileSelector.getActiveProfiles(availableProfiles, context, problems);
        try {
            DependencyResolution resolution = DependencyResolution.resolve(availableProfiles, activatedProfiles, context.getActiveProfileIds());
            return resolution.activeProfiles();
        } catch (ResolutionValidationException ex) {
            ModelProblemCollectorRequest request = new ModelProblemCollectorRequest(ModelProblem.Severity.FATAL, ModelProblem.Version.BASE);
            request.setMessage("\n" + ex.renderResolutionTree());
            problems.add(request);
            return Collections.emptyList();
        }
    }
}
