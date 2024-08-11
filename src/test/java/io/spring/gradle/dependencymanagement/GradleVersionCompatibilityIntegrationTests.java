/*
 * Copyright 2014-2024 the original author or authors.
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

package io.spring.gradle.dependencymanagement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the plugin's compatibility with different versions of Gradle.
 *
 * @author Andy Wilkinson
 */
class GradleVersionCompatibilityIntegrationTests {

	@RegisterExtension
	private final GradleBuild gradleBuild = new GradleBuild();

	@ParameterizedTest(name = "{displayName} (Gradle {0})")
	@MethodSource("gradleVersions")
	void pluginIsCompatible(String gradleVersion) {
		BuildResult result = this.gradleBuild.runner()
			.withGradleVersion(gradleVersion)
			.withArguments("resolve")
			.build();
		assertThat(result.task(":resolve").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
	}

	@ParameterizedTest(name = "{displayName} (Gradle {0})")
	@MethodSource("gradleVersions")
	void buildDoesNotProduceDeprecationWarnings(String gradleVersion) throws IOException {
		File projectDir = this.gradleBuild.runner().getProjectDir();
		File javaSource = new File(projectDir, "src/main/java/com/example/Main.java");
		javaSource.getParentFile().mkdirs();
		javaSource.createNewFile();
		BuildResult result = this.gradleBuild.runner().withGradleVersion(gradleVersion).withArguments("build").build();
		assertThat(result.getOutput()).doesNotContainIgnoringCase("deprecated");
		assertThat(result.task(":build").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
	}

	static List<String[]> gradleVersions() {
		List<String> versions = Arrays.asList("6.8.3", "6.9.4", "7.0.2", "7.1.1", "7.2", "7.3.3", "7.4.2", "7.5.1",
				"8.0.2", "8.1.1", "8.2.1", "8.3", "8.4", "8.5", "8.6", "8.7", "8.8", "8.9");
		List<String[]> result = new ArrayList<>();
		for (String version : versions) {
			result.add(new String[] { version });
		}
		return result;
	}

}
