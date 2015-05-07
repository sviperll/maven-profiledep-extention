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
        List<Profile> newActiveProfiles = new ArrayList<Profile>();
        Map<String, Set<Profile>> newActiveProfileIDs = new TreeMap<String, Set<Profile>>();
        Map<String, Set<Profile>> newForbiddenProfileIDs = new TreeMap<String, Set<Profile>>();
        Set<String> newUnresolvedProfileIDs = new TreeSet<String>();
        newUnresolvedProfileIDs.addAll(unresolvedProfileIDs);
        DependencyResolution resolution = new DependencyResolution(0, newActiveProfiles, newActiveProfileIDs, newForbiddenProfileIDs, newUnresolvedProfileIDs);
        for (Profile profile: activatedProfiles) {
            resolution = resolution.resolve(profile);
        }
        return resolution;
    }

    class DependencyResolution {
        private final List<Profile> activeProfiles;
        private final Map<String, Set<Profile>> activeProfileIDs;
        private final Map<String, Set<Profile>> forbiddenProfileIDs;
        private final Collection<String> unresolvedProfileIDs;
        private final int indent;
        
        DependencyResolution(int indent, List<Profile> activeProfiles, Map<String, Set<Profile>> activeProfileIDs, Map<String, Set<Profile>> forbiddenProfileIDs, Collection<String> unresolvedProfileIDs) {
            this.indent = indent;
            this.activeProfiles = activeProfiles;
            this.activeProfileIDs = activeProfileIDs;
            this.forbiddenProfileIDs = forbiddenProfileIDs;
            this.unresolvedProfileIDs = unresolvedProfileIDs;
        }

        List<Profile> activeProfiles() {
            List<Profile> result = new ArrayList<Profile>();
            result.addAll(activeProfiles);
            return result;
        }

        void addUnresolvedProfileIDs(Collection<String> profileIDs) {
            unresolvedProfileIDs.addAll(profileIDs);
        }

        private DependencyResolution resolve(Profile discoveredProfile) throws ResolutionException {
            activateWithoutProcessing(discoveredProfile);
            processValidationErrors();
            collectDependencies(discoveredProfile);
            processValidationErrors();
            return resolveUnresolved();
        }

        private void activateWithoutProcessing(Profile profile) {
            boolean isActivated = false;
            Collection<String> profileIDs = profileIDs(profile);
            for (String profileID: profileIDs) {
                unresolvedProfileIDs.remove(profileID);
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
            IndentationStringBuilder message = new IndentationStringBuilder();
            for (String profileID: activeProfileIDs.keySet()) {
                IndentationStringBuilder problem = new IndentationStringBuilder();
                boolean currentProfileIDHasError = false;
                Set<Profile> providedBy = activeProfileIDs.get(profileID);
                if (providedBy != null && providedBy.size() > 1) {
                    currentProfileIDHasError = true;
                    Iterator<Profile> iterator = providedBy.iterator();
                    if (iterator.hasNext()) {
                        problem.startNewLine(indent + 1);
                        problem.append(" * more than one profile provides it: ");
                        problem.append(iterator.next().getId());
                        while (iterator.hasNext()) {
                            problem.append(", ");
                            problem.append(iterator.next().getId());
                        }
                    }
                }
                Set<Profile> forbiddenBy = forbiddenProfileIDs.get(profileID);
                if (forbiddenBy != null && !forbiddenBy.isEmpty()) {
                    if (currentProfileIDHasError) {
                        problem.append(",");
                    }
                    currentProfileIDHasError = true;
                    Iterator<Profile> iterator = forbiddenBy.iterator();
                    if (iterator.hasNext()) {
                        problem.startNewLine(indent + 1);
                        problem.append(" * it conflicts with some profiles: ");
                        problem.append(iterator.next().getId());
                        while (iterator.hasNext()) {
                            problem.append(", ");
                            problem.append(iterator.next().getId());
                        }
                    }
                }
                if (currentProfileIDHasError) {
                    if (isError) {
                        message.append(";");
                    }
                    isError = true;
                    message.startNewLine(indent);
                    message.append("Can't provide ").append(profileID).append(": ");
                    message.append(problem.toString());
                }
            }
            if (isError)
                throw new ResolutionException(message.toString());
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

        private DependencyResolution resolveUnresolved() throws ResolutionException {
            Iterator<String> iterator = unresolvedProfileIDs.iterator();
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
                if (candidates.isEmpty()) {
                    return this;
                } else if (candidates.size() == 1) {
                    return resolve(candidates.get(0));
                } else {
                    IndentationStringBuilder message = new IndentationStringBuilder();
                    message.startNewLine(indent);
                    message.append("Can't resolve ").append(profileID);
                    for (Profile profile: candidates) {
                        try {
                            DependencyResolution resolution = createChild();
                            return resolution.resolve(profile);
                        } catch (ResolutionException ex) {
                            message.startNewLine(indent + 1);
                            message.append(" to ").append(profile.getId()).append(":\n").append(ex.getMessage());
                        }
                    }
                    throw new ResolutionException(message.toString());
                }
            }
        }

        protected DependencyResolution createChild() {
            List<Profile> newActiveProfiles = new ArrayList<Profile>();
            newActiveProfiles.addAll(this.activeProfiles);
            Map<String, Set<Profile>> newActiveProfileIDs = new TreeMap<String, Set<Profile>>();
            for (Map.Entry<String, Set<Profile>> entry: this.activeProfileIDs.entrySet()) {
                Set<Profile> value = new HashSet<Profile>();
                value.addAll(entry.getValue());
                newActiveProfileIDs.put(entry.getKey(), value);
            }
            Map<String, Set<Profile>> newForbiddenProfileIDs = new TreeMap<String, Set<Profile>>();
            for (Map.Entry<String, Set<Profile>> entry: this.forbiddenProfileIDs.entrySet()) {
                Set<Profile> value = new HashSet<Profile>();
                value.addAll(entry.getValue());
                newForbiddenProfileIDs.put(entry.getKey(), value);
            }
            Set<String> newUnresolvedProfileIDs = new TreeSet<String>();
            newUnresolvedProfileIDs.addAll(this.unresolvedProfileIDs);
            return new DependencyResolution(indent + 2, newActiveProfiles, newActiveProfileIDs, newForbiddenProfileIDs, newUnresolvedProfileIDs);
        }
    }
}
