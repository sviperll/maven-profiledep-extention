/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.sviperll.maven.profiledep;

import com.github.sviperll.maven.profiledep.util.TreeBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.maven.model.Profile;

/**
 *
 * @author vir
 */
class DependencyResolutionContext {
    private final Collection<Profile> availableProfiles;

    DependencyResolutionContext(Collection<Profile> availableProfiles) {
        this.availableProfiles = availableProfiles;
    }
    
    DependencyResolution resolve(List<Profile> activatedProfiles, Collection<String> unresolvedProfileIDs) throws ResolutionException {
        DependencyResolution resolution = new DependencyResolution();
        resolution.unresolvedProfileIDs.addAll(unresolvedProfileIDs);
        resolution.discoveredProfiles.addAll(activatedProfiles);
        return resolution.resolve();
    }

    class DependencyResolution {
        private final List<Profile> activeProfiles = new ArrayList<Profile>();
        private final Map<String, Set<Profile>> activeProfileIDs = new TreeMap<String, Set<Profile>>();
        private final Map<String, Set<Profile>> forbiddenProfileIDs = new TreeMap<String, Set<Profile>>();
        private final Set<String> unresolvedProfileIDs = new TreeSet<String>();
        private final Set<String> unresolvableProfileIDs = new TreeSet<String>();
        private final Set<String> ambigousProfileIDs = new TreeSet<String>();
        private List<Profile> discoveredProfiles = new ArrayList<Profile>();
        
        DependencyResolution() {
        }

        List<Profile> activeProfiles() {
            List<Profile> result = new ArrayList<Profile>();
            result.addAll(activeProfiles);
            return result;
        }

        void addUnresolvedProfileIDs(Collection<String> profileIDs) {
            unresolvedProfileIDs.addAll(profileIDs);
        }

        private DependencyResolution resolve() throws ResolutionException {
            for (;;) {
                List<Profile> profiles = discoveredProfiles;
                discoveredProfiles = new ArrayList<Profile>();
                for (Profile profile: profiles) {
                    activateWithoutProcessing(profile);
                }
                processValidationErrors();
                for (Profile profile: profiles) {
                    collectDependencies(profile);
                }
                processValidationErrors();
                if (unresolvedProfileIDs.isEmpty())
                    break;
                resolveUnambigous();
            }
            return resolveAmbigous();
        }

        private void activateWithoutProcessing(Profile profile) {
            boolean isActivated = false;
            Collection<String> profileIDs = profileIDs(profile);
            for (String profileID: profileIDs) {
                unresolvedProfileIDs.remove(profileID);
                ambigousProfileIDs.remove(profileID);
                unresolvableProfileIDs.remove(profileID);
                Set<Profile> providedBy = activeProfileIDs.get(profileID);
                if (providedBy == null) {
                    providedBy = new HashSet<Profile>();
                    activeProfileIDs.put(profileID, providedBy);
                }
                if (providedBy.contains(profile))
                    isActivated = true;
                providedBy.add(profile);
            }
            if (!isActivated)
                activeProfiles.add(profile);
        }

        private Set<String> profileIDs(Profile profile) {
            Set<String> ids = new TreeSet<String>();
            ids.add(profile.getId());
            String profileprovide = profile.getProperties().getProperty("profileprovide", "").trim();
            if (!profileprovide.isEmpty()) {
                String[] providedIDs = profileprovide.split("[;,]", -1);
                for (String providedID: providedIDs) {
                    providedID = providedID.trim();
                    if (!providedID.isEmpty())
                        ids.add(providedID);
                }
            }
            return ids;
        }

        private void processValidationErrors() throws ResolutionException {
            boolean isError = false;
            TreeBuilder<String> resolutionTreeBuilder = TreeBuilder.createInstance(".");
            for (String profileID: activeProfileIDs.keySet()) {
                try {
                    processValidationErrors(profileID);
                } catch (ResolutionException ex) {
                    isError = true;
                    resolutionTreeBuilder.subtree("Can't provide " + profileID, ex.tree().children());
                }
            }
            if (isError)
                throw new ResolutionException(resolutionTreeBuilder.build());
        }

