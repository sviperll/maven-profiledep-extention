/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.sviperll.maven.profiledep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Profile;

/**
 *
 * @author vir
 */
class DependencyResolver {
    private final Collection<Profile> availableProfiles;
    private final List<Profile> activeProfiles;
    private final Set<String> activeProfileIDs;

    DependencyResolver(Collection<Profile> availableProfiles, List<Profile> activeProfiles, Set<String> activeProfileIDs) {
        this.availableProfiles = availableProfiles;
        this.activeProfiles = activeProfiles;
        this.activeProfileIDs = activeProfileIDs;
    }

    void resolve(List<Profile> discoveredProfiles) throws ResolutionException {
        while (!discoveredProfiles.isEmpty()) {
            activeProfiles.addAll(discoveredProfiles);
            activeProfileIDs.addAll(collectProfileIDs(discoveredProfiles));
            Set<String> discoveredProfileIDs = new HashSet<String>();
            for (Profile profile: discoveredProfiles) {
                discoveredProfileIDs.addAll(discoverDependencies(profile));
            }
            discoveredProfiles = resolveProfileID(discoveredProfileIDs);
        }
    }

    private List<Profile> resolveProfileID(Set<String> profileIDs) throws ResolutionException {
        List<Profile> result = new ArrayList<Profile>();
        Set<String> unresolvedProfileIDs = new HashSet<String>();
        unresolvedProfileIDs.addAll(profileIDs);
        for (String profileID: profileIDs) {
            Profile profile = resolveProfileID(profileID);
            if (profile != null) {
                result.add(profile);
                unresolvedProfileIDs.remove(profileID);
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
            throw new ResolutionException(message.toString());
        }
        return result;
    }

    private Profile resolveProfileID(String profileID) {
        for (Profile anyProfile: availableProfiles) {
            if (anyProfile.getId().equals(profileID)) {
                return anyProfile;
            }
        }
        return null;
    }

    private Set<String> collectProfileIDs(List<Profile> discoveredProfiles) {
        Set<String> discoveredProfileIDs = new HashSet<String>();
        for (Profile profile: discoveredProfiles) {
            discoveredProfileIDs.add(profile.getId());
        }
        return discoveredProfileIDs;
    }

    private List<String> discoverDependencies(Profile profile) throws ResolutionException {
        String profiledep = profile.getProperties().getProperty("profiledep", "").trim();
        if (profiledep.isEmpty())
            return Collections.emptyList();
        else {
            String[] dependencies = profiledep.split("[,;]", -1);
            return discoverDependencies(profile, dependencies);
        }
    }

    private List<String> discoverDependencies(Profile profile, String[] dependencies) throws ResolutionException {
        List<String> result = new ArrayList<String>();
        for (String dependency: dependencies) {
            dependency = dependency.trim();
            if (dependency.startsWith("!")) {
                dependency = dependency.substring(1).trim();
                if (activeProfileIDs.contains(dependency)) {
                    throw new ResolutionException(profile.getId() + " profile conflicts with " + dependency + ", but both are to be activated");
                }
            } else {
                if (!activeProfileIDs.contains(dependency)) {
                    result.add(dependency);
                }
            }
        }
        return result;
    }

}
