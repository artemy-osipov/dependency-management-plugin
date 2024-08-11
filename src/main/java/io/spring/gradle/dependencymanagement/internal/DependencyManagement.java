/*
 * Copyright 2014-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.gradle.dependencymanagement.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.spring.gradle.dependencymanagement.internal.pom.Coordinates;
import io.spring.gradle.dependencymanagement.internal.pom.Dependency;
import io.spring.gradle.dependencymanagement.internal.pom.Pom;
import io.spring.gradle.dependencymanagement.internal.pom.PomReference;
import io.spring.gradle.dependencymanagement.internal.pom.PomResolver;
import io.spring.gradle.dependencymanagement.internal.properties.ProjectPropertySource;
import io.spring.gradle.dependencymanagement.internal.properties.PropertySource;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates dependency management information for a particular configuration in a
 * Gradle project.
 *
 * @author Andy Wilkinson
 */
public class DependencyManagement {

	private static final Logger logger = LoggerFactory.getLogger(DependencyManagement.class);

	private final Project project;

	private final Configuration targetConfiguration;

	private final PomResolver pomResolver;

	private boolean resolved;

	private final Map<String, String> versions = new HashMap<>();

	private final Map<String, String> explicitVersions = new HashMap<>();

	private final Exclusions explicitExclusions = new Exclusions();

	private final Exclusions allExclusions = new Exclusions();

	private final Map<String, String> bomProperties = new HashMap<>();

	private final List<PomReference> importedBoms = new ArrayList<>();

	DependencyManagement(Project project, PomResolver pomResolver) {
		this(project, null, pomResolver);
	}

	DependencyManagement(Project project, Configuration targetConfiguration, PomResolver pomResolver) {
		this.project = project;
		this.pomResolver = pomResolver;
		this.targetConfiguration = targetConfiguration;
	}

	void importBom(Coordinates coordinates, PropertySource properties) {
		this.importedBoms.add(new PomReference(coordinates, properties));
	}

	/**
	 * Returns the imported bom references.
	 * @return the imported bom references
	 */
	public List<PomReference> getImportedBomReferences() {
		return Collections.unmodifiableList(this.importedBoms);
	}

	Map<String, String> getImportedProperties() {
		resolveIfNecessary();
		return this.bomProperties;
	}

	void addImplicitManagedVersion(String group, String name, String version) {
		this.versions.put(createKey(group, name), version);
	}

	void addExplicitManagedVersion(String group, String name, String version, List<Exclusion> exclusions) {
		String key = createKey(group, name);
		this.explicitVersions.put(key, version);
		this.explicitExclusions.add(key, exclusions);
		this.allExclusions.add(key, exclusions);
		addImplicitManagedVersion(group, name, version);
	}

	String getManagedVersion(String group, String name) {
		resolveIfNecessary();
		return this.versions.get(createKey(group, name));
	}

	Map<String, String> getManagedVersions() {
		resolveIfNecessary();
		return new HashMap<>(this.versions);
	}

	/**
	 * Returns the managed dependencies.
	 * @return the managed dependencies
	 */
	public List<Dependency> getManagedDependencies() {
		List<Dependency> managedDependencies = new ArrayList<>();
		for (Map.Entry<String, String> entry : this.explicitVersions.entrySet()) {
			String[] components = entry.getKey().split(":");
			managedDependencies.add(new Dependency(new Coordinates(components[0], components[1], entry.getValue()),
					this.explicitExclusions.exclusionsForDependency(entry.getKey())));
		}
		return managedDependencies;
	}

	/**
	 * Returns the resolved boms.
	 * @param propertySource the property source
	 * @return the resolved boms
	 */
	public List<Pom> getResolvedBoms(PropertySource propertySource) {
		return this.pomResolver.resolvePoms(this.importedBoms, propertySource);
	}

	private String createKey(String group, String name) {
		return group + ":" + name;
	}

	Exclusions getExclusions() {
		resolveIfNecessary();
		return this.allExclusions;
	}

	private void resolveIfNecessary() {
		if (this.importedBoms.isEmpty() || this.resolved) {
			return;
		}
		try {
			this.resolved = true;
			resolve();
		}
		catch (Exception ex) {
			throw new GradleException("Failed to resolve imported Maven boms: " + getRootCause(ex).getMessage(), ex);
		}
	}

	private Throwable getRootCause(Exception ex) {
		Throwable candidate = ex;
		while (candidate.getCause() != null) {
			candidate = candidate.getCause();
		}
		return candidate;
	}

	private void resolve() {
		String projectName = this.project.getName();
		if (this.targetConfiguration != null) {
			logger.info("Resolving dependency management for configuration '{}' of project '{}'",
					this.targetConfiguration.getName(), projectName);
		}
		else {
			logger.info("Resolving global dependency management for project '{}'", projectName);
		}
		Map<String, String> existingVersions = new LinkedHashMap<>(this.versions);
		logger.debug("Preserving existing versions: {}", existingVersions);
		for (Pom resolvedBom : getResolvedBoms(new ProjectPropertySource(this.project))) {
			for (Dependency dependency : resolvedBom.getManagedDependencies()) {
				resolve(resolvedBom, dependency);
			}
			this.bomProperties.putAll(resolvedBom.getProperties());
		}
		this.versions.putAll(existingVersions);
	}

	private void resolve(Pom resolvedBom, Dependency dependency) {
		if (isEmpty(dependency.getClassifier())) {
			Coordinates coordinates = dependency.getCoordinates();
			if (isEmpty(coordinates.getVersion())) {
				logger.warn("Dependency management for {} in bom {} has no version and will be ignored.",
						coordinates.getGroupAndArtifactId(), resolvedBom.getCoordinates());
				return;
			}
			this.versions.put(coordinates.getGroupAndArtifactId(), coordinates.getVersion());
			this.allExclusions.add(coordinates.getGroupAndArtifactId(), dependency.getExclusions());
		}
	}

	private boolean isEmpty(String string) {
		return string == null || string.trim().length() == 0;
	}

}
