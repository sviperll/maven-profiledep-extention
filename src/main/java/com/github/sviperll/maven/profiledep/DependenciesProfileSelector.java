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
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.ProfileSelector;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

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
        Set<String> activeProfileIDs = new HashSet<String>();

        List<Profile> discoveredProfiles = new ArrayList<Profile>();
        discoveredProfiles.addAll(defaultProfileSelector.getActiveProfiles(availableProfiles, context, problems));
        Set<String> discoveredProfileIDs = new HashSet<String>();
        for (Profile profile: discoveredProfiles) {
            discoveredProfileIDs.add(profile.getId());
        }
        Set<String> unresolvedProfileIDs = new HashSet<String>();
        while (!discoveredProfiles.isEmpty()) {
            activeProfiles.addAll(discoveredProfiles);
            activeProfileIDs.addAll(discoveredProfileIDs);
            discoveredProfileIDs.clear();
            for (Profile profile: discoveredProfiles) {
                String profiledep = profile.getProperties().getProperty("profiledep", "").trim();
                if (!profiledep.isEmpty()) {
                    String[] dependencies = profiledep.split("[,;]", -1);
                    for (String dependency: dependencies) {
                        dependency = dependency.trim();
                        if (dependency.startsWith("!")) {
                            dependency = dependency.substring(1).trim();
                            if (activeProfileIDs.contains(dependency)) {
                                ModelProblemCollectorRequest request = new ModelProblemCollectorRequest(ModelProblem.Severity.FATAL, ModelProblem.Version.BASE);
                                request.setMessage(profile.getId() + " profile conflicts with " + dependency + ", but both are to be activated");
                                problems.add(request);
                            }
                        } else {
                            if (!activeProfileIDs.contains(dependency)) {
                                discoveredProfileIDs.add(dependency);
                            }
                        }
                    }
                }
            }
            discoveredProfiles.clear();
            unresolvedProfileIDs.clear();
            unresolvedProfileIDs.addAll(discoveredProfileIDs);
            for (String profileID: discoveredProfileIDs) {
                for (Profile anyProfile: availableProfiles) {
                    if (anyProfile.getId().equals(profileID)) {
                        discoveredProfiles.add(anyProfile);
                        unresolvedProfileIDs.remove(profileID);
                    }
                }
            }
            Iterator<String> iterator = unresolvedProfileIDs.iterator();
            if (iterator.hasNext()) {
                StringBuilder message = new StringBuilder();
                message.append("Unresolved profile ids found ");
                message.append(iterator.next());
                while (iterator.hasNext()) {
                    message.append(", ");
                    message.append(iterator.next());
                }
                ModelProblemCollectorRequest request = new ModelProblemCollectorRequest(ModelProblem.Severity.WARNING, ModelProblem.Version.BASE);
                request.setMessage(message.toString());
                problems.add(request);
            }
        }
        return activeProfiles;
    }

}