        private void processValidationErrors(String profileID) throws ResolutionException {
            boolean isError = false;
            TreeBuilder<String> resulutionTreeBuilder = TreeBuilder.createInstance("Can't provide " + profileID);
            Set<Profile> providedBy = activeProfileIDs.get(profileID);
            if (providedBy != null && providedBy.size() > 1) {
                isError = true;
                resulutionTreeBuilder.beginSubtree("more than one profile provides it");
                for (Profile profile: providedBy) {
                    resulutionTreeBuilder.node(profile.getId());
                }
                resulutionTreeBuilder.endSubtree();
            }
            Set<Profile> forbiddenBy = forbiddenProfileIDs.get(profileID);
            if (forbiddenBy != null && !forbiddenBy.isEmpty()) {
                isError = true;
                resulutionTreeBuilder.beginSubtree("it conflicts with some profiles");
                for (Profile profile: forbiddenBy) {
                    resulutionTreeBuilder.node(profile.getId());
                }
            }
            if (isError)
                throw new ResolutionException(resulutionTreeBuilder.build());
        }

        private void collectDependencies(Profile profile) {
            String profiledep = profile.getProperties().getProperty("profiledep", "").trim();
            if (!profiledep.isEmpty()) {
                String[] dependencies = profiledep.split("[,;]", -1);
                for (String dependency: dependencies) {
                    dependency = dependency.trim();
                    if (dependency.startsWith("!")) {
                        dependency = dependency.substring(1).trim();
                        Set<Profile> forbiddenBy = forbiddenProfileIDs.get(dependency);
                        if (forbiddenBy == null) {
                            forbiddenBy = new HashSet<Profile>();
                            forbiddenProfileIDs.put(dependency, forbiddenBy);
                        }
                        forbiddenBy.add(profile);
                    } else {
                        if (!activeProfileIDs.containsKey(dependency)) {
                            unresolvedProfileIDs.add(dependency);
                        }
                    }
                }
            }
        }

        private void resolveUnambigous() throws ResolutionException {
            Set<String> profileIDs = new TreeSet<String>();
            profileIDs.addAll(unresolvedProfileIDs);
            for (String profileID: profileIDs) {
                List<Profile> candidates = new ArrayList<Profile>();
                for (Profile profile: availableProfiles) {
                    Set<String> candidateProfileIDs = profileIDs(profile);
                    if (candidateProfileIDs.contains(profileID)) {
                        candidates.add(profile);
                    }
                }
                if (candidates.isEmpty()) {
                    unresolvableProfileIDs.add(profileID);
                    unresolvedProfileIDs.remove(profileID);
                } else if (candidates.size() > 1) {
                    ambigousProfileIDs.add(profileID);
                    unresolvedProfileIDs.remove(profileID);
                } else {
                    Profile candidate = candidates.get(0);
                    discoveredProfiles.add(candidate);
                }
            }
        }

        private DependencyResolution resolveAmbigous() throws ResolutionException {
            Iterator<String> iterator = ambigousProfileIDs.iterator();
            if (!iterator.hasNext()) {
                return this;
            } else {
                String profileID = iterator.next();
                List<Profile> candidates = new ArrayList<Profile>();
                for (Profile profile: availableProfiles) {
                    Set<String> profileIDs = profileIDs(profile);
                    if (profileIDs.contains(profileID)) {
                        candidates.add(profile);
                    }
                }
                TreeBuilder<String> resolutionTreeBuilder = TreeBuilder.createInstance(".");
                resolutionTreeBuilder.beginSubtree("Can't resolve " + profileID);
                for (Profile profile: candidates) {
                    try {
                        DependencyResolution resolution = createChild(profile);
                        return resolution.resolve();
                    } catch (ResolutionException ex) {
                        resolutionTreeBuilder.subtree(" to " + profile.getId(), ex.tree().children());
                    }
                }
                resolutionTreeBuilder.endSubtree();
                throw new ResolutionException(resolutionTreeBuilder.build());
            }
        }

        protected DependencyResolution createChild(Profile profile) {
            DependencyResolution child = new DependencyResolution();
            for (Map.Entry<String, Set<Profile>> entry: this.activeProfileIDs.entrySet()) {
                Set<Profile> value = new HashSet<Profile>();
                value.addAll(entry.getValue());
                child.activeProfileIDs.put(entry.getKey(), value);
            }
            child.activeProfiles.addAll(this.activeProfiles);
            child.ambigousProfileIDs.addAll(this.ambigousProfileIDs);
            child.discoveredProfiles.addAll(this.discoveredProfiles);
            for (Map.Entry<String, Set<Profile>> entry: this.forbiddenProfileIDs.entrySet()) {
                Set<Profile> value = new HashSet<Profile>();
                value.addAll(entry.getValue());
                child.forbiddenProfileIDs.put(entry.getKey(), value);
            }
            child.unresolvableProfileIDs.addAll(this.unresolvableProfileIDs);
            child.unresolvedProfileIDs.addAll(this.unresolvedProfileIDs);
            child.discoveredProfiles.add(profile);
            return child;
        }
    }
}
